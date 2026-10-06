package org.encinet.mik.module.afk.viewing;

import java.util.Objects;
import java.util.UUID;

/** Immutable server snapshot; a configured URL alone must never imply PLAYING. */
public record PhysicalScreen(UUID id, UUID worldId, ScreenGeometry geometry,
                             String contentKey, Playback playback) {
    public PhysicalScreen {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(contentKey, "contentKey");
        Objects.requireNonNull(playback, "playback");
    }

    public enum Playback { PLAYING, PAUSED, INACTIVE, UNKNOWN }
}
