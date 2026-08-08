package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkPolicyTest {

    private static final UUID WORLD_ID = new UUID(0L, 1L);

    @Test
    void onePolicyControlsDetectionAndTransitionTiming() {
        AfkPolicy policy = policy(100L, 300L, 25L);
        AfkPlayerSession session = new AfkPlayerSession(
                policy, 0L, WORLD_ID, 0.0D, 0.0D, 0.0D);

        assertFalse(session.checkAutomaticAfk(100L, true).shouldEnterAfk());
        assertFalse(session.checkAutomaticAfk(124L, true).shouldEnterAfk());
        assertTrue(session.checkAutomaticAfk(125L, true).shouldEnterAfk());
    }

    @Test
    void passiveTimeoutCannotBeShorterThanIdleTimeout() {
        assertThrows(IllegalArgumentException.class, () -> policy(300L, 299L, 25L));
    }

    @Test
    void invalidActionEvidenceThresholdsAreRejected() {
        AfkPolicy defaults = AfkPolicy.DEFAULT;
        assertThrows(IllegalArgumentException.class, () -> new AfkPolicy(
                defaults.idleTimeoutMillis(),
                defaults.passiveTimeoutMillis(),
                defaults.substantialMovementDistance(),
                defaults.actionDeduplicationMillis(),
                defaults.actionTargetDeduplicationMillis(),
                defaults.movementOnlyRewardTimeoutMillis(),
                1,
                2,
                defaults.automaticEntryGraceMillis(),
                defaults.automaticReentryCooldownMillis()
        ));
    }

    private static AfkPolicy policy(long idleMillis, long passiveMillis, long graceMillis) {
        AfkPolicy defaults = AfkPolicy.DEFAULT;
        return new AfkPolicy(
                idleMillis,
                passiveMillis,
                defaults.substantialMovementDistance(),
                defaults.actionDeduplicationMillis(),
                defaults.actionTargetDeduplicationMillis(),
                defaults.movementOnlyRewardTimeoutMillis(),
                defaults.requiredActions(),
                defaults.requiredUnlockActionTypes(),
                graceMillis,
                defaults.automaticReentryCooldownMillis()
        );
    }
}
