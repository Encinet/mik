package org.encinet.mik.feature.entertainment.music.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;

/** Only decorates the published saved page. A failed fetch never rewrites saved labels. */
final class SavedTrackMetadata {
    private static final int BATCH_SIZE = 24;
    private List<String> visible = List.of();
    private Map<String, MusicTrack> current = Map.of();
    private final List<CompletableFuture<?>> requests = new ArrayList<>();
    private long generation;

    MusicTrack decorate(MusicTrack saved) { return current.getOrDefault(saved.id(), saved); }

    void refresh(List<MusicTrack> page, Function<List<String>, CompletableFuture<Map<String, MusicTrack>>> lookup,
                 Consumer<Runnable> resultThread, BooleanSupplier ownsPage, Runnable redraw) {
        var ids = page.stream().filter(track -> track.target() instanceof TrackTarget.Managed)
                .map(MusicTrack::id).distinct().toList();
        if (visible.equals(ids)) return;
        invalidate();
        visible = ids;
        long epoch = generation;
        for (int offset = 0; offset < ids.size(); offset += BATCH_SIZE) {
            var batch = ids.subList(offset, Math.min(offset + BATCH_SIZE, ids.size()));
            var request = lookup.apply(batch);
            requests.add(request);
            request.whenComplete((metadata, error) -> resultThread.accept(() -> {
                requests.remove(request);
                if (error != null || metadata == null || epoch != generation || !ownsPage.getAsBoolean()) return;
                var next = new HashMap<>(current);
                for (String id : batch) {
                    var row = metadata.get(id);
                    if (row != null && id.equals(row.id())) next.put(id, row);
                }
                if (!current.equals(next)) {
                    current = Map.copyOf(next);
                    redraw.run();
                }
            }));
        }
    }

    void invalidate() {
        ++generation;
        var cancelled = List.copyOf(requests);
        requests.clear();
        visible = List.of();
        current = Map.of();
        cancelled.forEach(request -> request.cancel(false));
    }
}
