package org.encinet.mik.feature.server.prison;

import java.util.Map;
import java.util.UUID;

record PrisonData(Map<String, PrisonRecord> prisons, Map<UUID, PrisonerRecord> prisoners) {
    PrisonData {
        prisons = Map.copyOf(prisons);
        prisoners = Map.copyOf(prisoners);
    }

    static PrisonData empty() { return new PrisonData(Map.of(), Map.of()); }
}
