package org.encinet.mik.feature.entertainment.music.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.encinet.mik.feature.entertainment.music.MusicTestTracks;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SavedTrackMetadataTest {
    @Test
    void splitsLookupsAndDoesNotApplyAnAlreadyQueuedResultAfterNavigation() {
        var metadata = new SavedTrackMetadata();
        var page = IntStream.range(0, 25).mapToObj(index -> track(index, "Saved")).toList();
        var batches = new ArrayList<List<String>>();
        var requests = new ArrayList<CompletableFuture<Map<String, MusicTrack>>>();
        var main = new ArrayDeque<Runnable>();
        var redraws = new AtomicInteger();

        metadata.refresh(page, ids -> {
            batches.add(List.copyOf(ids));
            var request = new CompletableFuture<Map<String, MusicTrack>>();
            requests.add(request);
            return request;
        }, main::add, () -> true, redraws::incrementAndGet);
        assertEquals(List.of(24, 1), batches.stream().map(List::size).toList());
        assertEquals(page.stream().map(MusicTrack::id).toList(), batches.stream().flatMap(List::stream).toList());

        var updated = track(24, "Updated");
        requests.getLast().complete(Map.of(updated.id(), updated));
        main.remove().run();
        assertEquals(updated, metadata.decorate(page.getLast()));
        assertEquals(1, redraws.get());

        var stale = track(0, "Old result");
        requests.getFirst().complete(Map.of(stale.id(), stale));
        metadata.invalidate();
        main.remove().run();
        assertEquals(page.getFirst(), metadata.decorate(page.getFirst()));
        assertEquals(page.getLast(), metadata.decorate(page.getLast()));
        assertEquals(1, redraws.get());
    }

    private static MusicTrack track(int index, String title) {
        return MusicTestTracks.managed("saved-" + index, new TrackDetails(title, null, null, null));
    }
}
