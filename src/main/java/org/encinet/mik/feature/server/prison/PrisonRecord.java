package org.encinet.mik.feature.server.prison;

import java.util.UUID;

/** A prison plot: its identity is the plot UUID; the add-on flag and region live on the plot itself. */
record PrisonRecord(UUID plot, PrisonLocation exit) {
    PrisonRecord {
        if (plot == null) throw new IllegalArgumentException("Prison requires a plot UUID");
    }

    PrisonRecord exit(PrisonLocation value) { return new PrisonRecord(plot, value); }
}
