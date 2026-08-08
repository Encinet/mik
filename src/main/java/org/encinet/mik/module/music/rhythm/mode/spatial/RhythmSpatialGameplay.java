package org.encinet.mik.module.music.rhythm.mode.spatial;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.module.music.rhythm.RhythmChartView;
import org.encinet.mik.module.music.rhythm.RhythmCue;
import org.encinet.mik.module.music.rhythm.RhythmGameSession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * Owns the mutable presentation state for one spatial rhythm session.
 *
 * <p>The game service decides when to render and when input occurs. This class
 * keeps the spatial path, visibility decisions, presentation fairness and
 * slider transitions consistent across those callbacks.</p>
 */
public final class RhythmSpatialGameplay {
    private static final long TARGET_VISIBILITY_RECHECK_MILLIS = 250L;
    private static final long SLIDER_VISIBILITY_RECHECK_NANOS = 250_000_000L;
    private static final long EARLY_FEEDBACK_MILLIS = 360L;

    private final Location eyeAnchor;
    private final RhythmChartView chart;
    private final RhythmGameSession session;
    private final RhythmSpatialProfile profile;
    private final RhythmSpatialPath path;
    private final RhythmSpatialArena arena;
    private final RhythmSpatialSlider slider;
    private final RhythmSpatialSlider.State sliderState =
            new RhythmSpatialSlider.State();
    private final long minimumReactionMillis;
    private final LongSupplier nanoClock;
    private final Map<Long, Placement> placements = new HashMap<>();
    private final Map<Long, Long> unavailableCues = new HashMap<>();
    private final Map<Long, Long> presentedAtMillis = new HashMap<>();
    private final Map<String, ExpectedFrame> expectedFrames = new HashMap<>();
    private final Map<Long, Long> visibilityCheckedAtMillis = new HashMap<>();
    private final Map<SliderKey, RhythmSpatialSlider.Link> sliderLinks =
            new HashMap<>();
    private final Map<SliderKey, Long> sliderVisibilityCheckedAtNanos =
            new HashMap<>();
    private final Set<SliderKey> unavailableSliders = new HashSet<>();
    private long earlyFeedbackThroughMillis = Long.MIN_VALUE;
    private long earlyFeedbackMillis;

    public RhythmSpatialGameplay(
            String trackSeed, double startingYawDegrees,
            Location eyeAnchor, RhythmChartView chart,
            RhythmGameSession session, RhythmSpatialProfile profile,
            RhythmSpatialArena arena, long minimumReactionMillis) {
        this(trackSeed, startingYawDegrees, eyeAnchor, chart, session, profile,
                arena, minimumReactionMillis, System::nanoTime);
    }

    RhythmSpatialGameplay(String trackSeed, double startingYawDegrees,
                          Location eyeAnchor, RhythmChartView chart,
                          RhythmGameSession session,
                          RhythmSpatialProfile profile,
                          RhythmSpatialArena arena,
                          long minimumReactionMillis,
                          LongSupplier nanoClock) {
        this.eyeAnchor = Objects.requireNonNull(eyeAnchor, "eyeAnchor").clone();
        this.chart = Objects.requireNonNull(chart, "chart");
        this.session = Objects.requireNonNull(session, "session");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.arena = Objects.requireNonNull(arena, "arena");
        if (minimumReactionMillis < 1L) {
            throw new IllegalArgumentException(
                    "minimum reaction time must be positive");
        }
        this.minimumReactionMillis = minimumReactionMillis;
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        this.path = new RhythmSpatialPath(trackSeed, startingYawDegrees, profile);
        this.slider = new RhythmSpatialSlider(trackSeed, session.difficulty(),
                profile);
    }

    public RhythmSpatialProfile profile() {
        return profile;
    }

    public Placement placement(RhythmCue cue) {
        Objects.requireNonNull(cue, "cue");
        Placement existing = placements.get(cue.id());
        if (existing != null) return existing;
        if (unavailableCues.containsKey(cue.id())) return null;

        Optional<RhythmSpatialArena.Target> resolved = arena.resolve(path.point(cue));
        if (resolved.isEmpty()) {
            unavailableCues.put(cue.id(), cue.timeMillis());
            return null;
        }
        RhythmSpatialArena.Target target = resolved.get();
        Placement placement = new Placement(cue.timeMillis(), target.location(),
                target.depth(), target.hitRadius());
        placements.put(cue.id(), placement);
        return placement;
    }

    public boolean placementVisible(RhythmCue cue, Placement placement,
                                    long visualPositionMillis) {
        Objects.requireNonNull(cue, "cue");
        Objects.requireNonNull(placement, "placement");
        Long checkedAt = visibilityCheckedAtMillis.get(cue.id());
        if (checkedAt != null && visualPositionMillis >= checkedAt
                && visualPositionMillis - checkedAt
                < TARGET_VISIBILITY_RECHECK_MILLIS) {
            return true;
        }
        boolean visible = arena.visible(placement.location(), placement.hitRadius());
        if (visible) {
            visibilityCheckedAtMillis.put(cue.id(), visualPositionMillis);
        }
        return visible;
    }

