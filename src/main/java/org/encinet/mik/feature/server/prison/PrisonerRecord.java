package org.encinet.mik.feature.server.prison;

import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.world.plot.Plot;

import java.time.Instant;
import java.util.UUID;

/**
 * A player held in a prison plot. Nothing is stored for it: the ban is the sentence, and the
 * plot and cell are derived from the banned player's UUID through {@link PrisonCell}.
 */
record PrisonerRecord(BanRecord ban, Plot plot, Instant now) {
    PrisonerRecord {
        if (ban == null || plot == null) throw new IllegalArgumentException("A prisoner needs a ban and a plot");
    }

    UUID playerId() { return ban.playerUuid(); }

    /** The banned name, or the UUID when the ban only carries an account identifier. */
    String playerName() {
        return ban.playerName() == null || ban.playerName().isBlank() ? ban.playerUuid().toString() : ban.playerName();
    }

    String reason() { return ban.reason() == null ? "" : ban.reason(); }

    /** Remaining sentence in milliseconds; 0 for a permanent ban. */
    long remainingMillis() {
        return ban.expiresAt() == null ? 0 : Math.max(0, ban.expiresAt().toEpochMilli() - now.toEpochMilli());
    }

    boolean isPermanent() { return ban.expiresAt() == null; }
}
