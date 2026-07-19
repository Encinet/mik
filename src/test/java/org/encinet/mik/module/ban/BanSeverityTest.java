package org.encinet.mik.module.ban;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BanSeverityTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Test
    void mapsEachSeverityToItsFixedDuration() {
        assertEquals(NOW.plus(Duration.ofDays(1)), BanSeverity.EXCUSABLE.expiresAt(NOW));
        assertEquals(NOW.plus(Duration.ofDays(7)), BanSeverity.MINOR.expiresAt(NOW));
        assertEquals(NOW.plus(Duration.ofDays(30)), BanSeverity.SEVERE.expiresAt(NOW));
        assertEquals(NOW.plus(Duration.ofDays(3000)), BanSeverity.EXTREME.expiresAt(NOW));
    }

    @Test
    void roundTripsStoredReasonWithoutTreatingExternalReasonsAsSeverity() {
        for (BanSeverity severity : BanSeverity.values()) {
            assertEquals(severity, BanSeverity.fromStoredReason(severity.storedReason()).orElseThrow());
        }
        assertEquals(java.util.Optional.empty(), BanSeverity.fromStoredReason("custom external reason"));
    }
}
