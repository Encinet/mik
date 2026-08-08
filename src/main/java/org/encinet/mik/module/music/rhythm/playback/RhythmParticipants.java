package org.encinet.mik.module.music.rhythm.playback;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Token-based participant ownership for one rhythm transport.
 *
 * <p>A newer participation for the same player supersedes the older token, so
 * closing a stale menu cannot accidentally remove the current game.</p>
 */
public final class RhythmParticipants {
    private final Map<UUID, UUID> tokens = new HashMap<>();

    public synchronized Participation acquire(UUID playerId, Runnable onLastRelease) {
        UUID player = Objects.requireNonNull(playerId, "playerId");
        Runnable onLast = Objects.requireNonNull(onLastRelease, "onLastRelease");
        UUID token = UUID.randomUUID();
        UUID previous = tokens.put(player, token);
        boolean first = previous == null && tokens.size() == 1;
        return new Participation(first, () -> release(player, token, onLast));
    }

    public synchronized boolean isEmpty() {
        return tokens.isEmpty();
    }

    public synchronized int size() {
        return tokens.size();
    }

    private void release(UUID playerId, UUID token, Runnable onLastRelease) {
        boolean last;
        synchronized (this) {
            if (!tokens.remove(playerId, token)) return;
            last = tokens.isEmpty();
        }
        if (last) onLastRelease.run();
    }

    public static final class Participation implements AutoCloseable {
        private final boolean first;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Participation(boolean first, Runnable release) {
            this.first = first;
            this.release = release;
        }

        public boolean first() {
            return first;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) release.run();
        }
    }
}
