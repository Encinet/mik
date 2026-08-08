package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.module.music.rhythm.RhythmDifficulty;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmSpatialArenaTest {

    @Test
    void acceptsAnOpenVolumeAndKeepsTheRequestedDepth() {
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.NORMAL);
        RhythmSpatialArena arena = new RhythmSpatialArena(
                new Location(null, 0.0, 65.6, 0.0), profile,
                (direction, distance, radius) -> true);

        assertTrue(arena.playable());
        RhythmSpatialArena.Target target = arena.resolve(
                new RhythmSpatialPath.Point(45.0, 30.0, 4.6)).orElseThrow();
        assertEquals(4.6, target.depth(), 0.0);
        assertEquals(profile.worldHitRadius(4.6), target.hitRadius(), 0.0);
    }

    @Test
    void fallsBackInwardAndRejectsAnUnusableVolume() {
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.NORMAL);
        RhythmSpatialArena narrow = new RhythmSpatialArena(
                new Location(null, 0.0, 65.6, 0.0), profile,
                (direction, distance, radius) -> distance <= 2.4);

        assertFalse(narrow.playable());
        assertEquals(2.4, narrow.resolve(
                new RhythmSpatialPath.Point(0.0, 0.0, 4.6))
                .orElseThrow().depth(), 0.0);
    }

    @Test
    void deterministicallyMovesAroundABlockedRay() {
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.NORMAL);
        Vector requested = RhythmSpatialPath.direction(0.0, 0.0);
        RhythmSpatialArena arena = new RhythmSpatialArena(
                new Location(null, 0.0, 65.6, 0.0), profile,
                (direction, distance, radius) ->
                        RhythmSpatialPath.angularDistance(0.0, 0.0,
                                yaw(direction), pitch(direction)) >= 8.0);

        RhythmSpatialArena.Target target = arena.resolve(
                new RhythmSpatialPath.Point(0.0, 0.0, 3.5)).orElseThrow();

        Vector actual = target.location().toVector().subtract(
                new Vector(0.0, 65.6, 0.0)).normalize();
        assertTrue(requested.angle(actual) > Math.toRadians(8.0));
        assertEquals(3.5, target.depth(), 0.0);
    }

    @Test
    void rechecksVisibilityAfterAWorldChange() {
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.NORMAL);
        AtomicBoolean clear = new AtomicBoolean(true);
        RhythmSpatialArena arena = new RhythmSpatialArena(
                new Location(null, 0.0, 65.6, 0.0), profile,
                (direction, distance, radius) -> clear.get());
        Location target = new Location(null, 0.0, 65.6, 3.5);

        assertTrue(arena.visible(target, profile.worldHitRadius(3.5)));
        clear.set(false);
        assertFalse(arena.visible(target, profile.worldHitRadius(3.5)));
    }

    private static double yaw(Vector direction) {
        return Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
    }

    private static double pitch(Vector direction) {
        return Math.toDegrees(Math.asin(direction.getY()));
    }
}
