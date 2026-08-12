package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxAudibleFieldTest {

    @Test
    void clearRadiusAlwaysStaysInsidePlaybackRange() {
        assertEquals(32.0, JukeboxAudibleField.clearRadius(
                new JukeboxSoundSettings(100, 64)));
        assertEquals(16.0, JukeboxAudibleField.clearRadius(
                new JukeboxSoundSettings(25, 64)));
        assertEquals(0.0, JukeboxAudibleField.clearRadius(
                new JukeboxSoundSettings(0, 64)));
        assertTrue(JukeboxAudibleField.clearRadius(
                new JukeboxSoundSettings(100, 256)) < 256);
    }

    @Test
    void fieldsOverlapWhenTheirClearRadiiTouch() {
        World world = world();
        JukeboxSoundSettings settings = new JukeboxSoundSettings(100, 64);

        assertTrue(JukeboxAudibleField.overlaps(
                new Location(world, 0, 0, 0), settings,
                new Location(world, 64, 0, 0), settings));
        assertFalse(JukeboxAudibleField.overlaps(
                new Location(world, 0, 0, 0), settings,
                new Location(world, 64.01, 0, 0), settings));
    }

    @Test
    void mutedOrDifferentWorldFieldsDoNotOverlap() {
        World firstWorld = world();
        World secondWorld = world();
        JukeboxSoundSettings audible = new JukeboxSoundSettings(100, 64);
        JukeboxSoundSettings muted = new JukeboxSoundSettings(0, 64);

        assertFalse(JukeboxAudibleField.overlaps(
                new Location(firstWorld, 0, 0, 0), muted,
                new Location(firstWorld, 0, 0, 0), audible));
        assertFalse(JukeboxAudibleField.overlaps(
                new Location(firstWorld, 0, 0, 0), audible,
                new Location(secondWorld, 0, 0, 0), audible));
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[]{World.class}, (proxy, method, arguments) ->
                        switch (method.getName()) {
                            case "equals" -> proxy == arguments[0];
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "toString" -> "world";
                            default -> defaultValue(method.getReturnType());
                        });
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
