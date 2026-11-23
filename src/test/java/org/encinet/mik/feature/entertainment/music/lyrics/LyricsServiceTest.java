package org.encinet.mik.feature.entertainment.music.lyrics;

import com.google.protobuf.ByteString;
import org.encinet.mik.protocol.music.LyricsCue;
import org.encinet.mik.protocol.music.LyricsDocument;
import org.encinet.mik.protocol.music.LyricsPage;
import org.encinet.mik.protocol.music.LyricsResult;
import org.encinet.mik.protocol.music.LyricsTrack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LyricsServiceTest {
    private static final String VERSION = "1".repeat(32);
    private static final ByteString TICKET = ByteString.copyFrom(new byte[16]);

    @Test
    void assemblesIndependentTimedAndPlainTracksAcrossPagesAndReleasesTheRevision() throws Exception {
        var releases = new AtomicInteger();
        try (var service = new LyricsService(command -> CompletableFuture.completedFuture(switch (command.getOperationCase()) {
            case LOOKUP -> ready().setDocument(document(VERSION)).build();
            case READ -> {
                var read = command.getRead();
                var page = LyricsPage.newBuilder().setTrackKey(read.getTrackKey()).setOffset(read.getOffset());
                if (read.getTrackKey() == 2) page.addLines("Translated without verified time");
                else if (read.getOffset() == 0) page.addCues(cue(0, 500, "First")).setNextOffset(1);
                else page.addCues(cue(700, 800, "Second"));
                yield ready().setPage(page).build();
            }
            case RELEASE -> { releases.incrementAndGet(); yield LyricsResult.getDefaultInstance(); }
            default -> throw new AssertionError("Unexpected lyric request");
        }))) {
            var lyrics = assertInstanceOf(LyricsService.Available.class, service.load(VERSION).get(3, TimeUnit.SECONDS)).document();
            var original = lyrics.choose(Lyrics.Role.ORIGINAL, Locale.LanguageRange.parse("ja")).orElseThrow();
            assertEquals("First", original.textAt(499).orElseThrow());
            assertTrue(original.textAt(500).isEmpty());
            assertEquals("Second", original.textAt(700).orElseThrow());
            var translation = lyrics.choose(Lyrics.Role.TRANSLATION, Locale.LanguageRange.parse("en-US")).orElseThrow();
            assertEquals(List.of("Translated without verified time"), translation.transcription());
            assertTrue(translation.textAt(0).isEmpty());
            assertTrue(lyrics.choose(Lyrics.Role.TRANSLATION, Locale.LanguageRange.parse("zh")).isEmpty());
            assertEquals(1, releases.get());
        }
    }

    @Test
    void staleVersionIsRejectedEvenIfTheDaemonReturnsAValidTicket() {
        var releases = new AtomicInteger();
        try (var service = new LyricsService(command -> CompletableFuture.completedFuture(switch (command.getOperationCase()) {
            case LOOKUP -> ready().setDocument(document("2".repeat(32))).build();
            case RELEASE -> { releases.incrementAndGet(); yield LyricsResult.getDefaultInstance(); }
            default -> throw new AssertionError("Version mismatch must not start paging");
        }))) {
            assertThrows(ExecutionException.class, () -> service.load(VERSION).get(3, TimeUnit.SECONDS));
            assertEquals(1, releases.get());
        }
    }

    private static LyricsResult.Builder ready() {
        return LyricsResult.newBuilder().setState(LyricsResult.State.READY).setTicket(TICKET);
    }

    private static LyricsDocument.Builder document(String version) {
        return LyricsDocument.newBuilder().setMediaVersion(version).setRevision("a".repeat(64)).setDefaultOriginalTrack(1)
                .addTracks(LyricsTrack.newBuilder().setTrackKey(1).setRole(LyricsTrack.Role.ORIGINAL).setTimed(true))
                .addTracks(LyricsTrack.newBuilder().setTrackKey(2).setRole(LyricsTrack.Role.TRANSLATION).setLanguageTag("en"));
    }

    private static LyricsCue.Builder cue(long start, long end, String text) {
        return LyricsCue.newBuilder().setStartMs(start).setEndMs(end).setText(text);
    }
}
