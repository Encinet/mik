package org.encinet.mik.module.music.online;

import org.encinet.mik.module.music.catalog.MusicPlaybackStats;

import java.time.Duration;
import java.time.Instant;

/** Scores cache entries by decayed popularity, recency, and storage cost. */
final class CacheEvictionPolicy {

    private static final double DECAY_DAYS = 30.0;
    private static final double BYTES_PER_MIB = 1024.0 * 1024.0;

    private CacheEvictionPolicy() {
    }

    /** Lower scores are less valuable and should be evicted first. */
    static double retentionScore(long bytes, Instant lastAccessedAt,
                                 MusicPlaybackStats.TrackStats playback, Instant now) {
        Instant lastRelevantUse = lastAccessedAt;
        if (playback != null && playback.lastPlayedAt() != null
                && playback.lastPlayedAt().isAfter(lastRelevantUse)) {
            lastRelevantUse = playback.lastPlayedAt();
        }
        double ageDays = Math.max(0.0,
                Duration.between(lastRelevantUse, now).toSeconds() / 86_400.0);
        long playCount = playback == null ? 0 : playback.playCount();
        double popularity = 1.0 + 2.0 * Math.log1p(playCount);
        double freshness = Math.exp(-ageDays / DECAY_DAYS);
        double storageCost = Math.sqrt(Math.max(1.0, bytes / BYTES_PER_MIB));
        return popularity * freshness / storageCost;
    }
}
