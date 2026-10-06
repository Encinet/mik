package org.encinet.mik.module.governance.platform.paper.command;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceDurationParserTest {

    @Test
    void promotionPauseDurationRequiresFinitePositiveUnit() {
        assertEquals(Duration.ofMinutes(30),
                GovernanceDurationParser.parse("30m").orElseThrow());
        assertEquals(Duration.ofHours(12),
                GovernanceDurationParser.parse("12H").orElseThrow());
        assertEquals(Duration.ofDays(3),
                GovernanceDurationParser.parse("3d").orElseThrow());
        assertTrue(GovernanceDurationParser.parse("forever").isEmpty());
        assertTrue(GovernanceDurationParser.parse("0d").isEmpty());
        assertTrue(GovernanceDurationParser.parse("-1h").isEmpty());
    }
}
