package org.encinet.mik.feature.entertainment.music.application;

import java.util.Objects;
import java.util.UUID;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackSnapshot;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;

/** One main-thread sample of a logical player's identity, media and clock; physical discs have no queue entry. */
public record MusicPlaybackSnapshot(UUID target, UUID entry, UUID generation, MusicTrack track,
                                    JukeboxPlaybackSnapshot clock, long sampledAtNanos, UUID preparation, String mediaVersion) {
    public MusicPlaybackSnapshot {
        Objects.requireNonNull(target); Objects.requireNonNull(clock);
        if (clock.seekable() && (generation == null || track == null))
            throw new IllegalArgumentException("Seeking requires an identified playback run");
        if (preparation != null && (clock.status() != PlaybackStatus.LOADING || generation == null))
            throw new IllegalArgumentException("Preparation cancellation requires an identified loading run");
    }

    public PlaybackStatus status() { return clock.status(); }
    public long positionMillis() { return clock.positionMillis(); }
    public boolean seekable() { return clock.seekable(); }

    public long durationMillis() {
        if (clock.durationMillis() > 0) return clock.durationMillis();
        if (status() == PlaybackStatus.PLAYING || status() == PlaybackStatus.BUFFERING || status() == PlaybackStatus.PAUSED) return 0;
        if (track == null || track.details().durationHint() == null) return 0;
        try { return Math.max(0, track.details().durationHint().toMillis()); }
        catch (ArithmeticException overflow) { return 0; }
    }

    /** Extrapolate only until the next expected sample; stale/offline clocks must not run indefinitely. */
    public long positionAt(long nowNanos) {
        long elapsed = status() == PlaybackStatus.PLAYING
                ? Math.clamp((nowNanos - sampledAtNanos) / 1_000_000, 0, 1500) : 0;
        long positionMillis = positionMillis();
        long position = positionMillis > Long.MAX_VALUE - elapsed ? Long.MAX_VALUE : positionMillis + elapsed;
        long duration = durationMillis();
        return duration > 0 ? Math.min(duration, position) : position;
    }

    /** Queue edits may change a version without changing playback; track ids alone cannot identify repeats. */
    public boolean samePlayback(MusicPlaybackSnapshot other) {
        return other != null && target.equals(other.target) && Objects.equals(entry, other.entry)
                && Objects.equals(generation, other.generation)
                && Objects.equals(track == null ? null : track.id(), other.track == null ? null : other.track.id());
    }

    public boolean sameMedia(MusicPlaybackSnapshot other) {
        return generation != null && mediaVersion != null && samePlayback(other) && mediaVersion.equals(other.mediaVersion);
    }

    /** A reconnect may prepare again inside the same run; an old cancel must not stop that replacement. */
    public boolean samePreparation(MusicPlaybackSnapshot other) {
        return preparation != null && status() == PlaybackStatus.LOADING && samePlayback(other)
                && other.status() == PlaybackStatus.LOADING && preparation.equals(other.preparation);
    }
}
