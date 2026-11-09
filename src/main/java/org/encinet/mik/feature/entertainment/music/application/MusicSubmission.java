package org.encinet.mik.feature.entertainment.music.application;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;

/** One target and one durable queue receipt. Output retries cannot submit the songs twice. */
public final class MusicSubmission {
    private final UUID actor;
    private final UUID target;
    private final List<MusicTrack> tracks;
    private final PlaybackProgram.Submission placement;
    private record Receipt(PlaybackProgram program, long intent) { }
    private Receipt receipt;
    private boolean active;
    private boolean completed;

    public MusicSubmission(UUID actor, UUID target, List<MusicTrack> tracks, PlaybackProgram.Submission placement) {
        this.actor = Objects.requireNonNull(actor);
        this.target = Objects.requireNonNull(target);
        this.tracks = List.copyOf(tracks);
        this.placement = Objects.requireNonNull(placement);
        if (tracks.isEmpty()) throw new MusicProblem(MusicProblem.Code.INVALID);
        if (tracks.size() > 5000) throw new MusicProblem(MusicProblem.Code.LIMIT);
    }

    public UUID target() { return target; }
    public List<MusicTrack> tracks() { return tracks; }
    public PlaybackProgram.Submission placement() { return placement; }
    public synchronized boolean retryable() { return !active && !completed; }
    synchronized void begin(UUID player) {
        if (!actor.equals(player)) throw new MusicProblem(MusicProblem.Code.FORBIDDEN);
        if (active) throw new MusicProblem(MusicProblem.Code.BUSY);
        if (completed) throw new MusicProblem(MusicProblem.Code.CONFLICT);
        active = true;
    }
    private synchronized void committed(PlaybackProgram program, long intent) { receipt = new Receipt(program, intent); }
    CompletableFuture<Void> attempt(Supplier<PlaybackProgram> current, LongPredicate currentIntent,
            Function<BiConsumer<PlaybackProgram, Long>, CompletableFuture<Void>> insert, Runnable retryOutput) {
        Receipt saved;
        synchronized (this) { saved = receipt; }
        if (saved == null) return insert.apply(this::committed);
        if (!saved.program().equals(current.get()) || !currentIntent.test(saved.intent()))
            throw new MusicProblem(MusicProblem.Code.CONFLICT);
        if (placement == PlaybackProgram.Submission.INSERT) retryOutput.run();
        return CompletableFuture.completedFuture(null);
    }
    synchronized void finish() { active = false; completed = true; }
    synchronized void failed() { active = false; }
}
