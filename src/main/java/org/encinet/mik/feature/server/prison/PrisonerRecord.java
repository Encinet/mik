package org.encinet.mik.feature.server.prison;

import java.util.UUID;

/**
 * An inmate of a prison plot. The cell is derived from {@link PrisonCell} at read time,
 * so no cell field is stored here.
 */
record PrisonerRecord(UUID playerId, String playerName, UUID plot, long sentenceEndMillis,
                      boolean requireUnban, PrisonLocation returnLocation, long jailedAtMillis) {
    PrisonerRecord {
        if (playerId == null || playerName == null || playerName.isBlank() || plot == null
                || sentenceEndMillis < 0 || jailedAtMillis < 0)
            throw new IllegalArgumentException("Invalid prisoner record");
    }

    boolean hasSentence() { return sentenceEndMillis > 0; }
}
