package org.encinet.mik.feature.entertainment.music.playback;

import java.util.Objects;
import java.util.UUID;

/** One occurrence, including its submitting actor. The same track may occur more than once. */
public record QueueEntry(UUID id, String trackId, UUID actor) {
    public QueueEntry {
        Objects.requireNonNull(id);
        Objects.requireNonNull(trackId);
    }
    public static QueueEntry create(String track, UUID actor) {
        return new QueueEntry(UUID.randomUUID(), track, actor);
    }
}
