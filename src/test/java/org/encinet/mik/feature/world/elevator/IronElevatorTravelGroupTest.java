package org.encinet.mik.feature.world.elevator;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IronElevatorTravelGroupTest {
    private final IronElevatorTestWorld world = new IronElevatorTestWorld();
    private final List<Actor> actors = new ArrayList<>();

    @Test
    void transportsBranchesAndChainedAnimalsAndPreservesTheirPositionsAndLeashes() {
        floors(4);
        Actor player = actor(true, 0.5);
        Actor first = actor(false, 1.5).leashedTo(player);
        Actor second = actor(false, 2.5).leashedTo(first);
        Actor branch = actor(false, 3.5).leashedTo(player);
        var group = collect(player);
        assertNotNull(group);
        Location destination = group.landing(platform(30));
        assertNotNull(destination);
        assertTrue(group.teleport(destination, message -> fail(message)));
        for (Actor actor : List.of(player, first, second, branch)) {
            assertEquals(31, actor.location.getY());
            assertEquals(actor.x, actor.location.getX());
            assertEquals(70, actor.location.getYaw());
            assertEquals(-15, actor.location.getPitch());
            assertEquals(1, actor.teleports);
            assertEquals(new Vector(), actor.velocity);
            assertEquals(0, actor.fall);
        }
        assertSame(player.entity, first.holder);
        assertSame(first.entity, second.holder);
        assertSame(player.entity, branch.holder);
    }

    @Test
    void rejectsWholeGroupWhenAChainedAnimalIsOffThePlatformOrAirborne() {
        floors(2);
        Actor player = actor(true, 0.5);
        Actor first = actor(false, 1.5).leashedTo(player);
        Actor last = actor(false, 2.5).leashedTo(first);
        assertNull(collect(player));
        last.location.setX(1.5);
        last.location.setY(11.5);
        assertNull(collect(player));
        assertEquals(0, player.teleports);
    }

    @Test
    void rejectsNarrowOrObstructedDestinationAndFindsTheNextWholeGroupFloor() {
        floors(2);
        Actor player = actor(true, 0.5);
        actor(false, 1.5).leashedTo(player);
        var group = collect(player);
        world.put(1, 31, 0, Material.STONE, Set.of());
        assertNull(group.landing(platform(30)));
        world.iron(20); // A player-only floor must also be skipped.
        var floors = new IronElevatorFloors();
        assertNull(floors.destination(world.block(0, 10, 0), 1, null, player.location,
                IronElevatorLanding.DEFAULT_SIZE, box -> false, target -> group.landing(target) != null));
        for (int x = 0; x < 2; x++) world.put(x, 40, 0, Material.IRON_BLOCK, Set.of());
        floors.invalidate();
        assertEquals(41, floors.destination(world.block(0, 10, 0), 1, null, player.location,
                IronElevatorLanding.DEFAULT_SIZE, box -> false, target -> group.landing(target) != null).getY());
    }

    @Test
    void cancellationReturnsAlreadyMovedMembersAndRestoresTheirMotion() {
        floors(3);
        Actor player = actor(true, 0.5);
        Actor first = actor(false, 1.5).leashedTo(player);
        Actor last = actor(false, 2.5).leashedTo(first);
        last.cancel = true;
        var group = collect(player);
        assertFalse(group.teleport(group.landing(platform(30)), message -> fail(message)));
        for (Actor actor : List.of(player, first, last)) {
            assertEquals(11, actor.location.getY());
            assertEquals(new Vector(0.1, 0, 0), actor.velocity);
            assertEquals(3, actor.fall);
        }
        assertSame(player.entity, first.holder);
        assertSame(first.entity, last.holder);
    }

    @Test
    void detectsRedirectedTeleportsAndReportsRollbackRejection() {
        floors(2);
        Actor player = actor(true, 0.5);
        Actor animal = actor(false, 1.5).leashedTo(player);
        animal.redirect = true;
        var group = collect(player);
        List<String> warnings = new ArrayList<>();
        assertFalse(group.teleport(group.landing(platform(30)), warnings::add));
        assertEquals(11, player.location.getY());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void restoresLeashesRemovedByTheTeleportAndIgnoresUnrelatedAnimals() {
        floors(3);
        Actor player = actor(true, 0.5);
        Actor animal = actor(false, 1.5).leashedTo(player);
        Actor unrelated = actor(false, 2.5);
        animal.dropLeash = true;
        var group = collect(player);
        assertTrue(group.teleport(group.landing(platform(30)), message -> fail(message)));
        assertSame(player.entity, animal.holder);
        assertEquals(11, unrelated.location.getY());
        assertEquals(0, unrelated.teleports);
    }

    @Test
    void rejectsOversizedComponents() {
        floors(64);
        Actor player = actor(true, 0.5);
        Actor animal = actor(false, 1.5).leashedTo(player);
        for (int i = 0; i < 63; i++) actor(false, 0.5).leashedTo(player);
        assertNull(collect(player));
    }

    @Test
    void ridingPlayerTravelsWithMountAndLeashChainWithoutDismounting() {
        floors(3);
        Actor mount = actor(false, 0.5);
        Actor player = actor(true, 0.5).mountedOn(mount);
        Actor first = actor(false, 1.5).leashedTo(player);
        Actor last = actor(false, 2.5).leashedTo(first);
        assertEquals(world.block(0, 10, 0), IronElevatorTravelGroup.platformAt((Player) player.entity, player.location));
        var group = collect(player);
        assertNotNull(group);
        assertEquals(32.5, group.landing(platform(30)).getY());
        assertTrue(group.teleport(group.landing(platform(30)), message -> fail(message)));
        assertEquals(31, mount.location.getY());
        assertEquals(32.5, player.location.getY());
        assertSame(mount, player.vehicle);
        assertEquals(0, player.teleports);
        assertEquals(1, mount.teleports);
        assertEquals(31, last.location.getY());
    }

    @Test
    void nestedRidingAndMultiplePassengersMoveOnlyTheirSupportedRoot() {
        floors(1);
        Actor bottom = actor(false, 0.5);
        Actor middle = actor(false, 0.5).mountedOn(bottom);
        Actor player = actor(true, 0.5).mountedOn(middle);
        Actor companion = actor(false, 0.5).mountedOn(bottom);
        var group = collect(player);
        assertNotNull(group);
        assertTrue(group.teleport(group.landing(platform(30)), message -> fail(message)));
        assertEquals(34, player.location.getY());
        assertEquals(32.5, middle.location.getY());
        assertEquals(32.5, companion.location.getY());
        assertEquals(1, bottom.teleports);
        assertEquals(0, middle.teleports);
        assertEquals(0, companion.teleports);
    }

    @Test
    void mountedPlayerHeadroomAndOffPlatformMountAreChecked() {
        floors(1);
        Actor mount = actor(false, 0.5);
        Actor player = actor(true, 0.5).mountedOn(mount);
        var group = collect(player);
        world.put(0, 34, 0, Material.STONE, Set.of());
        assertNull(group.landing(platform(30)));
        mount.location.setX(2.5);
        assertNull(collect(player));
    }

    @Test
    void failedLeashedAnimalTeleportRollsBackTheWholeRidingTree() {
        floors(2);
        Actor mount = actor(false, 0.5);
        Actor player = actor(true, 0.5).mountedOn(mount);
        Actor animal = actor(false, 1.5).leashedTo(player);
        animal.cancel = true;
        var group = collect(player);
        assertFalse(group.teleport(group.landing(platform(30)), message -> fail(message)));
        assertEquals(11, mount.location.getY());
        assertEquals(12.5, player.location.getY());
        assertSame(mount, player.vehicle);
        assertEquals(0, player.teleports);
    }

    private void floors(int width) {
        for (int x = 0; x < width; x++) for (int y : List.of(10, 30))
            world.put(x, y, 0, Material.IRON_BLOCK, Set.of());
    }

    private IronElevatorPlatform platform(int y) {
        return IronElevatorPlatform.resolve(world.block(0, y, 0));
    }

    private IronElevatorTravelGroup collect(Actor player) {
        return IronElevatorTravelGroup.collect((Player) player.entity, platform(10));
    }

    private Actor actor(boolean player, double x) {
        Actor actor = new Actor(player, x);
        world.entities.add(actor.entity);
        actors.add(actor);
        return actor;
    }

    private final class Actor {
        final double x;
        final UUID id = UUID.randomUUID();
        final Entity entity;
        Location location;
        Entity holder;
        Vector velocity = new Vector(0.1, 0, 0);
        float fall = 3;
        int teleports;
        boolean cancel, redirect, dropLeash;
        Actor vehicle;
        final List<Actor> passengers = new ArrayList<>();

        Actor(boolean player, double x) {
            this.x = x;
            location = new Location(world.world, x, 11, 0.5, 70, -15);
            Class<?> type = player ? Player.class : LivingEntity.class;
            entity = (Entity) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getWorld" -> world.world;
                        case "isValid" -> true;
                        case "isDead", "wouldCollideUsing" -> false;
                        case "isInsideVehicle" -> vehicle != null;
                        case "getVehicle" -> vehicle == null ? null : vehicle.entity;
                        case "getPassengers" -> passengers.stream().map(actor -> actor.entity).toList();
                        case "addPassenger" -> {
                            Actor rider = actors.stream().filter(actor -> actor.entity == args[0]).findFirst().orElseThrow();
                            rider.mountedOn(this);
                            yield true;
                        }
                        case "getLocation" -> location.clone();
                        case "getBoundingBox" -> new BoundingBox(location.getX() - 0.3, location.getY(), 0.2,
                                location.getX() + 0.3, location.getY() + 1.8, 0.8);
                        case "getVelocity" -> velocity.clone();
                        case "setVelocity" -> { velocity = ((Vector) args[0]).clone(); yield null; }
                        case "getFallDistance" -> fall;
                        case "setFallDistance" -> { fall = (float) args[0]; yield null; }
                        case "isLeashed" -> holder != null;
                        case "getLeashHolder" -> holder;
                        case "setLeashHolder" -> { holder = (Entity) args[0]; yield true; }
                        case "teleport" -> {
                            teleports++;
                            if (cancel) yield false;
                            Location target = ((Location) args[0]).clone();
                            Vector delta = target.toVector().subtract(location.toVector());
                            shift(delta);
                            if (redirect) location.add(1, 0, 0);
                            if (dropLeash) holder = null;
                            yield true;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }

        private void shift(Vector delta) {
            location.add(delta);
            passengers.forEach(rider -> rider.shift(delta));
        }

        Actor mountedOn(Actor mount) {
            if (vehicle != null) vehicle.passengers.remove(this);
            vehicle = mount;
            mount.passengers.add(this);
            location = mount.location.clone().add(0, 1.5, 0);
            return this;
        }

        Actor leashedTo(Actor actor) { holder = actor.entity; return this; }
    }
}
