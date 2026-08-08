package org.encinet.mik.module.music.rhythm.calibration;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

/** Dedicated Plasmo Voice output used only by the complete latency test. */
public interface RhythmCalibrationAudioOutput extends AutoCloseable {

    boolean available(Player player);

    Optional<StagePlayback> play(Player player,
                                 RhythmCalibrationPattern pattern);

    @Override
    void close();

    interface StagePlayback extends AutoCloseable {
        boolean active();

        /** Returns cue frames produced since the previous drain. */
        List<RhythmCuePresentation> drainPresentations();

        PlaybackProgress progress();

        @Override
        void close();
    }

    record PlaybackProgress(long lastFrameAtNanos, long completedCycles,
                            int frameIndex) {
        public boolean hasStarted() {
            return lastFrameAtNanos != Long.MIN_VALUE;
        }

        public boolean stalled(long nowNanos, long maximumSilenceNanos) {
            return hasStarted() && nowNanos >= lastFrameAtNanos
                    && nowNanos - lastFrameAtNanos > maximumSilenceNanos;
        }
    }
}
