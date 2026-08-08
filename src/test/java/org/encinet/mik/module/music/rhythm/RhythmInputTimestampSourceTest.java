package org.encinet.mik.module.music.rhythm;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmInputTimestampSourceTest {

    @Test
    void claimsTheMatchingNetworkTimestampInsteadOfTheBukkitFallback() {
        RhythmInputTimestampSource source = new RhythmInputTimestampSource();
        UUID playerId = UUID.randomUUID();
        source.recordForTest(playerId, false, 2, 1_000_000_000L);

        RhythmInputTimestampSource.TimedInput result = source.claimHotbar(
                playerId, 2, 1_040_000_000L);

        assertEquals(1_000_000_000L, result.receivedAtNanos());
        assertFalse(result.coarse());
    }

    @Test
    void missingOrExpiredPacketsUseAnExplicitCoarseFallback() {
        RhythmInputTimestampSource source = new RhythmInputTimestampSource();
        UUID playerId = UUID.randomUUID();
        source.recordForTest(playerId, false, 1, 1L);

        RhythmInputTimestampSource.TimedInput result = source.claimHotbar(
                playerId, 1, RhythmInputTimestampSource.MAXIMUM_STAMP_AGE_NANOS + 2L);

        assertTrue(result.coarse());
        assertEquals(RhythmInputTimestampSource.MAXIMUM_STAMP_AGE_NANOS + 2L,
                result.receivedAtNanos());
    }

    @Test
    void duplicateRadialPacketsFromOnePhysicalClickAreConsumedTogether() {
        RhythmInputTimestampSource source = new RhythmInputTimestampSource();
        UUID playerId = UUID.randomUUID();
        source.recordForTest(playerId, true, -1, 100_000_000L);
        source.recordForTest(playerId, true, -1, 105_000_000L);

        assertFalse(source.claimRadial(playerId, 110_000_000L).coarse());
        assertTrue(source.claimRadial(playerId, 120_000_000L).coarse());
    }
}
