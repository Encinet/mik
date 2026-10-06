package org.encinet.mik.module.vehicle;

import java.util.List;

record VehicleEditorPlacement(VehicleModelDraft draft, long revision, List<String> command, long readyTick, long expires) {
    VehicleEditorPlacement {
        command = List.copyOf(command);
    }

    boolean current(VehicleModelDraft candidate, long now) {
        return candidate == draft && candidate != null && candidate.revision() == revision && now < expires;
    }

    boolean ready(long tick) { return tick >= readyTick; }
}
