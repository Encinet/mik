package org.encinet.mik.module.performance;

import org.encinet.mik.module.performance.NetworkEgressThrottlePolicy.ThrottleMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NetworkEgressThrottlePolicyTest {
    @Test
    void confirmsOrdinaryPressureButEscalatesCriticalPressureImmediately() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();

        assertEquals(ThrottleMode.OFF, policy.update(0.72D));
        assertEquals(ThrottleMode.SOFT, policy.update(0.72D));
        assertEquals(ThrottleMode.SOFT, policy.update(0.86D));
        assertEquals(ThrottleMode.HARD, policy.update(0.86D));
        assertEquals(ThrottleMode.CRITICAL, policy.update(0.96D));
    }

    @Test
    void recoversOneLevelAtATimeOnlyAfterSustainedLowerPressure() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        assertEquals(ThrottleMode.CRITICAL, policy.update(0.96D));

        for (int sample = 0; sample < 14; sample++) {
            assertEquals(ThrottleMode.CRITICAL, policy.update(0.59D));
        }
        assertEquals(ThrottleMode.HARD, policy.update(0.59D));
        for (int sample = 0; sample < 14; sample++) {
            assertEquals(ThrottleMode.HARD, policy.update(0.59D));
        }
        assertEquals(ThrottleMode.SOFT, policy.update(0.59D));
        for (int sample = 0; sample < 14; sample++) {
            assertEquals(ThrottleMode.SOFT, policy.update(0.59D));
        }
        assertEquals(ThrottleMode.OFF, policy.update(0.59D));
    }

    @Test
    void resetDiscardsPendingEscalation() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        assertEquals(ThrottleMode.OFF, policy.update(0.72D));

        assertEquals(ThrottleMode.OFF, policy.reset());
        assertEquals(ThrottleMode.OFF, policy.update(0.72D));
    }
}
