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
    void suppliesBothFloorsToThePanelAndAllowsSelectingTheOtherFloor() {
        Harness test = new Harness(1);
        test.world.put(0, 20, 0, Material.AIR, Set.of());
        test.world.terrainHeight = 40;
        test.controller.observe(test.player, test.location, false);
        test.until(() -> test.panels > 0, 100);

        assertEquals(List.of(40, 80), test.panelFloors.stream().map(floor -> floor.level().height()).toList());
        assertEquals(List.of(1, 2), test.panelFloors.stream().map(IronElevatorCatalog.Floor::number).toList());
        assertTrue(test.controller.request(test.player, test.location, new IronElevatorController.Floor(80), () -> true));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(81, test.location.getY());
    }

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
    void queuesTheFirstInputAndTravelsBeforeTheFullShaftIsReady() {
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
        assertTrue(test.controller.request(test.player, test.location, new IronElevatorController.Floor(80), () -> true));
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
        assertTrue(cancelled.controller.request(cancelled.player, cancelled.location, IronElevatorController.Direction.UP, allowed::get));
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
        blocked.until(() -> blocked.controller.pending(blocked.id), 20);
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
        quitting.until(() -> quitting.controller.pending(quitting.id), 20);
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
    void ordinaryIronAllowsJumpsBeforeDiscoveryAfterMovementAndAfterInvalidation() {
        Harness test = new Harness(1);
        test.world.put(0, 20, 0, Material.AIR, Set.of());
        test.world.put(0, 80, 0, Material.AIR, Set.of());

        assertFalse(test.controller.requestJump(test.player, test.location));
        assertFalse(test.controller.pending(test.id));
        int reads = test.world.blockReads;
        for (int input = 0; input < 20; input++)
            assertFalse(test.controller.requestJump(test.player, test.location));
        assertTrue(test.world.blockReads - reads <= 20, "repeated jumps must only check support, not scan");

        test.advance(10);
        reads = test.world.blockReads;
        for (int input = 0; input < 20; input++)
            assertFalse(test.controller.requestJump(test.player, test.location));
        test.tick();
        assertTrue(test.world.blockReads - reads <= 20, "finished negative queries must be reused");

        test.location.setX(0.6);
        test.controller.observe(test.player, test.location, false);
        assertFalse(test.controller.requestJump(test.player, test.location));
        test.advance(20);
        assertFalse(test.controller.requestJump(test.player, test.location));

        test.controller.invalidate(test.world.block(0, 40, 0));
        assertFalse(test.controller.requestJump(test.player, test.location));
        test.clock.addAndGet(IronElevatorCatalog.CACHE_NANOS + 1);
        assertFalse(test.controller.requestJump(test.player, test.location));
        test.advance(20);
        assertFalse(test.controller.pending(test.id));
        assertTrue(test.hints.isEmpty());
        assertEquals(0, test.teleports);
    }

    @Test
    void confirmedElevatorStillTravelsWithoutWaitingForTheFullCatalog() {
        Harness test = new Harness(8);
        assertFalse(test.controller.requestJump(test.player, test.location));
        test.until(() -> !test.hints.isEmpty(), 20);
        assertEquals(0, test.panels);
        assertTrue(test.controller.requestJump(test.player, test.location));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(81, test.location.getY());
        assertEquals(0, test.world.terrainReads);
    }

    @Test
    void confirmedJumpStillWorksAfterMovingWithinThePlatform() {
        Harness test = new Harness(2);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);

        test.location.setX(1.5);
        test.controller.observe(test.player, test.location, false);
        assertTrue(test.controller.requestJump(test.player, test.location));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(81, test.location.getY());
        assertEquals(1.5, test.location.getX());
    }

    @Test
    void refreshingHintsDoesNotTemporarilyDisableAConfirmedJump() {
        Harness test = new Harness(2);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);

        test.controller.observe(test.player, test.location, true);
        assertTrue(test.controller.requestJump(test.player, test.location));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(81, test.location.getY());
    }

    @Test
    void repeatedSneakInputsDoNotRestartThePendingScan() {
        Harness test = new Harness(8);
        for (int tick = 0; tick < 20 && test.teleports == 0; tick++) {
            test.request(-1);
            assertTrue(test.controller.pending(test.id));
            test.tick();
        }
        assertEquals(1, test.teleports);
        assertEquals(21, test.location.getY());
    }

    @Test
    void movingWithinTheSameSupportColumnDoesNotStarveASneakRequest() {
        Harness test = new Harness(8);
        assertTrue(test.request(-1));
        for (int tick = 0; tick < 20 && test.teleports == 0; tick++) {
            test.location.add(0.001, 0, 0);
            test.controller.observe(test.player, test.location, false);
            test.tick();
        }
        assertEquals(1, test.teleports);
        assertEquals(21, test.location.getY());
    }

    @Test
    void aConfirmedJumpStillAllowsVanillaJumpingWhenItsLandingBecomesBlocked() {
        Harness test = new Harness(2);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);

        test.world.put(0, 81, 0, Material.STONE, Set.of(BlockFace.UP));
        assertFalse(test.controller.requestJump(test.player, test.location));
        assertFalse(test.controller.pending(test.id));
        test.advance(20);
        assertEquals(0, test.teleports);
    }

    @Test
    void aDirectionalInputReplacesAnExplicitFloorScanAlreadyInProgress() {
        Harness test = new Harness(8);
        assertTrue(test.controller.request(test.player, test.location, new IronElevatorController.Floor(80), () -> true));
        test.advance(2);
        assertEquals(0, test.teleports);

        assertTrue(test.request(-1));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(21, test.location.getY());
    }

    @Test
    void changedStandingPositionRechecksHeadroomBeforeCancellingAnotherJump() {
        Harness test = new Harness(2);
        test.world.put(1, 81, 0, Material.STONE, Set.of(BlockFace.UP));
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);
        assertEquals(new Hint(true, true), test.hints.getFirst());

        test.location.setX(1.5);
        test.controller.observe(test.player, test.location, false);
        assertFalse(test.controller.requestJump(test.player, test.location));
        assertFalse(test.controller.pending(test.id));
        test.until(() -> test.hints.size() > 1, 20);
        assertEquals(new Hint(false, true), test.hints.getLast());
        assertFalse(test.controller.requestJump(test.player, test.location));
        assertEquals(0, test.teleports);
    }

    @Test
    void expiredOrInvalidatedPositiveResultsCannotCancelAnUnconfirmedJump() {
        Harness test = new Harness(1);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);
        test.world.put(0, 80, 0, Material.AIR, Set.of());
        test.clock.addAndGet(IronElevatorCatalog.CACHE_NANOS + 1);
        assertFalse(test.controller.requestJump(test.player, test.location));
        assertFalse(test.controller.pending(test.id));
        test.advance(20);
        assertFalse(test.controller.requestJump(test.player, test.location));

        test.world.iron(80);
        test.floors.invalidate(test.world.block(0, 80, 0));
        test.controller.invalidate(test.world.block(0, 80, 0));
        assertFalse(test.controller.requestJump(test.player, test.location));
        test.until(() -> test.hints.getLast().up(), 20);
        assertTrue(test.controller.requestJump(test.player, test.location));
        test.until(() -> test.teleports == 1, 20);
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

    @Test
    void geometryExpiryKeepsAnAcceptedInputAndItsOriginalDeadline() {
        Harness test = new Harness(1);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);
        test.clock.set(2_000_000_000L);
        assertTrue(test.request(-1));
        test.clock.addAndGet(200_000_000L);
        test.location.add(0.001, 0, 0);
        test.controller.observe(test.player, test.location, false);
        assertTrue(test.controller.pending(test.id));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(21, test.location.getY());

        Harness expired = new Harness(1);
        expired.controller.observe(expired.player, expired.location, false);
        expired.until(() -> !expired.hints.isEmpty(), 20);
        expired.clock.set(2_000_000_000L);
        assertTrue(expired.request(-1));
        expired.clock.addAndGet(200_000_000L);
        expired.location.add(0.001, 0, 0);
        expired.controller.observe(expired.player, expired.location, false);
        expired.clock.addAndGet(1_400_000_000L);
        expired.advance(20);
        assertFalse(expired.controller.pending(expired.id));
        assertEquals(0, expired.teleports);
    }

    @Test
    void expiryOnTheCommitTickRevalidatesGeometryAndStillCompletesTheInput() {
        Harness test = new Harness(1);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);
        test.clock.set(1_900_000_000L);
        assertTrue(test.request(-1));
        test.tick();
        assertTrue(test.controller.pending(test.id));
        test.clock.addAndGet(200_000_000L);
        test.tick();
        assertEquals(0, test.teleports, "source geometry must be revalidated before committing");
        test.until(() -> test.teleports == 1, 20);
        assertEquals(21, test.location.getY());
    }

    @Test
    void movingAwayFromAnObstructionReconsidersALowerFloorRejectedDuringTheScan() {
        Harness test = new Harness(1, 16);
        test.world.put(0, 80, 0, Material.AIR, Set.of());
        test.world.iron(300);
        test.world.put(1, 21, 0, Material.STONE, Set.of(BlockFace.UP));
        test.location.setX(0.95);
        AtomicBoolean checkedLowerLanding = new AtomicBoolean();
        test.world.blockRead = position -> {
            if (position.equals(new IronElevatorTestWorld.Position(1, 21, 0))) checkedLowerLanding.set(true);
        };
        test.controller.observe(test.player, test.location, false);
        test.until(checkedLowerLanding::get, 100);
        assertTrue(test.hints.isEmpty(), "the upper scan should still be running");

        test.location.setX(0.5);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 100);
        assertEquals(new Hint(true, true), test.hints.getLast());
        assertTrue(test.request(-1));
        test.until(() -> test.teleports == 1, 20);
        assertEquals(21, test.location.getY());
    }

    @Test
    void inputAndHintQueriesShareOneGroupMeasurementWithinTheSameTick() {
        Harness test = new Harness(1);
        test.controller.observe(test.player, test.location, false);
        test.until(() -> !test.hints.isEmpty(), 20);
        test.location.add(0.001, 0, 0);
        test.controller.observe(test.player, test.location, false);
        int queries = test.world.nearbyQueries;
        assertTrue(test.controller.requestJump(test.player, test.location));
        assertTrue(test.controller.requestJump(test.player, test.location));
        assertEquals(queries + 1, test.world.nearbyQueries);
        test.until(() -> test.teleports == 1, 20);
    }

    @Test
    void synchronousTeleportEventsCannotStartAnotherTripOrReplaceTheExecutingVisit() {
        var test = new Harness(1);
        test.duringTeleport = () -> {
            assertFalse(test.controller.request(test.player,test.location,IronElevatorController.Direction.UP,() -> true));
            test.controller.observe(test.player,test.location,true);
        };
        assertTrue(test.request(-1));
        test.until(() -> test.teleports == 1,20);
        test.advance(3);
        assertEquals(1,test.teleports);
        assertEquals(21,test.location.getY());
    }

    @Test
    void visibleFloorChecksAreBoundedAndRetiredVisitsCannotSelectAFloor() {
        var test = new Harness(1);
        test.controller.observe(test.player,test.location,false);
        test.until(() -> test.snapshot != null,100);
        var snapshot = test.snapshot;
        test.controller.visibleFloors(test.player,snapshot.visit(),snapshot.catalog(),
                new org.encinet.mik.shared.menu.FloatingMenuPage(0,snapshot.catalog().floors().size(),2));
        test.until(() -> !test.snapshot.available().isEmpty(),10);
        assertTrue(test.snapshot.available().containsKey(20));
        assertFalse(test.snapshot.available().containsKey(80));
        test.controller.forget(test.id);
        test.controller.selectFloor(test.player,snapshot.visit(),80);
        test.advance(3);
        assertEquals(0,test.teleports);
    }

    @Test
    void visibleFloorChecksRetryAfterQueueAdmissionRecoversWithoutWaitingForAPeriodicRefresh() {
        var test = new Harness(1);
        test.controller.observe(test.player,test.location,false);
        test.until(() -> test.snapshot != null,100);
        test.until(() -> test.clock.get() / 50_000_000L % 20 == 1,20);
        var snapshot = test.snapshot;
        var blockers = new ArrayList<IronElevatorWorkQueue.Task>();
        for (var task = test.queue.submit(IronElevatorWorkQueue.HINT,() -> false); task != null;
             task = test.queue.submit(IronElevatorWorkQueue.HINT,() -> false)) blockers.add(task);
        assertFalse(blockers.isEmpty());
        test.controller.visibleFloors(test.player,snapshot.visit(),snapshot.catalog(),
                new org.encinet.mik.shared.menu.FloatingMenuPage(0,snapshot.catalog().floors().size(),2));
        assertTrue(test.controller.request(test.player,test.location,IronElevatorController.Direction.UP,() -> false));
        test.tick();
        assertTrue(test.snapshot.available().isEmpty());

        blockers.forEach(IronElevatorWorkQueue.Task::cancel);
        assertTrue(test.controller.request(test.player,test.location,IronElevatorController.Direction.UP,() -> false));
        test.until(() -> test.snapshot.available().size() == 2,3);
        assertTrue(test.snapshot.available().get(20));
        assertFalse(test.snapshot.available().get(40));
        assertEquals(0,test.teleports);
        int reads = test.world.blockReads;
        int panels = test.panels;
        test.advance(2);
        assertEquals(panels,test.panels,"Completed floor checks must not restart after publishing their snapshot");
        assertEquals(reads,test.world.blockReads);
    }

    @Test
    void lostMountsClearOnlyThePanelAndDoNotDisableDirectionTravel() {
        var test = new Harness(1);
        test.controller.observe(test.player,test.location,false);
        test.until(() -> test.snapshot != null,100);
        int cleared = test.panelClears;
        for (var direction : List.of(BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST))
            test.world.put(direction.getModX(),42,direction.getModZ(),Material.STONE,Set.of());
        test.clock.addAndGet(IronElevatorCatalog.CACHE_NANOS + 1);
        test.controller.observe(test.player,test.location,true);
        test.until(() -> test.panelClears > cleared,100);
        assertTrue(test.request(1));
        test.until(() -> test.teleports == 1,20);
        assertEquals(81,test.location.getY());
    }

    private record Hint(boolean up, boolean down) { }

    private static final class Harness implements IronElevatorController.Presentation {
        final IronElevatorTestWorld world = new IronElevatorTestWorld();
        final AtomicLong clock = new AtomicLong();
        final IronElevatorCatalog floors = new IronElevatorCatalog(clock::get);
        final UUID id = UUID.randomUUID();
        final List<Hint> hints = new ArrayList<>();
        List<IronElevatorCatalog.Floor> panelFloors = List.of();
        final Player player;
        final IronElevatorController controller;
        final IronElevatorWorkQueue queue;
        Location location;
        Vector velocity = new Vector(0.1, 0, 0);
        float fall = 3;
        IronElevatorController.PanelSnapshot snapshot;
        Runnable duringTeleport = () -> { };
        int panels;
        int panelClears;
        int teleports;
        int trips;

        Harness(int width) { this(width, 256); }

        Harness(int width, int stepsPerTick) {
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
                        case "teleport" -> { duringTeleport.run(); location = ((Location) args[0]).clone(); teleports++; yield true; }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            world.entities.add(player);
            var queueClock = new AtomicLong();
            queue = new IronElevatorWorkQueue(() -> queueClock.getAndAdd(1_000_000 / stepsPerTick), error -> fail(error));
            controller = new IronElevatorController(floors, this,
                    query -> query.equals(id) ? player : null, message -> fail(message), clock::get, queue);
        }

        boolean request(int direction) {
            return controller.request(player, location, direction > 0 ? IronElevatorController.Direction.UP : IronElevatorController.Direction.DOWN, () -> true);
        }

        void tick() {
            clock.addAndGet(50_000_000L);
            controller.tick();
        }

        void advance(int ticks) { for (int tick = 0; tick < ticks; tick++) tick(); }

        void until(BooleanSupplier condition, int limit) {
            for (int tick = 0; tick < limit && !condition.getAsBoolean(); tick++) tick();
            assertTrue(condition.getAsBoolean(), "condition did not complete within " + limit + " ticks");
        }

        @Override public void hint(Player player, boolean up, boolean down) { hints.add(new Hint(up, down)); }
        @Override public void clearHint(Player player) { }
        @Override public void clearPanel(Player player) { panelClears++; }
        @Override public void clear(Player player) { }
        @Override public void forget(UUID player) { }
        @Override public void travelled(Player player, Location departure) { trips++; }
        @Override public void panel(Player player, IronElevatorController.PanelSnapshot snapshot) {
            panels++;
            this.snapshot = snapshot;
            panelFloors = snapshot.catalog().floors();
        }
    }
}
