package org.encinet.mik.module.music.catalog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class MusicLibrary {

    private final LocalMusicSource localSource;
    private final Path musicFolder;
    private final Executor reloadExecutor;
    private final Consumer<String> infoLogger;
    private final Consumer<String> errorLogger;
    private final AtomicBoolean loading = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private volatile List<MusicTrack> tracks = List.of();
    private volatile Map<String, MusicTrack> tracksById = Map.of();
    private volatile boolean catalogLoaded;
    private final AtomicBoolean closed = new AtomicBoolean();

    public MusicLibrary(Path musicFolder, Executor reloadExecutor,
                        Consumer<String> infoLogger, Consumer<String> warningLogger) {
        this.reloadExecutor = Objects.requireNonNull(reloadExecutor, "reloadExecutor");
        this.musicFolder = Objects.requireNonNull(musicFolder, "musicFolder")
                .toAbsolutePath().normalize();
        this.infoLogger = Objects.requireNonNull(infoLogger, "infoLogger");
        this.errorLogger = Objects.requireNonNull(warningLogger, "warningLogger");
        this.localSource = new LocalMusicSource(musicFolder,
                warningLogger);
    }

    public CompletableFuture<ReloadResult> reloadAsync() {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Music library is closed"));
        }
        if (!loading.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(new ReloadResult(
                    false, tracks.size(), "Music library reload is already in progress"));
        }
        try {
            return CompletableFuture.supplyAsync(this::reloadInternal, reloadExecutor)
                    .handle((count, error) -> {
                        loading.set(false);
                        if (error == null) {
                            return new ReloadResult(true, count, null);
                        }
                        String message = rootMessage(error);
                        if (!closed.get()) {
                            errorLogger.accept("Failed to load music library: " + message);
                        }
                        return new ReloadResult(false, tracks.size(), message);
                    });
        } catch (RuntimeException exception) {
            loading.set(false);
            return CompletableFuture.failedFuture(exception);
        }
    }

    private int reloadInternal() {
        try {
            if (closed.get()) {
                throw new IllegalStateException("Music library is closed");
            }
            List<MusicTrack> localTracks = localSource.load();

            Map<String, MusicTrack> merged = new LinkedHashMap<>();
            localTracks.forEach(track -> merged.put(track.id(), track));
            List<MusicTrack> loaded = new ArrayList<>(merged.values());
            loaded.sort(Comparator.comparing((MusicTrack track) -> track.details().title(),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(MusicTrack::id));

            synchronized (lifecycleLock) {
                if (closed.get()) {
                    throw new IllegalStateException("Music library is closed");
                }
                tracks = List.copyOf(loaded);
                tracksById = Map.copyOf(merged);
                catalogLoaded = true;
            }
            infoLogger.accept("Loaded " + localTracks.size()
                    + " local music tracks from " + musicFolder);
            return localTracks.size();
        } catch (Exception exception) {
            throw new CompletionException(exception);
        }
    }

    public List<MusicTrack> tracks() {
        return tracks;
    }

    public boolean isLoaded() {
        return catalogLoaded;
    }

    public MusicTrack track(String id) {
        if (id == null) {
            return null;
        }
        return tracksById.get(id);
    }

    public Path musicFolder() {
        return musicFolder;
    }

    public void close() {
        synchronized (lifecycleLock) {
            closed.set(true);
        }
    }

    public record ReloadResult(boolean successful, int trackCount, String error) {
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
