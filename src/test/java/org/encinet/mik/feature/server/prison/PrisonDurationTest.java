package org.encinet.mik.feature.server.prison;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrisonDurationTest {
    @Test
    void parsesCompositeAndLocalizedDurations() {
        assertEquals(2 * 86_400_000L + 3 * 3_600_000L + 4 * 60_000L + 5_000L,
                PrisonDuration.parse("2d3h4m5s"));
        assertEquals(90 * 60_000L, PrisonDuration.parse("1小时30分钟"));
        assertEquals(0, PrisonDuration.parse("永久"));
    }

    @Test
    void rejectsMalformedAndOverflowingDurations() {
        assertThrows(IllegalArgumentException.class, () -> PrisonDuration.parse("2x"));
        assertThrows(IllegalArgumentException.class, () -> PrisonDuration.parse("999999999999999999d"));
    }

    @Test
    void formatsRemainingTimeWithoutRoundingDown() {
        assertEquals("2m1s", PrisonDuration.remaining(121_000, 0));
        assertEquals("0s", PrisonDuration.remaining(1, 1));
    }
}
