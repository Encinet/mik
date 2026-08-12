package org.encinet.mik.module.music.jukebox;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.rhythm.analysis.RhythmAudioFilterFactory;
import org.encinet.mik.module.music.rhythm.analysis.RhythmBeat;
import org.encinet.mik.module.music.rhythm.analysis.RhythmPulse;
import org.encinet.mik.module.music.rhythm.analysis.RhythmSource;
import org.encinet.mik.module.music.rhythm.analysis.RhythmTimeline;
import org.encinet.mik.module.music.rhythm.analysis.WholeTrackRhythmExtractor;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Waits for immutable complete media, decodes the whole song off-thread, and
 * atomically publishes a finished rhythm track. Playback PCM is never observed.
 */
final class OfflineRhythmAnalyzer implements AutoCloseable {
    private static final int MAXIMUM_CONCURRENT_ANALYSES = 2;
    private static final long FRAME_WAIT_SECONDS = 10L;

    private final AudioTrackLoader loader;
    private final Consumer<String> warningLogger;
    // Each job mostly waits for Lavaplayer frames, so a virtual carrier is useful;
    // the fixed size is still essential because decoder/DSP work is CPU-bound.
    private final ExecutorService executor = Executors.newFixedThreadPool(
            MAXIMUM_CONCURRENT_ANALYSES,
            Thread.ofVirtual().name("mik-rhythm-analysis-", 0).factory());
    private final Set<Job> jobs = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    OfflineRhythmAnalyzer(AudioTrackLoader loader, Consumer<String> warningLogger) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.warningLogger = Objects.requireNonNull(warningLogger, "warningLogger");
    }

    Analysis analyze(MusicTrack music, RhythmTimeline destination) {
        Objects.requireNonNull(music, "music");
        Objects.requireNonNull(destination, "destination");
        if (closed.get()) {
            return Analysis.failed(new IllegalStateException("Rhythm analyzer is closed"));
        }
        Job job = new Job(music, destination);
        jobs.add(job);
        job.start();
        return job;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        List.copyOf(jobs).forEach(Job::close);
        jobs.clear();
        executor.shutdownNow();
    }

    interface Analysis extends AutoCloseable {
        CompletableFuture<Void> completion();

        @Override
        void close();

        static Analysis failed(Throwable error) {
            CompletableFuture<Void> completion = CompletableFuture.failedFuture(error);
            return new Analysis() {
                @Override
                public CompletableFuture<Void> completion() {
                    return completion;
                }

                @Override
                public void close() {
                }
            };
        }
    }

    private final class Job implements Analysis {
        private final MusicTrack music;
        private final RhythmTimeline destination;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicReference<CompletableFuture<AudioTrackLoader.LoadedAudio>> loading =
                new AtomicReference<>();
        private final AtomicReference<AudioTrackLoader.LoadedAudio> loadedAudio =
                new AtomicReference<>();
        private final AtomicReference<AudioPlayer> player = new AtomicReference<>();

        private Job(MusicTrack music, RhythmTimeline destination) {
            this.music = music;
            this.destination = destination;
        }

        private void start() {
            CompletableFuture<AudioTrackLoader.LoadedAudio> request =
                    loader.loadComplete(music);
            loading.set(request);
            request.whenComplete((loaded, error) -> {
                loading.compareAndSet(request, null);
                if (cancelled.get() || closed.get()) {
                    if (loaded != null) loaded.close();
                    finishCancelled();
                    return;
                }
                if (error != null) {
                    fail(error);
                    return;
                }
                loadedAudio.set(loaded);
                if (cancelled.get() || closed.get()) {
                    releaseLoaded(loaded);
                    finishCancelled();
                    return;
                }
                try {
                    executor.execute(() -> decode(loaded));
                } catch (RuntimeException exception) {
                    releaseLoaded(loaded);
                    fail(exception);
                }
            });
        }

        private void decode(AudioTrackLoader.LoadedAudio loaded) {
            AudioPlayer decoder = null;
            try {
                if (cancelled.get() || closed.get()) return;
                AudioTrack track = loaded.track();
                RhythmTimeline staging = new RhythmTimeline(destination.seed());
                decoder = loader.createPlayer();
                player.set(decoder);
                if (cancelled.get() || closed.get()) return;

                AtomicReference<Throwable> decoderFailure = new AtomicReference<>();
                decoder.addListener(new AudioEventAdapter() {
                    @Override
                    public void onTrackException(AudioPlayer ignored, AudioTrack failedTrack,
                                                 FriendlyException exception) {
                        decoderFailure.compareAndSet(null, exception);
                    }

                    @Override
                    public void onTrackEnd(AudioPlayer ignored, AudioTrack endedTrack,
                                           AudioTrackEndReason reason) {
                        if (reason == AudioTrackEndReason.LOAD_FAILED) {
                            decoderFailure.compareAndSet(null, new IllegalStateException(
                                    "Audio decoder failed during whole-track rhythm analysis"));
                        }
                    }
                });
                RhythmAudioFilterFactory filterFactory =
                        new RhythmAudioFilterFactory(staging,
                                WholeTrackRhythmExtractor::new);
                decoder.setFilterFactory(filterFactory);
                decoder.playTrack(track);
                drain(decoder);
                filterFactory.finish();

                if (cancelled.get() || closed.get()) return;
                Throwable failure = decoderFailure.get();
                if (failure != null) throw new CompletionException(failure);
                if (filterFactory.analyzedSampleFrames() == 0L) {
                    throw new IOException(
                            "Whole-track rhythm decoder produced no PCM sample frames");
                }
                if (!staging.playable()) {
                    warningLogger.accept("Whole-track rhythm analysis found no beats for "
                            + music.id() + " after "
                            + filterFactory.analyzedSampleFrames()
                            + " decoded PCM frames");
                }
                publish(track, staging);
                completion.complete(null);
            } catch (Throwable error) {
                if (!cancelled.get() && !closed.get()) {
                    Throwable cause = unwrap(error);
                    if (CorruptAudioRecovery.isRecoverable(music, cause)) {
                        loader.invalidate(music);
                    }
                    fail(cause);
                }
            } finally {
                if (decoder != null && player.compareAndSet(decoder, null)) {
                    decoder.destroy();
                }
                releaseLoaded(loaded);
                finish();
            }
        }

        private void drain(AudioPlayer decoder)
                throws InterruptedException, TimeoutException, IOException {
            while (!cancelled.get() && !closed.get()
                    && decoder.getPlayingTrack() != null) {
                try {
                    decoder.provide(FRAME_WAIT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException exception) {
                    throw new IOException("Timed out while decoding complete rhythm audio",
                            exception);
                }
            }
        }

        private void publish(AudioTrack track, RhythmTimeline staging) {
            long durationMillis = completeDuration(track,
                    staging.analyzedThroughMillis());
            List<RhythmPulse> pulses = staging.between(0L, durationMillis).stream()
                    .map(Job::pulse)
                    .toList();
            destination.publish(RhythmSource.AUDIO_ANALYSIS, pulses,
                    Math.max(1L, durationMillis), -1L, -1);
        }

        private static long completeDuration(AudioTrack track, long analyzedMillis) {
            long declaredMillis = track.getDuration();
            if (declaredMillis <= 0L || declaredMillis == Long.MAX_VALUE) {
                return analyzedMillis;
            }
            return Math.max(analyzedMillis, declaredMillis);
        }

        private static RhythmPulse pulse(RhythmBeat beat) {
            return new RhythmPulse(beat.timeMillis(), beat.strength(),
                    beat.stereoBalance(), beat.toneBalance(), beat.signature());
        }

        private void fail(Throwable error) {
            Throwable cause = unwrap(error);
            destination.markComplete(0L);
            completion.completeExceptionally(cause);
            warningLogger.accept("Failed to analyze complete rhythm track "
                    + music.id() + ": " + message(cause));
            finish();
        }

        private void finishCancelled() {
            completion.cancel(false);
            finish();
        }

        private void finish() {
            if (finished.compareAndSet(false, true)) jobs.remove(this);
        }

        private void releaseLoaded(AudioTrackLoader.LoadedAudio expected) {
            if (loadedAudio.compareAndSet(expected, null)) expected.close();
        }

        @Override
        public CompletableFuture<Void> completion() {
            return completion;
        }

        @Override
        public void close() {
            if (!cancelled.compareAndSet(false, true)) return;
            CompletableFuture<AudioTrackLoader.LoadedAudio> request = loading.getAndSet(null);
            if (request != null) request.cancel(false);
            AudioPlayer decoder = player.getAndSet(null);
            if (decoder != null) decoder.destroy();
            AudioTrackLoader.LoadedAudio loaded = loadedAudio.getAndSet(null);
            if (loaded != null) loaded.close();
            finishCancelled();
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName() : message;
    }
}
