package org.encinet.mik.feature.server.performance;

import org.encinet.mik.feature.server.performance.NetworkEgressModule.SamplingState;
import org.encinet.mik.feature.server.performance.NetworkEgressRateMeter.Reading;
import org.encinet.mik.feature.server.performance.NetworkEgressThrottlePolicy.Limit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkEgressSamplingTest {
    private static final long SECOND = 1_000_000_000L;

    @Test
    void briefProbeLossPreservesLimitsAndPersistentLossEventuallyReleasesThem() {
        SamplingState state = new SamplingState("auto", 1000.0D);
        Limit limited = congest(state);
        Reading missing = new Reading(-1.0D, 0.0D);
        assertEquals(limited, state.updateThrottle(missing, 7 * SECOND, 7 * SECOND, false));
        assertEquals(limited, state.updateThrottle(missing, 66 * SECOND, 66 * SECOND, false));
        assertEquals(NetworkEgressThrottlePolicy.OPEN,
                state.updateThrottle(missing, 67 * SECOND, 67 * SECOND, false));
    }

    @Test
    void delayedOrLongAveragesDoNotEscalateOrRestoreFromStaleData() {
        SamplingState state = new SamplingState("auto", 1000.0D);
        Limit limited = congest(state);
        assertEquals(limited, state.updateThrottle(new Reading(0.0D, 1.0D), 7 * SECOND, 30 * SECOND, false));
        assertEquals(limited, state.updateThrottle(new Reading(0.0D, 30.0D), 31 * SECOND, 31 * SECOND, false));
        assertEquals(limited, state.updateThrottle(new Reading(2000.0D, 30.0D), 61 * SECOND, 61 * SECOND, false));
        assertEquals(NetworkEgressThrottlePolicy.OPEN,
                state.updateThrottle(new Reading(0.0D, 60.0D), 91 * SECOND, 91 * SECOND, false));
    }

    @Test
    void freshSamplesAfterAnOutageStartANewObservationWindow() {
        SamplingState state = new SamplingState("auto", 1000.0D);
        Limit limited = congest(state);
        state.updateThrottle(new Reading(-1.0D, 0.0D), 7 * SECOND, 7 * SECOND, false);
        assertEquals(limited,
                state.updateThrottle(new Reading(900.0D, 1.0D), 60 * SECOND, 60 * SECOND, false));
        assertEquals(limited,
                state.updateThrottle(new Reading(900.0D, 1.0D), 61 * SECOND, 61 * SECOND, false));
    }

    private static Limit congest(SamplingState state) {
        Limit result = NetworkEgressThrottlePolicy.OPEN;
        for (long second = 1; second <= 6; second++) {
            result = state.updateThrottle(new Reading(900.0D, 1.0D), second * SECOND, second * SECOND, true);
        }
        assertTrue(result.activeRatio() < 1.0D);
        return result;
    }
}
