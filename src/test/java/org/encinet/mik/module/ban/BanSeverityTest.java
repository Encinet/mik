package org.encinet.mik.module.ban;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanSeverityTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    @Test
    void mapsEachSeverityToItsFixedDuration() {
        assertEquals(NOW.plus(Duration.ofDays(3)), BanSeverity.WARNING.expiresAt(NOW));
        assertEquals(NOW.plus(Duration.ofDays(30)), BanSeverity.EXCUSABLE.expiresAt(NOW));
        assertEquals(NOW.plus(Duration.ofDays(150)), BanSeverity.MINOR.expiresAt(NOW));
        assertEquals(NOW.plus(Duration.ofDays(360)), BanSeverity.SEVERE.expiresAt(NOW));
        assertEquals(NOW.plus(Duration.ofDays(1500)), BanSeverity.EXTREME.expiresAt(NOW));
    }

    @Test
    void roundTripsStoredReasonWithoutTreatingExternalReasonsAsSeverity() {
        for (BanSeverity severity : BanSeverity.values()) {
            assertEquals(severity, BanSeverity.fromStoredReason(severity.storedReason()).orElseThrow());
            String storedReason = severity.storedReason("Required reason");
            assertEquals(severity, BanSeverity.fromStoredReason(storedReason).orElseThrow());
            assertEquals("Required reason", BanSeverity.userReason(storedReason));
        }
        assertEquals(java.util.Optional.empty(), BanSeverity.fromStoredReason("custom external reason"));
        assertEquals("custom external reason", BanSeverity.userReason("custom external reason"));
        assertEquals("", BanSeverity.userReason(BanSeverity.MINOR.storedReason()));
    }

    @Test
    void requiresNonBlankUserReason() {
        assertTrue(BanReason.normalize(null).isEmpty());
        assertTrue(BanReason.normalize("   ").isEmpty());
        assertEquals("griefing", BanReason.normalize("  griefing  ").orElseThrow());
    }
}
