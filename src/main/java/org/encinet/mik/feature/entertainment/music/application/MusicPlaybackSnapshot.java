package org.encinet.mik.feature.entertainment.music.application;

import java.util.Objects;
import java.util.UUID;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;

/** One main-thread sample of a logical player's identity, media and clock; physical discs have no queue entry. */
public record MusicPlaybackSnapshot(UUID target, UUID entry, UUID generation, MusicTrack track,
                                    PlaybackStatus status, long positionMillis, long sampledAtNanos, boolean seekable, UUID preparation) {
    public MusicPlaybackSnapshot {
        Objects.requireNonNull(target); Objects.requireNonNull(status);
        positionMillis = Math.max(0, positionMillis);
        if (seekable && (generation == null || track == null))
            throw new IllegalArgumentException("Seeking requires an identified playback run");
        if (preparation != null && (status != PlaybackStatus.LOADING || generation == null))
            throw new IllegalArgumentException("Preparation cancellation requires an identified loading run");
    }

    public long durationMillis() {
        if (track == null || track.details().audio().duration() == null) return 0;
        try { return Math.max(0, track.details().audio().duration().toMillis()); }
        catch (ArithmeticException overflow) { return 0; }
    }

    /** Extrapolate only until the next expected sample; stale/offline clocks must not run indefinitely. */
    public long positionAt(long nowNanos) {
        long elapsed = status == PlaybackStatus.PLAYING
                ? Math.clamp((nowNanos - sampledAtNanos) / 1_000_000, 0, 1500) : 0;
        long position = positionMillis > Long.MAX_VALUE - elapsed ? Long.MAX_VALUE : positionMillis + elapsed;
        return durationMillis() > 0 ? Math.min(durationMillis(), position) : position;
    }

    /** Queue edits may change a version without changing playback; track ids alone cannot identify repeats. */
    public boolean samePlayback(MusicPlaybackSnapshot other) {
        return other != null && target.equals(other.target) && Objects.equals(entry, other.entry)
                && Objects.equals(generation, other.generation) && Objects.equals(track, other.track);
    }

    /** A reconnect may prepare again inside the same run; an old cancel must not stop that replacement. */
    public boolean samePreparation(MusicPlaybackSnapshot other) {
        return preparation != null && status == PlaybackStatus.LOADING && samePlayback(other)
                && other.status == PlaybackStatus.LOADING && preparation.equals(other.preparation);
    }
}
