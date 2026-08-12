package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JukeboxPlaybackCoordinationTest {

    private static final JukeboxSoundSettings SETTINGS =
            new JukeboxSoundSettings(100, 64);

    @Test
    void overlappingDifferentSongBlocksTheNewPlayback() {
        World world = world();
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup();

        JukeboxPlaybackCoordination.Decision decision =
                JukeboxPlaybackCoordination.decide(
                        new Location(world, 0, 0, 0), "second", SETTINGS,
                        List.of(new JukeboxPlaybackCoordination.ActiveField(
                                new Location(world, 32, 0, 0), "first", SETTINGS, group)));

        assertTrue(decision.blocked());
        assertNull(decision.playbackGroup());
    }

    @Test
    void overlappingSameSongSharesTheExistingClock() {
        World world = world();
        JukeboxPlaybackGroup group = new JukeboxPlaybackGroup();

        JukeboxPlaybackCoordination.Decision decision =
                JukeboxPlaybackCoordination.decide(
                        new Location(world, 0, 0, 0), "same", SETTINGS,
                        List.of(new JukeboxPlaybackCoordination.ActiveField(
                                new Location(world, 32, 0, 0), "same", SETTINGS, group)));

        assertFalse(decision.blocked());
        assertSame(group, decision.playbackGroup());
    }

    @Test
    void separateSoundFieldsMayPlayDifferentSongs() {
        World world = world();

        JukeboxPlaybackCoordination.Decision decision =
                JukeboxPlaybackCoordination.decide(
                        new Location(world, 0, 0, 0), "second", SETTINGS,
                        List.of(new JukeboxPlaybackCoordination.ActiveField(
                                new Location(world, 65, 0, 0), "first", SETTINGS,
                                new JukeboxPlaybackGroup())));

        assertFalse(decision.blocked());
        assertNull(decision.playbackGroup());
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
