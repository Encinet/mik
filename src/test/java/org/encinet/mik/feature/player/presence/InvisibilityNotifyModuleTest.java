package org.encinet.mik.feature.player.presence;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class InvisibilityNotifyModuleTest {
    @Test
    void noInvisiblePlayersNeedNoLocationOrSpatialQueries() {
        var world = new TestWorld();
        for (int index = 0; index < 500; index++) world.add(index, 64, 0, false);

        assertTrue(InvisibilityNotifyModule.detectNearbyInvisiblePlayers(world.players()).isEmpty());
        assertEquals(0, world.spatialQueries);
        assertEquals(0, world.locationReads);
    }

    @Test
    void spatialDetectionMatchesExhaustiveSearchAcrossWorldsAndVisibility() {
        var random = new Random(42);
        var worlds = List.of(new TestWorld(), new TestWorld());
        var players = new ArrayList<Player>();
        for (TestWorld world : worlds) {
            // Include an exact radius boundary and a box-corner outside the sphere.
            world.add(-24, 64, 0, true);
            world.add(0, 64, 0, false);
            world.add(-24, 88, 24, false);
            for (int index = 0; index < 100; index++)
                world.add(random.nextDouble(-80, 80), random.nextDouble(40, 100),
                        random.nextDouble(-80, 80), random.nextInt(4) == 0);
            for (TestPlayer viewer : world.entries)
                for (TestPlayer target : world.entries)
                    if (random.nextInt(8) == 0) viewer.hidden.add(target.player);
            players.addAll(world.players());
        }

        var detections = InvisibilityNotifyModule.detectNearbyInvisiblePlayers(players);
        for (TestWorld world : worlds) {
            assertEquals(world.entries.stream().filter(entry -> entry.invisible).count(), world.spatialQueries);
            for (TestPlayer viewer : world.entries) {
                int count = 0;
                double nearest = Double.POSITIVE_INFINITY;
                for (TestPlayer target : world.entries) {
                    if (target == viewer || !target.invisible || viewer.hidden.contains(target.player)) continue;
                    double distance = viewer.location.distanceSquared(target.location);
                    if (distance <= 24 * 24) {
                        count++;
                        nearest = Math.min(nearest, distance);
                    }
                }
                var actual = detections.get(viewer.player);
                if (count == 0) assertNull(actual);
                else {
                    assertNotNull(actual);
                    assertEquals(count, actual.count());
                    assertEquals((int) Math.round(Math.sqrt(nearest)), actual.nearestDistance());
                }
            }
        }
    }

    private static final class TestWorld {
        final List<TestPlayer> entries = new ArrayList<>();
        int spatialQueries;
        int locationReads;
        final World world = proxy(World.class, (object, method, args) -> switch (method) {
            case "getNearbyPlayers" -> {
                spatialQueries++;
                Location center = (Location) args[0];
                double radius = (double) args[1];
                yield entries.stream().filter(entry ->
                        Math.abs(entry.location.getX() - center.getX()) <= radius
                                && Math.abs(entry.location.getY() - center.getY()) <= radius
                                && Math.abs(entry.location.getZ() - center.getZ()) <= radius)
                        .map(entry -> entry.player).toList();
            }
            case "getPlayers" -> throw new AssertionError("Must use spatial lookup");
            default -> throw new AssertionError(method);
        });

        void add(double x, double y, double z, boolean invisible) {
            entries.add(new TestPlayer(this, x, y, z, invisible));
        }

        List<Player> players() {
            return entries.stream().map(entry -> entry.player).toList();
        }
    }

    private static final class TestPlayer {
        final Location location;
        final boolean invisible;
        final Set<Player> hidden = new HashSet<>();
        final Player player;

        TestPlayer(TestWorld world, double x, double y, double z, boolean invisible) {
            this.location = new Location(world.world, x, y, z);
            this.invisible = invisible;
            player = proxy(Player.class, (object, method, args) -> switch (method) {
                case "getWorld" -> world.world;
                case "getLocation" -> {
                    world.locationReads++;
                    yield location.clone();
                }
                case "isInvisible" -> invisible;
                case "canSee" -> !hidden.contains(args[0]);
                default -> throw new AssertionError(method);
            });
        }
    }

    private interface Method { Object invoke(Object object, String method, Object[] args); }

    private static <T> T proxy(Class<T> type, Method call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (object, method, args) -> switch (method.getName()) {
                    case "equals" -> object == args[0];
                    case "hashCode" -> System.identityHashCode(object);
                    case "toString" -> type.getSimpleName();
                    default -> call.invoke(object, method.getName(), args);
                }));
    }
}
