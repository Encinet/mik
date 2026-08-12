package org.encinet.mik.module.music.jukebox;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/** Shared progress and announcement state for synchronized same-song jukeboxes. */
final class JukeboxPlaybackGroup {

    private static final long NOT_STARTED = Long.MIN_VALUE;

    private final LongSupplier nanoClock;
    private final AtomicLong originNanos = new AtomicLong(NOT_STARTED);
    private final AtomicLong publishedPositionMillis = new AtomicLong(-1L);
    private final AtomicBoolean startedAnnouncementClaimed = new AtomicBoolean();

    JukeboxPlaybackGroup() {
        this(System::nanoTime);
    }

    JukeboxPlaybackGroup(LongSupplier nanoClock) {
        this.nanoClock = java.util.Objects.requireNonNull(nanoClock, "nanoClock");
    }

    long synchronizedPositionMillis() {
        long published = publishedPositionMillis.get();
        if (published >= 0L) {
            return published;
        }
        long now = nanoClock.getAsLong();
        long origin = originNanos.get();
        if (origin == NOT_STARTED) {
            if (originNanos.compareAndSet(NOT_STARTED, now)) {
                return 0L;
            }
            origin = originNanos.get();
        }
        return Math.max(0L, (now - origin) / 1_000_000L);
    }

    void publishPositionMillis(long positionMillis) {
        publishedPositionMillis.accumulateAndGet(
                Math.max(0L, positionMillis), Math::max);
    }

    boolean claimStartedAnnouncement() {
        return startedAnnouncementClaimed.compareAndSet(false, true);
    }
}
