package org.encinet.mik.module.music.jukebox;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/** Shared progress and announcement state for synchronized same-song jukeboxes. */
final class JukeboxPlaybackGroup {

    private static final long NOT_STARTED = Long.MIN_VALUE;

    private final LongSupplier nanoClock;
    private final AtomicLong originNanos = new AtomicLong(NOT_STARTED);
    private final AtomicLong publishedPositionMillis = new AtomicLong(-1L);
    private final AtomicBoolean startedAnnouncementClaimed = new AtomicBoolean();
    private final AtomicReference<Member> leader = new AtomicReference<>();

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

    /** Whether any member has started establishing or publishing this song clock. */
    boolean playbackHasStarted() {
        return originNanos.get() != NOT_STARTED
                || publishedPositionMillis.get() >= 0L;
    }

    Member newMember() {
        return new Member(this);
    }

    boolean claimStartedAnnouncement() {
        return startedAnnouncementClaimed.compareAndSet(false, true);
    }

    /** One independently audible jukebox participating in this shared song clock. */
    static final class Member implements AutoCloseable {
        private final JukeboxPlaybackGroup group;
        private final AtomicBoolean active = new AtomicBoolean();

        private Member(JukeboxPlaybackGroup group) {
            this.group = group;
        }

        void activate() {
            active.set(true);
            group.leader.compareAndSet(null, this);
        }

        boolean ensureLeadership() {
            if (!active.get()) return false;
            Member current = group.leader.get();
            return current == this
                    || current == null && group.leader.compareAndSet(null, this);
        }

        boolean isLeader() {
            return active.get() && group.leader.get() == this;
        }

        long synchronizedPositionMillis() {
            return group.synchronizedPositionMillis();
        }

        void publishPositionMillis(long positionMillis) {
            if (ensureLeadership()) {
                group.publishPositionMillis(positionMillis);
            }
        }

        @Override
        public void close() {
            active.set(false);
            group.leader.compareAndSet(this, null);
        }
    }
}
