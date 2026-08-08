package org.encinet.mik.module.music.rhythm;

import org.encinet.mik.module.music.rhythm.playback.RhythmAudioChannel;

import java.util.Objects;

/** Complete channel-specific calibration selected by the actual playback backend. */
record RhythmCalibrationProfiles(RhythmLatencyProfile minecraft,
                                 RhythmLatencyProfile plasmoVoice) {
    RhythmCalibrationProfiles {
        minecraft = Objects.requireNonNull(minecraft, "minecraft");
        plasmoVoice = Objects.requireNonNull(plasmoVoice, "plasmoVoice");
    }

    RhythmLatencyProfile forChannel(RhythmAudioChannel channel) {
        return switch (Objects.requireNonNull(channel, "channel")) {
            case MINECRAFT -> minecraft;
            case PLASMO_VOICE -> plasmoVoice;
        };
    }

    static RhythmCalibrationProfiles fromTests(int visualTapOffsetMillis,
                                                int minecraftTapOffsetMillis,
                                                int plasmoVoiceTapOffsetMillis) {
        return new RhythmCalibrationProfiles(
                RhythmLatencyProfile.fromTests(
                        visualTapOffsetMillis, minecraftTapOffsetMillis),
                RhythmLatencyProfile.fromTests(
                        visualTapOffsetMillis, plasmoVoiceTapOffsetMillis));
    }
}
