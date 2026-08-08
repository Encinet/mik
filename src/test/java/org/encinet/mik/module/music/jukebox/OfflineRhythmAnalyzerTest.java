package org.encinet.mik.module.music.jukebox;

import com.sun.net.httpserver.HttpServer;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.online.OnlineAudioCache;
import org.encinet.mik.module.music.online.OnlineAudioCacheTestSupport;
import org.encinet.mik.module.music.rhythm.RhythmChartView;
import org.encinet.mik.module.music.rhythm.RhythmDifficulty;
import org.encinet.mik.module.music.rhythm.analysis.RhythmSource;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfflineRhythmAnalyzerTest {

    @TempDir
    Path directory;

    private OnlineAudioCache cache;
    private AudioTrackLoader loader;
    private OfflineRhythmAnalyzer analyzer;
    private HttpServer server;

    @AfterEach
    void close() {
        if (analyzer != null) analyzer.close();
        if (loader != null) loader.close();
        if (cache != null) cache.close();
        if (server != null) server.stop(0);
    }

    @Test
    void publishesOnlyAFinishedTrackAfterWholeFileDecoding() throws Exception {
        Path audio = Files.write(directory.resolve("complete.wav"), wav(16_000));
        MusicTrack track = new MusicTrack("complete.wav",
                new TrackDetails("Complete", "Test", null, "WAV",
                        new AudioProperties(null, null, Duration.ofSeconds(1))),
                new TrackTarget.LocalFile(audio));
        cache = OnlineAudioCacheTestSupport.create(directory.resolve("cache"),
                ignored -> CompletableFuture.failedFuture(
                        new AssertionError("Local audio must not resolve online")));
        loader = new AudioTrackLoader(new PlaybackResourceResolver(cache));
        analyzer = new OfflineRhythmAnalyzer(loader, ignored -> {});
        RhythmTimeline timeline = new RhythmTimeline(track.id());

        try (OfflineRhythmAnalyzer.Analysis analysis = analyzer.analyze(track, timeline)) {
            analysis.completion().get(10, TimeUnit.SECONDS);
        }

        assertTrue(timeline.complete());
        assertEquals(1_000L, timeline.analyzedThroughMillis());
        assertEquals(RhythmSource.AUDIO_ANALYSIS, timeline.source());
    }

    @Test
    void completeDecoderPipelinePublishesFastRhythmicAudioAsPlayable() throws Exception {
        Path audio = Files.write(directory.resolve("rhythmic.wav"), rhythmicWav());
        MusicTrack track = new MusicTrack("rhythmic.wav",
                new TrackDetails("Rhythmic", "Test", null, "WAV",
                        new AudioProperties(null, null, Duration.ofSeconds(4))),
                new TrackTarget.LocalFile(audio));
        cache = OnlineAudioCacheTestSupport.create(directory.resolve("rhythm-cache"),
                ignored -> CompletableFuture.failedFuture(
                        new AssertionError("Local audio must not resolve online")));
        loader = new AudioTrackLoader(new PlaybackResourceResolver(cache));
        List<String> warnings = new java.util.ArrayList<>();
        analyzer = new OfflineRhythmAnalyzer(loader, warnings::add);
        RhythmTimeline timeline = new RhythmTimeline(track.id());

        try (OfflineRhythmAnalyzer.Analysis analysis = analyzer.analyze(track, timeline)) {
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
    void keepsThePublishedTimelineEmptyUntilOnlineCachingAndAnalysisFinish()
            throws Exception {
        byte[] audio = wav(16_000);
        int initialBytes = 8_192;
        CountDownLatch initialChunkSent = new CountDownLatch(1);
        CountDownLatch releaseRemainingAudio = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/complete.wav", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, audio.length);
            exchange.getResponseBody().write(audio, 0, initialBytes);
            exchange.getResponseBody().flush();
            initialChunkSent.countDown();
            try {
                releaseRemainingAudio.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write(audio, initialBytes,
                        audio.length - initialBytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort()
                + "/complete.wav";
        cache = OnlineAudioCacheTestSupport.create(directory.resolve("online-cache"),
                ignored -> CompletableFuture.completedFuture(url));
        loader = new AudioTrackLoader(new PlaybackResourceResolver(cache));
        analyzer = new OfflineRhythmAnalyzer(loader, ignored -> {});
        MusicTrack track = onlineTrack();
        RhythmTimeline timeline = new RhythmTimeline(track.id());

        try (OfflineRhythmAnalyzer.Analysis analysis = analyzer.analyze(track, timeline)) {
            assertTrue(initialChunkSent.await(5, TimeUnit.SECONDS));
            try {
                assertFalse(analysis.completion().isDone());
                assertFalse(timeline.complete());
                assertEquals(0L, timeline.analyzedThroughMillis());
                assertEquals(0, timeline.baseBeatCount());
            } finally {
                releaseRemainingAudio.countDown();
            }
            analysis.completion().get(10, TimeUnit.SECONDS);
        }

        assertTrue(timeline.complete());
        assertEquals(1_000L, timeline.analyzedThroughMillis());
    }

    private static MusicTrack onlineTrack() {
        TrackTarget.Lx target = new TrackTarget.Lx("kw", "rhythm-complete-test",
                List.of("320k"),
                "{\"id\":\"rhythm-complete-test\",\"source\":\"kw\","
                        + "\"meta\":{\"songId\":\"rhythm-complete-test\"}}");
        return new MusicTrack("lx:kw:rhythm-complete-test",
                new TrackDetails("Complete", "Test", null, "WAV",
                        new AudioProperties(null, null, Duration.ofSeconds(1))), target);
    }

    private static byte[] wav(int pcmBytes) throws Exception {
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
