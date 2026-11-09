package org.encinet.mik.feature.world.vehicle;

import java.util.Objects;

/** Reading pages belong to a view; selecting another object or tool starts its own reading state. */
record VehicleEditorView(VehicleEditorRoute route, int itemPage, int actionPage) {
    VehicleEditorView {
        Objects.requireNonNull(route);
        if (itemPage < 0 || actionPage < 0) throw new IllegalArgumentException("Negative reading page");
    }

    static VehicleEditorView open(VehicleEditorRoute route) { return new VehicleEditorView(route, 0, 0); }
    VehicleEditorView items(int page) { return new VehicleEditorView(route, page, actionPage); }
    VehicleEditorView actions(int page) { return new VehicleEditorView(route, itemPage, page); }
}
