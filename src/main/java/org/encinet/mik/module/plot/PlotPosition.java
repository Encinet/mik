package org.encinet.mik.module.plot;

import java.util.Objects;
import java.util.UUID;

/** Block position with the world identity retained across plot selections. */
record PlotPosition(UUID world, int x, int y, int z) {
    PlotPosition {
        Objects.requireNonNull(world, "world");
    }
}
