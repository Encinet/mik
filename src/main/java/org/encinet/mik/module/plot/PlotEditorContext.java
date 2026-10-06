package org.encinet.mik.module.plot;

import java.util.Objects;
import java.util.UUID;

record PlotEditorContext(UUID plotId, Intent intent) {
    enum Intent { CREATE_PLOT, EDIT_AREA, CREATE_SUBPLOT }

    PlotEditorContext {
        Objects.requireNonNull(intent);
        if ((plotId == null) != (intent == Intent.CREATE_PLOT))
            throw new IllegalArgumentException("Plot and editor intent do not match");
    }

    static PlotEditorContext area(UUID plotId) {
        return new PlotEditorContext(plotId, plotId == null ? Intent.CREATE_PLOT : Intent.EDIT_AREA);
    }

    static PlotEditorContext subPlot(UUID parentId) {
        return new PlotEditorContext(parentId, Intent.CREATE_SUBPLOT);
    }

    boolean subPlot() { return intent == Intent.CREATE_SUBPLOT; }

    PlotPermission permission() {
        return switch (intent) {
            case CREATE_PLOT -> null;
            case EDIT_AREA -> PlotPermission.MANAGE_AREA;
            case CREATE_SUBPLOT -> PlotPermission.CREATE_SUBPLOT;
        };
    }
}
