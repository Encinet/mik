package org.encinet.mik.feature.entertainment.music.jukebox;

import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorruptAudioRecoveryTest {

    @Test
    void distinguishesDecoderEofFromAnInterruptedRead() {
        Throwable error = new CompletionException(
                new IllegalStateException("decoder failed", new EOFException("truncated")));

        assertFalse(CorruptAudioRecovery.containsUnexpectedEof(new IOException("network unavailable")));
        var interrupted = new InterruptedIOException("seek interrupted the read");
        interrupted.initCause(new EOFException("socket closed"));
        assertFalse(CorruptAudioRecovery.containsUnexpectedEof(interrupted));
        assertTrue(CorruptAudioRecovery.containsUnexpectedEof(error));
    }

    @Test
    void eachUnfixedIntentGetsOnlyOneRetryAndOnlyBeforeItsFirstPreparedFrame() {
        assertFalse(new CorruptAudioRecovery().takeRetry(true, false));
        assertFalse(new CorruptAudioRecovery().takeRetry(false, true));
        var fresh = new CorruptAudioRecovery();
        assertTrue(fresh.takeRetry(false, false));
        assertFalse(fresh.takeRetry(false, false));
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

}
