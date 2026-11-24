package org.encinet.mik.feature.server.prison;

import java.util.Map;
import java.util.UUID;

/**
 * The prison add-on's only persisted state: an exit point per plot. Who is imprisoned,
 * where, and for how long all come from {@code BanManager}; a ban is the sentence.
 */
record PrisonData(Map<UUID, PrisonRecord> prisons) {
    PrisonData {
        prisons = Map.copyOf(prisons);
    }

    static PrisonData empty() { return new PrisonData(Map.of()); }
}
