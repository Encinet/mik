package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;

import java.io.EOFException;
import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Allows one retry before output when a decoder reports a truncated representation. */
final class CorruptAudioRecovery {
    private boolean retryUsed;

    boolean takeRetry(boolean versionFixed, boolean preparedFrame) {
        if (versionFixed || preparedFrame || retryUsed) return false;
        retryUsed = true;
        return true;
    }

    static boolean containsUnexpectedEof(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        boolean eof = false;
        while (current != null && seen.add(current)) {
            if (current instanceof MusicMedia.ReadException || current instanceof InterruptedIOException) return false;
            eof |= current instanceof EOFException;
            current = current.getCause();
        }
        return eof;
    }

    /** Chooses the closest safe playback position within a seekable representation. */
    static long resumePositionMillis(long previousPositionMillis, boolean seekable,
                                     long durationMillis) {
        if (!seekable) {
            return 0L;
        }
        long position = Math.max(0L, previousPositionMillis);
        if (durationMillis > 0L && durationMillis != Long.MAX_VALUE) {
            return Math.min(position, Math.max(0L, durationMillis - 1L));
        }
        return position;
    }
}
