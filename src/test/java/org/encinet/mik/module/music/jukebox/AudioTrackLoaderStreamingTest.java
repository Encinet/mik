package org.encinet.mik.module.music.jukebox;

import com.sun.net.httpserver.HttpServer;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.online.OnlineAudioCache;
import org.encinet.mik.module.music.online.OnlineAudioCacheTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioTrackLoaderStreamingTest {

    @TempDir
    Path directory;

    private HttpServer server;
    private OnlineAudioCache cache;
    private AudioTrackLoader loader;

    @AfterEach
    void close() {
        if (loader != null) {
            loader.close();
        }
        if (cache != null) {
            cache.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void loadsOnlineTrackBeforeTheHttpResponseFinishes() throws Exception {
        byte[] wav = wav(4096);
        int initialBytes = 1024;
        CountDownLatch initialChunkSent = new CountDownLatch(1);
        CountDownLatch releaseRemainingAudio = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stream.wav", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, wav.length);
            exchange.getResponseBody().write(wav, 0, initialBytes);
            exchange.getResponseBody().flush();
            initialChunkSent.countDown();
            try {
                releaseRemainingAudio.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write(wav, initialBytes,
                        wav.length - initialBytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/stream.wav";
        cache = OnlineAudioCacheTestSupport.create(directory,
                ignored -> CompletableFuture.completedFuture(url));
        loader = new AudioTrackLoader(new PlaybackResourceResolver(cache));
        MusicTrack track = onlineTrack();

        CompletableFuture<AudioTrackLoader.LoadedAudio> loading = loader.load(track);
        assertTrue(initialChunkSent.await(5, TimeUnit.SECONDS));
        try (AudioTrackLoader.LoadedAudio loaded = loading.get(2, TimeUnit.SECONDS)) {
            assertFalse(cache.isCached((TrackTarget.Lx) track.target()));
        } finally {
            releaseRemainingAudio.countDown();
        }

        assertTrue(cache.resolve((TrackTarget.Lx) track.target())
                .thenApply(Path::of).thenApply(java.nio.file.Files::isRegularFile)
                .get(5, TimeUnit.SECONDS));
    }

    @Test
    void completeLoadWaitsForTheEntireOnlineResponse() throws Exception {
        byte[] wav = wav(4096);
        int initialBytes = 1024;
        CountDownLatch initialChunkSent = new CountDownLatch(1);
        CountDownLatch releaseRemainingAudio = new CountDownLatch(1);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stream.wav", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/wav");
            exchange.sendResponseHeaders(200, wav.length);
            exchange.getResponseBody().write(wav, 0, initialBytes);
            exchange.getResponseBody().flush();
            initialChunkSent.countDown();
            try {
                releaseRemainingAudio.await(5, TimeUnit.SECONDS);
                exchange.getResponseBody().write(wav, initialBytes,
                        wav.length - initialBytes);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/stream.wav";
        cache = OnlineAudioCacheTestSupport.create(directory,
                ignored -> CompletableFuture.completedFuture(url));
        loader = new AudioTrackLoader(new PlaybackResourceResolver(cache));
        MusicTrack track = onlineTrack();

        CompletableFuture<AudioTrackLoader.LoadedAudio> loading = loader.loadComplete(track);
        assertTrue(initialChunkSent.await(5, TimeUnit.SECONDS));
        try {
            assertFalse(loading.isDone());
            assertFalse(cache.isCached((TrackTarget.Lx) track.target()));
        } finally {
            releaseRemainingAudio.countDown();
        }

        try (AudioTrackLoader.LoadedAudio ignored = loading.get(5, TimeUnit.SECONDS)) {
            assertTrue(cache.isCached((TrackTarget.Lx) track.target()));
        }
    }

    private static MusicTrack onlineTrack() {
        TrackTarget.Lx target = new TrackTarget.Lx("kw", "streaming-test",
                List.of("320k"),
                "{\"id\":\"streaming-test\",\"source\":\"kw\","
                        + "\"meta\":{\"songId\":\"streaming-test\"}}");
        return new MusicTrack("lx:kw:streaming-test",
                new TrackDetails("Streaming", "Test", null, "WAV",
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

    private static void writeLittleEndianInt(DataOutputStream output, int value)
            throws Exception {
        output.writeInt(Integer.reverseBytes(value));
    }

    private static void writeLittleEndianShort(DataOutputStream output, int value)
            throws Exception {
        output.writeShort(Short.reverseBytes((short) value));
    }
}
