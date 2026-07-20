package org.encinet.mik.module.player;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerAssociationNotifierTest {

    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Singapore");
    private static final Instant NOW = Instant.parse("2026-07-21T08:00:00Z");

    @Test
    void calculatesCalendarDaysInServerTimezone() {
        assertEquals(0, PlayerAssociationNotifier.daysAgo(
                Instant.parse("2026-07-20T16:30:00Z"), NOW, SERVER_ZONE));
        assertEquals(1, PlayerAssociationNotifier.daysAgo(
                Instant.parse("2026-07-20T15:59:59Z"), NOW, SERVER_ZONE));
        assertEquals(4, PlayerAssociationNotifier.daysAgo(
                Instant.parse("2026-07-17T08:00:00Z"), NOW, SERVER_ZONE));
    }

    @Test
    void clampsFutureRecordsToToday() {
        assertEquals(0, PlayerAssociationNotifier.daysAgo(
                NOW.plusSeconds(86_400), NOW, SERVER_ZONE));
    }
}
