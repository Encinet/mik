package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JukeboxAutoPlayServiceTest {

    @Test
    void findsNearestPlayerOnlyWithinConfiguredPlaybackRange() {
        List<Player> players = new ArrayList<>();
        World world = world(players);
        Player nearest = player(world, 8, 0, 0);
        Player boundary = player(world, 0, 0, 64);
        Player outside = player(world, 65, 0, 0);
        players.add(outside);
        players.add(boundary);
        players.add(nearest);

        assertEquals(nearest, JukeboxAutoPlayService.findNearestPlayer(
                new Location(world, 0, 0, 0), 64));

        players.clear();
        players.add(boundary);
        assertEquals(boundary, JukeboxAutoPlayService.findNearestPlayer(
                new Location(world, 0, 0, 0), 64));
    }

    @Test
    void returnsNullWhenNoPlayerIsWithinPlaybackRange() {
        List<Player> players = new ArrayList<>();
        World world = world(players);
        players.add(player(world, 9, 0, 0));

        assertNull(JukeboxAutoPlayService.findNearestPlayer(
                new Location(world, 0, 0, 0), 8));
    }

    private static World world(List<Player> players) {
        return proxy(World.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getPlayers" -> players;
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "world";
            default -> defaultValue(method.getReturnType());
        });
    }

    private static Player player(World world, double x, double y, double z) {
        return proxy(Player.class, (proxy, method, arguments) -> switch (method.getName()) {
            case "getLocation" -> new Location(world, x, y, z);
            case "equals" -> proxy == arguments[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "player@" + x + "," + y + "," + z;
            default -> defaultValue(method.getReturnType());
        });
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        throw new AssertionError("Unknown primitive: " + type);
    }
}
