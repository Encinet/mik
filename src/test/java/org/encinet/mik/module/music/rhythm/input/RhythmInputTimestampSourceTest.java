package org.encinet.mik.module.music.rhythm.input;
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

        assertFalse(source.claimPointer(playerId, 110_000_000L).coarse());
        assertTrue(source.claimPointer(playerId, 120_000_000L).coarse());
    }

    @Test
    void pointerInputCarriesTheLatestPacketViewSnapshot() {
        RhythmInputTimestampSource source = new RhythmInputTimestampSource();
        UUID playerId = UUID.randomUUID();
        source.recordViewForTest(playerId, 72.0F, -18.0F, 80_000_000L);
        source.recordForTest(playerId, true, -1, 100_000_000L);

        RhythmInputTimestampSource.TimedInput result = source.claimPointer(
                playerId, 110_000_000L);

        assertTrue(result.hasView());
        assertEquals(RhythmWorldAim.viewDirection(72.0F, -18.0F),
                result.viewDirection().orElseThrow());
    }

    @Test
    void packetAgeMatchingSurvivesNanoTimeSignedWrap() {
        RhythmInputTimestampSource source = new RhythmInputTimestampSource();
        UUID playerId = UUID.randomUUID();
        long receivedAt = Long.MAX_VALUE - 20_000_000L;
        long claimedAt = Long.MIN_VALUE + 20_000_000L;
        source.recordForTest(playerId, false, 3, receivedAt);

        RhythmInputTimestampSource.TimedInput result = source.claimHotbar(
                playerId, 3, claimedAt);

        assertFalse(result.coarse());
        assertEquals(receivedAt, result.receivedAtNanos());
    }
}
