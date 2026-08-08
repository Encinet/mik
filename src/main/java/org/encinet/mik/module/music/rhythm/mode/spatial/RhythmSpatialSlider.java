package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.bukkit.util.Vector;
import org.encinet.mik.module.music.rhythm.RhythmCue;
import org.encinet.mik.module.music.rhythm.RhythmDifficulty;

import java.util.Objects;
import java.util.Optional;

/**
 * Builds deterministic camera-trace links between spatial click checkpoints.
 *
 * <p>Minecraft does not report a general mouse-button release while looking at
 * air, so links never require a held-button state. Existing cues remain the
 * authoritative click checkpoints; the interpolated head only guides the
 * player's view between them.</p>
 */
public final class RhythmSpatialSlider {
    static final long MAXIMUM_INTERVAL_MILLIS = 1_400L;
    public static final int PATH_MARKERS = 7;
    static final int MAXIMUM_CONSECUTIVE_LINKS = 3;
    static final long MINIMUM_TRAVEL_MILLIS = 220L;
    static final long ENDPOINT_SETTLE_MILLIS = 50L;
    private static final int MAXIMUM_VISIBILITY_SAMPLE_SEGMENTS = 32;
    private static final double MAXIMUM_VISIBILITY_ANGLE_STEP_DEGREES = 2.5;
    private static final double MAXIMUM_VISIBILITY_WORLD_STEP = 0.25;

    private final long seed;
    private final RhythmDifficulty difficulty;
    private final RhythmSpatialProfile profile;

    RhythmSpatialSlider(String trackSeed, RhythmDifficulty difficulty,
                        RhythmSpatialProfile profile) {
        this.seed = mix(Objects.requireNonNull(trackSeed, "trackSeed").hashCode());
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    Optional<Link> link(RhythmCue from, Vector fromCenter,
                        RhythmCue to, Vector toCenter, Vector eye) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Vector safeFrom = finiteClone(fromCenter, "fromCenter");
        Vector safeTo = finiteClone(toCenter, "toCenter");
        Vector safeEye = finiteClone(eye, "eye");
        long interval = to.timeMillis() - from.timeMillis();
        long minimum = difficulty.goodWindowMillis() * 2L
                + MINIMUM_TRAVEL_MILLIS + ENDPOINT_SETTLE_MILLIS;
        if (from.id() == to.id() || interval < minimum
                || interval > MAXIMUM_INTERVAL_MILLIS) {
            return Optional.empty();
        }
        Vector fromOffset = safeFrom.clone().subtract(safeEye);
        Vector toOffset = safeTo.clone().subtract(safeEye);
        if (fromOffset.lengthSquared() < 1.0E-8
                || toOffset.lengthSquared() < 1.0E-8) {
            return Optional.empty();
        }
        double angle = Math.toDegrees(fromOffset.angle(toOffset));
        if (!Double.isFinite(angle)
                || angle < profile.aimRadiusDegrees() * 1.55
                || angle > Math.min(72.0,
                profile.maximumTurnDegrees(interval) + 8.0)) {
            return Optional.empty();
        }
        long selector = mix(seed ^ from.signature()
                ^ Long.rotateLeft(to.signature(), 21)
                ^ from.id() * 0x9E3779B97F4A7C15L
                ^ Long.rotateLeft(to.id(), 9));
        if (Math.floorMod(selector, 100L) >= 64L) return Optional.empty();
        return Optional.of(new Link(from.id(), to.id(), from.timeMillis(),
                to.timeMillis(), difficulty.perfectWindowMillis(),
                difficulty.goodWindowMillis(), safeEye, safeFrom, safeTo));
    }

    private static Vector finiteClone(Vector vector, String name) {
        Vector result = Objects.requireNonNull(vector, name).clone();
        if (!Double.isFinite(result.getX()) || !Double.isFinite(result.getY())
                || !Double.isFinite(result.getZ())) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return result;
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }

    public record Link(long fromCueId, long toCueId,
                       long fromTimeMillis, long toTimeMillis,
                       long perfectWindowMillis, long goodWindowMillis,
                       Vector eye, Vector fromCenter, Vector toCenter) {
        public Link {
            if (fromCueId == 0L || toCueId == 0L || fromCueId == toCueId
                    || fromTimeMillis < 0L || toTimeMillis <= fromTimeMillis) {
                throw new IllegalArgumentException("invalid spatial slider link");
            }
            long intervalMillis = toTimeMillis - fromTimeMillis;
            if (perfectWindowMillis < 1L
                    || goodWindowMillis <= perfectWindowMillis
                    || goodWindowMillis > (MAXIMUM_INTERVAL_MILLIS
                    - MINIMUM_TRAVEL_MILLIS - ENDPOINT_SETTLE_MILLIS) / 2L
                    || intervalMillis
                    < goodWindowMillis * 2L + MINIMUM_TRAVEL_MILLIS
                    + ENDPOINT_SETTLE_MILLIS
                    || intervalMillis > MAXIMUM_INTERVAL_MILLIS) {
                throw new IllegalArgumentException("invalid spatial slider link");
            }
            eye = finiteClone(eye, "eye");
            fromCenter = finiteClone(fromCenter, "fromCenter");
            toCenter = finiteClone(toCenter, "toCenter");
            Vector fromOffset = fromCenter.clone().subtract(eye);
            Vector toOffset = toCenter.clone().subtract(eye);
            if (fromOffset.lengthSquared() < 1.0E-8
                    || toOffset.lengthSquared() < 1.0E-8) {
                throw new IllegalArgumentException(
                        "spatial slider endpoints must not overlap the eye");
            }
            double angle = Math.toDegrees(fromOffset.angle(toOffset));
            if (!Double.isFinite(angle) || angle >= 120.0) {
                throw new IllegalArgumentException(
                        "spatial slider angle must support stable interpolation");
            }
        }

        @Override
        public Vector eye() {
            return eye.clone();
        }

        @Override
        public Vector fromCenter() {
            return fromCenter.clone();
        }

        @Override
        public Vector toCenter() {
            return toCenter.clone();
        }

        boolean canActivate(long judgementPositionMillis,
                            long visualPositionMillis) {
            long earliestHit = Math.max(0L, fromTimeMillis - goodWindowMillis);
            long latestHit = saturatedAdd(fromTimeMillis, goodWindowMillis);
            if (judgementPositionMillis < earliestHit
                    || judgementPositionMillis > latestHit
                    || visualPositionMillis < 0L) {
                return false;
            }
            long movementStart = Math.max(visualPositionMillis,
                    saturatedAdd(fromTimeMillis, perfectWindowMillis));
            return movementStart <= movementEndMillis() - MINIMUM_TRAVEL_MILLIS;
        }

        long movementStartMillis(long judgementPositionMillis,
                                 long visualPositionMillis) {
            if (!canActivate(judgementPositionMillis, visualPositionMillis)) {
                throw new IllegalArgumentException(
                        "slider activation must retain a fair travel window");
            }
            return Math.max(visualPositionMillis,
                    saturatedAdd(fromTimeMillis, perfectWindowMillis));
        }

        long movementEndMillis() {
            return toTimeMillis - goodWindowMillis - ENDPOINT_SETTLE_MILLIS;
        }

        double progress(long positionMillis, long movementStartMillis) {
            long movementEnd = movementEndMillis();
            if (movementStartMillis >= movementEnd) {
                throw new IllegalArgumentException(
                        "slider movement must retain positive travel time");
            }
            double raw = (positionMillis - movementStartMillis)
                    / (double) (movementEnd - movementStartMillis);
            return Math.clamp(raw, 0.0, 1.0);
        }

        public Vector pointAtProgress(double progress) {
            if (!Double.isFinite(progress)) {
                throw new IllegalArgumentException("slider progress must be finite");
            }
            double bounded = Math.clamp(progress, 0.0, 1.0);
            double eased = bounded * bounded * (3.0 - 2.0 * bounded);
            Vector fromOffset = fromCenter.clone().subtract(eye);
            Vector toOffset = toCenter.clone().subtract(eye);
            double fromDepth = fromOffset.length();
            double toDepth = toOffset.length();
            Vector direction = sphericalInterpolate(
                    fromOffset.multiply(1.0 / fromDepth),
                    toOffset.multiply(1.0 / toDepth), eased);
            double depth = fromDepth + (toDepth - fromDepth) * eased;
            return eye.clone().add(direction.multiply(depth));
        }

        double viewErrorDegrees(Vector viewDirection, double progress) {
            Vector view = finiteClone(viewDirection, "viewDirection");
            if (view.lengthSquared() < 1.0E-8) return 180.0;
            Vector toward = pointAtProgress(progress).subtract(eye);
            return Math.toDegrees(view.angle(toward));
        }

        int visibilitySampleSegments() {
            Vector fromOffset = fromCenter.clone().subtract(eye);
            Vector toOffset = toCenter.clone().subtract(eye);
            double angleRadians = fromOffset.angle(toOffset);
            double angleDegrees = Math.toDegrees(angleRadians);
            double estimatedLength = Math.max(fromOffset.length(), toOffset.length())
                    * angleRadians
                    + Math.abs(fromOffset.length() - toOffset.length());
            int angularSegments = (int) Math.ceil(angleDegrees
                    / MAXIMUM_VISIBILITY_ANGLE_STEP_DEGREES);
            int spatialSegments = (int) Math.ceil(estimatedLength
                    / MAXIMUM_VISIBILITY_WORLD_STEP);
            return Math.clamp(Math.max(PATH_MARKERS + 1,
                            Math.max(angularSegments, spatialSegments)),
                    PATH_MARKERS + 1, MAXIMUM_VISIBILITY_SAMPLE_SEGMENTS);
        }

        private static Vector sphericalInterpolate(Vector from, Vector to,
                                                   double progress) {
            double dot = Math.clamp(from.dot(to), -1.0, 1.0);
            if (dot > 0.9995) {
                return from.multiply(1.0 - progress)
                        .add(to.multiply(progress)).normalize();
            }
            double angle = Math.acos(dot);
            double sine = Math.sin(angle);
            double fromWeight = Math.sin((1.0 - progress) * angle) / sine;
            double toWeight = Math.sin(progress * angle) / sine;
            return from.multiply(fromWeight).add(to.multiply(toWeight)).normalize();
        }

        private static long saturatedAdd(long value, long increment) {
            return value > Long.MAX_VALUE - increment
                    ? Long.MAX_VALUE : value + increment;
        }
    }