    public void expectFrame(RhythmCue cue, long visualPositionMillis) {
        Objects.requireNonNull(cue, "cue");
        expectedFrames.putIfAbsent(decorationId(cue.id()),
                new ExpectedFrame(cue.id(), cue.timeMillis(),
                        visualPositionMillis));
    }

    public void framePresented(String decorationId) {
        ExpectedFrame frame = expectedFrames.get(decorationId);
        if (frame != null) {
            presentedAtMillis.putIfAbsent(frame.cueId(),
                    frame.visualPositionMillis());
        }
    }

    public boolean presentationFair(RhythmCue cue) {
        Objects.requireNonNull(cue, "cue");
        Long presented = presentedAtMillis.get(cue.id());
        return presented != null
                && cue.timeMillis() - presented >= minimumReactionMillis;
    }

    public boolean presented(RhythmCue cue) {
        Objects.requireNonNull(cue, "cue");
        return presentedAtMillis.containsKey(cue.id());
    }

    public void ignore(RhythmCue cue) {
        Objects.requireNonNull(cue, "cue");
        session.ignore(cue);
        sliderState.invalidateCue(cue.id());
        placements.remove(cue.id());
        presentedAtMillis.remove(cue.id());
        expectedFrames.remove(decorationId(cue.id()));
        visibilityCheckedAtMillis.remove(cue.id());
        unavailableCues.put(cue.id(), cue.timeMillis());
        removeSliderLinksForCue(cue.id());
    }

    public RhythmSpatialSlider.Link previewSlider(
            VisibleTarget from, VisibleTarget to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return slider(from.cue(), from.placement(), to.cue(), to.placement());
    }

    public RhythmSpatialSlider.Presentation displayedSlider(
            RhythmSpatialSlider.Link preview, long visualPositionMillis) {
        sliderState.expire(visualPositionMillis);
        RhythmSpatialSlider.Active active = sliderState.active();
        if (active != null) {
            RhythmSpatialSlider.Link link = active.link();
            if (session.isJudged(link.toCueId())
                    || unavailableCues.containsKey(link.toCueId())) {
                sliderState.invalidateCue(link.toCueId());
            } else if (sliderStillVisible(link)) {
                return RhythmSpatialSlider.Presentation.active(active);
            }
        }
        return preview != null && sliderStillVisible(preview)
                ? RhythmSpatialSlider.Presentation.preview(preview) : null;
    }

    public void hit(RhythmCue cue, long judgementPositionMillis,
                    long visualPositionMillis) {
        Objects.requireNonNull(cue, "cue");
        RhythmSpatialSlider.Link nextLink = null;
        if (sliderState.mayStartAfterHit(cue.id())) {
            RhythmCue next = chart.between(cue.timeMillis() + 1L,
                            saturatedAdd(cue.timeMillis(),
                                    RhythmSpatialSlider.MAXIMUM_INTERVAL_MILLIS))
                    .stream()
                    .filter(candidate -> !session.isJudged(candidate.id()))
                    .findFirst().orElse(null);
            if (next != null) {
                Placement fromPlacement = placement(cue);
                Placement toPlacement = placement(next);
                if (fromPlacement != null && toPlacement != null) {
                    nextLink = slider(cue, fromPlacement, next, toPlacement);
                }
            }
        }
        if (nextLink != null && !nextLink.canActivate(
                judgementPositionMillis, visualPositionMillis)) {
            nextLink = null;
        }
        sliderState.hit(cue.id(), judgementPositionMillis,
                visualPositionMillis, nextLink);
    }

    public void miss(RhythmCue cue) {
        Objects.requireNonNull(cue, "cue");
        sliderState.miss();
        removeSliderLinksForCue(cue.id());
    }

    public int completedSliderLinks() {
        return sliderState.completedLinks();
    }

    public void showEarlyFeedback(
            long earlyByMillis, long judgementPositionMillis) {
        earlyFeedbackMillis = Math.max(0L, earlyByMillis);
        earlyFeedbackThroughMillis = saturatedAdd(judgementPositionMillis,
                EARLY_FEEDBACK_MILLIS);
    }

    public boolean earlyFeedbackVisible(long judgementPositionMillis) {
        return judgementPositionMillis <= earlyFeedbackThroughMillis;
    }

    public long earlyFeedbackMillis() {
        return earlyFeedbackMillis;
    }

