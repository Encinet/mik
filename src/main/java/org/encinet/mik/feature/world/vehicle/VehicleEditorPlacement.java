package org.encinet.mik.feature.world.vehicle;


record VehicleEditorPlacement(VehicleModelDraft draft, long revision, Action action, long readyTick, long expires) {
    sealed interface Action permits Target, Position { }
    enum Target implements Action { PICK, FRONT, CENTER, CORNER_FIRST, CORNER_SECOND }
    record Position(VehicleEditorRoute.Position route) implements Action { }

    boolean current(VehicleModelDraft candidate, long now) {
        return candidate == draft && candidate != null && candidate.revision() == revision && now < expires;
    }

    boolean ready(long tick) { return tick >= readyTick; }
}
