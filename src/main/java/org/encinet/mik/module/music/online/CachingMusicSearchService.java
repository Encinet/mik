package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.MusicTrack;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Coalesces identical online searches and briefly reuses their immutable results. */
final class CachingMusicSearchService implements MusicSearchService {

    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);
    private static final int DEFAULT_MAX_ENTRIES = 128;

    private final MusicSearchService delegate;
    private final long ttlNanos;
    private final int maxEntries;
    private final LongSupplier nanoTime;
    private final Object cacheLock = new Object();
    private final LinkedHashMap<SearchKey, CacheEntry> cache =
            new LinkedHashMap<>(16, 0.75F, true);
    private final ConcurrentHashMap<SearchKey,
            CompletableFuture<MusicSearchResult<MusicTrack>>> inFlight =
            new ConcurrentHashMap<>();
    private final AtomicLong generation = new AtomicLong();

    CachingMusicSearchService(MusicSearchService delegate) {
        this(delegate, DEFAULT_TTL, DEFAULT_MAX_ENTRIES, System::nanoTime);
    }

    CachingMusicSearchService(MusicSearchService delegate, Duration ttl,
                              int maxEntries, LongSupplier nanoTime) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(ttl, "ttl");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.ttlNanos = ttl.toNanos();
        this.maxEntries = maxEntries;
    }

    @Override
    public CompletableFuture<MusicSearchResult<MusicTrack>> searchMusic(
            String keyword, int page, int limit) {
        long requestGeneration = generation.get();
        SearchKey key = new SearchKey(normalize(keyword), page, limit, requestGeneration);
        MusicSearchResult<MusicTrack> cached = cached(key, nanoTime.getAsLong());
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<MusicSearchResult<MusicTrack>> placeholder = new CompletableFuture<>();
        CompletableFuture<MusicSearchResult<MusicTrack>> shared =
                inFlight.putIfAbsent(key, placeholder);
        if (shared != null) {
            return shared.thenApply(result -> result);
        }

        MusicSearchResult<MusicTrack> cachedAfterClaim = cached(key, nanoTime.getAsLong());
        if (cachedAfterClaim != null) {
            placeholder.complete(cachedAfterClaim);
            inFlight.remove(key, placeholder);
            return placeholder.thenApply(result -> result);
        }

        CompletableFuture<MusicSearchResult<MusicTrack>> request;
        try {
            request = delegate.searchMusic(keyword, page, limit);
            if (request == null) {
                request = CompletableFuture.failedFuture(
                        new IllegalStateException("Online search returned no future"));
            }
        } catch (RuntimeException exception) {
            request = CompletableFuture.failedFuture(exception);
        }
        request.whenComplete((result, error) -> {
            if (error == null && result != null && generation.get() == requestGeneration) {
                store(key, result, nanoTime.getAsLong());
            }
            if (error == null && result != null) {
                placeholder.complete(result);
            } else if (error != null) {
                placeholder.completeExceptionally(error);
            } else {
                placeholder.completeExceptionally(
                        new IllegalStateException("Online search returned no result"));
            }
            inFlight.remove(key, placeholder);
        });
        return placeholder.thenApply(result -> result);
    }

    /** Invalidates completed results while allowing already-running callers to finish safely. */
    void invalidate() {
        generation.incrementAndGet();
        synchronized (cacheLock) {
            cache.clear();
        }
    }

    private MusicSearchResult<MusicTrack> cached(SearchKey key, long now) {
        synchronized (cacheLock) {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            if (now - entry.createdAtNanos() >= ttlNanos) {
                cache.remove(key);
                return null;
            }
            return entry.result();
        }
    }

    private void store(SearchKey key, MusicSearchResult<MusicTrack> result, long now) {
        synchronized (cacheLock) {
            cache.put(key, new CacheEntry(result, now));
            while (cache.size() > maxEntries) {
                var iterator = cache.entrySet().iterator();
                iterator.next();
                iterator.remove();
            }
        }
    }

    private static String normalize(String keyword) {
        return keyword == null ? "" : keyword.strip();
    }

    private record SearchKey(String keyword, int page, int limit, long generation) {
    }

    private record CacheEntry(MusicSearchResult<MusicTrack> result, long createdAtNanos) {
    }
}