    public void discardBefore(long timeMillis) {
        path.discardBefore(timeMillis);
        placements.values().removeIf(value -> value.timeMillis() < timeMillis);
        presentedAtMillis.entrySet().removeIf(entry -> {
            Placement placement = placements.get(entry.getKey());
            return placement == null || placement.timeMillis() < timeMillis;
        });
        expectedFrames.values().removeIf(
                value -> value.timeMillis() < timeMillis);
        visibilityCheckedAtMillis.keySet().removeIf(
                cueId -> !placements.containsKey(cueId));
        unavailableCues.entrySet().removeIf(
                entry -> entry.getValue() < timeMillis);
        sliderLinks.values().removeIf(
                link -> link.toTimeMillis() < timeMillis);
        sliderVisibilityCheckedAtNanos.keySet().removeIf(
                key -> !sliderLinks.containsKey(key));
        unavailableSliders.removeIf(key -> {
            Placement placement = placements.get(key.toCueId());
            return placement == null || placement.timeMillis() < timeMillis;
        });
        RhythmSpatialSlider.Active active = sliderState.active();
        if (active != null && active.link().toTimeMillis() < timeMillis) {
            sliderState.invalidate(active.link());
        }
    }

    int retainedPlacementCount() {
        return placements.size();
    }

    int retainedExpectedFrameCount() {
        return expectedFrames.size();
    }

    int retainedSliderLinkCount() {
        return sliderLinks.size();
    }

    private RhythmSpatialSlider.Link slider(
            RhythmCue from, Placement fromPlacement,
            RhythmCue to, Placement toPlacement) {
        SliderKey key = new SliderKey(from.id(), to.id());
        RhythmSpatialSlider.Link existing = sliderLinks.get(key);
        if (existing != null) return existing;
        if (unavailableSliders.contains(key)) return null;
        Optional<RhythmSpatialSlider.Link> candidate = slider.link(
                from, fromPlacement.location().toVector(),
                to, toPlacement.location().toVector(), eyeAnchor.toVector());
        if (candidate.isEmpty() || !sliderVisible(candidate.get())) {
            unavailableSliders.add(key);
            return null;
        }
        RhythmSpatialSlider.Link link = candidate.get();
        sliderLinks.put(key, link);
        sliderVisibilityCheckedAtNanos.put(key, nanoClock.getAsLong());
        return link;
    }

    private boolean sliderVisible(RhythmSpatialSlider.Link link) {
        int segments = link.visibilitySampleSegments();
        for (int sample = 0; sample <= segments; sample++) {
            Vector point = link.pointAtProgress(sample / (double) segments);
            Location location = new Location(eyeAnchor.getWorld(),
                    point.getX(), point.getY(), point.getZ());
            double depth = point.distance(eyeAnchor.toVector());
            double radius = Math.max(0.08,
                    profile.worldHitRadius(depth) * 0.28);
            if (!arena.visible(location, radius)) return false;
        }
        return true;
    }

    private boolean sliderStillVisible(RhythmSpatialSlider.Link link) {
        SliderKey key = new SliderKey(link.fromCueId(), link.toCueId());
        long nowNanos = nanoClock.getAsLong();
        Long checkedAt = sliderVisibilityCheckedAtNanos.get(key);
        if (checkedAt != null && nowNanos - checkedAt
                < SLIDER_VISIBILITY_RECHECK_NANOS) {
            return true;
        }
        if (sliderVisible(link)) {
            sliderVisibilityCheckedAtNanos.put(key, nowNanos);
            return true;
        }
        sliderLinks.remove(key);
        sliderVisibilityCheckedAtNanos.remove(key);
        unavailableSliders.add(key);
        sliderState.invalidate(link);
        return false;
    }

    private void removeSliderLinksForCue(long cueId) {
        sliderLinks.keySet().removeIf(key ->
                key.fromCueId() == cueId || key.toCueId() == cueId);
        sliderVisibilityCheckedAtNanos.keySet().removeIf(key ->
                key.fromCueId() == cueId || key.toCueId() == cueId);
        unavailableSliders.removeIf(key ->
                key.fromCueId() == cueId || key.toCueId() == cueId);
    }

    private static String decorationId(long cueId) {
        return "spatial:core:" + cueId;
    }

    private static long saturatedAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    public record Placement(long timeMillis, Location location,
                            double depth, double hitRadius) {
        public Placement {
            location = Objects.requireNonNull(location, "location").clone();
        }

        @Override
        public Location location() {
            return location.clone();
        }
    }

    public record VisibleTarget(RhythmCue cue, Placement placement) {
        public VisibleTarget {
            Objects.requireNonNull(cue, "cue");
            Objects.requireNonNull(placement, "placement");
        }
    }

    private record ExpectedFrame(long cueId, long timeMillis,
                                 long visualPositionMillis) {
    }

    private record SliderKey(long fromCueId, long toCueId) {
    }
}
