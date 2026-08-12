package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorruptAudioRecoveryTest {

    @Test
    void retriesANestedUnexpectedEofForOnlineAudioOnlyOnce() {
        CorruptAudioRecovery recovery = new CorruptAudioRecovery();
        Throwable error = new CompletionException(
                new IllegalStateException("decoder failed", new EOFException("truncated")));

        assertTrue(recovery.shouldRetry(onlineTrack(), error));
        assertFalse(recovery.shouldRetry(onlineTrack(), error));
    }

    @Test
    void unrelatedFailureDoesNotConsumeTheSingleCorruptionRetry() {
        CorruptAudioRecovery recovery = new CorruptAudioRecovery();

        assertFalse(recovery.shouldRetry(onlineTrack(), new IOException("network unavailable")));
        assertTrue(recovery.shouldRetry(onlineTrack(), new EOFException("truncated")));
    }

    @Test
    void neverRetriesAUserManagedLocalFile() {
        CorruptAudioRecovery recovery = new CorruptAudioRecovery();
        MusicTrack local = new MusicTrack("local:test.mp3",
                details(), new TrackTarget.LocalFile(Path.of("music/test.mp3")));

        assertFalse(recovery.shouldRetry(local, new EOFException("truncated")));
    }

    @Test
    void resumesAtThePreviousPositionWhenTheReplacementTrackIsSeekable() {
        assertEquals(42_000L,
                CorruptAudioRecovery.resumePositionMillis(42_000L, true, 180_000L));
        assertEquals(9_999L,
                CorruptAudioRecovery.resumePositionMillis(12_000L, true, 10_000L));
        assertEquals(42_000L,
                CorruptAudioRecovery.resumePositionMillis(42_000L, true, Long.MAX_VALUE));
    }

    @Test
    void restartsOnlyWhenTheReplacementTrackCannotSeek() {
        assertEquals(0L,
                CorruptAudioRecovery.resumePositionMillis(42_000L, false, 180_000L));
    }

    private static MusicTrack onlineTrack() {
        return new MusicTrack("lx:kw:test", details(),
                new TrackTarget.Lx("kw", "test", List.of("320k"),
                        "{\"id\":\"test\",\"source\":\"kw\","
                                + "\"meta\":{\"songId\":\"test\"}}"));
    }

    private static TrackDetails details() {
        return new TrackDetails("Test", "Artist", null, "MP3", AudioProperties.EMPTY);
    }
}
