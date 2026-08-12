package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.MusicTrack;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CachingMusicSearchServiceTest {

    @Test
    void coalescesInFlightRequestsAndIsolatesCallerCancellation() {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<MusicSearchResult<MusicTrack>> pending = new CompletableFuture<>();
        CachingMusicSearchService service = service((keyword, page, limit) -> {
            calls.incrementAndGet();
            return pending;
        }, new AtomicLong());

        CompletableFuture<MusicSearchResult<MusicTrack>> first =
                service.searchMusic("song", 1, 30);
        CompletableFuture<MusicSearchResult<MusicTrack>> second =
                service.searchMusic("song", 1, 30);
        first.cancel(false);
        MusicSearchResult<MusicTrack> result = new MusicSearchResult<>(List.of(), 0, List.of());
        pending.complete(result);

        assertEquals(1, calls.get());
        assertEquals(result, second.join());
        assertFalse(pending.isCancelled());
    }

    @Test
    void reusesResultUntilTtlAndReloadInvalidation() {
        AtomicInteger calls = new AtomicInteger();
        AtomicLong now = new AtomicLong();
        CachingMusicSearchService service = service((keyword, page, limit) -> {
            calls.incrementAndGet();
            return CompletableFuture.completedFuture(
                    new MusicSearchResult<>(List.of(), calls.get(), List.of()));
        }, now);

        assertEquals(1, service.searchMusic("song", 1, 30).join().total());
        assertEquals(1, service.searchMusic("song", 1, 30).join().total());

        now.addAndGet(Duration.ofSeconds(31).toNanos());
        assertEquals(2, service.searchMusic("song", 1, 30).join().total());

        service.invalidate();
        assertEquals(3, service.searchMusic("song", 1, 30).join().total());
    }

    @Test
    void failedRequestsAreNotCached() {
        AtomicInteger calls = new AtomicInteger();
        CachingMusicSearchService service = service((keyword, page, limit) -> {
            if (calls.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("failed"));
            }
            return CompletableFuture.completedFuture(
                    new MusicSearchResult<>(List.of(), 0, List.of()));
        }, new AtomicLong());

        service.searchMusic("song", 1, 30).handle((result, error) -> null).join();
        service.searchMusic("song", 1, 30).join();

        assertEquals(2, calls.get());
    }

    @Test
    void invalidationDoesNotJoinOrCacheAnOlderInFlightRequest() {
        AtomicInteger calls = new AtomicInteger();
        ConcurrentLinkedQueue<CompletableFuture<MusicSearchResult<MusicTrack>>> requests =
                new ConcurrentLinkedQueue<>();
        CachingMusicSearchService service = service((keyword, page, limit) -> {
            calls.incrementAndGet();
            CompletableFuture<MusicSearchResult<MusicTrack>> request = new CompletableFuture<>();
            requests.add(request);
            return request;
        }, new AtomicLong());

        CompletableFuture<MusicSearchResult<MusicTrack>> old =
                service.searchMusic("song", 1, 30);
        service.invalidate();
        CompletableFuture<MusicSearchResult<MusicTrack>> current =
                service.searchMusic("song", 1, 30);

        MusicSearchResult<MusicTrack> oldResult =
                new MusicSearchResult<>(List.of(), 1, List.of());
        MusicSearchResult<MusicTrack> currentResult =
                new MusicSearchResult<>(List.of(), 2, List.of());
        requests.remove().complete(oldResult);
        requests.remove().complete(currentResult);

        assertEquals(2, calls.get());
        assertEquals(oldResult, old.join());
        assertEquals(currentResult, current.join());
        assertEquals(currentResult, service.searchMusic("song", 1, 30).join());
    }

    private static CachingMusicSearchService service(
            MusicSearchService delegate, AtomicLong now) {
        return new CachingMusicSearchService(
                delegate, Duration.ofSeconds(30), 8, now::get);
    }
}
