package org.encinet.mik.module.afk.viewing;

import java.util.Objects;
import java.util.UUID;

/**
 * Presence-only state machine for one viewer. All timestamps use the AFK module's
 * monotonic clock; no activity clock or reward lock is changed here.
 *
 * <p>A session needs continuous confirmation. Look-away and pause deadlines are
 * independent and anchored at the first failure, not renewed by polling or by
 * changing the failure reason. Only a confirmed session earns one exit grace.
 * Missing telemetry, a long sampling gap, teleports and disconnects must discard
 * the session rather than turn uncertainty into an indefinitely renewable lease.</p>
 */
public final class ViewingSession {
    private static final long UNSET = Long.MIN_VALUE;

    private final ViewingPolicy policy;
    private UUID screenId;
    private String contentKey;
    private long confirmedSince = UNSET;
    private long lookAwaySince = UNSET;
    private long pauseSince = UNSET;
    private long lastChecked = UNSET;
    private long exitUntil = UNSET;
    private boolean active;

    public ViewingSession(ViewingPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public UUID screenId() { return screenId; }
    public boolean isConfirmed() { return active; }

    /** A null observation means ordinary departure; unavailable telemetry requires clear(). */
    public void update(Observation observation, long now) {
        if (lastChecked != UNSET && (now < lastChecked || now - lastChecked > policy.freshnessMillis())) {
            clear();
        }
        lastChecked = now;
        if (observation == null) {
            end(now);
            return;
        }
        if (!observation.screenId().equals(screenId) || !observation.contentKey().equals(contentKey)) {
            end(now);
            screenId = observation.screenId();
            contentKey = observation.contentKey();
        }
        if (observation.playback() == PhysicalScreen.Playback.INACTIVE
                || observation.playback() == PhysicalScreen.Playback.UNKNOWN) {
            end(now);
            return;
        }
        if (!active) {
            if (!observation.looking() || observation.playback() != PhysicalScreen.Playback.PLAYING) {
                confirmedSince = UNSET;
                return;
            }
            if (confirmedSince == UNSET) confirmedSince = now;
            if (now - confirmedSince >= policy.confirmationMillis()) {
                active = true;
                exitUntil = UNSET;
            }
            return;
        }
        if (observation.looking()) {
            lookAwaySince = UNSET;
        } else if (lookAwaySince == UNSET) {
            lookAwaySince = now;
        }
        if (observation.playback() == PhysicalScreen.Playback.PLAYING) {
            pauseSince = UNSET;
        } else if (pauseSince == UNSET) {
            pauseSince = now;
        }
        if (graceExpired(now)) end(now);
    }

    public boolean suppressesAutomaticAfk(long now) {
        return lastChecked != UNSET && now >= lastChecked
                && now - lastChecked <= policy.freshnessMillis()
                && ((active && !graceExpired(now)) || (exitUntil != UNSET && now < exitUntil));
    }

    /** Hard invalidation deliberately grants no exit grace. */
    public void clear() {
        screenId = null;
        contentKey = null;
        confirmedSince = UNSET;
        lookAwaySince = UNSET;
        pauseSince = UNSET;
        lastChecked = UNSET;
        exitUntil = UNSET;
        active = false;
    }

    private boolean graceExpired(long now) {
        return (lookAwaySince != UNSET && now - lookAwaySince >= policy.lookAwayGraceMillis())
                || (pauseSince != UNSET && now - pauseSince >= policy.pauseGraceMillis());
    }

    private void end(long now) {
        if (active) exitUntil = now + policy.exitGraceMillis();
        active = false;
        screenId = null;
        contentKey = null;
        confirmedSince = UNSET;
        lookAwaySince = UNSET;
        pauseSince = UNSET;
    }

    public record Observation(UUID screenId, String contentKey, PhysicalScreen.Playback playback, boolean looking) {
        public Observation {
            Objects.requireNonNull(screenId, "screenId");
            Objects.requireNonNull(contentKey, "contentKey");
            Objects.requireNonNull(playback, "playback");
        }
    }
}
