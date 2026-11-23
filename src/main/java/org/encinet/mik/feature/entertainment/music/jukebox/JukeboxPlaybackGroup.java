package org.encinet.mik.feature.entertainment.music.jukebox;

import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricsService;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Shares control intent and the backend session clock across physical outputs. */
final class JukeboxPlaybackGroup {
    private final LongSupplier nanoClock;
    private final AtomicReference<Seek> seek = new AtomicReference<>(new Seek(0L, 0L));
    private boolean startedAnnouncement;
    private String resumeVersion;
    private LyricsService.Playback lyrics;

    LyricsService.Playback lyrics() { return lyrics; }

    LyricsService.Playback lyrics(Supplier<LyricsService.Playback> create) {
        if (lyrics == null) lyrics = create.get();
        return lyrics;
    }

    void pinVersion(String version) {
        if (version == null) return;
        if (resumeVersion != null && !resumeVersion.equals(version)) throw new IllegalStateException("Media version changed within a run");
        resumeVersion = version;
    }
    String resumeVersion() { return resumeVersion; }
    private RhythmTimeline timeline;
    private Supplier<JukeboxPlaybackSnapshot> clock;
    private JukeboxPlaybackSnapshot detached;
    private volatile Pause pause = new Pause(0L, false);

    JukeboxPlaybackGroup() { this(System::nanoTime); }

    JukeboxPlaybackGroup(LongSupplier nanoClock) { this.nanoClock = nanoClock; }

    long nanoTime() { return nanoClock.getAsLong(); }

    void seekTo(long positionMillis) {
        seek.updateAndGet(previous -> new Seek(previous.version() + 1L, Math.max(0L, positionMillis)));
    }

    Seek currentSeek() { return seek.get(); }

    RhythmTimeline timeline(String trackId) {
        if (timeline == null) timeline = new RhythmTimeline(trackId);
        return timeline;
    }

    boolean claimStartedAnnouncement() {
        if (startedAnnouncement) return false;
        startedAnnouncement = true;
        return true;
    }

    boolean paused() { return pause.paused(); }

    Pause currentPause() { return pause; }

    void preparing() { detached = null; }

    void follow(Supplier<JukeboxPlaybackSnapshot> backendClock) { clock = backendClock; detached = null; }

    void releaseClock() {
        if (resumeVersion != null && clock != null) {
            long position = recoveryPosition();
            seekTo(position);
            detached = new JukeboxPlaybackSnapshot(PlaybackStatus.RESUMABLE, position);
        }
        clock = null;
        timeline = null;
    }

    JukeboxPlaybackSnapshot snapshot() {
        return clock != null ? clock.get() : detached != null ? detached
                : new JukeboxPlaybackSnapshot(PlaybackStatus.LOADING, currentSeek().positionMillis());
    }

    long recoveryPosition() {
        if (resumeVersion == null) return 0;
        var sample = snapshot();
        if (sample.status() == PlaybackStatus.LOADING || sample.status() == PlaybackStatus.RESUMABLE)
            return currentSeek().positionMillis();
        return sample.seekable() ? sample.positionMillis() : 0;
    }

    JukeboxPlaybackSnapshot present(JukeboxPlaybackSnapshot backend) {
        return switch (backend.status()) {
            case PLAYING, BUFFERING, PAUSED -> backend.withStatus(paused() ? PlaybackStatus.PAUSED
                    : backend.status() == PlaybackStatus.PAUSED ? PlaybackStatus.PLAYING : backend.status());
            default -> backend;
        };
    }

    void setPaused(boolean value) {
        if (value != pause.paused()) pause = new Pause(pause.version() + 1L, value);
    }

    record Pause(long version, boolean paused) { }

    record Seek(long version, long positionMillis) { }
}
