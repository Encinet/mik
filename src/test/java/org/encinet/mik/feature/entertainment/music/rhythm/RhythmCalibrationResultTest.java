package org.encinet.mik.feature.entertainment.music.rhythm;

import org.encinet.mik.feature.entertainment.music.rhythm.playback.RhythmAudioChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmCalibrationResultTest {

    @Test
    void fourMeasuredStagesProduceBothChannelsAndPointerBaseline() {
        RhythmCalibrationResult result = RhythmCalibrationResult.fromTests(
                55, 100, 145, 73);

        assertEquals(new RhythmLatencyProfile(100, 45),
                result.profiles().forChannel(RhythmAudioChannel.MINECRAFT));
        assertEquals(new RhythmLatencyProfile(145, 90),
                result.profiles().forChannel(RhythmAudioChannel.PLASMO_VOICE));
        assertEquals(18, result.pointerInputDeltaMillis());
    }

    @Test
    void persistentCalibrationIsStrictlyAllOrNothing() {
        assertTrue(RhythmCalibrationResult.fromStored(
                100, 45, 145, 90, 18).isPresent());
        assertTrue(RhythmCalibrationResult.fromStored(
                100, 45, 145, null, 18).isEmpty());
        assertTrue(RhythmCalibrationResult.fromStored(
                351, 45, 145, 90, 18).isEmpty());
        assertTrue(RhythmCalibrationResult.fromStored(
                100, 45, 145, 90, 201).isEmpty());
    }

    @Test
    void exactSupportedPersistenceBoundariesRemainValid() {
        RhythmCalibrationResult result = RhythmCalibrationResult.fromStored(
                RhythmLatencyProfile.MINIMUM_JUDGEMENT_OFFSET_MILLIS,
                RhythmLatencyProfile.MAXIMUM_ANIMATION_OFFSET_MILLIS,
                RhythmLatencyProfile.MAXIMUM_JUDGEMENT_OFFSET_MILLIS,
                RhythmLatencyProfile.MINIMUM_ANIMATION_OFFSET_MILLIS,
                RhythmCalibrationResult.MINIMUM_POINTER_DELTA_MILLIS)
                .orElseThrow();

        assertEquals(RhythmCalibrationResult.MINIMUM_POINTER_DELTA_MILLIS,
                result.pointerInputDeltaMillis());
    }
}
