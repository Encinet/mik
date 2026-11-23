package org.encinet.mik.feature.entertainment.music.jukebox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.gateway.MusicMedia.MediaLease;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmAudioFilterFactory;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmSource;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.RhythmTimeline;
import org.encinet.mik.feature.entertainment.music.rhythm.analysis.WholeTrackRhythmExtractor;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Shares bounded whole-track analysis without sharing the callers' timelines or leases with playback. */
final class OfflineRhythmAnalyzer implements AutoCloseable {
    private static final int MAXIMUM_CONCURRENT_ANALYSES = 2;
    private static final int MAXIMUM_QUEUED_ANALYSES = 8;
    private static final int MAXIMUM_CACHED_ANALYSES = 16;
    private static final long FRAME_WAIT_SECONDS = 10L;

    private final AudioTrackLoader loader;
    private final Consumer<String> warningLogger;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            MAXIMUM_CONCURRENT_ANALYSES, MAXIMUM_CONCURRENT_ANALYSES, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(MAXIMUM_QUEUED_ANALYSES),
            Thread.ofVirtual().name("mik-rhythm-analysis-", 0).factory());
    private final Object lock = new Object();
    private final Map<MediaVersion, Job> jobs = new HashMap<>();
    private final Map<MediaVersion, Result> cache = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;

    OfflineRhythmAnalyzer(AudioTrackLoader loader, Consumer<String> warningLogger) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.warningLogger = Objects.requireNonNull(warningLogger, "warningLogger");
    }

    Analysis analyze(MusicTrack music, RhythmTimeline destination, MediaLease lease) {
        var key = new MediaVersion(lease.mediaVersion());
        synchronized (lock) {
            var request = new Analysis(destination);
            if (closed) {
                lease.close();
                request.completion.completeExceptionally(new IllegalStateException("Rhythm analyzer is closed"));
                return request;
            }
            var job = jobs.get(key);
            if (job != null) {
                lease.close();
                subscribe(job, request);
                return request;
            }
            job = new Job(key, music, lease);
            jobs.put(key, job);
            subscribe(job, request);
            try {
                executor.execute(job);
            } catch (RejectedExecutionException unavailable) {
                jobs.remove(key);
                lease.close();
                job.subscribers.clear();
                request.completion.completeExceptionally(unavailable);
            }
            return request;
        }
    }

    private void subscribe(Job job, Analysis request) {
        job.subscribers.add(request);
        request.completion.whenComplete((_, _) -> {
            if (!request.completion.isCancelled()) return;
            synchronized (lock) {
                if (!job.subscribers.remove(request) || !job.subscribers.isEmpty()
                        || !jobs.remove(job.key, job)) return;
            }
            job.cancel();
        });
    }

    @Override
    public void close() {
        List<Job> active;
        List<Analysis> subscribers;
        synchronized (lock) {
            if (closed) return;
            closed = true;
            active = List.copyOf(jobs.values());
            subscribers = active.stream().flatMap(job -> job.subscribers.stream()).toList();
            active.forEach(job -> job.subscribers.clear());
            jobs.clear();
            cache.clear();
        }
        subscribers.forEach(Analysis::close);
        active.forEach(Job::cancel);
        executor.shutdownNow();
    }

    static final class Analysis implements AutoCloseable {
        private final RhythmTimeline destination;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private MediaVersion mediaVersion;

        private Analysis(RhythmTimeline destination) { this.destination = destination; }

        public CompletableFuture<Void> completion() { return completion; }

        MediaVersion mediaVersion() { return mediaVersion; }

        private void accept(MediaVersion version, Result result, Throwable error) {
            if (completion.isDone()) return;
            if (error != null) {
                destination.markComplete(0L);
                completion.completeExceptionally(error);
            } else {
                mediaVersion = version;
                destination.publish(RhythmSource.AUDIO_ANALYSIS, result.pulses(), result.durationMillis(), -1L, -1);
                completion.complete(null);
            }
        }

        @Override public void close() { completion.cancel(false); }
    }


    private record Result(List<RhythmPulse> pulses, long durationMillis) { }

    private final class Job implements Runnable {
        private final MediaVersion key;
        private final MediaLease lease;
        private final MusicTrack music;
        private final Set<Analysis> subscribers = new LinkedHashSet<>();
        private final AtomicReference<CompletableFuture<?>> pending = new AtomicReference<>();
        private final AtomicReference<AudioPlayer> player = new AtomicReference<>();
        private volatile boolean cancelled;

        private Job(MediaVersion key, MusicTrack music, MediaLease lease) {
            this.key = key;
            this.music = music;
            this.lease = lease;
        }

        @Override
        public void run() {
            if (cancelled) return;
            MediaVersion version = null;
            try {
                Result result;
                try (var resource = loader.resource(lease)) {
                    version = resource.version();
                    synchronized (lock) { result = cache.get(version); }
                    if (result == null) {
                        try (var loaded = await(loader.load(music, resource))) {
                            result = decode(loaded.track());
                        }
                    }
                }
                finish(version, result, null);
            } catch (Exception failure) {
                Throwable cause = failure instanceof CompletionException ? failure.getCause() : failure;
                if (finish(null, null, cause)) {
                    if (version != null && CorruptAudioRecovery.containsUnexpectedEof(cause)) {
                        loader.reportCorrupt(music, version);
                    }
                    warningLogger.accept("Failed to analyze complete rhythm track " + music.id() + ": " + cause);
                }
            }
        }

        private <T> T await(CompletableFuture<T> future) {
            pending.set(future);
            if (cancelled) future.cancel(false);
            try {
                return future.join();
            } finally {
                pending.compareAndSet(future, null);
            }
        }

        private Result decode(AudioTrack track) throws IOException, InterruptedException, TimeoutException {
            var staging = new RhythmTimeline(music.id());
            var filter = new RhythmAudioFilterFactory(staging, WholeTrackRhythmExtractor::new);
            var decoder = loader.createPlayer();
            player.set(decoder);
            try {
                if (cancelled) throw new CancellationException();
                var failure = new AtomicReference<Throwable>();
                decoder.addListener(new AudioEventAdapter() {
                    @Override
                    public void onTrackException(AudioPlayer ignored, AudioTrack failed, FriendlyException error) {
                        failure.compareAndSet(null, error);
                    }

                    @Override
                    public void onTrackEnd(AudioPlayer ignored, AudioTrack ended, AudioTrackEndReason reason) {
                        if (reason == AudioTrackEndReason.LOAD_FAILED) {
                            failure.compareAndSet(null, new IOException("Audio decoder failed during analysis"));
                        }
                    }
                });
                decoder.setFilterFactory(filter);
                decoder.playTrack(track);
                while (!cancelled && decoder.getPlayingTrack() != null) {
                    decoder.provide(FRAME_WAIT_SECONDS, TimeUnit.SECONDS);
                }
                if (cancelled) throw new CancellationException();
                filter.finish();
                if (failure.get() != null) throw new CompletionException(failure.get());
                if (filter.analyzedSampleFrames() == 0L) {
                    throw new IOException("Whole-track rhythm decoder produced no PCM sample frames");
                }
                if (!staging.playable()) {
                    warningLogger.accept("Whole-track rhythm analysis found no beats for " + music.id());
                }
                long duration = staging.analyzedThroughMillis();
                if (track.getDuration() != Long.MAX_VALUE) duration = Math.max(duration, track.getDuration());
                var pulses = staging.between(0L, duration).stream().map(beat -> new RhythmPulse(
                        beat.timeMillis(), beat.strength(), beat.stereoBalance(), beat.toneBalance(), beat.signature()))
                        .toList();
                return new Result(pulses, Math.max(1L, duration));
            } finally {
                player.set(null);
                decoder.destroy();
            }
        }

        private boolean finish(MediaVersion version, Result result, Throwable error) {
            List<Analysis> ready;
            synchronized (lock) {
                if (!jobs.remove(key, this)) return false;
                if (result != null && version != null) {
                    cache.put(version, result);
                    if (cache.size() > MAXIMUM_CACHED_ANALYSES) cache.remove(cache.keySet().iterator().next());
                }
                ready = List.copyOf(subscribers);
                subscribers.clear();
            }
            ready.forEach(request -> request.accept(version, result, error));
            return true;
        }

        private void cancel() {
            cancelled = true;
            lease.close();
            executor.remove(this);
            var request = pending.get();
            if (request != null) request.cancel(false);
            var decoder = player.get();
            if (decoder != null) decoder.stopTrack();
        }
    }
}
