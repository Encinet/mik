package org.encinet.mik.module.plot;

import java.util.Objects;
import java.util.UUID;

/** Immutable current-plot context shared with the main menu. */
public record PlotNoticeBoard(UUID plotId, String name, UUID ownerId,
                              String ownerName, String body, String parentName) {
    public PlotNoticeBoard {
        Objects.requireNonNull(plotId, "plotId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(ownerName, "ownerName");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(parentName, "parentName");
    }

    public String preview() { return PlotNoticeText.preview(body); }
}
