package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.runtime.FloatingMenuTestWorld;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MenuSpaceRefitsTest {
    private static final Vector FORWARD = new Vector(0, 0, 1);
    private static final Location ORIGIN = new Location(null, 0, 0, 0);

    @Test void planningAndSolvingAreOffThreadWhileCaptureIsBoundedAndReusable() throws Exception {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0.5, 64.5, 0.5);
        var main = Thread.currentThread();
        var coverage = new BoundingBox(-3, -3, -.1, 3, 3, 6);
        var viewer = UUID.randomUUID();
        try (var refits = new MenuSpaceRefits()) {
            Supplier<MenuSpaceRefits.Plan<Boolean>> plan = () -> {
                assertNotSame(main, Thread.currentThread());
                return new MenuSpaceRefits.Plan<>(coverage, space -> {
                    assertNotSame(main, Thread.currentThread());
                    return Double.isInfinite(space.firstHit(ORIGIN, FORWARD, 5));
                });
            };
            var initial = await(refits, refits.request(viewer, eye, null, plan), world);
            assertEquals(MenuSpaceRefits.Status.COMPLETE, initial.status());
            assertTrue(initial.value());
            int reads = world.blockReads();
            assertTrue(reads > MenuSpaceRefits.BLOCKS_PER_TICK);
            var next = await(refits, refits.request(viewer, eye, initial.evidence(), plan), world);
            assertTrue(next.value());
            assertEquals(reads, world.blockReads(), "A valid covering terrain survey must not be read again");
        }
    }

    @Test void anExpiredModelSampleCanHoldOnlyTheVerifiedFrameDuringARefit() throws Exception {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), .5, 64.5, .5);
        var pose = new MenuSpaceRefits.Pose(eye, FORWARD);
        try (var refits = new MenuSpaceRefits()) {
            var result = await(refits, refits.request(UUID.randomUUID(), pose, null, null, _ -> forwardRay()), world);
            assertEquals(MenuSpaceRefits.Status.COMPLETE, result.status());
            var evidence = result.evidence();
            while (evidence.fresh(refits.tick())) refits.sample(_ -> null, _ -> List.of());

            assertTrue(evidence.canHoldPublishedFrame(refits.tick(), result.pose(), eye, FORWARD));
            assertFalse(evidence.canHoldPublishedFrame(refits.tick(), null, eye, FORWARD));
            assertFalse(evidence.canHoldPublishedFrame(refits.tick(), result.pose(), eye.clone().add(.01, 0, 0), FORWARD));
            assertFalse(evidence.canHoldPublishedFrame(refits.tick(), result.pose(), eye, new Vector(1, 0, 0)));
            assertFalse(evidence.canHoldPublishedFrame(refits.tick() + MenuSpaceRefits.PUBLISHED_FRAME_TICKS, result.pose(), eye, FORWARD));

            world.block(0, 64, 1, new BoundingBox(0, 0, 0, 1, 1, 1));
            assertTrue(evidence.invalidate(new Location(world.world(), 0, 64, 1)));
            assertFalse(evidence.canHoldPublishedFrame(refits.tick(), result.pose(), eye, FORWARD));
        }
    }

    @Test void anInFlightTerrainChangeDiscardsTheFitInsteadOfPublishingOldCollisionData() throws Exception {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), .5, 64.5, .5);
        var viewer = UUID.randomUUID();
        try (var refits = new MenuSpaceRefits()) {
            var old = refits.request(viewer, eye, null, MenuSpaceRefitsTest::forwardRay);
            until(() -> {
                refits.sample(_ -> null, _ -> List.of());
                return world.blockReads() > 0;
            });
            world.block(0, 64, 1, new BoundingBox(0, 0, 0, 1, 1, 1));
            refits.invalidate(new Location(world.world(), 0, 64, 1));
            assertEquals(MenuSpaceRefits.Status.STALE, refits.poll(old).status());
            var fresh = await(refits, refits.request(viewer, eye, null, MenuSpaceRefitsTest::forwardRay), world);
            assertEquals(.5, fresh.value(), 1e-9);
        }
    }

    @Test void aMovingReaderUsesTheLatestPoseWithoutMovingAnAlreadySolvedAnchor() throws Exception {
        var world = new FloatingMenuTestWorld();
        world.block(0, 64, 2, new BoundingBox(0, 0, 0, 1, 1, 1));
        var eye = new Location(world.world(), .5, 64.5, .5);
        var old = new MenuSpaceRefits.Pose(eye, FORWARD);
        var latest = new AtomicReference<>(old);
        try (var refits = new MenuSpaceRefits()) {
            var ticket = refits.request(UUID.randomUUID(), old, null, latest::get, _ -> forwardRay());
            latest.set(new MenuSpaceRefits.Pose(eye.clone().add(0, 0, .75), FORWARD));
            var fit = await(refits, ticket, world);
            assertEquals(latest.get(), fit.pose());
            assertEquals(.75, fit.value(), 1e-9, "The worker must test the wall relative to the actual solved eye");
            assertFalse(MenuSpaceRefits.poseClose(fit.pose().eye(), FORWARD, eye.clone().add(8, 0, 0), FORWARD));
        }
    }

    @Test void unloadedChunksAreSolidAndOversizeRequestsFailWithoutReadingBlocks() throws Exception {
        var world = new FloatingMenuTestWorld();
        world.loaded((_, chunkZ) -> chunkZ == 0);
        var eye = new Location(world.world(), .5, 64.5, 15.5);
        var viewer = UUID.randomUUID();
        try (var refits = new MenuSpaceRefits()) {
            var boundary = await(refits, refits.request(viewer, eye, null, MenuSpaceRefitsTest::forwardRay), world);
            assertEquals(.5, boundary.value(), 1e-9);
            assertEquals(0, world.rayReads(), "The indexed solve must not call Bukkit ray tracing");
            int reads = world.blockReads();
            var oversized = refits.request(viewer, eye, null, () -> new MenuSpaceRefits.Plan<>(
                    new BoundingBox(-128, -128, -128, 128, 128, 128), _ -> fail("An uncovered envelope must not be solved")));
            assertEquals(MenuSpaceRefits.Status.OUTSIDE_BUDGET, await(refits, oversized, world).status());
            assertEquals(reads, world.blockReads());
        }
    }

    @Test void aCompletedFitCannotPublishAfterItsSampledChunkUnloads() throws Exception {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), .5, 64.5, 15.5);
        var solving = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var refits = new MenuSpaceRefits()) {
            var request = refits.request(UUID.randomUUID(), eye, null, () -> new MenuSpaceRefits.Plan<Double>(forwardRay().region(), space -> {
                solving.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { throw new AssertionError(error); }
                return space.firstHit(ORIGIN, FORWARD, 3);
            }));
            until(() -> {
                refits.sample(_ -> null, _ -> List.of());
                return solving.getCount() == 0;
            });
            world.loaded((_, chunkZ) -> chunkZ == 0);
            release.countDown();
            var result = new AtomicReference<MenuSpaceRefits.Result<Double>>();
            until(() -> { result.set(refits.poll(request)); return result.get() != null; });
            assertEquals(MenuSpaceRefits.Status.STALE, result.get().status());
        }
    }

    @Test void oversizedReadingFramesContractBeforeSamplingAndKeepRealObstacleDistances() throws Exception {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), .5, 64.5, .5);
        world.block(0, 64, 2, new BoundingBox(0, 0, 0, 1, 1, 1));
        var region = new BoundingBox(-2000, -2000, 0, 2000, 2000, 2000);
        try (var refits = new MenuSpaceRefits()) {
            var result = await(refits, refits.request(UUID.randomUUID(), eye, null, () ->
                    MenuSpaceRefits.Plan.bounded(region, (space, scale) -> space.firstHit(ORIGIN, FORWARD, 2000) * scale,
                            () -> fail("A uniformly contracted region should be usable"))), world);
            assertEquals(MenuSpaceRefits.Status.COMPLETE, result.status());
            assertEquals(1.5, result.value(), 1e-9);
            assertTrue(world.blockReads() <= 4096);
        }
    }

    @Test void previewsCannotFillReservedSlotsAndSupersededPlansNeverRun() throws Exception {
        var world = new FloatingMenuTestWorld();
        var pose = new MenuSpaceRefits.Pose(new Location(world.world(), .5, 64.5, .5), FORWARD);
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var planned = new LinkedBlockingQueue<MenuSpaceRefits.Priority>();
        var replaced = new AtomicInteger();
        try (var refits = new MenuSpaceRefits()) {
            assertNotNull(refits.request(key(), UUID.randomUUID(), MenuSpaceRefits.Priority.INTERACTIVE, pose, null, null, _ -> {
                entered.countDown();
                awaitLatch(release);
                return forwardRay();
            }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var viewer = UUID.randomUUID();
            var replaceable = key();
            var old = refits.request(replaceable, viewer, MenuSpaceRefits.Priority.PREVIEW, pose, null, null, _ -> {
                replaced.incrementAndGet();
                return forwardRay();
            });
            assertNotNull(old);
            for (int index = 1; index < MenuSpaceRefits.MAX_REQUESTS - 8; index++)
                assertNotNull(enqueue(refits, pose, MenuSpaceRefits.Priority.PREVIEW, planned));
            assertNull(enqueue(refits, pose, MenuSpaceRefits.Priority.PREVIEW, planned));
            for (int index = 0; index < 4; index++) assertNotNull(enqueue(refits, pose, MenuSpaceRefits.Priority.VERIFY, planned));
            assertNull(enqueue(refits, pose, MenuSpaceRefits.Priority.VERIFY, planned));
            for (int index = 0; index < 3; index++) assertNotNull(enqueue(refits, pose, MenuSpaceRefits.Priority.INTERACTIVE, planned));
            assertNull(enqueue(refits, pose, MenuSpaceRefits.Priority.INTERACTIVE, planned));
            var latest = refits.request(replaceable, viewer, MenuSpaceRefits.Priority.INTERACTIVE, pose, null, null, _ -> forwardRay());
            assertNotNull(latest, "Replacing a preview with confirmation must not require a second slot");
            assertEquals(MenuSpaceRefits.Status.STALE, refits.poll(old).status());
            release.countDown();
            for (int index = 0; index < 3; index++) assertEquals(MenuSpaceRefits.Priority.INTERACTIVE, planned.poll(5, TimeUnit.SECONDS));
            for (int index = 0; index < 4; index++) assertEquals(MenuSpaceRefits.Priority.VERIFY, planned.poll(5, TimeUnit.SECONDS));
            assertEquals(MenuSpaceRefits.Priority.PREVIEW, planned.poll(5, TimeUnit.SECONDS));
            assertEquals(0, replaced.get());
        }
    }

    @Test void aBusySolverDoesNotAllowEntityCapturesToPileUp() throws Exception {
        var world = new FloatingMenuTestWorld();
        var viewer = mock(org.bukkit.entity.Player.class);
        when(viewer.getWorld()).thenReturn(world.world());
        var pose = new MenuSpaceRefits.Pose(new Location(world.world(), .5, 64.5, .5), FORWARD);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var planned = new CountDownLatch(6);
        var captures = new AtomicInteger();
        try (var refits = new MenuSpaceRefits()) {
            for (int index = 0; index < 6; index++) {
                assertNotNull(refits.request(key(), UUID.randomUUID(), MenuSpaceRefits.Priority.INTERACTIVE, pose, null, null, _ -> {
                    planned.countDown();
                    return new MenuSpaceRefits.Plan<>(new BoundingBox(-.1, -.1, -.1, .1, .1, .1), _ -> {
                        entered.countDown();
                        awaitLatch(release);
                        return true;
                    });
                }));
            }
            assertTrue(planned.await(5, TimeUnit.SECONDS));
            java.util.function.Function<org.bukkit.entity.Player, java.util.Collection<org.bukkit.entity.Entity>> models = _ -> {
                captures.incrementAndGet();
                return List.of();
            };
            until(() -> {
                refits.sample(_ -> viewer, models);
                return captures.get() > 0;
            });
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            for (int index = 0; index < 3; index++) refits.sample(_ -> viewer, models);
            assertEquals(2, captures.get(), "There is room for the active solve and one queued snapshot, not the whole request queue");
            release.countDown();
        }
    }

    @Test void theCenteredSurveyEndsEarlyOrExpandsWithoutReadingItsCellsTwice() throws Exception {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), .5, 64.5, .5);
        var full = new BoundingBox(-4, -1, -.1, 4, 1, 6);
        var prefix = new BoundingBox(-1, -1, -.1, 1, 1, 2);
        var viewer = UUID.randomUUID();
        try (var refits = new MenuSpaceRefits()) {
            var centered = await(refits, refits.request(viewer, eye, null, () -> new MenuSpaceRefits.Plan<>(full,
                    _ -> fail("A successful probe must not search alternatives"), new MenuSpaceRefits.Probe<>(prefix, _ -> 1.0))), world);
            int centeredReads = world.blockReads();
            assertEquals(1.0, centered.value());
            assertFalse(centered.evidence().clear(eye, full), "The fast path must not authorize unsampled space");

            world.block(0, 64, 4, new BoundingBox(0, 0, 0, 1, 1, 1));
            var ray = (java.util.function.Function<MenuCollisionSpace, Double>) space -> space.firstHit(ORIGIN, FORWARD, 5);
            var expanded = await(refits, refits.request(viewer, eye, null, () -> new MenuSpaceRefits.Plan<>(full, ray,
                    new MenuSpaceRefits.Probe<>(prefix, _ -> null))), world);
            int expandedReads = world.blockReads() - centeredReads;
            int beforeFull = world.blockReads();
            var withoutProbe = await(refits, refits.request(viewer, eye, null, () -> new MenuSpaceRefits.Plan<>(full, ray)), world);
            assertEquals(3.5, expanded.value(), 1e-9);
            assertEquals(withoutProbe.value(), expanded.value());
            assertEquals(world.blockReads() - beforeFull, expandedReads, "Expanding must not re-read the prefix");
            assertTrue(centeredReads < expandedReads);
        }
    }

    private static MenuSpaceRefits.Key key() {
        return new MenuSpaceRefits.Key(UUID.randomUUID(), MenuSpaceRefits.Surface.ANCHOR);
    }

    private static MenuSpaceRefits.Ticket<Double> enqueue(MenuSpaceRefits refits, MenuSpaceRefits.Pose pose,
            MenuSpaceRefits.Priority priority, LinkedBlockingQueue<MenuSpaceRefits.Priority> planned) {
        return refits.request(key(), UUID.randomUUID(), priority, pose, null, null, _ -> {
            planned.add(priority);
            return forwardRay();
        });
    }

    private static void awaitLatch(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException cancelled) { Thread.currentThread().interrupt(); }
    }

    private static MenuSpaceRefits.Plan<Double> forwardRay() {
        return new MenuSpaceRefits.Plan<>(new BoundingBox(-1, -1, -.1, 1, 1, 3), space -> space.firstHit(ORIGIN, FORWARD, 2.5));
    }

    private static <T> MenuSpaceRefits.Result<T> await(MenuSpaceRefits refits, MenuSpaceRefits.Ticket<T> ticket,
                                                       FloatingMenuTestWorld world) throws Exception {
        assertNotNull(ticket);
        var result = new AtomicReference<MenuSpaceRefits.Result<T>>();
        until(() -> {
            int before = world.blockReads();
            refits.sample(_ -> null, _ -> List.of());
            assertTrue(world.blockReads() - before <= MenuSpaceRefits.BLOCKS_PER_TICK);
            result.set(refits.poll(ticket));
            return result.get() != null;
        });
        return result.get();
    }

    private static void until(Supplier<Boolean> done) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() - deadline < 0) {
            if (done.get()) return;
            Thread.sleep(2);
        }
        fail("The geometry request did not finish");
    }
}
