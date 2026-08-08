package org.encinet.mik.module.afk;

import java.util.UUID;

/**
 * Integration boundary for interactive modules that provide stronger activity
 * evidence than generic Bukkit events can infer.
 */
public interface AfkActivityService {

    AfkActivityService NONE = new AfkActivityService() {
        @Override
        public ActivityLease suppressAutomaticAfk(UUID playerId, String reason) {
            return ActivityLease.NONE;
        }

        @Override
        public void recordTrustedActivity(UUID playerId) {
        }
    };

    /** Prevents automatic AFK only while the returned lease remains open. */
    ActivityLease suppressAutomaticAfk(UUID playerId, String reason);

    /** Records activity already validated by the owning gameplay module. */
    void recordTrustedActivity(UUID playerId);

    @FunctionalInterface
    interface ActivityLease extends AutoCloseable {

        ActivityLease NONE = () -> { };

        @Override
        void close();
    }
}
