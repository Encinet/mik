package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.encinet.mik.module.music.catalog.TrackTarget;

import java.io.IOException;
import java.io.EOFException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Persistent, restart-safe cache for resolved online audio. */
public final class OnlineAudioCache implements AutoCloseable {

    private static final long MAX_AUDIO_BYTES = 512L * 1024 * 1024;
    private static final long MAX_CACHE_BYTES = 8L * 1024 * 1024 * 1024;
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration DOWNLOAD_IDLE_TIMEOUT = Duration.ofSeconds(15);
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_CONCURRENT_DOWNLOADS = 4;
    private static final int MAX_AUDIO_SOURCE_ATTEMPTS = 4;
    private static final int MAX_TRANSIENT_ATTEMPTS_PER_SOURCE = 2;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(200);
    private static final String CACHE_SUFFIX = ".audio";
    private static final String PART_SUFFIX = ".part";

    private final Path cacheDirectory;
    private final LxTrackResolver upstream;
    private final Consumer<String> warningLogger;
    private final long maxAudioBytes;
    private final long maxCacheBytes;
    private final Duration downloadTimeout;
    private final Duration downloadIdleTimeout;
    private final HttpClient httpClient;
    private final ExecutorService downloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentHashMap<String, DownloadOperation> inFlight = new ConcurrentHashMap<>();
    private final Set<DownloadOperation> activeDownloads = ConcurrentHashMap.newKeySet();
    private final Set<String> cachedEntries = ConcurrentHashMap.newKeySet();
    private final CachedOnlineTrackCatalog trackCatalog;
    private final MusicPlaybackStats playbackStats;
    private final Semaphore downloadPermits = new Semaphore(MAX_CONCURRENT_DOWNLOADS);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();
    private final Object cacheLock = new Object();
    private final Map<String, Integer> activeEntries = new HashMap<>();
    private final Set<String> deleteOnRelease = new HashSet<>();
    private long reservedDownloadBytes;
    private CompletableFuture<CacheStats> clearInFlight;

    public OnlineAudioCache(Path cacheDirectory, LxSourceService upstream,
                           Consumer<String> warningLogger) {
        this(cacheDirectory, upstream, warningLogger,
                MAX_AUDIO_BYTES, MAX_CACHE_BYTES, DOWNLOAD_TIMEOUT,
                MusicPlaybackStats.EMPTY);
    }

    public OnlineAudioCache(Path cacheDirectory, LxSourceService upstream,
                            Consumer<String> warningLogger,
                            MusicPlaybackStats playbackStats) {
        this(cacheDirectory, upstream, warningLogger,
                MAX_AUDIO_BYTES, MAX_CACHE_BYTES, DOWNLOAD_TIMEOUT, playbackStats);
    }

    OnlineAudioCache(Path cacheDirectory, LxTrackResolver upstream,
                    Consumer<String> warningLogger, long maxAudioBytes,
                    Duration downloadTimeout) {
        this(cacheDirectory, upstream, warningLogger, maxAudioBytes,
                Math.max(maxAudioBytes, multiplySaturated(maxAudioBytes, 16)), downloadTimeout,
                MusicPlaybackStats.EMPTY);
    }

    OnlineAudioCache(Path cacheDirectory, LxTrackResolver upstream,
                    Consumer<String> warningLogger, long maxAudioBytes,
                    long maxCacheBytes, Duration downloadTimeout) {
        this(cacheDirectory, upstream, warningLogger, maxAudioBytes,
                maxCacheBytes, downloadTimeout, MusicPlaybackStats.EMPTY);
    }

    OnlineAudioCache(Path cacheDirectory, LxTrackResolver upstream,
                     Consumer<String> warningLogger, long maxAudioBytes,
                     long maxCacheBytes, Duration downloadTimeout,
                     MusicPlaybackStats playbackStats) {
        this(cacheDirectory, upstream, warningLogger, maxAudioBytes, maxCacheBytes,
                downloadTimeout,
                downloadTimeout.compareTo(DOWNLOAD_IDLE_TIMEOUT) < 0
                        ? downloadTimeout : DOWNLOAD_IDLE_TIMEOUT,
                playbackStats);
    }

    OnlineAudioCache(Path cacheDirectory, LxTrackResolver upstream,
                     Consumer<String> warningLogger, long maxAudioBytes,
                     long maxCacheBytes, Duration downloadTimeout,
                     Duration downloadIdleTimeout,
                     MusicPlaybackStats playbackStats) {
        this.cacheDirectory = cacheDirectory.toAbsolutePath().normalize();
        this.upstream = upstream;
        this.warningLogger = warningLogger;
        this.trackCatalog = new CachedOnlineTrackCatalog(this.cacheDirectory, warningLogger);
        this.playbackStats = Objects.requireNonNull(playbackStats, "playbackStats");
        this.maxAudioBytes = maxAudioBytes;
        this.maxCacheBytes = maxCacheBytes;
        this.downloadTimeout = downloadTimeout;
        this.downloadIdleTimeout = downloadIdleTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        if (maxAudioBytes < 1) {
            throw new IllegalArgumentException("maxAudioBytes must be positive");
        }
        if (maxCacheBytes < maxAudioBytes) {
            throw new IllegalArgumentException("maxCacheBytes must cover one audio file");
        }
        if (downloadTimeout.isNegative() || downloadTimeout.isZero()) {
            throw new IllegalArgumentException("downloadTimeout must be positive");
        }
        if (downloadIdleTimeout.isNegative() || downloadIdleTimeout.isZero()
                || downloadIdleTimeout.compareTo(downloadTimeout) > 0) {
            throw new IllegalArgumentException(
                    "downloadIdleTimeout must be positive and no greater than downloadTimeout");
        }
        cleanupPartFiles();
        loadCacheIndex();
    }

    /** Returns a memory-only snapshot of whether this exact online playback target is cached. */
    public boolean isCached(TrackTarget.Lx target) {
        return target != null && cachedEntries.contains(cacheKey(target));
    }

    /** Returns validated online tracks whose exact playback targets have complete cached audio. */
    public List<MusicTrack> cachedTracks() {
        return trackCatalog.tracks();
    }

