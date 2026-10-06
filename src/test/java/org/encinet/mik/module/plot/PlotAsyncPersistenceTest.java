package org.encinet.mik.module.plot;

import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PlotAsyncPersistenceTest {
    @TempDir Path directory;
    private final UUID world = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final BlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<>();

    @Test
    void atomicInvitationPublishesOneCompleteRevisionBeforeBackgroundPersistence() throws Exception {
        try (PlotRepository store = open("atomic-invitation")) {
            PlotRegistry registry = new PlotRegistry(store);
            PlotEdits edits = new PlotEdits(registry);
            Plot original = edits.create(owner, "Invite", world, shape(0, 3));
            registry.enableAsyncWrites(callbacks::add);
            UUID guest = UUID.randomUUID();
            PlotMemberDraft draft = new PlotMemberDraft(guest, Plot.Role.COLLABORATOR,
                    Map.of(PlotPermission.PLACE, false, PlotPermission.MANAGE_MEMBERS, true));
            PlotAccessPlan plan = edits.planAccess(original, draft.request());
            long revision = registry.revision();
            assertFalse(registry.byId(original.id()).members().containsKey(guest));
            CompletableFuture<Void> saved = edits.writeBehind(staged -> {
                staged.applyAccess(plan, owner, false);
                return null;
            });
            assertTrue(saved.isDone());
            assertEquals(revision + 1, registry.revision());
            Plot accepted = registry.byId(original.id());
            assertEquals(plan.after(), accepted);
            assertEquals(Plot.Role.COLLABORATOR, accepted.members().get(guest));
            assertFalse(accepted.allows("place", guest, false, false));
            assertTrue(accepted.allows("manage_members", guest, false, false));
            assertFalse(original.members().containsKey(guest));
            drain(store);
            assertEquals(accepted, store.load().getFirst());
        }
    }

    @Test
    void geometryPreparationRunsOffThreadAndPublicationDoesNotWaitForPersistence() throws Exception {
        try (var store = open("publication")) {
            var registry = async(store);
            Thread server = Thread.currentThread();
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var worker = new AtomicReference<Thread>();
            long revision = registry.revision();
            CompletableFuture<Plot> saved;
            try {
                saved = registry.writeAsync(staged -> {
                    worker.set(Thread.currentThread());
                    entered.countDown();
                    await(release);
                    return new PlotEdits(staged).create(owner, "Async", world, shape(0, 15));
                });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertNotEquals(server, worker.get());
                assertEquals("mik-plot-prepare", worker.get().getName());
                assertTrue(registry.saving());
                assertFalse(saved.isDone());
                assertTrue(registry.all().isEmpty());
                assertNull(registry.at(world, 0, 64, 0));
                assertEquals(revision, registry.revision());
            } finally { release.countDown(); }
            Runnable complete = callback();
            assertFalse(saved.isDone());
            assertTrue(registry.all().isEmpty());
            complete.run();
            Plot plot = saved.get(5, TimeUnit.SECONDS);
            assertSame(plot, registry.byId(plot.id()));
            assertEquals(plot, registry.at(world, 15, 67, 15));
            assertTrue(registry.revision() > revision);
            assertFalse(registry.saving());
            drain(store);
            assertEquals(plot, store.load().getFirst());
        }
    }

    @Test
    void aRealSqliteWriteLockDoesNotBlockTheCallerOrExistingProtectionReads() throws Exception {
        Path path = directory.resolve("locked.db");
        try (var store = new PlotRepository(path)) {
            store.open();
            var registry = new PlotRegistry(store);
            Plot existing = new PlotEdits(registry).create(owner, "Existing", world, shape(-16, -1));
            registry.enableAsyncWrites(callbacks::add);
            var prepared = new CountDownLatch(1);
            try (Connection lock = DriverManager.getConnection("jdbc:sqlite:" + path);
                 var statement = lock.createStatement()) {
                statement.execute("BEGIN IMMEDIATE");
                CompletableFuture<Plot> saved;
                try {
                    saved = assertTimeout(Duration.ofMillis(500), () -> registry.writeAsync(staged -> {
                        Plot plot = new PlotEdits(staged).create(owner, "Full height", world,
                                PlotSelectionShape.of(new PlotPosition(world, 0, -64, 0),
                                        new PlotPosition(world, 127, 319, 127)));
                        prepared.countDown();
                        return plot;
                    }));
                    assertTrue(prepared.await(5, TimeUnit.SECONDS));
                    callback().run();
                    Plot created = saved.get(5, TimeUnit.SECONDS);
                    assertTimeout(Duration.ofMillis(500), () -> {
                        for (int read = 0; read < 1000; read++) {
                            assertSame(existing, registry.at(world, -1, 64, -1));
                            assertSame(created, registry.at(world, 0, 64, 0));
                        }
                    });
                    assertTrue(saved.isDone());
                } finally { statement.execute("COMMIT"); }
                Plot created = saved.get(5, TimeUnit.SECONDS);
                assertEquals(98_304, created.cells().size());
                assertSame(created, registry.at(world, 127, 319, 127));
                assertNull(registry.at(world, 128, 319, 127));
                drain(store);
            }
        }
    }

    @Test
    void failedDiskBatchRollsBackAndRetriesWithoutRevertingAcceptedCreation() throws Exception {
        try (var store = open("rollback")) {
            trigger("rollback", "CREATE TRIGGER fail_cells BEFORE INSERT ON plot_cells WHEN NEW.x=16 "
                    + "BEGIN SELECT RAISE(ABORT,'test failure after a complete batch'); END");
            var registry = async(store);
            var rolledBack = new CountDownLatch(1);
            observeRollback(store, rolledBack);
            var selections = new PlotSelectionState();
            UUID player = UUID.randomUUID();
            selections.bind(player, null, world);
            selections.set(player, new PlotSelection(new PlotPosition(world, 0, 64, 0),
                    new PlotPosition(world, 127, 67, 127)));
            var submitted = selections.submission(player, null, world);
            var saved = new PlotEdits(registry).writeAsync(staged -> staged.create(owner, "Fail", world, submitted.shape()));
            saved.thenAccept(plot -> selections.clearIfMatches(player, null, submitted));
            callback().run();
            Plot accepted = saved.join();
            assertTrue(rolledBack.await(5, TimeUnit.SECONDS));
            assertFalse(registry.saving());
            assertSame(accepted, registry.byId(accepted.id()));
            assertNull(selections.first(player));
            assertNull(selections.second(player));
            assertEquals(0, rows("rollback", "plots"));
            assertEquals(0, rows("rollback", "plot_cells"));
            trigger("rollback", "DROP TRIGGER fail_cells");
            drain(store);
            assertEquals(accepted, store.load().getFirst());
            assertEquals(1024, accepted.cells().size());
        }
    }

    @Test
    void failedResizePersistenceKeepsNewLogicalCoverageAndRetriesAtomically() throws Exception {
        try (var store = open("resize")) {
            var registry = new PlotRegistry(store);
            Plot original = new PlotEdits(registry).create(owner, "Original", world, shape(0, 3));
            long revision = registry.revision();
            trigger("resize", "CREATE TRIGGER fail_cells BEFORE INSERT ON plot_cells WHEN NEW.x=4 "
                    + "BEGIN SELECT RAISE(ABORT,'test resize failure'); END");
            registry.enableAsyncWrites(callbacks::add);
            var rolledBack = new CountDownLatch(1);
            observeRollback(store, rolledBack);
            var changed = new PlotEdits(registry).writeAsync(staged -> {
                staged.resize(original, owner, false, shape(0, 31));
                return null;
            });
            callback().run();
            changed.join();
            assertTrue(rolledBack.await(5, TimeUnit.SECONDS));
            Plot accepted = registry.byId(original.id());
            assertSame(accepted, registry.at(world, 4, 64, 0));
            assertTrue(registry.revision() > revision);
            assertEquals(1, rows("resize", "plot_cells"));
            trigger("resize", "DROP TRIGGER fail_cells");
            drain(store);
            assertEquals(accepted, store.load().getFirst());
        }
    }

    @Test
    void metadataArrivalMembershipAndDeletionUseTheSameAtomicAsyncPath() throws Exception {
        try (var store = open("metadata")) {
            var registry = new PlotRegistry(store);
            Plot original = new PlotEdits(registry).create(owner, "Original", world, shape(0, 15));
            registry.enableAsyncWrites(callbacks::add);
            UUID guest = UUID.randomUUID();
            var arrival = new PlotArrival(1, 65, 1, 0, 0, "Title", "Subtitle");
            var changed = registry.writeBehind(staged -> {
                staged.rename(original.id(), "Renamed");
                staged.setNoticeBoard(original.id(), "Notice");
                staged.setAtmosphere(original.id(), PlotAtmosphere.WORLD.withTime(6000));
                staged.setArrival(original.id(), arrival);
                new PlotEdits(staged).invite(staged.byId(original.id()), owner, false, guest, Plot.Role.COLLABORATOR);
                return staged.byId(original.id());
            });
            assertTrue(changed.isDone());
            assertTrue(callbacks.isEmpty());
            assertEquals("Renamed", changed.join().name());
            assertEquals(Plot.Role.COLLABORATOR, registry.byId(original.id()).members().get(guest));
            assertEquals("Notice", registry.noticeBoard(original.id()));
            assertEquals(arrival, registry.arrival(original.id()));
            assertEquals(6000, registry.atmosphere(original.id()).timeTicks());
            drain(store);
            assertEquals(changed.join(), store.load().getFirst());
            assertEquals("Notice", store.loadNoticeBoards().get(original.id()));
            assertEquals(arrival, store.loadArrivals().get(original.id()));
            var removed = registry.writeAsync(staged -> { staged.remove(staged.byId(original.id())); return null; });
            callback().run();
            removed.join();
            assertNull(registry.at(world, 0, 64, 0));
            drain(store);
            assertTrue(store.load().isEmpty());
            assertTrue(store.loadArrivals().isEmpty());
            assertTrue(store.loadNoticeBoards().isEmpty());
            assertTrue(store.loadAtmospheres().isEmpty());
        }
    }

    @Test
    void successfulCommitDoesNotDiscardANewerDraftOrLoseNegativeCoordinateHoles() throws Exception {
        try (var store = open("draft")) {
            var registry = async(store);
            var selections = new PlotSelectionState();
            UUID player = UUID.randomUUID();
            selections.bind(player, null, world);
            Set<Cell> cells = Set.of(new Cell(-3, 16, -2), new Cell(-2, 16, -2), new Cell(-1, 16, -2),
                    new Cell(-3, 16, -1), new Cell(-1, 16, -1), new Cell(-3, 16, 0),
                    new Cell(-2, 16, 0), new Cell(-1, 16, 0));
            selections.initialize(player, world, cells);
            var submitted = selections.submission(player, null, world);
            var saved = new PlotEdits(registry).writeAsync(staged -> staged.create(owner, "Courtyard", world, submitted.shape()));
            saved.thenAccept(plot -> selections.clearIfMatches(player, null, submitted));
            selections.mark(player, true, new PlotPosition(world, 100, 64, 100));
            long current = selections.draft(player, null).revision();
            callback().run();
            Plot plot = saved.join();
            assertEquals(current, selections.draft(player, null).revision());
            assertEquals(cells, plot.cells());
            assertSame(plot, registry.at(world, -12, 64, -8));
            assertNull(registry.at(world, -8, 64, -4));
            drain(store);
            assertEquals(cells, store.load().getFirst().cells());
        }
    }

    @Test
    void shutdownDrainsAcknowledgedChangesButCancelsUnpublishedPreparations() throws Exception {
        Path path = directory.resolve("shutdown.db");
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        AtomicReference<UUID> id = new AtomicReference<>();
        CompletableFuture<Plot> saved;
        try (var store = new PlotRepository(path)) {
            store.open();
            var registry = new PlotRegistry(store);
            Plot existing = new PlotEdits(registry).create(owner, "Existing", world, shape(-16, -1));
            registry.enableAsyncWrites(callbacks::add);
            assertTrue(registry.writeBehind(staged -> { staged.rename(existing.id(), "Accepted"); return null; }).isDone());
            try {
                saved = registry.writeAsync(staged -> {
                    entered.countDown();
                    await(release);
                    Plot plot = new PlotEdits(staged).create(owner, "Shutdown", world, shape(0, 15));
                    id.set(plot.id());
                    return plot;
                });
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                registry.stopWrites();
                assertTrue(saved.isCancelled());
                assertThrows(PlotProblem.class, () -> registry.writeAsync(staged -> null));
            } finally { release.countDown(); }
        }
        assertTrue(callbacks.isEmpty());
        try (var reloaded = new PlotRepository(path)) {
            reloaded.open();
            assertEquals(1, reloaded.load().size());
            assertEquals("Accepted", reloaded.load().getFirst().name());
            assertNotEquals(id.get(), reloaded.load().getFirst().id());
        }
    }

    @Test
    void runtimeSynchronousWritesAndReloadsFailFastInsteadOfAccessingSqlite() throws Exception {
        try (var store = open("guard")) {
            var registry = new PlotRegistry(store);
            Plot plot = new PlotEdits(registry).create(owner, "Guard", world, shape(0, 3));
            registry.enableAsyncWrites(callbacks::add);
            assertThrows(SQLException.class, () -> registry.put(plot));
            assertThrows(SQLException.class, () -> registry.rename(plot.id(), "Unsafe"));
            assertThrows(SQLException.class, () -> registry.remove(plot));
            assertThrows(SQLException.class, () -> registry.setNoticeBoard(plot.id(), "Unsafe"));
            assertThrows(SQLException.class, () -> registry.setAtmosphere(plot.id(), PlotAtmosphere.WORLD));
            assertThrows(SQLException.class, () -> registry.setArrival(plot.id(), new PlotArrival(1, 65, 1, 0, 0, "", "")));
            assertThrows(SQLException.class, () -> registry.clearArrival(plot.id()));
            assertThrows(SQLException.class, registry::load);
            assertSame(plot, registry.byId(plot.id()));
            assertEquals(plot, store.load().getFirst());
        }
    }

    @Test
    void parentAndChildCanBeCommittedTogetherWithoutExposingAnIncompleteHierarchy() throws Exception {
        try (var store = open("hierarchy")) {
            var registry = async(store);
            var saved = registry.writeAsync(staged -> {
                var edits = new PlotEdits(staged);
                Plot parent = edits.create(owner, "Parent", world, shape(0, 15));
                return edits.createSubPlot(parent, owner, false, owner, "Child", shape(0, 3));
            });
            Runnable publish = callback();
            assertTrue(registry.all().isEmpty());
            publish.run();
            Plot child = saved.join();
            assertNotNull(registry.parentOf(child));
            assertSame(child, registry.at(world, 1, 65, 1));
            assertSame(registry.parentOf(child), registry.at(world, 15, 65, 15));
            drain(store);
            assertEquals(2, store.load().size());
            assertEquals(1, rows("hierarchy", "plot_children"));
        }
    }

    @Test
    void staleResizeConfirmationIsRejectedAgainstTheLatestCommittedSnapshot() throws Exception {
        try (var store = open("stale")) {
            var registry = new PlotRegistry(store);
            Plot original = new PlotEdits(registry).create(owner, "Original", world, shape(0, 3));
            registry.enableAsyncWrites(callbacks::add);
            var first = new PlotEdits(registry).writeAsync(staged -> {
                staged.resize(original, owner, false, shape(0, 15));
                return null;
            });
            callback().run();
            first.join();
            Plot latest = registry.byId(original.id());
            var stale = new PlotEdits(registry).writeAsync(staged -> {
                staged.resize(original, owner, false, shape(0, 31));
                return null;
            });
            callback().run();
            Throwable error = assertThrows(CompletionException.class, stale::join).getCause();
            assertEquals(Message.PLOT_AREA_CHANGED, assertInstanceOf(PlotProblem.class, error).message());
            assertSame(latest, registry.byId(original.id()));
            assertNull(registry.at(world, 16, 64, 16));
            drain(store);
            assertEquals(latest, store.load().getFirst());
        }
    }

    @Test
    void aQueuedCommitCallbackCannotPublishOrRunSuccessActionsAfterShutdown() throws Exception {
        try (var store = open("late-callback")) {
            var registry = async(store);
            AtomicInteger success = new AtomicInteger();
            var saved = new PlotEdits(registry).writeAsync(staged -> staged.create(owner, "Late", world, shape(0, 3)));
            saved.thenAccept(plot -> success.incrementAndGet());
            Runnable publish = callback();
            registry.stopWrites();
            publish.run();
            assertTrue(saved.isCancelled());
            assertEquals(0, success.get());
            assertTrue(registry.all().isEmpty());
            assertEquals(0, store.load().size());
        }
    }

    @Test
    void nativeJdbcCellBatchesAreBoundedSortedAndNeverExecutedOnTheCallerThread() throws Exception {
        try (var store = open("batches")) {
            var field = PlotRepository.class.getDeclaredField("connection");
            field.setAccessible(true);
            Connection original = (Connection) field.get(store);
            Thread caller = Thread.currentThread();
            AtomicInteger maximum = new AtomicInteger();
            AtomicInteger executions = new AtomicInteger();
            Connection tracked = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                        if (Set.of("prepareStatement", "setAutoCommit", "commit", "rollback").contains(method.getName()))
                            assertNotSame(caller, Thread.currentThread());
                        Object value;
                        try { value = method.invoke(original, arguments); }
                        catch (InvocationTargetException error) { throw error.getCause(); }
                        if (value instanceof PreparedStatement statement && ((String) arguments[0]).contains("plot_cells"))
                            return track(statement, maximum, executions);
                        return value;
                    });
            field.set(store, tracked);
            var registry = async(store);
            var saved = new PlotEdits(registry).writeAsync(staged -> staged.create(owner, "Batches", world,
                    PlotSelectionShape.of(new PlotPosition(world, 0, -64, 0), new PlotPosition(world, 127, 319, 127))));
            callback().run();
            Plot plot = saved.join();
            assertEquals(98_304, plot.cells().size());
            drain(store);
            assertEquals(PlotRepository.BATCH_SIZE, maximum.get());
            assertEquals(192, executions.get());
            var resized = new PlotEdits(registry).writeAsync(staged -> {
                staged.resize(plot, owner, false,
                        PlotSelectionShape.of(new PlotPosition(world, 64, -64, 0), new PlotPosition(world, 191, 319, 127)));
                return null;
            });
            callback().run();
            resized.join();
            assertNull(registry.at(world, 0, 64, 0));
            assertNotNull(registry.at(world, 191, 319, 127));
            drain(store);
            assertEquals(PlotRepository.BATCH_SIZE, maximum.get());
            assertEquals(384, executions.get());
        }
    }

    @Test
    void permissionClicksImmediatelyPublishOrderedResultsEvenWhileSqliteIsLocked() throws Exception {
        Path path = directory.resolve("instant.db");
        try (var store = new PlotRepository(path)) {
            store.open();
            var registry = new PlotRegistry(store);
            Plot original = new PlotEdits(registry).create(owner, "Instant", world, shape(0, 15));
            registry.enableAsyncWrites(callbacks::add);
            var edits = new PlotEdits(registry);
            var subject = PlotAccessPolicy.Subject.group(PlotAccessPolicy.Group.NEWCOMER);
            UUID visitor = UUID.randomUUID();
            try (Connection lock = DriverManager.getConnection("jdbc:sqlite:" + path);
                 var statement = lock.createStatement()) {
                statement.execute("BEGIN IMMEDIATE");
                try {
                    for (var expected : java.util.List.of(PlotAccessPolicy.Setting.ALLOW,
                            PlotAccessPolicy.Setting.DENY, PlotAccessPolicy.Setting.DEFAULT, PlotAccessPolicy.Setting.ALLOW)) {
                        var changed = assertTimeout(Duration.ofMillis(500), () -> edits.writeBehind(staged ->
                                staged.cycleAccess(original, owner, false, subject, "place")));
                        assertTrue(changed.isDone());
                        assertEquals(expected, changed.join());
                        assertEquals(expected == PlotAccessPolicy.Setting.ALLOW,
                                registry.allowed(world, 1, 65, 1, visitor, false, false, "place"));
                    }
                    assertTrue(callbacks.isEmpty());
                    assertFalse(registry.saving());
                } finally { statement.execute("COMMIT"); }
            }
            drain(store);
            assertEquals(registry.byId(original.id()), store.load().getFirst());
        }
    }

    @Test
    void failingPersistenceRetriesTheHeadBeforeNewerAcknowledgedChanges() throws Exception {
        try (var store = open("ordered-retry")) {
            var registry = new PlotRegistry(store);
            Plot original = new PlotEdits(registry).create(owner, "Original", world, shape(0, 3));
            registry.enableAsyncWrites(callbacks::add);
            trigger("ordered-retry", "CREATE TRIGGER fail_name BEFORE UPDATE ON plots WHEN NEW.name='First' "
                    + "BEGIN SELECT RAISE(ABORT,'retry first'); END");
            var rolledBack = new CountDownLatch(1);
            observeRollback(store, rolledBack);
            var first = registry.writeBehind(staged -> { staged.rename(original.id(), "First"); return null; });
            var second = new PlotEdits(registry).writeBehind(staged -> {
                staged.rename(original, original.owner(), false, "Second");
                staged.flag(original, original.owner(), false, "newcomer.place", true);
                return null;
            });
            assertTrue(first.isDone());
            assertTrue(second.isDone());
            first.join();
            second.join();
            assertEquals("Second", registry.byId(original.id()).name());
            assertTrue(registry.byId(original.id()).flags().get("newcomer.place"));
            assertTrue(rolledBack.await(5, TimeUnit.SECONDS));
            trigger("ordered-retry", "DROP TRIGGER fail_name");
            drain(store);
            assertEquals(registry.byId(original.id()), store.load().getFirst());
        }
    }

    @Test
    void rejectedLogicalMutationPublishesNothingAndSchedulesNoDatabaseWork() throws Exception {
        try (var store = open("invalid-logical")) {
            var registry = new PlotRegistry(store);
            Plot original = new PlotEdits(registry).create(owner, "Original", world, shape(0, 3));
            registry.enableAsyncWrites(callbacks::add);
            long revision = registry.revision();
            assertThrows(PlotProblem.class, () -> new PlotEdits(registry).writeBehind(staged -> {
                staged.rename(original, original.owner(), false, "Must not appear");
                staged.invite(original, owner, false, owner, Plot.Role.COLLABORATOR);
                return null;
            }));
            assertSame(original, registry.byId(original.id()));
            assertEquals(revision, registry.revision());
            drain(store);
            assertEquals(original, store.load().getFirst());
        }
    }

    @Test
    void geometryCannotAccidentallyBeExpandedOnTheImmediateMetadataPath() throws Exception {
        try (var store = open("geometry-guard")) {
            var registry = async(store);
            assertThrows(IllegalStateException.class, () -> new PlotEdits(registry).writeBehind(staged ->
                    staged.create(owner, "Unsafe", world,
                            PlotSelectionShape.of(new PlotPosition(world, 0, -64, 0),
                                    new PlotPosition(world, 100_000, 319, 100_000)))));
            assertTrue(registry.all().isEmpty());
            assertTrue(callbacks.isEmpty());
            assertTrue(store.load().isEmpty());
        }
    }

    @Test
    void staleGeometryPreparationRevalidatesWithoutOverwritingNewerImmediatePermissions() throws Exception {
        try (var store = open("rebase")) {
            var registry = new PlotRegistry(store);
            Plot original = new PlotEdits(registry).create(owner, "Original", world, shape(0, 3));
            registry.enableAsyncWrites(callbacks::add);
            var prepared = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            AtomicInteger attempts = new AtomicInteger();
            CompletableFuture<Void> resized;
            try {
                resized = new PlotEdits(registry).writeAsync(staged -> {
                    staged.resize(original, owner, false, shape(0, 15));
                    if (attempts.incrementAndGet() == 1) {
                        prepared.countDown();
                        await(release);
                    }
                    return null;
                });
                assertTrue(prepared.await(5, TimeUnit.SECONDS));
                new PlotEdits(registry).writeBehind(staged -> {
                    staged.flag(original, original.owner(), false, "newcomer.place", true);
                    return null;
                }).join();
            } finally { release.countDown(); }
            callback().run();
            assertFalse(resized.isDone());
            callback().run();
            resized.join();
            assertEquals(2, attempts.get());
            Plot latest = registry.byId(original.id());
            assertSame(latest, registry.at(world, 15, 65, 15));
            assertTrue(latest.flags().get("newcomer.place"));
            drain(store);
            assertEquals(latest, store.load().getFirst());
        }
    }

    @Test
    void geometryPreparedBeforeOwnershipTransferDoesNotBypassTheNewOwnersRights() throws Exception {
        try (var store = open("recheck-owner")) {
            var registry = new PlotRegistry(store);
            Plot original = new PlotEdits(registry).create(owner, "Original", world, shape(0, 3));
            registry.enableAsyncWrites(callbacks::add);
            var prepared = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            UUID successor = UUID.randomUUID();
            CompletableFuture<Void> resized;
            try {
                resized = new PlotEdits(registry).writeAsync(staged -> {
                    staged.resize(original, owner, false, shape(0, 15));
                    prepared.countDown();
                    await(release);
                    return null;
                });
                assertTrue(prepared.await(5, TimeUnit.SECONDS));
                new PlotEdits(registry).writeBehind(staged -> {
                    staged.transfer(original, owner, successor);
                    return null;
                }).join();
            } finally { release.countDown(); }
            callback().run();
            callback().run();
            Throwable error = assertThrows(CompletionException.class, resized::join).getCause();
            assertEquals(Message.PLOT_ERROR_OWNER_ONLY, assertInstanceOf(PlotProblem.class, error).message());
            assertEquals(successor, registry.byId(original.id()).owner());
            assertNull(registry.at(world, 15, 65, 15));
            drain(store);
            assertEquals(registry.byId(original.id()), store.load().getFirst());
        }
    }

    @Test
    void concurrentOverlappingCreationsAreRevalidatedWithoutBusyErrorsOrGhostDatabaseRows() throws Exception {
        try (var store = open("overlapping")) {
            var registry = async(store);
            var edits = new PlotEdits(registry);
            var first = edits.writeAsync(staged -> staged.create(owner, "First", world, shape(0, 15)));
            var second = edits.writeAsync(staged -> staged.create(owner, "Second", world, shape(0, 15)));
            callback().run();
            callback().run();
            callback().run();
            assertEquals("First", first.join().name());
            Throwable error = assertThrows(CompletionException.class, second::join).getCause();
            assertEquals(Message.PLOT_ERROR_OVERLAP, assertInstanceOf(PlotProblem.class, error).message());
            assertEquals(1, registry.all().size());
            drain(store);
            assertEquals(registry.all(), store.load());
        }
    }

    private static void drain(PlotRepository store) throws Exception {
        store.executeAsync(() -> null).get(10, TimeUnit.SECONDS);
    }

    private static void observeRollback(PlotRepository store, CountDownLatch rolledBack) throws Exception {
        var field = PlotRepository.class.getDeclaredField("connection");
        field.setAccessible(true);
        Connection original = (Connection) field.get(store);
        Connection tracked = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                    Object value;
                    try { value = method.invoke(original, arguments); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                    if (method.getName().equals("rollback")) rolledBack.countDown();
                    return value;
                });
        field.set(store, tracked);
    }

    private PreparedStatement track(PreparedStatement statement, AtomicInteger maximum, AtomicInteger executions) {
        int[] coordinate = new int[3];
        AtomicInteger batch = new AtomicInteger();
        AtomicReference<Cell> previous = new AtomicReference<>();
        Comparator<Cell> order = Comparator.comparingInt(Cell::x).thenComparingInt(Cell::y).thenComparingInt(Cell::z);
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                new Class<?>[] {PreparedStatement.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("setInt")) coordinate[(int) arguments[0] - 2] = (int) arguments[1];
                    if (method.getName().equals("addBatch")) {
                        Cell next = new Cell(coordinate[0], coordinate[1], coordinate[2]);
                        Cell before = previous.getAndSet(next);
                        assertTrue(before == null || order.compare(before, next) < 0);
                        maximum.accumulateAndGet(batch.incrementAndGet(), Math::max);
                    }
                    if (method.getName().equals("executeBatch")) {
                        assertTrue(batch.get() > 0 && batch.get() <= PlotRepository.BATCH_SIZE);
                        executions.incrementAndGet();
                    }
                    if (method.getName().equals("clearBatch")) batch.set(0);
                    try { return method.invoke(statement, arguments); }
                    catch (InvocationTargetException error) { throw error.getCause(); }
                });
    }

    private PlotRepository open(String name) throws Exception {
        var store = new PlotRepository(directory.resolve(name + ".db"));
        store.open();
        return store;
    }

    private PlotRegistry async(PlotRepository store) {
        var registry = new PlotRegistry(store);
        registry.enableAsyncWrites(callbacks::add);
        return registry;
    }

    private PlotSelectionShape shape(int minimum, int maximum) {
        return PlotSelectionShape.of(new PlotPosition(world, minimum, 64, minimum),
                new PlotPosition(world, maximum, 67, maximum));
    }

    private Runnable callback() throws InterruptedException {
        Runnable callback = callbacks.poll(10, TimeUnit.SECONDS);
        assertNotNull(callback, "Plot writer did not schedule completion");
        return callback;
    }

    private static void await(CountDownLatch latch) throws SQLException {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new SQLException("Test gate timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new SQLException(error);
        }
    }

    private void trigger(String name, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(name + ".db"));
             var statement = connection.createStatement()) { statement.execute(sql); }
    }

    private int rows(String name, String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve(name + ".db"));
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return result.getInt(1);
        }
    }
}
