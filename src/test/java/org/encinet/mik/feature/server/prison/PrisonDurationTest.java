package org.encinet.mik.feature.server.prison;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrisonDurationTest {
    @Test
    void formatsRemainingTimeWithoutRoundingDown() {
        assertEquals("2m1s", PrisonDuration.remaining(121_000, 0));
        assertEquals("1s", PrisonDuration.remaining(1, 0));
        assertEquals("0s", PrisonDuration.remaining(1, 1));
    }
}