    /** Persists metadata for a complete cache entry without blocking the caller thread. */
    public CompletableFuture<Boolean> indexAsync(MusicTrack track) {
        Objects.requireNonNull(track, "track");
        if (!(track.target() instanceof TrackTarget.Lx target)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Only online music tracks can be indexed"));
        }
        String entryKey = cacheKey(target);
        try {
            return resolve(target).thenApplyAsync(
                            ignored -> indexNow(entryKey, track), downloadExecutor)
                    .exceptionally(ignored -> false);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private boolean indexNow(String entryKey, MusicTrack track) {
        synchronized (cacheLock) {
            if (closed.get() || clearInFlight != null || deleteOnRelease.contains(entryKey)
                    || !isUsable(entryKey, cachePath(entryKey))) {
                return false;
            }
            return trackCatalog.store(entryKey, track);
        }
    }

    public CompletableFuture<String> resolve(TrackTarget.Lx target) {
        Objects.requireNonNull(target, "target");
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IOException("Music audio cache is closed"));
        }

        return start(target).thenCompose(DownloadAccess::completed)
                .thenApply(Path::toString);
    }

    private CompletableFuture<DownloadAccess> start(TrackTarget.Lx target) {
        Objects.requireNonNull(target, "target");
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IOException("Music audio cache is closed"));
        }

        String entryKey = cacheKey(target);
        Path destination = cachePath(entryKey);
        DownloadOperation created;
        long expectedGeneration;
        synchronized (cacheLock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IOException("Music audio cache is closed"));
            }
            if (clearInFlight != null) {
                return clearInFlight.thenCompose(ignored -> start(target));
            }
            if (deleteOnRelease.contains(entryKey)) {
                return CompletableFuture.failedFuture(new IOException(
                        "Music cache entry is pending deletion after active playback"));
            }
            if (isUsable(entryKey, destination)) {
                touch(destination);
                try {
                    StreamingState state = StreamingState.completed(
                            destination, Files.size(destination));
                    return CompletableFuture.completedFuture(new DownloadAccess(
                            CompletableFuture.completedFuture(destination),
                            CompletableFuture.completedFuture(state), () -> {}));
                } catch (IOException exception) {
                    return CompletableFuture.failedFuture(exception);
                }
            }
            DownloadOperation existing = inFlight.get(entryKey);
            if (existing != null) {
                return CompletableFuture.completedFuture(existing.access());
            }
            created = track(new DownloadOperation());
            expectedGeneration = generation.get();
            inFlight.put(entryKey, created);
        }

        DownloadWorker worker = created.newWorker(() -> {
            try {
                finishDownload(entryKey, created, downloadWithFailover(
                        target, entryKey, destination, expectedGeneration, created), null);
            } catch (Throwable throwable) {
                finishDownload(entryKey, created, null, throwable);
            }
        });
        created.setWorker(worker);
        try {
            downloadExecutor.execute(worker);
        } catch (RuntimeException exception) {
            worker.rejected();
            finishDownload(entryKey, created, null, exception);
        }
        return CompletableFuture.completedFuture(created.access());
    }

    private Path downloadWithFailover(TrackTarget.Lx target, String entryKey, Path destination,
                                      long expectedGeneration, DownloadOperation owner)
            throws IOException {
        Set<String> excludedProviderIds = new LinkedHashSet<>();
        RemoteAudioException lastRemoteFailure = null;
        for (int sourceAttempt = 1; sourceAttempt <= MAX_AUDIO_SOURCE_ATTEMPTS; sourceAttempt++) {
            LxTrackResolver.Resolution resolution;
            try {
                resolution = awaitResolution(target, excludedProviderIds, owner);
            } catch (IOException resolutionFailure) {
                if (lastRemoteFailure != null) {
                    lastRemoteFailure.addSuppressed(resolutionFailure);
                    throw lastRemoteFailure;
                }
                throw resolutionFailure;
            }

            for (int transientAttempt = 1;
                 transientAttempt <= MAX_TRANSIENT_ATTEMPTS_PER_SOURCE;
                 transientAttempt++) {
                try {
                    Path downloaded = download(entryKey, resolution.url(), destination,
                            expectedGeneration, owner);
                    reportCandidateSuccess(target, resolution);
                    return downloaded;
                } catch (Throwable throwable) {
                    Throwable failure = unwrap(throwable);
                    if (owner.cancelled()) {
                        throw asIOException(failure);
                    }
                    if (!(failure instanceof RemoteAudioException remoteFailure)) {
                        throw asIOException(failure);
                    }
                    if (lastRemoteFailure != null && lastRemoteFailure != remoteFailure) {
                        remoteFailure.addSuppressed(lastRemoteFailure);
                    }
                    lastRemoteFailure = remoteFailure;
                    boolean retryStateAvailable = owner.prepareRetry(remoteFailure);
                    if (remoteFailure.transientFailure()
                            && transientAttempt < MAX_TRANSIENT_ATTEMPTS_PER_SOURCE
                            && retryStateAvailable) {
                        warningLogger.accept("Online audio source " + sourceLabel(resolution)
                                + " failed; retrying once: " + remoteFailure.getMessage());
                        retryBackoff();
                        continue;
                    }

                    reportCandidateFailure(target, resolution, remoteFailure);
                    if (!retryStateAvailable || resolution.providerId() == null
                            || sourceAttempt >= MAX_AUDIO_SOURCE_ATTEMPTS) {
                        throw remoteFailure;
                    }
                    excludedProviderIds.add(resolution.providerId());
                    warningLogger.accept("Online audio source " + sourceLabel(resolution)
                            + " failed; trying another source: " + remoteFailure.getMessage());
                    break;
                }
            }
        }
        throw lastRemoteFailure == null
                ? new IOException("No online audio source could be resolved") : lastRemoteFailure;
    }

    private LxTrackResolver.Resolution awaitResolution(
            TrackTarget.Lx target, Set<String> excludedProviderIds, DownloadOperation owner)
            throws IOException {
        CompletableFuture<LxTrackResolver.Resolution> pending;
        try {
            pending = upstream.resolveCandidate(target, Set.copyOf(excludedProviderIds));
        } catch (RuntimeException exception) {
            throw asIOException(unwrap(exception));
        }
        if (pending == null) {
            throw new IOException("Online audio source resolver returned no future");
        }
        owner.setUpstream(pending);
        try {
            LxTrackResolver.Resolution resolution = pending.get();
            if (resolution == null) {
                throw new IOException("Online audio source resolver returned no candidate");
            }
            return resolution;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException(
                    "Audio source resolution was interrupted");
            interrupted.initCause(exception);
            throw interrupted;
        } catch (java.util.concurrent.ExecutionException exception) {
            throw asIOException(unwrap(exception));
        } catch (java.util.concurrent.CancellationException exception) {
            throw new IOException("Audio source resolution was cancelled", exception);
        }
    }

    private void reportCandidateSuccess(
            TrackTarget.Lx target, LxTrackResolver.Resolution resolution) {
        try {
            upstream.candidateSucceeded(target, resolution);
        } catch (RuntimeException exception) {
            warningLogger.accept("Failed to record online audio source recovery: "
                    + rootMessage(exception));
        }
    }

    private void reportCandidateFailure(TrackTarget.Lx target,
                                        LxTrackResolver.Resolution resolution,
                                        Throwable error) {
        try {
            upstream.candidateFailed(target, resolution, error);
        } catch (RuntimeException exception) {
            warningLogger.accept("Failed to record online audio source failure: "
                    + rootMessage(exception));
        }
    }

    private static String sourceLabel(LxTrackResolver.Resolution resolution) {
        return resolution.providerId() == null ? "provider" : "[" + resolution.providerId() + "]";
    }

    private static void retryBackoff() throws InterruptedIOException {
        try {
            Thread.sleep(RETRY_BACKOFF);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException(
                    "Interrupted before retrying online audio");
            interrupted.initCause(exception);
            throw interrupted;
        }
    }

    private void finishDownload(String entryKey, DownloadOperation operation,
                                Path path, Throwable error) {
        inFlight.remove(entryKey, operation);
        if (error == null) {
            operation.complete(path);
        } else {
            operation.fail(error);
        }
    }

    private DownloadOperation track(DownloadOperation operation) {
        activeDownloads.add(operation);
        operation.terminated().whenComplete((ignored, error) -> activeDownloads.remove(operation));
        return operation;
    }

    public CompletableFuture<CachedAudio> acquire(TrackTarget.Lx target) {
        Objects.requireNonNull(target, "target");
        EntryReservation reservation = reserve(cacheKey(target));
        CompletableFuture<CachedAudio> result = new CompletableFuture<>();
        resolve(target).whenComplete((identifier, error) -> {
            if (error != null) {
                reservation.close();
                result.completeExceptionally(error);
                return;
            }
            CachedAudio audio = new CachedAudio(identifier, reservation::close);
            if (!result.complete(audio)) {
                audio.close();
            }
        });
        result.whenComplete((audio, error) -> {
            if (error != null) {
                reservation.close();
            }
        });
        return result;
    }

    /** Acquires a lease which can be read while the online audio is still downloading. */
    public CompletableFuture<StreamingAudio> acquireStreaming(TrackTarget.Lx target) {
        Objects.requireNonNull(target, "target");
        EntryReservation reservation;
        try {
            reservation = reserve(cacheKey(target));
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        CompletableFuture<StreamingAudio> result = new CompletableFuture<>();
        start(target).thenCompose(DownloadAccess::acquireStreaming).whenComplete((state, error) -> {
            if (error != null) {
                reservation.close();
                result.completeExceptionally(error);
                return;
            }
            StreamingAudio audio = new StreamingAudio(state, reservation::close);
            if (!result.complete(audio)) {
                audio.close();
            }
        });
        result.whenComplete((audio, error) -> {
            if (error != null) {
                reservation.close();
            }
        });
        return result;
    }

    private EntryReservation reserve(String entryKey) {
        synchronized (cacheLock) {
            ensureOpen();
            activeEntries.merge(entryKey, 1, Integer::sum);
        }
        return new EntryReservation(entryKey);
    }

    public void invalidate(TrackTarget.Lx target) {
        Objects.requireNonNull(target, "target");
        String entryKey = cacheKey(target);
        synchronized (cacheLock) {
            DownloadOperation download = inFlight.remove(entryKey);
            if (download != null) {
                download.cancel();
            }
            deleteOrDefer(entryKey, "invalid music cache entry");
        }
    }

    public CompletableFuture<CacheStats> statsAsync() {
        return CompletableFuture.supplyAsync(this::stats, downloadExecutor);
    }

    public CompletableFuture<CacheStats> clearAsync() {
        CompletableFuture<CacheStats> operation;
        List<DownloadOperation> downloads;
        synchronized (cacheLock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new IOException("Music audio cache is closed"));
            }
            if (clearInFlight != null) {
                return clearInFlight;
            }
            generation.incrementAndGet();
            downloads = new ArrayList<>(activeDownloads);
            inFlight.clear();
            operation = new CompletableFuture<>();
            clearInFlight = operation;
        }
        downloads.forEach(DownloadOperation::cancel);
        CompletableFuture.allOf(downloads.stream()
                        .map(DownloadOperation::terminated)
                        .toArray(CompletableFuture[]::new))
                .whenComplete((ignored, error) -> submitClear(operation, error));
        return operation;
    }

    private void submitClear(CompletableFuture<CacheStats> operation, Throwable terminationError) {
        if (terminationError != null) {
            failClear(operation, terminationError);
            return;
        }
        try {
            downloadExecutor.execute(() -> clearCache(operation));
        } catch (RuntimeException exception) {
            failClear(operation, exception);
        }
    }

    private void failClear(CompletableFuture<CacheStats> operation, Throwable error) {
        synchronized (cacheLock) {
            if (clearInFlight == operation) {
                clearInFlight = null;
            }
        }
        operation.completeExceptionally(error);
    }

    private void clearCache(CompletableFuture<CacheStats> operation) {
        CacheStats result = null;
        Throwable error = null;
        try {
            try {
                synchronized (cacheLock) {
                    ensureOpen();
                    Files.createDirectories(cacheDirectory);
                    Set<Path> activePaths = activeCachePaths();
                    deleteOnRelease.addAll(activeEntries.keySet());
                    cachedEntries.removeAll(activeEntries.keySet());
                    trackCatalog.clear();
                    try (Stream<Path> paths = Files.list(cacheDirectory)) {
                        for (Path path : paths.filter(path -> Files.isRegularFile(
                                path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                            if (!activePaths.contains(path.toAbsolutePath().normalize())) {
                                Files.deleteIfExists(path);
                                if (isCacheFile(path)) {
                                    cachedEntries.remove(cacheEntryKey(path));
                                }
                            }
                        }
                    }
                }
            } catch (IOException exception) {
                error = exception;
            }
        } catch (RuntimeException exception) {
            error = exception;
        }
        if (error == null) {
            try {
                result = stats();
            } catch (RuntimeException exception) {
                error = exception;
            }
        }
        synchronized (cacheLock) {
            if (clearInFlight == operation) {
                clearInFlight = null;
            }
        }
        if (error == null) {
            operation.complete(result);
        } else {
            operation.completeExceptionally(error);
        }
    }

    public long maxCacheBytes() {
        return maxCacheBytes;
    }

    /** Returns whether a playback error originated from the remote HTTP audio stream. */
    public static boolean isRecoverableRemoteFailure(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = error;
        while (current != null && seen.add(current)) {
            if (current instanceof RemoteAudioException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private CacheStats stats() {
        ensureOpen();
        long files = 0;
        long bytes = 0;
        try {
            Files.createDirectories(cacheDirectory);
            try (Stream<Path> paths = Files.list(cacheDirectory)) {
                for (Path path : paths.filter(path -> Files.isRegularFile(
                                path, LinkOption.NOFOLLOW_LINKS))
                        .filter(this::isCacheFile).toList()) {
                    long size = Files.size(path);
                    if (size > 0) {
                        files++;
                        bytes += size;
                    }
                }
            }
            return new CacheStats(files, bytes);
        } catch (IOException exception) {
            throw new java.util.concurrent.CompletionException(exception);
        }
    }

    private Path download(String entryKey, String url, Path destination, long expectedGeneration,
                          DownloadOperation owner) {
        if (closed.get()) {
            throw new java.util.concurrent.CompletionException(
                    new IOException("Music audio cache is closed"));
        }
        if (isUsable(entryKey, destination)) {
            try {
                owner.useCompleted(destination, Files.size(destination));
            } catch (IOException exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
            return destination;
        }

        Path part = null;
        boolean permitAcquired = false;
        boolean quotaReserved = false;
        try {
            downloadPermits.acquire();
            permitAcquired = true;
            synchronized (cacheLock) {
                ensureCurrentDownload(entryKey, expectedGeneration, owner);
                evictFor(maxAudioBytes, destination);
                reservedDownloadBytes = addSaturated(reservedDownloadBytes, maxAudioBytes);
                quotaReserved = true;
            }
            URI uri;
            try {
                uri = validateUri(url);
            } catch (IOException exception) {
                throw new RemoteAudioException(exception.getMessage(), exception, false);
            }
            Files.createDirectories(cacheDirectory);
            part = Files.createTempFile(cacheDirectory, entryKey + "-", PART_SUFFIX);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(downloadTimeout)
                    .header("Accept", "audio/*, application/octet-stream;q=0.9, */*;q=0.1")
                    .header("User-Agent", "MIK-Music/1.0")
                    .GET()
                    .build();
            Path target = part;
            AtomicReference<LimitedFileSubscriber> subscriberRef = new AtomicReference<>();
            DownloadProgress progress = new DownloadProgress();
            CompletableFuture<HttpResponse<Path>> pending = httpClient.sendAsync(request, response -> {
                progress.advanced();
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    return new RejectedBodySubscriber(
                            new RemoteAudioException("Audio download returned HTTP " + status,
                                    null, retryableStatus(status)));
                }
                long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declaredLength > maxAudioBytes) {
                    return new RejectedBodySubscriber(
                            new RemoteAudioException(
                                    "Audio download exceeds " + maxAudioBytes + " bytes",
                                    null, false));
                }
                try {
                    String contentType = response.headers().firstValue("Content-Type")
                            .map(OnlineAudioCache::baseContentType).orElse(null);
                    StreamingState state = owner.createStreamingState(
                            target, declaredLength, contentType);
                    LimitedFileSubscriber subscriber = new LimitedFileSubscriber(
                            target, maxAudioBytes, declaredLength, state, owner, progress);
                    subscriberRef.set(subscriber);
                    owner.setSubscriber(subscriber);
                    return subscriber;
                } catch (IOException exception) {
                    return new RejectedBodySubscriber(new LocalCacheException(
                            "Failed to prepare the streaming cache file", exception));
                }
            });
            owner.setHttpRequest(pending);
            try {
                awaitDownload(pending, subscriberRef, progress);
            } catch (InterruptedException exception) {
                pending.cancel(true);
                abort(subscriberRef, new InterruptedIOException("Audio download was interrupted"));
                throw exception;
            } catch (java.util.concurrent.ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RemoteAudioException remoteAudioException) {
                    throw remoteAudioException;
                }
                if (cause instanceof LocalCacheException localCacheException) {
                    throw localCacheException;
                }
                if (cause instanceof IOException ioException) {
                    throw new RemoteAudioException(
                            "Audio download connection failed", ioException, true);
                }
                throw new RemoteAudioException("Audio download failed", cause, true);
            }
            synchronized (cacheLock) {
                ensureCurrentDownload(entryKey, expectedGeneration, owner);
                reservedDownloadBytes -= maxAudioBytes;
                quotaReserved = false;
                evictFor(Files.size(part), destination);
                owner.publish(destination);
                cachedEntries.add(entryKey);
                touch(destination);
            }
            return destination;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CompletionException(
                    new InterruptedIOException("Audio download was interrupted"));
        } catch (IOException | RuntimeException exception) {
            throw new java.util.concurrent.CompletionException(exception);
        } finally {
            if (quotaReserved) {
                synchronized (cacheLock) {
                    reservedDownloadBytes -= maxAudioBytes;
                }
            }
            if (permitAcquired) {
                downloadPermits.release();
            }
            if (part != null) {
                try {
                    Files.deleteIfExists(part);
                } catch (IOException ignored) {
                    // A failed temporary-file cleanup is retried on the next startup.
                }
            }
        }
    }

    private void awaitDownload(CompletableFuture<HttpResponse<Path>> pending,
                               AtomicReference<LimitedFileSubscriber> subscriberRef,
                               DownloadProgress progress)
            throws IOException, InterruptedException, java.util.concurrent.ExecutionException {
        long startedAt = System.nanoTime();
        long totalNanos = downloadTimeout.toNanos();
        long idleNanos = downloadIdleTimeout.toNanos();
        while (true) {
            if (pending.isDone()) {
                pending.get();
                return;
            }
            long now = System.nanoTime();
            long totalRemaining = totalNanos - Math.max(0L, now - startedAt);
            long idleRemaining = idleNanos - Math.max(0L, now - progress.lastProgressNanos());
            if (totalRemaining <= 0L || idleRemaining <= 0L) {
                if (pending.isDone()) {
                    pending.get();
                    return;
                }
                pending.cancel(true);
                boolean stalled = idleRemaining <= 0L;
                RemoteAudioException timeout = new RemoteAudioException(stalled
                        ? "Audio download stalled for " + durationLabel(downloadIdleTimeout)
                        : "Audio download timed out after " + durationLabel(downloadTimeout),
                        null, true);
                abort(subscriberRef, timeout);
                throw timeout;
            }
            long waitNanos = Math.max(1L, Math.min(
                    TimeUnit.SECONDS.toNanos(1), Math.min(totalRemaining, idleRemaining)));
            try {
                pending.get(waitNanos, TimeUnit.NANOSECONDS);
                return;
            } catch (TimeoutException ignored) {
                // Recheck both the overall deadline and progress-based idle deadline.
            }
        }
    }

    private static String durationLabel(Duration duration) {
        long millis = duration.toMillis();
        return millis < 1_000L ? millis + " ms" : duration.toSeconds() + " seconds";
    }

    private void ensureCurrentDownload(String entryKey, long expectedGeneration,
                                       DownloadOperation owner) throws IOException {
        ensureOpen();
        if (generation.get() != expectedGeneration) {
            throw new IOException("Music cache was cleared while the audio was downloading");
        }
        if (inFlight.get(entryKey) != owner) {
            throw new IOException("Music cache entry was invalidated while downloading");
        }
    }

    private static void moveIntoPlace(Path part, Path destination) throws IOException {
        try {
            Files.move(part, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void abort(AtomicReference<LimitedFileSubscriber> subscriberRef,
                              IOException exception) {
        LimitedFileSubscriber subscriber = subscriberRef.get();
        if (subscriber != null) {
            subscriber.abort(exception);
        }
    }

    private boolean isUsable(String entryKey, Path path) {
        try {
            long size = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? Files.size(path) : 0;
            if (size > 0 && size <= maxAudioBytes) {
                cachedEntries.add(entryKey);
                return true;
            }
            removeIndex(entryKey, true);
            if (size == 0 || size > maxAudioBytes) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            removeIndex(entryKey, true);
            warningLogger.accept("Failed to inspect music cache file " + path.getFileName()
                    + ": " + exception.getMessage());
        }
        return false;
    }

    private void evictFor(long incomingBytes, Path destination) throws IOException {
        List<CacheEntry> entries;
        Set<Path> activePaths = activeCachePaths();
        Instant now = Instant.now();
        try (Stream<Path> paths = Files.list(cacheDirectory)) {
            entries = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isCacheFile)
                    .filter(path -> !path.equals(destination))
                    .map(this::cacheEntry)
                    .filter(java.util.Objects::nonNull)
                    .sorted(java.util.Comparator
                            .comparingDouble((CacheEntry entry) -> evictionScore(entry, now))
                            .thenComparing(CacheEntry::lastUsed)
                            .thenComparing(java.util.Comparator.comparingLong(
                                    CacheEntry::bytes).reversed())
                            .thenComparing(entry -> entry.path().getFileName().toString()))
                    .toList();
        }
        long usedBytes = 0;
        for (CacheEntry entry : entries) {
            usedBytes = addSaturated(usedBytes, entry.bytes());
        }
        long available = maxCacheBytes - reservedDownloadBytes - incomingBytes;
        for (CacheEntry entry : entries) {
            if (usedBytes <= available) {
                break;
            }
            if (activePaths.contains(entry.path().toAbsolutePath().normalize())) {
                continue;
            }
            Files.deleteIfExists(entry.path());
            removeIndex(cacheEntryKey(entry.path()), true);
            usedBytes -= entry.bytes();
        }
        if (usedBytes > available) {
            throw new IOException("Music cache cannot free enough disk quota");
        }
    }

    private double evictionScore(CacheEntry entry, Instant now) {
        MusicTrack track = trackCatalog.track(cacheEntryKey(entry.path()));
        MusicPlaybackStats.TrackStats stats = track == null
                ? MusicPlaybackStats.TrackStats.EMPTY : playbackStats.stats(track);
        return CacheEvictionPolicy.retentionScore(entry.bytes(),
                entry.lastUsed().toInstant(), stats, now);
    }

    private CacheEntry cacheEntry(Path path) {
        try {
            return new CacheEntry(path, Files.size(path), Files.getLastModifiedTime(path));
        } catch (IOException exception) {
            warningLogger.accept("Failed to inspect music cache entry " + path.getFileName()
                    + ": " + exception.getMessage());
            return null;
        }
    }

    private void touch(Path path) {
        try {
            long now = System.currentTimeMillis();
            Files.setLastModifiedTime(path, FileTime.fromMillis(now));
        } catch (IOException exception) {
            warningLogger.accept("Failed to update music cache access time " + path.getFileName()
                    + ": " + exception.getMessage());
        }
    }

    private void cleanupPartFiles() {
        try {
            Files.createDirectories(cacheDirectory);
            try (Stream<Path> paths = Files.list(cacheDirectory)) {
                for (Path path : paths.filter(path -> Files.isRegularFile(
                                path, LinkOption.NOFOLLOW_LINKS))
                        .filter(value -> value.getFileName().toString().endsWith(PART_SUFFIX))
                        .toList()) {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException exception) {
            warningLogger.accept("Failed to clean incomplete music cache downloads: "
                    + exception.getMessage());
        }
    }

    private void loadCacheIndex() {
        try {
            Files.createDirectories(cacheDirectory);
            Set<String> audioEntries = new HashSet<>();
            try (Stream<Path> paths = Files.list(cacheDirectory)) {
                for (Path path : paths.filter(value -> Files.isRegularFile(
                                value, LinkOption.NOFOLLOW_LINKS))
                        .filter(this::isCacheFile).toList()) {
                    String entryKey = cacheEntryKey(path);
                    if (isUsable(entryKey, path)) {
                        audioEntries.add(entryKey);
                        trackCatalog.load(entryKey);
                    }
                }
            }
            trackCatalog.deleteOrphans(audioEntries);
        } catch (IOException exception) {
            warningLogger.accept("Failed to index music cache: " + exception.getMessage());
        }
    }

    private Path cachePath(String entryKey) {
        return cacheDirectory.resolve(entryKey + CACHE_SUFFIX);
    }

    private Set<Path> activeCachePaths() {
        Set<Path> paths = new HashSet<>();
        activeEntries.keySet().forEach(entryKey -> paths.add(
                cachePath(entryKey).toAbsolutePath().normalize()));
        return paths;
    }

    private void release(String entryKey) {
        synchronized (cacheLock) {
            Integer count = activeEntries.get(entryKey);
            if (count == null) {
                return;
            }
            if (count > 1) {
                activeEntries.put(entryKey, count - 1);
                return;
            }
            activeEntries.remove(entryKey);
            if (deleteOnRelease.remove(entryKey)) {
                deleteNow(entryKey, "deferred music cache entry");
            }
        }
    }

    private void deleteOrDefer(String entryKey, String description) {
        removeIndex(entryKey, false);
        if (activeEntries.containsKey(entryKey)) {
            deleteOnRelease.add(entryKey);
        } else {
            deleteNow(entryKey, description);
        }
    }

    private void deleteNow(String entryKey, String description) {
        removeIndex(entryKey, false);
        try {
            Files.deleteIfExists(cachePath(entryKey));
        } catch (IOException exception) {
            warningLogger.accept("Failed to remove " + description + " "
                    + entryKey + ": " + exception.getMessage());
        } finally {
            trackCatalog.remove(entryKey);
        }
    }

    private boolean isCacheFile(Path path) {
        return path.getFileName().toString().endsWith(CACHE_SUFFIX);
    }

    private static String cacheEntryKey(Path path) {
        String name = path.getFileName().toString();
        return name.substring(0, name.length() - CACHE_SUFFIX.length());
    }

    private void removeIndex(String entryKey, boolean deleteTrackFile) {
        cachedEntries.remove(entryKey);
        if (deleteTrackFile) {
            trackCatalog.remove(entryKey);
        } else {
            trackCatalog.forget(entryKey);
        }
    }

    static String cacheKey(TrackTarget.Lx target) {
        Objects.requireNonNull(target, "target");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, "mik-lx-audio-v3");
            updateDigest(digest, target.source());
            updateDigest(digest, target.songId());
            updateDigest(digest, target.providerId() == null ? "" : target.providerId());
            for (String quality : target.qualities()) {
                updateDigest(digest, quality);
            }
            updateDigest(digest, target.musicInfoJson());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static long addSaturated(long first, long second) {
        try {
            return Math.addExact(first, second);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static long multiplySaturated(long value, long multiplier) {
        try {
            return Math.multiplyExact(value, multiplier);
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean retryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static IOException asIOException(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        return cause instanceof IOException ioException ? ioException
                : new IOException("Online audio download failed", cause);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = unwrap(throwable);
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static URI validateUri(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https"))
                    || uri.getHost() == null || uri.getHost().isBlank()
                    || uri.getUserInfo() != null) {
                throw new IOException("Audio source returned an invalid HTTP URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Audio source returned an invalid HTTP URL", exception);
        }
    }

    private static String baseContentType(String value) {
        int separator = value.indexOf(';');
        String contentType = (separator < 0 ? value : value.substring(0, separator))
                .strip().toLowerCase(Locale.ROOT);
        return contentType.isEmpty() ? null : contentType;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new java.util.concurrent.CompletionException(
                    new IOException("Music audio cache is closed"));
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<DownloadOperation> downloads;
        CompletableFuture<CacheStats> clear;
        synchronized (cacheLock) {
            generation.incrementAndGet();
            downloads = new ArrayList<>(activeDownloads);
            inFlight.clear();
            activeEntries.clear();
            deleteOnRelease.clear();
            cachedEntries.clear();
            trackCatalog.clear();
            clear = clearInFlight;
            clearInFlight = null;
        }
        downloads.forEach(DownloadOperation::cancel);
        if (clear != null) {
            clear.completeExceptionally(new IOException("Music audio cache is closed"));
        }
        downloadExecutor.shutdownNow();
    }

    public record CacheStats(long files, long bytes) {
    }

    public static final class CachedAudio implements AutoCloseable {
        private final String identifier;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        private CachedAudio(String identifier, Runnable release) {
            this.identifier = identifier;
            this.release = release;
        }

        public String identifier() {
            return identifier;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }

    /** A leased cache entry which remains readable while its backing download grows. */
    public static final class StreamingAudio implements AutoCloseable {
        private final StreamingState state;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        private StreamingAudio(StreamingState state, Runnable release) {
            this.state = state;
            this.release = release;
        }

        /** HTTP content length, or {@code -1} when the server did not declare one. */
        public long contentLength() {
            return state.contentLength();
        }

        /** Normalized HTTP media type without parameters, when provided by the server. */
        public String contentType() {
            return state.contentType();
        }

        public Reader openReader() throws IOException {
            if (closed.get()) {
                throw new IOException("Streaming audio lease is closed");
            }
            return state.openReader();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }

        /** Independent seekable reader over the bytes currently entering the cache. */
        public static final class Reader implements AutoCloseable {
            private final StreamingState state;
            private final Object channelLock = new Object();
            private final AtomicBoolean closed = new AtomicBoolean();
            private FileChannel channel;
            private long position;

            private Reader(StreamingState state, FileChannel channel) {
                this.state = state;
                this.channel = channel;
            }

            public int read() throws IOException {
                byte[] single = new byte[1];
                int read = read(single, 0, 1);
                return read < 0 ? -1 : Byte.toUnsignedInt(single[0]);
            }

            public int read(byte[] bytes, int offset, int length) throws IOException {
                Objects.checkFromIndexSize(offset, length, bytes.length);
                if (length == 0) {
                    return 0;
                }
                while (true) {
                    int readable = state.awaitReadable(position, length, closed);
                    if (readable < 0) {
                        return -1;
                    }
                    int read = readableChannel().read(
                            ByteBuffer.wrap(bytes, offset, readable), position);
                    if (read > 0) {
                        position += read;
                        return read;
                    }
                }
            }

            private FileChannel readableChannel() throws IOException {
                synchronized (channelLock) {
                    if (closed.get()) {
                        throw new IOException("Streaming audio reader is closed");
                    }
                    // Lavaplayer interrupts its decoding thread to perform a seek. NIO
                    // permanently closes a FileChannel when an in-progress read is
                    // interrupted, so the next read must reopen the same cache file.
                    if (!channel.isOpen()) {
                        channel = state.openChannel();
                    }
                    return channel;
                }
            }

            public long position() {
                return position;
            }

            public void seek(long newPosition) throws IOException {
                if (newPosition < 0) {
                    throw new IOException("Cannot seek before the start of streaming audio");
                }
                state.validatePosition(newPosition);
                position = newPosition;
            }

            public int available() throws IOException {
                if (closed.get()) {
                    throw new IOException("Streaming audio reader is closed");
                }
                return state.availableFrom(position);
            }

            @Override
            public void close() throws IOException {
                if (closed.compareAndSet(false, true)) {
                    state.readerClosed();
                    synchronized (channelLock) {
                        channel.close();
                    }
                }
            }
        }
    }

    private final class EntryReservation implements AutoCloseable {
        private final String entryKey;
        private final AtomicBoolean released = new AtomicBoolean();

        private EntryReservation(String entryKey) {
            this.entryKey = entryKey;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                release(entryKey);
            }
        }
    }

    private record CacheEntry(Path path, long bytes, FileTime lastUsed) {
    }

    private record DownloadAccess(CompletableFuture<Path> completed,
                                  CompletableFuture<StreamingState> streaming,
                                  Runnable streamingRequest) {
        private CompletableFuture<StreamingState> acquireStreaming() {
            streamingRequest.run();
            return streaming;
        }
    }

    private static final class StreamingState {
        private Path path;
        private final long declaredLength;
        private final String contentType;
        private long availableBytes;
        private boolean completed;
        private IOException failure;

        private StreamingState(Path path, long declaredLength, String contentType) {
            this.path = path;
            this.declaredLength = declaredLength;
            this.contentType = contentType;
        }

        private static StreamingState completed(Path path, long length) {
            StreamingState state = new StreamingState(path, length, null);
            state.availableBytes = length;
            state.completed = true;
            return state;
        }

        private synchronized long contentLength() {
            return declaredLength;
        }

        private synchronized String contentType() {
            return contentType;
        }

        private synchronized StreamingAudio.Reader openReader() throws IOException {
            return new StreamingAudio.Reader(this, openChannel());
        }

        private synchronized FileChannel openChannel() throws IOException {
            throwIfFailed();
            try {
                return FileChannel.open(path, StandardOpenOption.READ);
            } catch (NoSuchFileException exception) {
                throw new IOException("Streaming audio file is no longer available", exception);
            }
        }

        private synchronized void bytesWritten(long bytes) {
            if (failure == null && !completed && bytes > availableBytes) {
                availableBytes = bytes;
                notifyAll();
            }
        }

        private synchronized void publish(Path destination) throws IOException {
            throwIfFailed();
            moveIntoPlace(path, destination);
            path = destination;
            completed = true;
            notifyAll();
        }

        private synchronized void fail(Throwable throwable) {
            if (failure != null || completed) {
                return;
            }
            failure = asIOException(throwable);
            notifyAll();
        }

        private synchronized int awaitReadable(long position, int requested,
                                                AtomicBoolean readerClosed) throws IOException {
            while (position >= availableBytes && !completed && failure == null
                    && !readerClosed.get()) {
                try {
                    wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    InterruptedIOException interrupted = new InterruptedIOException(
                            "Interrupted while buffering online audio");
                    interrupted.initCause(exception);
                    throw interrupted;
                }
            }
            if (readerClosed.get()) {
                throw new IOException("Streaming audio reader is closed");
            }
            throwIfFailed();
            long available = availableBytes - position;
            if (available <= 0) {
                return -1;
            }
            return (int) Math.min(requested, available);
        }

        private synchronized void validatePosition(long position) throws IOException {
            throwIfFailed();
            long knownLength = completed ? availableBytes : declaredLength;
            if (knownLength >= 0 && position > knownLength) {
                throw new EOFException("Cannot seek beyond the end of streaming audio");
            }
        }

        private synchronized int availableFrom(long position) throws IOException {
            throwIfFailed();
            return (int) Math.min(Integer.MAX_VALUE,
                    Math.max(0, availableBytes - position));
        }

        private synchronized void readerClosed() {
            notifyAll();
        }

        private void throwIfFailed() throws IOException {
            if (failure != null) {
                throw failure;
            }
        }

        private static IOException asIOException(Throwable throwable) {
            Throwable cause = throwable;
            while ((cause instanceof java.util.concurrent.CompletionException
                    || cause instanceof java.util.concurrent.ExecutionException)
                    && cause.getCause() != null) {
                cause = cause.getCause();
            }
            return cause instanceof IOException ioException ? ioException
                    : new IOException("Online audio download failed", cause);
        }
    }

    private static final class DownloadOperation {
        private final CompletableFuture<Path> result = new CompletableFuture<>();
        private final CompletableFuture<StreamingState> streaming = new CompletableFuture<>();
        private final CompletableFuture<Void> terminated = new CompletableFuture<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<CompletableFuture<?>> upstream = new AtomicReference<>();
        private final AtomicReference<Future<?>> worker = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<?>> httpRequest = new AtomicReference<>();
        private final AtomicReference<LimitedFileSubscriber> subscriber = new AtomicReference<>();
        private final AtomicReference<StreamingState> streamingState = new AtomicReference<>();
        private final AtomicBoolean streamingRequested = new AtomicBoolean();
        private final AtomicBoolean streamingBytesAvailable = new AtomicBoolean();

        private DownloadAccess access() {
            return new DownloadAccess(result, streaming, this::requestStreaming);
        }

        private StreamingState createStreamingState(Path path, long declaredLength,
                                                    String contentType) {
            StreamingState state = new StreamingState(path, declaredLength, contentType);
            if (!streamingState.compareAndSet(null, state)) {
                throw new IllegalStateException("Streaming state is already initialized");
            }
            if (cancelled.get()) {
                state.fail(new IOException("Music download was cancelled"));
            }
            return state;
        }

        private void useCompleted(Path path, long length) {
            StreamingState state = StreamingState.completed(path, length);
            if (streamingState.compareAndSet(null, state)) {
                streaming.complete(state);
            }
        }

        private void bytesAvailable(StreamingState state) {
            streamingBytesAvailable.set(true);
            if (streamingRequested.get()) {
                streaming.complete(state);
            }
        }

        private void requestStreaming() {
            streamingRequested.set(true);
            StreamingState state = streamingState.get();
            if (state != null && streamingBytesAvailable.get()) {
                streaming.complete(state);
            }
        }

        private void publish(Path destination) throws IOException {
            StreamingState state = streamingState.get();
            if (state == null) {
                throw new IOException("Audio response did not initialize a streaming cache file");
            }
            state.publish(destination);
            streaming.complete(state);
        }

        private void complete(Path path) {
            result.complete(path);
        }

        private void fail(Throwable throwable) {
            StreamingState state = streamingState.get();
            if (state != null) {
                state.fail(throwable);
            }
            IOException failure = StreamingState.asIOException(throwable);
            streaming.completeExceptionally(failure);
            result.completeExceptionally(failure);
        }

        private void setUpstream(CompletableFuture<?> future) {
            setCancellable(upstream, future);
        }

        private void setWorker(DownloadWorker future) {
            setCancellable(worker, future);
        }

        private DownloadWorker newWorker(Runnable work) {
            return new DownloadWorker(work, terminated);
        }

        private CompletableFuture<Void> terminated() {
            return terminated;
        }

        private void setHttpRequest(CompletableFuture<?> future) {
            setCancellable(httpRequest, future);
        }

        private void setSubscriber(LimitedFileSubscriber value) {
            subscriber.set(value);
            if (cancelled.get() && subscriber.compareAndSet(value, null)) {
                value.abort(new IOException("Music download was cancelled"));
            }
        }

        private boolean prepareRetry(Throwable failure) {
            if (cancelled.get()) {
                return false;
            }
            StreamingState state = streamingState.get();
            if (state == null) {
                return true;
            }
            if (streaming.isDone() || !streamingState.compareAndSet(state, null)) {
                return false;
            }
            state.fail(failure);
            streamingBytesAvailable.set(false);
            subscriber.set(null);
            httpRequest.set(null);
            return !cancelled.get();
        }

        private boolean cancelled() {
            return cancelled.get();
        }

        private <T extends Future<?>> void setCancellable(AtomicReference<T> target, T future) {
            target.set(future);
            if (cancelled.get() && target.compareAndSet(future, null)) {
                future.cancel(true);
            }
        }

        private void cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return;
            }
            IOException cancellation = new IOException("Music download was cancelled");
            StreamingState state = streamingState.get();
            if (state != null) {
                state.fail(cancellation);
            }
            streaming.completeExceptionally(cancellation);
            result.completeExceptionally(cancellation);
            cancel(upstream.getAndSet(null));
            cancel(httpRequest.getAndSet(null));
            LimitedFileSubscriber activeSubscriber = subscriber.getAndSet(null);
            if (activeSubscriber != null) {
                activeSubscriber.abort(cancellation);
            }
            cancel(worker.getAndSet(null));
        }

        private static void cancel(Future<?> future) {
            if (future != null) {
                future.cancel(true);
            }
        }
    }

    private static final class DownloadWorker extends FutureTask<Void> {
        private final CompletableFuture<Void> terminated;
        private final AtomicBoolean submitted = new AtomicBoolean();

        private DownloadWorker(Runnable work, CompletableFuture<Void> terminated) {
            super(work, null);
            this.terminated = terminated;
        }

        @Override
        public void run() {
            submitted.set(true);
            try {
                super.run();
            } finally {
                terminated.complete(null);
            }
        }

        private void rejected() {
            if (!submitted.get()) {
                cancel(false);
                terminated.complete(null);
            }
        }
    }

    private static final class LimitedFileSubscriber implements HttpResponse.BodySubscriber<Path> {
        private final Path target;
        private final long maximumBytes;
        private final long declaredLength;
        private final StreamingState streamingState;
        private final DownloadOperation owner;
        private final DownloadProgress progress;
        private final CompletableFuture<Path> body = new CompletableFuture<>();
        private final OutputStream output;
        private Flow.Subscription subscription;
        private long received;
        private boolean finished;

        private LimitedFileSubscriber(Path target, long maximumBytes, long declaredLength,
                                      StreamingState streamingState,
                                      DownloadOperation owner,
                                      DownloadProgress progress) throws IOException {
            this.target = target;
            this.maximumBytes = maximumBytes;
            this.declaredLength = declaredLength;
            this.streamingState = streamingState;
            this.owner = owner;
            this.progress = progress;
            this.output = Files.newOutputStream(target);
        }

        @Override
        public java.util.concurrent.CompletionStage<Path> getBody() {
            return body;
        }

        @Override
        public synchronized void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            progress.advanced();
            subscription.request(1);
        }

        @Override
        public synchronized void onNext(List<ByteBuffer> buffers) {
            if (finished) {
                return;
            }
            try {
                for (ByteBuffer buffer : buffers) {
                    int remaining = buffer.remaining();
                    received += remaining;
                    if (received > maximumBytes) {
                        throw new RemoteAudioException(
                                "Audio download exceeds " + maximumBytes + " bytes",
                                null, false);
                    }
                    byte[] bytes = new byte[Math.min(COPY_BUFFER_BYTES, remaining)];
                    while (buffer.hasRemaining()) {
                        int length = Math.min(bytes.length, buffer.remaining());
                        buffer.get(bytes, 0, length);
                        output.write(bytes, 0, length);
                    }
                    if (remaining > 0) {
                        progress.advanced();
                    }
                }
                streamingState.bytesWritten(received);
                owner.bytesAvailable(streamingState);
                subscription.request(1);
            } catch (IOException exception) {
                subscription.cancel();
                fail(exception instanceof RemoteAudioException
                        ? exception : new LocalCacheException(
                        "Failed to write the streaming cache file", exception));
            }
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            if (declaredLength >= 0L && received < declaredLength) {
                EOFException truncated = incompleteDownload(received, declaredLength);
                truncated.initCause(throwable);
                fail(new RemoteAudioException(truncated.getMessage(), truncated, true));
                return;
            }
            fail(new RemoteAudioException(
                    "Audio download connection failed", throwable, true));
        }

        @Override
        public synchronized void onComplete() {
            if (finished) {
                return;
            }
            finished = true;
            progress.advanced();
            try {
                output.close();
                if (received == 0) {
                    body.completeExceptionally(new RemoteAudioException(
                            "Audio download returned an empty file", null, true));
                } else if (declaredLength >= 0L && received != declaredLength) {
                    EOFException truncated = incompleteDownload(received, declaredLength);
                    body.completeExceptionally(new RemoteAudioException(
                            truncated.getMessage(), truncated, true));
                } else {
                    body.complete(target);
                }
            } catch (IOException exception) {
                body.completeExceptionally(new LocalCacheException(
                        "Failed to finish the streaming cache file", exception));
            }
        }

        private void fail(Throwable throwable) {
            if (finished) {
                return;
            }
            finished = true;
            try {
                output.close();
            } catch (IOException closeError) {
                throwable.addSuppressed(closeError);
            }
            body.completeExceptionally(throwable);
        }

        private synchronized void abort(IOException exception) {
            if (subscription != null) {
                subscription.cancel();
            }
            fail(exception);
        }
    }

    private static final class DownloadProgress {
        private final AtomicLong lastProgressNanos = new AtomicLong(System.nanoTime());

        private void advanced() {
            lastProgressNanos.set(System.nanoTime());
        }

        private long lastProgressNanos() {
            return lastProgressNanos.get();
        }
    }

    private static EOFException incompleteDownload(long received, long declaredLength) {
        return new EOFException("Audio download ended after " + received
                + " bytes; expected " + declaredLength + " bytes");
    }

    private static final class RemoteAudioException extends IOException {
        private final boolean transientFailure;

        private RemoteAudioException(String message, Throwable cause, boolean transientFailure) {
            super(message, cause);
            this.transientFailure = transientFailure;
        }

        private boolean transientFailure() {
            return transientFailure;
        }
    }

    private static final class LocalCacheException extends IOException {
        private LocalCacheException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class RejectedBodySubscriber implements HttpResponse.BodySubscriber<Path> {
        private final CompletableFuture<Path> body = new CompletableFuture<>();

        private RejectedBodySubscriber(IOException exception) {
            body.completeExceptionally(exception);
        }

        @Override
        public java.util.concurrent.CompletionStage<Path> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.cancel();
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onComplete() {
        }
    }
}
