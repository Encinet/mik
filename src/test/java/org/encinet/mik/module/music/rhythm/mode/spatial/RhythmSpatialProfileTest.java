package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.encinet.mik.module.music.rhythm.RhythmDifficulty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmSpatialProfileTest {

    @Test
    void everyDifficultyHasEnoughPreviewCapacityForItsMaximumDensity() {
        for (RhythmDifficulty difficulty : RhythmDifficulty.values()) {
            RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(difficulty);
            double simultaneouslyVisible = difficulty.maximumCuesPerSecond()
                    * profile.approachMillis() / 1_000.0;

            assertTrue(profile.maximumVisibleCues(difficulty)
                    >= Math.ceil(simultaneouslyVisible) + 2);
        }
    }

    @Test
    void worldHitRadiusPreservesTheConfiguredAngularRadius() {
        RhythmSpatialProfile profile = RhythmSpatialProfile.forDifficulty(
                RhythmDifficulty.NORMAL);

        for (double depth : profile.depths()) {
            double reconstructed = Math.toDegrees(Math.atan(
                    profile.worldHitRadius(depth) / depth));
            assertEquals(profile.aimRadiusDegrees(), reconstructed, 1.0E-9);
        }
    }
}
