package org.encinet.mik.module.plot;

import java.util.Objects;
import java.util.UUID;

/** Stable target of an assisted plot registration. */
record PlotOwner(UUID id, String name) {
    PlotOwner {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
