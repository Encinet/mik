package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.MusicPlaybackStats;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheEvictionPolicyTest {

    private static final long MIB = 1024L * 1024;
    private static final Instant NOW = Instant.parse("2026-07-26T06:00:00Z");

    @Test
    void frequentPlaybackCanProtectAnOlderEntryFromARecentColdEntry() {
        double popular = CacheEvictionPolicy.retentionScore(8 * MIB,
                NOW.minusSeconds(20 * 86_400L),
                new MusicPlaybackStats.TrackStats(100, NOW.minusSeconds(20 * 86_400L)), NOW);
        double cold = CacheEvictionPolicy.retentionScore(8 * MIB,
                NOW.minusSeconds(86_400L), MusicPlaybackStats.TrackStats.EMPTY, NOW);

        assertTrue(popular > cold);
    }

    @Test
    void largerEntriesAreEvictedBeforeEquivalentSmallEntries() {
        MusicPlaybackStats.TrackStats stats =
                new MusicPlaybackStats.TrackStats(3, NOW.minusSeconds(3_600));
        double small = CacheEvictionPolicy.retentionScore(MIB,
                NOW.minusSeconds(3_600), stats, NOW);
        double large = CacheEvictionPolicy.retentionScore(64 * MIB,
                NOW.minusSeconds(3_600), stats, NOW);

        assertTrue(small > large);
    }
}
