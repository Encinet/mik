package org.encinet.mik.feature.entertainment.music.playback;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread command generations: stopped work cannot start after its durable commit completes. */
public final class PlaybackIntents {
    private final Map<UUID, Long> generations = new HashMap<>();
    public long begin(UUID target) {
        return generations.merge(target, 1L, Long::sum);
    }
    public void cancel(UUID target) {
        begin(target);
    }
    public boolean current(UUID target, long generation) {
        return generations.getOrDefault(target, 0L) == generation;
    }
    public void clear() {
        generations.clear();
    }
}
