package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.encinet.mik.feature.entertainment.music.device.JukeboxExperienceMode;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSoundSettings;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia.MediaLease;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import org.encinet.mik.protocol.music.MusicFailure;
import org.encinet.mik.shared.util.ShutdownSequence;

/** One negotiated representation and initial lease per group, independent of its number of outputs. */
final class ManagedPlaybackEngine implements AutoCloseable {
    private final Consumer<Runnable> onMain;
    private final MusicMedia media;
    private final AudioOutput audio;
    private final NoteOutput notes;
    private final Map<JukeboxPlaybackGroup, Group> groups = new HashMap<>();

    @FunctionalInterface
    interface AudioOutput {
        PlaybackSession create(Location location, MusicTrack track, JukeboxSoundSettings settings,
                               PlaybackCallbacks callbacks, RhythmTimeline timeline,
                               JukeboxExperienceMode mode, JukeboxPlaybackGroup group, MediaLease initial);
    }

    @FunctionalInterface
    interface NoteOutput {
        PlaybackSession create(Location location, MediaLease initial, JukeboxSoundSettings settings,
                               PlaybackCallbacks callbacks, RhythmTimeline timeline, JukeboxPlaybackGroup group);
    }

    ManagedPlaybackEngine(Consumer<Runnable> onMain, MusicMedia media, AudioOutput audio, NoteOutput notes) {
        this.onMain = onMain;
        this.media = media;
        this.audio = audio;
        this.notes = notes;
    }

    PlaybackSession create(Location location, MusicTrack track, JukeboxSoundSettings settings, PlaybackCallbacks callbacks,
                           RhythmTimeline timeline, JukeboxExperienceMode mode, JukeboxPlaybackGroup group, Runnable audienceChanged) {
        var shared = groups.computeIfAbsent(group, key -> new Group(track, timeline, mode, key, audienceChanged));
        if (!shared.track.id().equals(track.id())) throw new IllegalArgumentException("Playback group track mismatch");
        var output = new Output(shared, location.clone(), settings, callbacks);
        shared.outputs.add(output);
        return output;
    }

    @Override public void close() {
        var shutdown = new ShutdownSequence();
        for (var group : List.copyOf(groups.values())) {
            for (var output : List.copyOf(group.outputs)) shutdown.attempt("managed output", output::stop);
        }
        shutdown.finish("managed playback");
    }

    private final class Group {
        private final MusicTrack track;
        private final RhythmTimeline timeline;
        private final JukeboxExperienceMode mode;
        private final JukeboxPlaybackGroup group;
        private final Runnable audienceChanged;
        private final Set<Output> outputs = new LinkedHashSet<>();
        private final AtomicBoolean leaseClaimed = new AtomicBoolean();
        private CompletableFuture<? extends MediaLease> opening;
        private PlaybackSession.OutputKind kind = PlaybackSession.OutputKind.UNRESOLVED;
        private boolean disposed;

        private Group(MusicTrack track, RhythmTimeline timeline, JukeboxExperienceMode mode, JukeboxPlaybackGroup group, Runnable audienceChanged) {
            this.track = track;
            this.timeline = timeline;
            this.mode = mode;
            this.group = group;
            this.audienceChanged = audienceChanged;
        }

        void prepare() {
            if (opening != null || disposed) return;
            group.preparing();
            var wait = mode.waitsForRhythmAnalysis() ? MusicMedia.Readiness.COMPLETE : MusicMedia.Readiness.READABLE;
            var target = (TrackTarget.Managed) track.target();
            String exact = group.resumeVersion();
            opening = exact == null ? media.open(target, wait) : media.openExact(target, exact, wait);
            opening.whenComplete((lease, failure) -> {
                try {
                    onMain.accept(() -> {
                        if (lease == null || leaseClaimed.compareAndSet(false, true)) accept(lease, failure);
                    });
                } catch (RuntimeException disabled) {
                    releaseUnclaimed(lease);
                }
            });
        }

