package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.online.OnlineAudioCache;

import java.io.EOFException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Allows one fresh download after an online decoder reaches a broken remote stream. */
final class CorruptAudioRecovery {
    private final AtomicBoolean retryUsed = new AtomicBoolean();

    boolean shouldRetry(MusicTrack music, Throwable error) {
        return isRecoverable(music, error)
                && retryUsed.compareAndSet(false, true);
    }

    static boolean isRecoverable(MusicTrack music, Throwable error) {
        return music != null
                && music.target() instanceof TrackTarget.Lx
                && (containsUnexpectedEof(error)
                || OnlineAudioCache.isRecoverableRemoteFailure(error));
    }

    static boolean containsUnexpectedEof(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        while (current != null && seen.add(current)) {
            if (current instanceof EOFException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** Chooses the closest safe playback position for a freshly resolved copy of the track. */
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
