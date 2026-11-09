package org.encinet.mik.feature.server.performance;

import org.encinet.mik.feature.server.performance.NetworkEgressThrottlePolicy.Limit;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkEgressThrottlePolicyTest {
    @Test
    void observesAfkReliefBeforeTouchingActivePlayers() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        Limit afkOnly = policy.update(0.90D, 1.0D, true);
        assertEquals(1.0D, afkOnly.activeRatio());
        assertEquals(2, afkOnly.sendDistance(32, true));
        assertTrue(policy.explorationPressure() >= 0.75D && policy.explorationPressure() < 0.85D,
                "Waiting for AFK relief must not look like recovery headroom to the explorer controller");
        for (int second = 0; second < 4; second++) {
            assertEquals(afkOnly, policy.update(0.90D, 1.0D, false));
        }
        assertTrue(policy.update(0.90D, 1.0D, false).activeRatio() < 1.0D);
    }

    @Test
    void successfulAfkReliefKeepsActivePlayersAtTheirOriginalDistance() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        policy.update(0.93D, 1.0D, true);
        for (int second = 0; second < 120; second++) {
            Limit limit = policy.update(0.72D, 1.0D, false);
            assertEquals(1.0D, limit.activeRatio());
            assertEquals(2, limit.sendDistance(16, true));
        }
    }

    @Test
    void givesHeavyExplorersOneBoundedWindowBeforeGlobalActiveLimits() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        policy.observeExplorers(true);
        Limit afkOnly = feed(policy, 0.90D, 6);
        assertEquals(1.0D, afkOnly.activeRatio());
        assertTrue(policy.explorationPressure() >= 0.85D);
        for (int second = 0; second < 9; second++) {
            assertEquals(afkOnly, policy.update(0.90D, 1.0D, false));
        }
        assertTrue(policy.update(0.90D, 1.0D, false).activeRatio() < 1.0D,
                "Continuous explorer pressure cannot postpone global protection forever");
    }

    @Test
    void successfulTargetedReliefAvoidsLimitingOrdinaryPlayers() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        policy.observeExplorers(true);
        assertEquals(1.0D, feed(policy, 0.90D, 6).activeRatio());
        assertEquals(1.0D, feed(policy, 0.75D, 60).activeRatio());
    }

    @Test
    void doesNotWaitForNonexistentAfkSavingsButStillConfirmsActivePressure() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        Limit first = policy.update(0.90D, 1.0D, false);
        assertEquals(1.0D, first.activeRatio());
        assertEquals(first, policy.update(0.90D, 1.0D, false));
        assertTrue(policy.update(0.90D, 1.0D, false).activeRatio() < 1.0D);
    }

    @Test
    void singleSaturatedSampleCannotReduceActivePlayers() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        assertEquals(1.0D, policy.update(1.0D, 1.0D, true).activeRatio());
        for (int second = 0; second < 30; second++) {
            assertEquals(1.0D, policy.update(0.40D, 1.0D, false).activeRatio());
        }
    }

    @Test
    void sustainedSaturationShortensObservationWithoutSkippingAfkFirst() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        Limit first = policy.update(1.0D, 1.0D, true);
        assertEquals(1.0D, first.activeRatio());
        assertEquals(first, policy.update(1.0D, 1.0D, false));
        Limit next = policy.update(1.0D, 1.0D, false);
        assertTrue(next.activeRatio() < 1.0D);
        assertTrue(next.sendDistance(16, false) > 3);
        assertTrue(policy.explorationPressure() >= 0.98D,
                "Emergency global steps must also enable the explorer controller's busy budget");
    }

    @Test
    void suddenSaturationAfterAnIdlePeriodDoesNotWaitForTheSlowlyRisingEwma() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        feed(policy, 0.0D, 30);
        assertEquals(1.0D, policy.update(1.0D, 1.0D, true).activeRatio());
        Limit afkOnly = policy.update(1.0D, 1.0D, true);
        assertEquals(2, afkOnly.sendDistance(32, true));
        assertEquals(1.0D, afkOnly.activeRatio());
        assertEquals(afkOnly, policy.update(1.0D, 1.0D, false));
        assertTrue(policy.update(1.0D, 1.0D, false).activeRatio() < 1.0D);
    }

    @Test
    void reductionRespondsToRemainingPressureInsteadOfFixedDistanceTiers() {
        NetworkEgressThrottlePolicy moderate = new NetworkEgressThrottlePolicy();
        NetworkEgressThrottlePolicy severe = new NetworkEgressThrottlePolicy();
        Limit moderateLimit = feed(moderate, 0.86D, 6);
        Limit severeLimit = feed(severe, 1.40D, 6);
        assertTrue(severeLimit.activeRatio() < moderateLimit.activeRatio());
        assertTrue(moderateLimit.activeRatio() > 0.75D);
        assertEquals(2, severeLimit.sendDistance(32, true));
    }

    @Test
    void windowsAndFiltersUseElapsedTimeInsteadOfSampleCounts() {
        NetworkEgressThrottlePolicy once = new NetworkEgressThrottlePolicy();
        NetworkEgressThrottlePolicy twice = new NetworkEgressThrottlePolicy();
        once.update(0.90D, 1.0D, true);
        twice.update(0.90D, 1.0D, true);
        for (int second = 0; second < 5; second++) {
            once.update(0.90D, 1.0D, false);
            twice.update(0.90D, 0.5D, false);
            twice.update(0.90D, 0.5D, false);
        }
        assertEquals(once.limit(), twice.limit());
        assertEquals(once.fastPressure(), twice.fastPressure(), 1.0E-12D);
        assertEquals(once.slowPressure(), twice.slowPressure(), 1.0E-12D);
    }

    @Test
    void missingOrLongSamplesCannotSupplyRecoveryOrConfirmationEvidence() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        Limit limited = feed(policy, 0.90D, 6);
        for (double pressure : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1.0D}) {
            assertEquals(limited, policy.update(pressure, 1.0D, false));
        }
        assertEquals(limited, policy.update(0.0D, 60.0D, false));
        assertEquals(limited, policy.update(0.0D, 1.0D, false));
        policy.suspend();
        assertEquals(limited, policy.update(0.90D, 1.0D, false));
    }

    @Test
    void recoversActivePlayersBeforeGraduallyReleasingAfkPlayers() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        Limit last = feed(policy, 0.90D, 6);
        double afkMinimum = last.afkRatio();
        boolean activeRecovered = false;
        boolean afkRecoveredGradually = false;
        for (int second = 0; second < 1500; second++) {
            Limit next = policy.update(0.20D, 1.0D, false);
            if (next.activeRatio() < 1.0D) {
                assertEquals(afkMinimum, next.afkRatio());
            } else {
                activeRecovered = true;
            }
            if (next.afkRatio() > afkMinimum && next.afkRatio() < 1.0D) {
                afkRecoveredGradually = true;
            }
            assertTrue(next.activeRatio() - last.activeRatio() <= 0.05D + 1.0E-12D);
            assertTrue(next.afkRatio() - last.afkRatio() <= 0.05D + 1.0E-12D);
            last = next;
        }
        assertTrue(activeRecovered);
        assertTrue(afkRecoveredGradually);
        assertFalse(last.throttled());
    }

    @Test
    void failedRecoveryRollsBackAndLearnsToWaitLonger() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        Limit stable = feed(policy, 0.90D, 6);
        Limit probe = waitForProbe(policy, stable);
        assertTrue(probe.activeRatio() > stable.activeRatio());
        Limit rolledBack = probe;
        for (int second = 0; second < 8 && rolledBack.equals(probe); second++) {
            rolledBack = policy.update(0.95D, 1.0D, false);
        }
        assertEquals(stable, rolledBack);
        assertEquals(30.0D, policy.recoveryDelay());
        Limit nextProbe = waitForProbe(policy, stable);
        assertTrue(nextProbe.activeRatio() - stable.activeRatio() <= 0.025D + 1.0E-12D);
    }

    @Test
    void constrainedOriginalDistancesAreNeverRaisedAndSmallClientsShareTheReduction() {
        Limit limit = new Limit(0.50D, 2.0D / 32.0D);
        assertEquals(16, limit.sendDistance(32, false));
        assertEquals(5, limit.sendDistance(9, false));
        assertEquals(3, limit.sendDistance(4, false));
        assertEquals(2, limit.sendDistance(2, false));
        for (int distance = 2; distance <= 32; distance++) {
            assertEquals(2, limit.sendDistance(distance, true));
            assertTrue(limit.sendDistance(distance, false) <= distance);
        }
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        Limit minimum = feed(policy, 2.0D, 300);
        assertEquals(3, minimum.sendDistance(32, false));
        assertEquals(2, minimum.sendDistance(2, false));
        assertEquals(NetworkEgressThrottlePolicy.OPEN, policy.reset());
    }

    @Test
    void delayedChunkDemandConvergesWithoutCuttingToTheMinimumOrRepeatedRecovery() {
        NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        // Background, active and AFK traffic respond differently; effects arrive three seconds later.
        Deque<Double> demand = new ArrayDeque<>();
        demand.add(0.94D);
        demand.add(0.94D);
        demand.add(0.94D);
        Limit limit = NetworkEgressThrottlePolicy.OPEN;
        double minimumActive = 1.0D;
        double maximumLatePressure = 0.0D;
        int lateChanges = 0;
        for (int second = 0; second < 300; second++) {
            double observed = demand.removeFirst();
            Limit next = policy.update(observed, 1.0D, !limit.afkThrottled());
            double activeRadius = next.sendDistance(16, false) / 16.0D;
            double afkRadius = next.sendDistance(16, true) / 16.0D;
            demand.addLast(0.20D + 0.68D * activeRadius * activeRadius + 0.06D * afkRadius);
            minimumActive = Math.min(minimumActive, next.activeRatio());
            if (second > 100) {
                maximumLatePressure = Math.max(maximumLatePressure, observed);
                if (!limit.equals(next)) lateChanges++;
            }
            limit = next;
        }
        assertTrue(minimumActive > 0.70D, "Should preserve most of the active radius");
        assertTrue(maximumLatePressure < 0.85D, "Residual pressure should settle below escalation");
        assertEquals(0, lateChanges, "A steady load should keep its learned limit");
    }

    private static Limit waitForProbe(NetworkEgressThrottlePolicy policy, Limit stable) {
        for (int second = 0; second < 300; second++) {
            Limit next = policy.update(0.20D, 1.0D, false);
            if (next.activeRatio() > stable.activeRatio()) return next;
        }
        throw new AssertionError("No recovery probe");
    }

    private static Limit feed(NetworkEgressThrottlePolicy policy, double pressure, int seconds) {
        Limit limit = policy.limit();
        for (int second = 0; second < seconds; second++) {
            limit = policy.update(pressure, 1.0D, true);
        }
        return limit;
    }
}
