package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;
import org.encinet.mik.feature.entertainment.music.rhythm.RhythmChartView;
import org.encinet.mik.feature.entertainment.music.rhythm.RhythmDifficulty;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmSource;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class OfflineRhythmAnalyzerTest {

    private AudioTrackLoader loader;
    private OfflineRhythmAnalyzer analyzer;

    @AfterEach
    void close() {
        if (analyzer != null) analyzer.close();
        if (loader != null) loader.close();
    }

    @Test
    void publishesOnlyAFinishedTrackAfterWholeFileDecoding() throws Exception {
        byte[] audio = wav(16_000);
        MusicTrack track = MusicTrack.reference(MusicTestTracks.target("complete"));
        loader = new AudioTrackLoader(new PlaybackResourceResolver(mock(MusicMedia.class)));
        analyzer = new OfflineRhythmAnalyzer(loader, _ -> {});
        RhythmTimeline timeline = new RhythmTimeline(track.id());

        try (OfflineRhythmAnalyzer.Analysis analysis = analyzer.analyze(track, timeline, AudioTrackLoaderStreamingTest.wavLease(audio))) {
            analysis.completion().get(10, TimeUnit.SECONDS);
        }

        assertTrue(timeline.complete());
        assertEquals(1_000L, timeline.analyzedThroughMillis());
        assertEquals(RhythmSource.AUDIO_ANALYSIS, timeline.source());
    }

    @Test
    void completeDecoderPipelinePublishesFastRhythmicAudioAsPlayable() throws Exception {
        byte[] audio = rhythmicWav();
        MusicTrack track = MusicTrack.reference(MusicTestTracks.target("rhythmic"));
        loader = new AudioTrackLoader(new PlaybackResourceResolver(mock(MusicMedia.class)));
        List<String> warnings = new java.util.ArrayList<>();
        analyzer = new OfflineRhythmAnalyzer(loader, warnings::add);
        RhythmTimeline timeline = new RhythmTimeline(track.id());

        try (OfflineRhythmAnalyzer.Analysis analysis = analyzer.analyze(track, timeline, AudioTrackLoaderStreamingTest.wavLease(audio))) {
            analysis.completion().get(10, TimeUnit.SECONDS);
        }

        assertTrue(timeline.complete());
        assertTrue(timeline.playable(), () -> "warnings=" + warnings);
        assertTrue(timeline.baseBeatCount() >= 12,
                () -> "decoded rhythmic beats=" + timeline.baseBeatCount());
        int normalCues = new RhythmChartView(timeline, RhythmDifficulty.NORMAL)
                .between(0L, 4_000L).size();
        assertTrue(normalCues >= 12,
                () -> "normal projected cues=" + normalCues
                        + ", extracted beats=" + timeline.baseBeatCount());
    }

    @Test
    void cancellingOneSubscriberDoesNotPublishItsTimelineOrCancelSharedAnalysis() throws Exception {
        var track = MusicTrack.reference(MusicTestTracks.target("shared-analysis"));
        var underlying = AudioTrackLoaderStreamingTest.wavLease(wav(16_000));
        var lease = mock(MusicMedia.MediaLease.class, org.mockito.AdditionalAnswers.delegatesTo(underlying));
        var reading = new java.util.concurrent.CountDownLatch(1);
        var proceed = new java.util.concurrent.CountDownLatch(1);
        doAnswer(_ -> {
            reading.countDown();
            if (!proceed.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Test read timed out");
            return underlying.openReader();
        }).when(lease).openReader();
        loader = new AudioTrackLoader(new PlaybackResourceResolver(mock(MusicMedia.class)));
        analyzer = new OfflineRhythmAnalyzer(loader, _ -> {});
        var cancelledTimeline = new RhythmTimeline(track.id());
        var timeline = new RhythmTimeline(track.id());
        var cancelled = analyzer.analyze(track, cancelledTimeline, lease);
        try (var analysis = analyzer.analyze(track, timeline, AudioTrackLoaderStreamingTest.wavLease(wav(16_000)))) {
            assertTrue(reading.await(5, TimeUnit.SECONDS));
            cancelled.close();
            assertFalse(analysis.completion().isDone());
            assertFalse(timeline.complete());
            assertEquals(0L, timeline.analyzedThroughMillis());
            proceed.countDown();
            analysis.completion().get(10, TimeUnit.SECONDS);
        } finally {
            cancelled.close();
            proceed.countDown();
        }
        assertTrue(timeline.complete());
        assertEquals(1_000L, timeline.analyzedThroughMillis());
        assertFalse(cancelledTimeline.complete());
    }

    static byte[] wav(int pcmBytes) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(44 + pcmBytes);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeBytes("RIFF");
            writeLittleEndianInt(output, 36 + pcmBytes);
            output.writeBytes("WAVE");
            output.writeBytes("fmt ");
            writeLittleEndianInt(output, 16);
            writeLittleEndianShort(output, 1);
            writeLittleEndianShort(output, 1);
            writeLittleEndianInt(output, 8000);
            writeLittleEndianInt(output, 16000);
            writeLittleEndianShort(output, 2);
            writeLittleEndianShort(output, 16);
            output.writeBytes("data");
            writeLittleEndianInt(output, pcmBytes);
            output.write(new byte[pcmBytes]);
        }
        return bytes.toByteArray();
    }

    private static byte[] rhythmicWav() throws Exception {
        int sampleRate = 8_000;
        int sampleCount = sampleRate * 4;
        int pcmBytes = sampleCount * 2;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(44 + pcmBytes);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeBytes("RIFF");
            writeLittleEndianInt(output, 36 + pcmBytes);
            output.writeBytes("WAVE");
            output.writeBytes("fmt ");
            writeLittleEndianInt(output, 16);
            writeLittleEndianShort(output, 1);
            writeLittleEndianShort(output, 1);
            writeLittleEndianInt(output, sampleRate);
            writeLittleEndianInt(output, sampleRate * 2);
            writeLittleEndianShort(output, 2);
            writeLittleEndianShort(output, 16);
            output.writeBytes("data");
            writeLittleEndianInt(output, pcmBytes);
            for (int sample = 0; sample < sampleCount; sample++) {
                int pulsePosition = sample < 1_000
                        ? Integer.MAX_VALUE : (sample - 1_000) % 2_000;
                double envelope = pulsePosition < 360
                        ? Math.exp(-pulsePosition / 105.0) : 0.0;
                double carrier = Math.sin(2.0 * Math.PI * 95.0
                        * sample / sampleRate);
                writeLittleEndianShort(output,
                        (int) Math.round(envelope * carrier * 18_000.0));
            }
        }
        return bytes.toByteArray();
    }

    private static void writeLittleEndianInt(DataOutputStream output, int value)
            throws Exception {
        output.writeInt(Integer.reverseBytes(value));
    }

    private static void writeLittleEndianShort(DataOutputStream output, int value)
            throws Exception {
        output.writeShort(Short.reverseBytes((short) value));
    }
}