    /** One successfully activated link. Mouse-button hold state is not required. */
    record Active(Link link, long movementStartMillis) {
        Active {
            Objects.requireNonNull(link, "link");
            if (movementStartMillis < link.fromTimeMillis()
                    || movementStartMillis > link.movementEndMillis()
                    - MINIMUM_TRAVEL_MILLIS) {
                throw new IllegalArgumentException("invalid slider movement start");
            }
        }

        static Active start(Link link, long judgementPositionMillis,
                            long visualPositionMillis) {
            Objects.requireNonNull(link, "link");
            return new Active(link, link.movementStartMillis(
                    judgementPositionMillis, visualPositionMillis));
        }

        double progress(long positionMillis) {
            return link.progress(positionMillis, movementStartMillis);
        }

        Vector point(long positionMillis) {
            return link.pointAtProgress(progress(positionMillis));
        }

        double viewErrorDegrees(Vector viewDirection, long positionMillis) {
            return link.viewErrorDegrees(viewDirection, progress(positionMillis));
        }
    }

    /** Render-safe distinction between a static preview and an activated link. */
    public record Presentation(
            Link link, boolean active, long movementStartMillis) {
        public Presentation {
            Objects.requireNonNull(link, "link");
            if (active && (movementStartMillis < link.fromTimeMillis()
                    || movementStartMillis > link.movementEndMillis()
                    - MINIMUM_TRAVEL_MILLIS)) {
                throw new IllegalArgumentException("invalid active slider presentation");
            }
        }

        static Presentation preview(Link link) {
            Objects.requireNonNull(link, "link");
            return new Presentation(link, false, link.fromTimeMillis());
        }

        static Presentation active(Active active) {
            Objects.requireNonNull(active, "active");
            return new Presentation(active.link(), true,
                    active.movementStartMillis());
        }

        public double progress(long positionMillis) {
            return active
                    ? link.progress(positionMillis, movementStartMillis) : 0.0;
        }

        public Vector point(long positionMillis) {
            return link.pointAtProgress(progress(positionMillis));
        }

        public double viewErrorDegrees(
                Vector viewDirection, long positionMillis) {
            return link.viewErrorDegrees(viewDirection, progress(positionMillis));
        }
    }

    /** Pure transition state kept separate from rendering and Bukkit events. */
    static final class State {
        private Active active;
        private int completedLinks;

        Active active() {
            return active;
        }

        int completedLinks() {
            return completedLinks;
        }

        boolean mayStartAfterHit(long cueId) {
            return active == null || active.link().toCueId() != cueId
                    || completedLinks + 1 < MAXIMUM_CONSECUTIVE_LINKS;
        }

        void hit(long cueId, long judgementPositionMillis,
                 long visualPositionMillis, Link nextLink) {
            boolean completed = active != null
                    && active.link().toCueId() == cueId;
            if (nextLink != null && nextLink.fromCueId() != cueId) {
                throw new IllegalArgumentException(
                        "the next slider must start at the hit cue");
            }
            if (nextLink != null && !mayStartAfterHit(cueId)) {
                throw new IllegalArgumentException(
                        "the slider chain exceeded its consecutive limit");
            }
            int nextCompletedLinks = completed ? completedLinks + 1 : 0;
            if (nextLink == null) {
                clear();
                return;
            }
            active = Active.start(nextLink, judgementPositionMillis,
                    visualPositionMillis);
            completedLinks = nextCompletedLinks;
        }

        void miss() {
            clear();
        }

        void invalidateCue(long cueId) {
            if (active != null && (active.link().fromCueId() == cueId
                    || active.link().toCueId() == cueId)) {
                clear();
            }
        }

        void invalidate(Link link) {
            Objects.requireNonNull(link, "link");
            if (active != null
                    && active.link().fromCueId() == link.fromCueId()
                    && active.link().toCueId() == link.toCueId()) {
                clear();
            }
        }

        void expire(long positionMillis) {
            if (active != null && positionMillis > Link.saturatedAdd(
                    active.link().toTimeMillis(),
                    active.link().goodWindowMillis())) {
                clear();
            }
        }

        private void clear() {
            active = null;
            completedLinks = 0;
        }
    }
}
