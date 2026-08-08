package org.encinet.mik.module.music.jukebox;

import org.encinet.mik.module.music.rhythm.playback.RhythmPlaybackIsolation;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Thread-safe, reference-counted exclusions shared by every jukebox backend. */
final class JukeboxAudioAudience {
    private final ConcurrentHashMap<UUID, Integer> exclusions =
            new ConcurrentHashMap<>();

    RhythmPlaybackIsolation.SilenceLease silence(UUID playerId) {
        UUID id = Objects.requireNonNull(playerId, "playerId");
        exclusions.merge(id, 1, Integer::sum);
        AtomicBoolean restored = new AtomicBoolean();
        return () -> {
            if (!restored.compareAndSet(false, true)) return;
            exclusions.computeIfPresent(id,
                    (ignored, count) -> count <= 1 ? null : count - 1);
        };
    }

    boolean canHear(UUID playerId) {
        return !exclusions.containsKey(Objects.requireNonNull(playerId, "playerId"));
    }
}
