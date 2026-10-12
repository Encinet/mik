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
    private final AtomicReference<Progress> progress =
            new AtomicReference<>(new Progress(0L, -1L));
    private final AtomicReference<Seek> seek = new AtomicReference<>(new Seek(0L, 0L));
    private final AtomicBoolean startedAnnouncementClaimed = new AtomicBoolean();
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicReference<Member> leader = new AtomicReference<>();

    JukeboxPlaybackGroup() {
        this(System::nanoTime);
    }

    JukeboxPlaybackGroup(LongSupplier nanoClock) {
        this.nanoClock = java.util.Objects.requireNonNull(nanoClock, "nanoClock");
    }

    long synchronizedPositionMillis() {
        long published = progress.get().positionMillis();
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
        publishPositionMillis(positionMillis, seek.get().version());
    }

    private void publishPositionMillis(long positionMillis, long version) {
        progress.updateAndGet(current -> current.version() == version
                ? new Progress(version, Math.max(current.positionMillis(),
                        Math.max(0L, positionMillis))) : current);
    }

    void seekTo(long positionMillis) {
        long target = Math.max(0L, positionMillis);
        Seek requested = seek.updateAndGet(previous ->
                new Seek(previous.version() + 1L, target));
        progress.updateAndGet(current -> current.version() < requested.version()
                ? new Progress(requested.version(), target) : current);
    }

    /** Whether any member has started establishing or publishing this song clock. */
    boolean playbackHasStarted() {
        return originNanos.get() != NOT_STARTED
                || progress.get().positionMillis() >= 0L;
    }

    Member newMember() {
        return new Member(this);
    }

    boolean claimStartedAnnouncement() {
        return startedAnnouncementClaimed.compareAndSet(false, true);
    }

    boolean paused() {
        return paused.get();
    }

    void setPaused(boolean value) {
        paused.set(value);
    }

    record Seek(long version, long positionMillis) { }

    private record Progress(long version, long positionMillis) { }

    /** One independently audible jukebox participating in this shared song clock. */
    static final class Member implements AutoCloseable {
        private final JukeboxPlaybackGroup group;
        private final AtomicBoolean active = new AtomicBoolean();
        private volatile long appliedSeekVersion;

        private Member(JukeboxPlaybackGroup group) {
            this.group = group;
            this.appliedSeekVersion = group.seek.get().version();
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
            publishPositionMillis(positionMillis, appliedSeekVersion);
        }

        void publishPositionMillis(long positionMillis, long version) {
            if (ensureLeadership()) group.publishPositionMillis(positionMillis, version);
        }

        long seekVersion() {
            return appliedSeekVersion;
        }

        Seek pendingSeek() {
            Seek requested = group.seek.get();
            return requested.version() == appliedSeekVersion ? null : requested;
        }

        void appliedSeek(Seek requested) {
            appliedSeekVersion = requested.version();
        }

        @Override
        public void close() {
            active.set(false);
            group.leader.compareAndSet(this, null);
        }
    }
}
