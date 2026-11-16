package org.encinet.mik.feature.server.prison;

import java.util.UUID;

record PrisonerRecord(UUID playerId, String playerName, String prison, String cell, long sentenceEndMillis,
                      boolean requireUnban, PrisonLocation returnLocation, long jailedAtMillis) {
    PrisonerRecord {
        if (playerId == null || playerName == null || playerName.isBlank() || prison == null || cell == null
                || sentenceEndMillis < 0 || jailedAtMillis < 0)
            throw new IllegalArgumentException("Invalid prisoner record");
        prison = PrisonRecord.normalize(prison);
        cell = PrisonRecord.normalize(cell);
    }

    boolean hasSentence() { return sentenceEndMillis > 0; }
}
