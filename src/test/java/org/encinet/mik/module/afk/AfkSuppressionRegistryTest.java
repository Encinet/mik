package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkSuppressionRegistryTest {

    private static final UUID PLAYER_ID = new UUID(0L, 1L);

    @Test
    void independentOwnersMustAllReleaseTheirLeases() {
        AfkSuppressionRegistry registry = new AfkSuppressionRegistry();
        AfkActivityService.ActivityLease rhythm = registry.acquire(PLAYER_ID, "rhythm-game");
        AfkActivityService.ActivityLease cutscene = registry.acquire(PLAYER_ID, "cutscene");

        assertTrue(registry.isSuppressed(PLAYER_ID));
        assertEquals(2, registry.leaseCount(PLAYER_ID));
        rhythm.close();
        rhythm.close();
        assertTrue(registry.isSuppressed(PLAYER_ID));
        assertEquals(1, registry.leaseCount(PLAYER_ID));

        cutscene.close();
        assertFalse(registry.isSuppressed(PLAYER_ID));
    }

    @Test
    void playerCleanupInvalidatesOutstandingLeases() {
        AfkSuppressionRegistry registry = new AfkSuppressionRegistry();
        AfkActivityService.ActivityLease lease = registry.acquire(PLAYER_ID, "rhythm-game");

        registry.clear(PLAYER_ID);
        lease.close();

        assertFalse(registry.isSuppressed(PLAYER_ID));
        assertEquals(0, registry.leaseCount(PLAYER_ID));
    }

    @Test
    void blankReasonsAreRejected() {
        AfkSuppressionRegistry registry = new AfkSuppressionRegistry();
        assertThrows(IllegalArgumentException.class,
                () -> registry.acquire(PLAYER_ID, "  "));
    }
}