        private void accept(MediaLease lease, Throwable error) {
            if (disposed) {
                if (lease != null) lease.close();
                return;
            }
            if (error != null) { fail(error); return; }
            if (group.resumeVersion() != null && !group.resumeVersion().equals(lease.mediaVersion())) {
                lease.close();
                fail(new MusicMedia.ReadException(MusicFailure.Code.VERSION_GONE));
                return;
            }
            kind = switch (lease.descriptor().getRepresentation()) {
                case NOTE_EVENTS -> PlaybackSession.OutputKind.NOTE_EVENTS;
                case ENCODED_AUDIO -> PlaybackSession.OutputKind.ENCODED_AUDIO;
                default -> PlaybackSession.OutputKind.UNRESOLVED;
            };
            if (kind == PlaybackSession.OutputKind.UNRESOLVED || lease.descriptor().getRepresentationVersion() != 1
                    || group.currentSeek().positionMillis() > 0 && !lease.descriptor().getSeekable()) {
                lease.close();
                fail(new MusicMedia.ReadException(MusicFailure.Code.UNSUPPORTED_MEDIA));
                return;
            }
            boolean transferred = false;
            try {
                for (var output : List.copyOf(outputs)) {
                    if (output.stopped) continue;
                    output.attach(transferred ? null : lease);
                    transferred = true;
                }
                audienceChanged.run();
                for (var output : List.copyOf(outputs)) output.begin();
            } catch (RuntimeException failure) {
                fail(failure);
            } finally {
                if (!transferred) lease.close();
            }
        }

        private void fail(Throwable failure) {
            var failed = List.copyOf(outputs);
            for (var output : failed) {
                try { output.stop(); }
                catch (RuntimeException cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            }
            failed.forEach(output -> output.callbacks.failed(failure));
        }

        private void drop(Output output) {
            outputs.remove(output);
            if (!outputs.isEmpty()) return;
            disposed = true;
            groups.remove(group, this);
            if (opening != null && !opening.cancel(false) && opening.isDone() && !opening.isCompletedExceptionally()) {
                releaseUnclaimed(opening.getNow(null));
            }
        }

        private void releaseUnclaimed(MediaLease lease) {
            if (lease != null && leaseClaimed.compareAndSet(false, true)) lease.close();
        }
    }

    private final class Output implements PlaybackSession {
        private final Group shared;
        private final Location location;
        private final PlaybackCallbacks callbacks;
        private JukeboxSoundSettings settings;
        private PlaybackSession backend;
        private boolean wantsStart;
        private boolean stopped;

        private Output(Group shared, Location location, JukeboxSoundSettings settings, PlaybackCallbacks callbacks) {
            this.shared = shared;
            this.location = location;
            this.settings = settings;
            this.callbacks = callbacks;
        }

        private void attach(MediaLease initial) {
            backend = switch (shared.kind) {
                case ENCODED_AUDIO -> audio.create(location, shared.track, settings,
                        callbacks, shared.timeline, shared.mode, shared.group, initial);
                case NOTE_EVENTS -> notes.create(location, initial, settings, callbacks, shared.timeline, shared.group);
                default -> throw new IllegalStateException("No negotiated output");
            };
        }

        private void begin() {
            if (stopped || backend == null) return;
            backend.prepare();
            if (wantsStart) backend.start();
        }

        @Override public void prepare() {
            if (stopped) return;
            if (backend == null && shared.kind != OutputKind.UNRESOLVED) attach(null);
            if (backend == null) shared.prepare();
            else begin();
        }
        @Override public void start() { wantsStart = true; prepare(); }
        @Override public JukeboxPlaybackSnapshot snapshot() {
            return backend == null ? shared.group.snapshot() : backend.snapshot();
        }
        @Override public String mediaVersion() { return backend == null ? null : backend.mediaVersion(); }
        @Override public OutputKind outputKind() { return shared.kind; }
        @Override public void updateSettings(JukeboxSoundSettings updated) {
            settings = updated;
            if (backend != null) backend.updateSettings(updated);
        }
        @Override public void stop() {
            if (stopped) return;
            stopped = true;
            try {
                if (backend != null) backend.stop();
            } finally {
                shared.drop(this);
            }
        }
    }
}
