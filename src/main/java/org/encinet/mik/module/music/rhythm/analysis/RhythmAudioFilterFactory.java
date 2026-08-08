package org.encinet.mik.module.music.rhythm.analysis;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.PcmFilterFactory;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** Analyzes decoded PCM while forwarding it unchanged through the decoder chain. */
public final class RhythmAudioFilterFactory implements PcmFilterFactory {
    private final RhythmExtractionSink extractionOutput;
    private final PcmRhythmExtractorFactory extractorFactory;
    private final List<ManagedExtractor> extractors = new CopyOnWriteArrayList<>();
    private final AtomicBoolean finished = new AtomicBoolean();

    public RhythmAudioFilterFactory(RhythmExtractionSink output) {
        this(output, RhythmOnsetDetector::new);
    }

    /** Allows another PCM rhythm algorithm to reuse the unchanged decoder filter chain. */
    public RhythmAudioFilterFactory(RhythmExtractionSink output,
                                    PcmRhythmExtractorFactory extractorFactory) {
        this.extractionOutput = Objects.requireNonNull(output, "output");
        this.extractorFactory = Objects.requireNonNull(extractorFactory,
                "extractorFactory");
    }

    @Override
    public List<AudioFilter> buildChain(AudioTrack track, AudioDataFormat format,
                                        UniversalPcmAudioFilter output) {
        ManagedExtractor extractor = new ManagedExtractor(extractorFactory.create(
                extractionOutput, format.sampleRate,
                track == null ? 0L : track.getPosition()));
        extractors.add(extractor);
        if (finished.get()) extractor.finish();
        return List.of(new Tap(extractor, output));
    }

    /** Finalizes every extractor even when a Lavaplayer container omits filter flushing. */
    public void finish() {
        finished.set(true);
        extractors.forEach(ManagedExtractor::finish);
    }

    /** Number of decoded per-channel sample frames observed across built chains. */
    public long analyzedSampleFrames() {
        return extractors.stream().mapToLong(ManagedExtractor::acceptedFrames).sum();
    }

    private record Tap(ManagedExtractor extractor,
                       FloatPcmAudioFilter output) implements FloatPcmAudioFilter {
        private Tap {
            Objects.requireNonNull(extractor, "extractor");
            Objects.requireNonNull(output, "output");
        }

        @Override
        public void process(float[][] input, int offset, int length)
                throws InterruptedException {
            extractor.accept(input, offset, length);
            output.process(input, offset, length);
        }

        @Override
        public void seekPerformed(long requestedTime, long providedTime) {
            extractor.seek(providedTime);
            output.seekPerformed(requestedTime, providedTime);
        }

        @Override
        public void flush() throws InterruptedException {
            extractor.finish();
            output.flush();
        }

        @Override
        public void close() {
            extractor.finish();
        }
    }

    private static final class ManagedExtractor implements PcmRhythmExtractor {
        private final PcmRhythmExtractor delegate;
        private final AtomicBoolean finished = new AtomicBoolean();
        // Lavaplayer may clear its playing-track reference before the final PCM
        // callback returns. Serialize finalization with that callback so a whole
        // song can never be published from a partially processed last chunk.
        private final Object lifecycleLock = new Object();
        private final java.util.concurrent.atomic.AtomicLong acceptedFrames =
                new java.util.concurrent.atomic.AtomicLong();

        private ManagedExtractor(PcmRhythmExtractor delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public void accept(float[][] channels, int offset, int length) {
            synchronized (lifecycleLock) {
                if (finished.get()) return;
                acceptedFrames.addAndGet(Math.max(0, length));
                delegate.accept(channels, offset, length);
            }
        }

        @Override
        public void seek(long positionMillis) {
            synchronized (lifecycleLock) {
                if (finished.get()) return;
                delegate.seek(positionMillis);
            }
        }

        @Override
        public void flush() {
            finish();
        }

        private void finish() {
            synchronized (lifecycleLock) {
                if (finished.compareAndSet(false, true)) delegate.flush();
            }
        }

        private long acceptedFrames() {
            return acceptedFrames.get();
        }

    }
}
