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
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
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
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_CONCURRENT_DOWNLOADS = 4;
    private static final String CACHE_SUFFIX = ".audio";
    private static final String PART_SUFFIX = ".part";

    private final Path cacheDirectory;
    private final LxTrackResolver upstream;
    private final Consumer<String> warningLogger;
    private final long maxAudioBytes;
    private final long maxCacheBytes;
    private final Duration downloadTimeout;
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
        this.cacheDirectory = cacheDirectory.toAbsolutePath().normalize();
        this.upstream = upstream;
        this.warningLogger = warningLogger;
        this.trackCatalog = new CachedOnlineTrackCatalog(this.cacheDirectory, warningLogger);
        this.playbackStats = Objects.requireNonNull(playbackStats, "playbackStats");
        this.maxAudioBytes = maxAudioBytes;
        this.maxCacheBytes = maxCacheBytes;
        this.downloadTimeout = downloadTimeout;
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
                            CompletableFuture.completedFuture(state)));
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

        CompletableFuture<String> resolved;
        try {
            resolved = upstream.resolve(target);
        } catch (RuntimeException exception) {
            inFlight.remove(entryKey, created);
            created.fail(exception);
            created.completeWithoutWorker();
            return CompletableFuture.completedFuture(created.access());
        }
        created.setUpstream(resolved);
        resolved.whenComplete((url, error) -> {
            if (error != null) {
                finishDownload(entryKey, created, null, error);
                created.completeWithoutWorker();
                return;
            }
            DownloadWorker worker = created.newWorker(() -> {
                try {
                    finishDownload(entryKey, created,
                            download(entryKey, url, destination, expectedGeneration, created), null);
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
        });
        return CompletableFuture.completedFuture(created.access());
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
        start(target).thenCompose(DownloadAccess::streaming).whenComplete((state, error) -> {
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
            URI uri = validateUri(url);
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
            CompletableFuture<HttpResponse<Path>> pending = httpClient.sendAsync(request, response -> {
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    return new RejectedBodySubscriber(
                            new IOException("Audio download returned HTTP " + status));
                }
                long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declaredLength > maxAudioBytes) {
                    return new RejectedBodySubscriber(
                            new IOException("Audio download exceeds " + maxAudioBytes + " bytes"));
                }
                try {
                    String contentType = response.headers().firstValue("Content-Type")
                            .map(OnlineAudioCache::baseContentType).orElse(null);
                    StreamingState state = owner.createStreamingState(
                            target, declaredLength, contentType);
                    LimitedFileSubscriber subscriber = new LimitedFileSubscriber(
                            target, maxAudioBytes, state, owner);
                    subscriberRef.set(subscriber);
                    owner.setSubscriber(subscriber);
                    return subscriber;
                } catch (IOException exception) {
                    return new RejectedBodySubscriber(exception);
                }
            });
            owner.setHttpRequest(pending);
            try {
                pending.get(downloadTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                pending.cancel(true);
                abort(subscriberRef, new InterruptedIOException("Audio download was interrupted"));
                throw exception;
            } catch (TimeoutException exception) {
                pending.cancel(true);
                IOException timeout = new IOException("Audio download timed out after "
                        + downloadTimeout.toSeconds() + " seconds", exception);
                abort(subscriberRef, timeout);
                throw timeout;
            } catch (java.util.concurrent.ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof IOException ioException) {
                    throw ioException;
                }
                throw new IOException("Audio download failed", cause);
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
            private final FileChannel channel;
            private final AtomicBoolean closed = new AtomicBoolean();
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
                    int read = channel.read(ByteBuffer.wrap(bytes, offset, readable), position);
                    if (read > 0) {
                        position += read;
                        return read;
                    }
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
                    channel.close();
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
                                  CompletableFuture<StreamingState> streaming) {
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
            throwIfFailed();
            try {
                return new StreamingAudio.Reader(this,
                        FileChannel.open(path, StandardOpenOption.READ));
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

        private DownloadAccess access() {
            return new DownloadAccess(result, streaming);
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
            streaming.complete(state);
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

        private void completeWithoutWorker() {
            terminated.complete(null);
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
        private final StreamingState streamingState;
        private final DownloadOperation owner;
        private final CompletableFuture<Path> body = new CompletableFuture<>();
        private final OutputStream output;
        private Flow.Subscription subscription;
        private long received;
        private boolean finished;

        private LimitedFileSubscriber(Path target, long maximumBytes,
                                      StreamingState streamingState,
                                      DownloadOperation owner) throws IOException {
            this.target = target;
            this.maximumBytes = maximumBytes;
            this.streamingState = streamingState;
            this.owner = owner;
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
                        throw new IOException("Audio download exceeds " + maximumBytes + " bytes");
                    }
                    byte[] bytes = new byte[Math.min(COPY_BUFFER_BYTES, remaining)];
                    while (buffer.hasRemaining()) {
                        int length = Math.min(bytes.length, buffer.remaining());
                        buffer.get(bytes, 0, length);
                        output.write(bytes, 0, length);
                    }
                }
                streamingState.bytesWritten(received);
                owner.bytesAvailable(streamingState);
                subscription.request(1);
            } catch (IOException exception) {
                subscription.cancel();
                fail(exception);
            }
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            fail(throwable);
        }

        @Override
        public synchronized void onComplete() {
            if (finished) {
                return;
            }
            finished = true;
            try {
                output.close();
                if (received == 0) {
                    body.completeExceptionally(new IOException("Audio download returned an empty file"));
                } else {
                    body.complete(target);
                }
            } catch (IOException exception) {
                body.completeExceptionally(exception);
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
