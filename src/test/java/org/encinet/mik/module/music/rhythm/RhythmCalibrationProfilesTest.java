package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.rhythm.playback.RhythmAudioChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RhythmCalibrationProfilesTest {

    @Test
    void keepsMinecraftAndPlasmoVoiceOffsetsIndependent() {
        RhythmCalibrationProfiles profiles = RhythmCalibrationProfiles.fromTests(
                40, 70, 145);

        assertEquals(70, profiles.forChannel(
                RhythmAudioChannel.MINECRAFT).judgementOffsetMillis());
        assertEquals(30, profiles.forChannel(
                RhythmAudioChannel.MINECRAFT).animationOffsetMillis());
        assertEquals(145, profiles.forChannel(
                RhythmAudioChannel.PLASMO_VOICE).judgementOffsetMillis());
        assertEquals(105, profiles.forChannel(
                RhythmAudioChannel.PLASMO_VOICE).animationOffsetMillis());
    }
}
