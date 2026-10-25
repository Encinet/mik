package org.encinet.mik.feature.world.elevator;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class IronElevatorControllerTest {
    @Test
    void firstHintHasFinalColorsAndPrecedesTheCompleteCatalogAndTerrainSampling() {
        Harness test = new Harness(8);
        test.controller.observe(test.player, test.location, false);
        assertTrue(test.hints.isEmpty());
        assertEquals(0, test.world.terrainReads);
        assertTrue(test.world.blockReads < 5, "movement must not scan a shaft");

        test.until(() -> !test.hints.isEmpty(), 20);

        assertEquals(List.of(new Hint(true, true)), test.hints);
        assertEquals(0, test.panels);
        assertEquals(0, test.world.terrainReads);
        assertNull(test.floors.cached(test.world.block(0, 40, 0)));
        test.until(() -> test.panels > 0, 400);
        assertTrue(test.world.terrainReads > 0);
    }

    @Test
    void queuesTheFirstJumpAndTravelsBeforeTheFullShaftIsReady() {
        Harness test = new Harness(8);
        assertTrue(test.request(1));
        assertTrue(test.controller.pending(test.id));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(81, test.location.getY());
        assertEquals(0, test.world.terrainReads);
        assertEquals(1, test.trips);
        assertFalse(test.request(-1), "cooldown applies after a successful trip");
    }

    @Test
    void keepsOnlyTheLatestDirectionAndSupportsExplicitFloorSelection() {
        Harness test = new Harness(2);
        assertTrue(test.request(1));
        assertTrue(test.request(-1));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(21, test.location.getY());

        test.clock.addAndGet(600_000_000L);
        assertTrue(test.controller.request(test.player, test.location, 0, 80, () -> true));
        test.until(() -> test.teleports == 2, 20);
        assertEquals(81, test.location.getY());
    }

    @Test
    void discardsRequestsWhenThePlayerLeavesOrTheEventIsCancelled() {
        Harness leaving = new Harness(8);
        assertTrue(leaving.request(1));
        leaving.location.setX(20);
        leaving.controller.observe(leaving.player, leaving.location, false);
        leaving.advance(30);
        assertEquals(0, leaving.teleports);
        assertTrue(leaving.hints.isEmpty());
        assertFalse(leaving.controller.pending(leaving.id));

        Harness cancelled = new Harness(2);
        AtomicBoolean allowed = new AtomicBoolean(true);
        assertTrue(cancelled.controller.request(cancelled.player, cancelled.location, 1, null, allowed::get));
        allowed.set(false);
        cancelled.advance(30);
        assertEquals(0, cancelled.teleports);
        assertFalse(cancelled.controller.pending(cancelled.id));
    }

    @Test
    void expiresOldInputsAndRechecksHeadroomOnTheActualTeleportTick() {
        Harness expired = new Harness(8);
        assertTrue(expired.request(1));
        expired.clock.addAndGet(2_000_000_000L);
        expired.advance(30);
        assertEquals(0, expired.teleports);
        assertFalse(expired.controller.pending(expired.id));

        Harness blocked = new Harness(2);
        assertTrue(blocked.request(1));
        blocked.until(() -> !blocked.next.isEmpty(), 20);
        blocked.world.put(0, 81, 0, Material.STONE, Set.of(BlockFace.UP));
        blocked.tick();
        assertEquals(0, blocked.teleports);
        assertEquals(0, blocked.trips);
    }

    @Test
    void invalidationQuitAndDisablePreventLateResultsAndTeleports() {
        Harness changed = new Harness(8);
        assertTrue(changed.request(1));
        changed.controller.invalidate(changed.world.worldId);
        changed.advance(30);
        assertEquals(0, changed.teleports);

        Harness quitting = new Harness(2);
        assertTrue(quitting.request(1));
        quitting.until(() -> !quitting.next.isEmpty(), 20);
        quitting.controller.quit(quitting.id);
        quitting.advance(30);
        assertEquals(0, quitting.teleports);

        Harness disabled = new Harness(2);
        assertTrue(disabled.request(1));
        disabled.controller.invalidate(disabled.world.worldId);
        disabled.controller.clear();
        int reads = disabled.world.blockReads;
        disabled.advance(30);
        assertEquals(reads, disabled.world.blockReads);
        assertTrue(disabled.hints.isEmpty());
        assertEquals(0, disabled.teleports);
    }

    @Test
    void ordinaryIronWithNoDestinationDoesNotKeepSwallowingJumpInputs() {
        Harness test = new Harness(1);
        test.world.put(0, 20, 0, Material.AIR, Set.of());
        test.world.put(0, 80, 0, Material.AIR, Set.of());
        test.controller.observe(test.player, test.location, false);
        test.advance(20);
        assertTrue(test.hints.isEmpty());
        assertFalse(test.request(1));
        assertFalse(test.request(-1));
    }

    @Test
    void unrelatedConstructionAndChunkUnloadsDoNotCancelAnAcceptedInput() {
        Harness test = new Harness(8);
        assertTrue(test.request(1));
        test.controller.invalidate(test.world.block(1000, 80, 1000));
        test.controller.invalidateChunk(test.world.worldId, 100, 100);
        assertTrue(test.controller.pending(test.id));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(81, test.location.getY());
    }

    @Test
    void unloadingTheSupportChunkNeverReadsItOrExecutesThePendingTrip() {
        Harness test = new Harness(8);
        assertTrue(test.request(1));
        test.controller.invalidateChunk(test.world.worldId, 0, 0);
        test.world.unloadedChunks.add("0,0");
        test.advance(20);
        assertEquals(0, test.teleports);
        assertTrue(test.hints.isEmpty());
    }

    @Test
    void movingWithinThePlatformRecomputesAvailabilityBeforeTheFirstHint() {
        Harness test = new Harness(2);
        test.controller.observe(test.player, test.location, false);
        test.location.setX(1.5);
        test.world.put(1, 81, 0, Material.STONE, Set.of(BlockFace.UP));
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);
        assertEquals(new Hint(false, true), test.hints.getFirst());
    }

    @Test
    void expiryRebuildsSourceGeometryAfterAnExternalEditWithoutEvents() {
        Harness test = new Harness(2);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);
        test.until(() -> test.panels > 0, 50);
        // An oversized connected source platform is no longer an elevator.
        for (int x = 2; x < 70; x++)
            test.world.put(x, 40, 0, Material.IRON_BLOCK, Set.of(BlockFace.UP));
        test.clock.addAndGet(2_100_000_000L);
        test.controller.observe(test.player, test.location, true);
        int oldHints = test.hints.size();
        test.advance(20);
        assertEquals(oldHints, test.hints.size());
        assertFalse(test.controller.pending(test.id));
        assertEquals(0, test.teleports);
    }

    private record Hint(boolean up, boolean down) { }

    private static final class Harness implements IronElevatorController.Presentation {
        final IronElevatorTestWorld world = new IronElevatorTestWorld();
        final AtomicLong clock = new AtomicLong();
        final IronElevatorFloors floors = new IronElevatorFloors(clock::get);
        final UUID id = UUID.randomUUID();
        final List<Runnable> next = new ArrayList<>();
        final List<Hint> hints = new ArrayList<>();
        final Player player;
        final IronElevatorController controller;
        Location location;
        Vector velocity = new Vector(0.1, 0, 0);
        float fall = 3;
        int panels;
        int teleports;
        int trips;

        Harness(int width) {
            for (int y : List.of(20, 40, 80))
                for (int x = 0; x < width; x++)
                    for (int z = 0; z < width; z++)
                        world.put(x, y, z, Material.IRON_BLOCK, Set.of(BlockFace.UP));
            location = world.origin(40);
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> world.world;
                        case "getLocation" -> location.clone();
                        case "isOnline", "isValid" -> true;
                        case "isDead", "isFlying", "isGliding", "isSwimming", "isInsideVehicle",
                                "isLeashed", "wouldCollideUsing" -> false;
                        case "getGameMode" -> GameMode.SURVIVAL;
                        case "getVehicle" -> null;
                        case "getPassengers" -> List.of();
                        case "getBoundingBox" -> new BoundingBox(location.getX() - 0.3, location.getY(), location.getZ() - 0.3,
                                location.getX() + 0.3, location.getY() + 1.8, location.getZ() + 0.3);
                        case "getVelocity" -> velocity.clone();
                        case "setVelocity" -> { velocity = ((Vector) args[0]).clone(); yield null; }
                        case "getFallDistance" -> fall;
                        case "setFallDistance" -> { fall = (float) args[0]; yield null; }
                        case "teleport" -> { location = ((Location) args[0]).clone(); teleports++; yield true; }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            world.entities.add(player);
            var queue = new IronElevatorWorkQueue(() -> 0, error -> fail(error));
            controller = new IronElevatorController(floors, this, next::add,
                    query -> query.equals(id) ? player : null, message -> fail(message), clock::get, queue);
        }

        boolean request(int direction) {
            return controller.request(player, location, direction, null, () -> true);
        }

        void tick() {
            var callbacks = List.copyOf(next);
            next.clear();
            callbacks.forEach(Runnable::run);
            controller.tick();
        }

        void advance(int ticks) { for (int tick = 0; tick < ticks; tick++) tick(); }

        void until(BooleanSupplier condition, int limit) {
            for (int tick = 0; tick < limit && !condition.getAsBoolean(); tick++) tick();
            assertTrue(condition.getAsBoolean(), "condition did not complete within " + limit + " ticks");
        }

        @Override public void hint(Player player, boolean up, boolean down) { hints.add(new Hint(up, down)); }
        @Override public void clear(Player player) { }
        @Override public void forget(UUID player) { }
        @Override public void travelled(Player player, Location departure) { trips++; }
        @Override public void panel(Player player, IronElevatorPlatform source, IronElevatorTravelGroup group,
                                    List<IronElevatorFloors.Floor> floors) { panels++; }
    }
}
