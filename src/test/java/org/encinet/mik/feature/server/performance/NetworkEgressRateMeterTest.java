package org.encinet.mik.feature.server.performance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NetworkEgressRateMeterTest {
    @Test
    void usesTheActualIntervalAndAcceptsZeroTraffic() {
        NetworkEgressRateMeter meter = new NetworkEgressRateMeter();
        assertFalse(meter.sample(100L, 1_000_000_000L).measured());
        var measured = meter.sample(1100L, 3_000_000_000L);
        assertEquals(500.0D, measured.bytesPerSecond());
        assertEquals(2.0D, measured.seconds());
        assertEquals(0.0D, meter.sample(1100L, 4_000_000_000L).bytesPerSecond());
    }

    @Test
    void neverTreatsCounterResetsOrProbeGapsAsLowTraffic() {
        NetworkEgressRateMeter meter = new NetworkEgressRateMeter();
        meter.sample(1000L, 1_000_000_000L);
        assertFalse(meter.sample(10L, 2_000_000_000L).measured());
        assertEquals(100.0D, meter.sample(110L, 3_000_000_000L).bytesPerSecond());
        assertFalse(meter.sample(-1L, 4_000_000_000L).measured());
        assertFalse(meter.sample(210L, 20_000_000_000L).measured());
        assertEquals(100.0D, meter.sample(310L, 21_000_000_000L).bytesPerSecond());
        meter.reset();
        assertFalse(meter.sample(100_000L, 22_000_000_000L).measured());
    }

    @Test
    void rejectsDuplicateOrBackwardTimestamps() {
        NetworkEgressRateMeter meter = new NetworkEgressRateMeter();
        meter.sample(100L, 3_000_000_000L);
        assertFalse(meter.sample(200L, 3_000_000_000L).measured());
        assertFalse(meter.sample(300L, 2_000_000_000L).measured());
    }
}
