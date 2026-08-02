package org.encinet.mik.module.music.rhythm;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;
import java.util.Objects;

/** Taps decoded PCM for rhythm analysis while forwarding it unchanged to playback. */
public final class RhythmAudioFilterFactory implements PcmFilterFactory {
    private final RhythmTimeline timeline;

    public RhythmAudioFilterFactory(RhythmTimeline timeline) {
        this.timeline = Objects.requireNonNull(timeline, "timeline");
    }

    @Override
    public List<AudioFilter> buildChain(AudioTrack track, AudioDataFormat format,
                                        UniversalPcmAudioFilter output) {
        RhythmOnsetDetector detector = new RhythmOnsetDetector(
                timeline, format.sampleRate, track == null ? 0L : track.getPosition());
        return List.of(new Tap(detector, output));
    }

    private record Tap(RhythmOnsetDetector detector,
                       FloatPcmAudioFilter output) implements FloatPcmAudioFilter {
        private Tap {
            Objects.requireNonNull(detector, "detector");
            Objects.requireNonNull(output, "output");
        }

        @Override
        public void process(float[][] input, int offset, int length)
                throws InterruptedException {
            detector.accept(input, offset, length);
            output.process(input, offset, length);
        }

        @Override
        public void seekPerformed(long requestedTime, long providedTime) {
            detector.seek(providedTime);
        }

        @Override
        public void flush() throws InterruptedException {
            detector.flush();
            output.flush();
        }

        @Override
        public void close() {
        }
    }
}
