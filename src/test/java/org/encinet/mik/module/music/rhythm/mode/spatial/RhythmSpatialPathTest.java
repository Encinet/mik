package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.module.music.rhythm.RhythmCue;
import org.encinet.mik.module.music.rhythm.RhythmDifficulty;
import org.encinet.mik.module.music.rhythm.RhythmInput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmSpatialPathTest {

    @Test
    void firstCueStartsAheadAtTheMiddleDepth() {
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.NORMAL);
        RhythmSpatialPath path = new RhythmSpatialPath("forward", 37.0, profile);

        RhythmSpatialPath.Point point = path.point(cue(1L, 1_000L));

        assertEquals(37.0, point.yawDegrees(), 0.0);
        assertEquals(0.0, point.pitchDegrees(), 0.0);
        assertEquals(3.5, point.depth(), 0.0);
        Vector expected = RhythmSpatialPath.direction(37.0, 0.0);
        assertEquals(0.0, expected.distance(point.direction()), 1.0E-12);
    }

    @Test
    void projectionIsDeterministicContinuousAndActuallyThreeDimensional() {
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.EXPERT);
        RhythmSpatialPath first = new RhythmSpatialPath("constellation", -18.0, profile);
        RhythmSpatialPath second = new RhythmSpatialPath("constellation", -18.0, profile);
        List<RhythmSpatialPath.Point> points = new ArrayList<>();
        Set<Integer> pitchBands = new HashSet<>();
        Set<Double> depths = new HashSet<>();

        RhythmSpatialPath.Point previous = null;
        for (int index = 0; index < 120; index++) {
            long time = 1_000L + index * 180L;
            RhythmCue cue = new RhythmCue(index + 1L, time,
                    RhythmInput.values()[index % 4], 0.35 + index % 5 * 0.13,
                    Math.sin(index * 0.37), Math.cos(index * 0.29),
                    index * 0x9E3779B97F4A7C15L);
            RhythmSpatialPath.Point point = first.point(cue);
            assertEquals(point, second.point(cue));
            assertEquals(point, first.point(cue));
            if (previous != null) {
                assertTrue(RhythmSpatialPath.angularDistance(
                        previous.yawDegrees(), previous.pitchDegrees(),
                        point.yawDegrees(), point.pitchDegrees())
                        <= profile.maximumTurnDegrees(180L) + 1.0E-8);
                assertTrue(Math.abs(depthIndex(profile, point.depth())
                        - depthIndex(profile, previous.depth())) <= 1);
            }
            previous = point;
            points.add(point);
            pitchBands.add((int) Math.floor((point.pitchDegrees() + 60.0) / 30.0));
            depths.add(point.depth());
        }

        assertTrue(pitchBands.size() >= 3, () -> "pitch bands=" + pitchBands);
        assertEquals(3, depths.size(), () -> "depths=" + depths);
        assertTrue(points.stream().mapToDouble(RhythmSpatialPath.Point::yawDegrees).max()
                .orElseThrow() - points.stream().mapToDouble(
                RhythmSpatialPath.Point::yawDegrees).min().orElseThrow() >= 180.0);
    }

    @Test
    void pointsUseARealWorldSpaceVolumeAndHistoryCanBeReleased() {
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.NORMAL);
        RhythmSpatialPath path = new RhythmSpatialPath("world", 0.0, profile);
        Location eye = new Location(null, 4.0, 65.6, -2.0);
        for (int index = 0; index < 100; index++) {
            RhythmSpatialPath.Point point = path.point(cue(index + 1L,
                    index * 250L));
            Location world = point.worldPoint(eye);
            assertEquals(point.depth(), world.toVector().distance(
                    eye.toVector()), 1.0E-9);
        }

        path.discardBefore(20_000L);

        assertTrue(path.retainedPointCount() <= 20,
                () -> "retained=" + path.retainedPointCount());
    }

    private static RhythmCue cue(long id, long timeMillis) {
        return new RhythmCue(id, timeMillis, RhythmInput.ONE, 0.65,
                0.0, 0.0, id * 31L);
    }

    private static int depthIndex(RhythmSpatialProfile profile, double depth) {
        double[] depths = profile.depths();
        for (int index = 0; index < depths.length; index++) {
            if (depths[index] == depth) return index;
        }
        throw new AssertionError("unknown depth " + depth);
    }
}
