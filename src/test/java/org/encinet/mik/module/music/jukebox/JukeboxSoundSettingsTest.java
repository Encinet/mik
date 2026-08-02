package org.encinet.mik.module.music.jukebox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JukeboxSoundSettingsTest {

    @Test
    void keepsTheDefaultRangeAt64AndAllowsUpTo256Blocks() {
        assertEquals(64, JukeboxSoundSettings.defaults().rangeBlocks());
        assertEquals(256, new JukeboxSoundSettings(100, Integer.MAX_VALUE).rangeBlocks());
        assertEquals(8, new JukeboxSoundSettings(100, Integer.MIN_VALUE).rangeBlocks());
    }

    @Test
    void reachesTheNewMaximumInSixCoarseAdjustments() {
        JukeboxSoundSettings settings = JukeboxSoundSettings.defaults();
        for (int adjustment = 0; adjustment < 6; adjustment++) {
            settings = settings.withRangeDelta(JukeboxSoundSettings.RANGE_COARSE_STEP);
        }

        assertEquals(256, settings.rangeBlocks());
        assertEquals(256, settings.withRangeDelta(Integer.MAX_VALUE).rangeBlocks());
    }
}
