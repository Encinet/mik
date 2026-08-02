package org.encinet.mik.module.music.rhythm;

import com.sedmelluq.discord.lavaplayer.filter.AudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.FloatPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.filter.UniversalPcmAudioFilter;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import org.junit.jupiter.api.Test;

import java.nio.ShortBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final class RecordingOutput implements UniversalPcmAudioFilter {
        private int floatSamples;
        private boolean flushed;

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
