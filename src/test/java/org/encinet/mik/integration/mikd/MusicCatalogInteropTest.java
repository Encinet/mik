package org.encinet.mik.integration.mikd;

import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;
import org.encinet.mik.feature.entertainment.music.gateway.RemoteMusic;
import org.encinet.mik.protocol.music.LyricsCommand;
import org.encinet.mik.protocol.music.LyricsLookup;
import org.encinet.mik.protocol.music.LyricsRead;
import org.encinet.mik.protocol.music.LyricsRelease;
import org.encinet.mik.protocol.music.LyricsResult;
import org.encinet.mik.protocol.music.LyricsTrack;
import org.encinet.mik.protocol.music.MusicRepresentation;
import org.encinet.mik.protocol.music.NoteScore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("mikd-interop")
class MusicCatalogInteropTest {
    @TempDir Path directory;

    @Test
    void catalogMediaAndCliLyricsRetainTheirIdentityAcrossRestart() throws Exception {
        var source = directory.resolve("fixture.nbs");
        var bytes = ByteBuffer.allocate(59).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort((short) 2).putShort((short) 1);
        bytes.position(20); // empty metadata strings, then initial tempo
        bytes.putShort((short) 1000);
        bytes.position(49); // end of the classic header
        bytes.put(new byte[]{1, 0, 1, 0, 0, 45, 0, 0, 0, 0});
        Files.write(source, bytes.array());

        TrackTarget.Managed target;
        String version;
        String revision;
        try (var backend = MikdTestBackend.music(directory); var music = backend.music()) {
            var ids = new ArrayList<String>();
            for (int index = 0; index < 25; index++) {
                var imported = JsonParser.parseString(admin(backend, "library", "import", source.toString()))
                        .getAsJsonObject().getAsJsonObject("data");
                ids.add(imported.get("id").getAsString());
            }
            Files.delete(source);
            var firstId = ids.getFirst();
            target = new TrackTarget.Managed(firstId);
            var page = music.catalog("", false, ByteString.EMPTY).get(5, TimeUnit.SECONDS);
            assertEquals(24, page.tracks().size());
            assertFalse(page.nextCursor().isEmpty());
            assertFalse(page.partial());
            var next = music.catalog("", false, page.nextCursor()).get(5, TimeUnit.SECONDS);
            assertTrue(next.nextCursor().isEmpty());
            var returned = new HashSet<>(page.tracks().stream().map(MusicTrack::id).toList());
            assertTrue(returned.add(next.tracks().getFirst().id()));
            assertEquals(new HashSet<>(ids), returned);
            var lookup = music.tracks(List.of(firstId, MusicTestTracks.id("missing"))).get(5, TimeUnit.SECONDS);
            assertEquals(1, lookup.size());
            assertEquals(MusicTrack.Availability.READY, lookup.get(firstId).availability());
            try (var media = music.open(target, MusicMedia.Readiness.COMPLETE).get(5, TimeUnit.SECONDS)) {
                version = media.mediaVersion();
                assertEquals(MusicRepresentation.NOTE_EVENTS, media.descriptor().getRepresentation());
                assertScore(media);
            }
            var diagnostics = JsonParser.parseString(admin(backend, "track", "inspect", firstId))
                    .getAsJsonObject().getAsJsonObject("data");
            assertEquals(version, diagnostics.getAsJsonObject("media").get("version").getAsString());
            assertTrue(backend.log().contains("track=" + firstId + " version=" + version + " source=managed_library"));

            var correction = directory.resolve("lyrics.json");
            Files.writeString(correction, """
                    [
                      {"role":"original","language_tag":"ja","kind":"timed",
                       "cues":[{"start_ms":30,"end_ms":75,"text":"鐘"}]},
                      {"role":"translation","language_tag":"en","kind":"text","lines":["Bell"]}
                    ]
                    """);
            admin(backend, "lyrics", "set", version, correction.toString());
            revision = assertLyrics(music, version);
        }
        try (var backend = MikdTestBackend.music(directory); var music = backend.music();
             var media = music.openExact(target, version, MusicMedia.Readiness.COMPLETE).get(5, TimeUnit.SECONDS)) {
            assertEquals(version, media.mediaVersion());
            assertScore(media);
            assertEquals(revision, assertLyrics(music, version));
            admin(backend, "lyrics", "clear", version);
            assertEquals(LyricsResult.State.EMPTY, lookupLyrics(music, version).getState());
        }
    }

    private static String assertLyrics(RemoteMusic music, String version) throws Exception {
        var lookup = lookupLyrics(music, version);
        assertEquals(LyricsResult.State.READY, lookup.getState());
        var document = lookup.getDocument();
        assertEquals(version, document.getMediaVersion());
        assertEquals(2, document.getTracksCount());
        for (var track : document.getTracksList()) {
            var page = music.lyrics(LyricsCommand.newBuilder().setRead(LyricsRead.newBuilder()
                    .setTicket(lookup.getTicket()).setTrackKey(track.getTrackKey())).build())
                    .get(5, TimeUnit.SECONDS).getPage();
            if (track.getRole() == LyricsTrack.Role.ORIGINAL) {
                assertEquals(track.getTrackKey(), document.getDefaultOriginalTrack());
                assertEquals("ja", track.getLanguageTag());
                assertTrue(track.getTimed());
                assertEquals(30, page.getCues(0).getStartMs());
                assertEquals(75, page.getCues(0).getEndMs());
                assertEquals("鐘", page.getCues(0).getText());
            } else {
                assertEquals(LyricsTrack.Role.TRANSLATION, track.getRole());
                assertEquals("en", track.getLanguageTag());
                assertFalse(track.getTimed());
                assertEquals(List.of("Bell"), page.getLinesList());
            }
        }
        assertEquals(LyricsResult.State.RELEASED, music.lyrics(LyricsCommand.newBuilder()
                .setRelease(LyricsRelease.newBuilder().setTicket(lookup.getTicket())).build())
                .get(5, TimeUnit.SECONDS).getState());
        return document.getRevision();
    }

    private static LyricsResult lookupLyrics(RemoteMusic music, String version) throws Exception {
        return music.lyrics(LyricsCommand.newBuilder().setLookup(LyricsLookup.newBuilder().setMediaVersion(version)).build())
                .get(5, TimeUnit.SECONDS);
    }

    private String admin(MikdTestBackend backend, String... args) throws Exception {
        var command = new ArrayList<>(List.of(
                System.getProperty("mikd.binary", Path.of("target/debug/mikd").toAbsolutePath().toString()),
                "admin", "--socket", backend.adminSocket().toString(), "music"));
        command.addAll(List.of(args));
        Path output = Files.createTempFile(directory, "mikd-cli-", ".log");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().keySet().removeIf(name -> name.startsWith("MIKD_"));
        var process = builder.start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "mikd CLI did not finish");
            String result = Files.readString(output);
            assertEquals(0, process.exitValue(), result);
            return result;
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void assertScore(MusicMedia.MediaLease media) throws Exception {
        try (var reader = media.openReader(); var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[128];
            for (int count; (count = reader.read(buffer, 0, buffer.length)) >= 0;) output.write(buffer, 0, count);
            var score = NoteScore.parseFrom(output.toByteArray());
            assertEquals(200_000, score.getDurationUs());
            assertEquals(1, score.getEventsCount());
            assertEquals("harp", score.getEvents(0).getSound().getTone());
        }
    }
}
