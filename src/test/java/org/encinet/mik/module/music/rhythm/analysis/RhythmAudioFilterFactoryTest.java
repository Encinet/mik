package org.encinet.mik.module.music.rhythm.analysis;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import org.junit.jupiter.api.Test;

import java.nio.ShortBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmAudioFilterFactoryTest {

    @Test
    void acceptsLavaplayersNullTrackAndForwardsPcmAndFlush() throws Exception {
        RhythmTimeline timeline = new RhythmTimeline("factory");
        RecordingOutput output = new RecordingOutput();

        List<AudioFilter> filters = new RhythmAudioFilterFactory(timeline).buildChain(
                null, StandardAudioDataFormats.DISCORD_PCM_S16_LE, output);
        FloatPcmAudioFilter tap = (FloatPcmAudioFilter) filters.getFirst();
        tap.process(new float[][] {new float[960], new float[960]}, 0, 960);
        tap.flush();

        assertEquals(960, output.floatSamples);
        assertTrue(output.flushed);
    }

    @Test
    void delegatesPcmLifecycleThroughTheExtractorFactoryBoundary() throws Exception {
        RhythmTimeline timeline = new RhythmTimeline("replaceable-extractor");
        RecordingOutput output = new RecordingOutput();
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger sought = new AtomicInteger();
        AtomicInteger flushed = new AtomicInteger();
        PcmRhythmExtractorFactory factory = (sink, sampleRate, initialPosition) -> {
            assertEquals(timeline, sink);
            assertTrue(sampleRate > 0);
            assertEquals(0L, initialPosition);
            return new PcmRhythmExtractor() {
                @Override
                public void accept(float[][] channels, int offset, int length) {
                    accepted.addAndGet(length);
                }

                @Override
                public void seek(long positionMillis) {
                    sought.set((int) positionMillis);
                }

                @Override
                public void flush() {
                    flushed.incrementAndGet();
                }
            };
        };

        RhythmAudioFilterFactory filterFactory = new RhythmAudioFilterFactory(
                timeline, factory);
        FloatPcmAudioFilter tap = (FloatPcmAudioFilter) filterFactory.buildChain(null,
                StandardAudioDataFormats.DISCORD_PCM_S16_LE, output).getFirst();
        tap.process(new float[][] {new float[20], new float[20]}, 0, 20);
        tap.seekPerformed(120L, 100L);
        tap.flush();
        filterFactory.finish();
        filterFactory.finish();
        tap.close();

        assertEquals(20, accepted.get());
        assertEquals(100, sought.get());
        assertEquals(100, output.providedSeekMillis);
        assertEquals(1, flushed.get());
        assertEquals(20, output.floatSamples);
        assertTrue(output.flushed);
    }

    @Test
    void finishWaitsForTheLastPcmCallbackBeforeFlushingTheExtractor()
            throws Exception {
        RhythmTimeline timeline = new RhythmTimeline("finish-race");
        RecordingOutput output = new RecordingOutput();
        CountDownLatch acceptEntered = new CountDownLatch(1);
        CountDownLatch releaseAccept = new CountDownLatch(1);
        CountDownLatch finishAttempted = new CountDownLatch(1);
        CountDownLatch flushCalled = new CountDownLatch(1);
        AtomicBoolean accepted = new AtomicBoolean();
        AtomicBoolean flushSawAccepted = new AtomicBoolean();
        PcmRhythmExtractorFactory extractorFactory = (sink, sampleRate,
                                                        initialPosition) ->
                new PcmRhythmExtractor() {
                    @Override
                    public void accept(float[][] channels, int offset, int length) {
                        acceptEntered.countDown();
                        try {
                            if (!releaseAccept.await(5, TimeUnit.SECONDS)) {
                                throw new AssertionError("PCM callback was not released");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError(exception);
                        }
                        accepted.set(true);
                    }

                    @Override
                    public void seek(long positionMillis) {
                    }

                    @Override
                    public void flush() {
                        flushSawAccepted.set(accepted.get());
                        flushCalled.countDown();
                    }
                };
        RhythmAudioFilterFactory filterFactory = new RhythmAudioFilterFactory(
                timeline, extractorFactory);
        FloatPcmAudioFilter tap = (FloatPcmAudioFilter) filterFactory.buildChain(null,
                StandardAudioDataFormats.DISCORD_PCM_S16_LE, output).getFirst();
        AtomicReference<Throwable> processingFailure = new AtomicReference<>();
        Thread processing = Thread.ofVirtual().start(() -> {
            try {
                tap.process(new float[][] {new float[960], new float[960]}, 0, 960);
            } catch (Throwable error) {
                processingFailure.set(error);
            }
        });

        assertTrue(acceptEntered.await(5, TimeUnit.SECONDS));
        Thread finishing = Thread.ofVirtual().start(() -> {
            finishAttempted.countDown();
            filterFactory.finish();
        });
        assertTrue(finishAttempted.await(5, TimeUnit.SECONDS));
        try {
            assertFalse(flushCalled.await(100, TimeUnit.MILLISECONDS),
                    "flush must wait while the final PCM callback is active");
        } finally {
            releaseAccept.countDown();
        }
        processing.join();
        finishing.join();

        assertNull(processingFailure.get());
        assertTrue(flushCalled.await(1, TimeUnit.SECONDS));
        assertTrue(flushSawAccepted.get());
        assertEquals(960, filterFactory.analyzedSampleFrames());
    }

    private static final class RecordingOutput implements UniversalPcmAudioFilter {
        private int floatSamples;
        private boolean flushed;
        private long providedSeekMillis = -1L;

        @Override
        public void process(float[][] input, int offset, int length) {
            floatSamples += length;
        }

        @Override
        public void process(short[][] input, int offset, int length) {
        }

        @Override
        public void process(short[] input, int offset, int length) {
        }

        @Override
        public void process(ShortBuffer buffer) {
        }

        @Override
        public void seekPerformed(long requestedTime, long providedTime) {
            providedSeekMillis = providedTime;
        }

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
        }
    }
}
