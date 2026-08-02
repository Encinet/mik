package org.encinet.mik.module.menu;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Finds a visible, reachable frame for a complete floating-menu scene. */
final class FloatingMenuAnchorResolver {
    static final double PREFERRED_DISTANCE = 2.80;
    /**
     * Vanilla does not report its FOV setting to the server. Calibrate the
     * default angular size for common 90-100 FOV clients, then let scene bounds
     * reduce unusually large layouts instead of forcing every menu to 1:1.
     */
    static final double HIGH_FOV_BASE_SCALE = 1.28;
    static final double REFERENCE_LAYOUT_DISTANCE =
            PREFERRED_DISTANCE / HIGH_FOV_BASE_SCALE;
    static final double MIN_COMFORTABLE_DISTANCE = 1.25;

    private static final double WALL_CLEARANCE = 0.28;
    private static final double MIN_FALLBACK_DISTANCE = 0.08;
    private static final double HORIZONTAL_PADDING = 0.55;
    private static final double VERTICAL_PADDING = 0.48;
    private static final double DEPTH_PADDING = 0.20;
    private static final double YAW_PENALTY_PER_DEGREE = 0.015;
    private static final double VERTICAL_PENALTY = 0.45;

    private static final List<Candidate> CANDIDATES = List.of(
            new Candidate(0.0, 0.0),
            new Candidate(0.0, 0.55),
            new Candidate(0.0, -0.40),
            new Candidate(-16.0, 0.0),
            new Candidate(16.0, 0.0),
            new Candidate(-28.0, 0.0),
            new Candidate(28.0, 0.0),
            new Candidate(-16.0, 0.40),
            new Candidate(16.0, 0.40)
    );

    private FloatingMenuAnchorResolver() {
    }

    static Anchor resolve(Location eye, Vector requestedForward, SceneBounds bounds) {
        return resolve(eye, requestedForward, bounds, 1.0);
    }

    static Anchor resolve(Location eye, Vector requestedForward, SceneBounds bounds,
                          double interfaceScale) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(bounds, "bounds");
        if (!Double.isFinite(interfaceScale) || interfaceScale <= 0.0) {
            throw new IllegalArgumentException("Interface scale must be positive and finite");
        }
        if (eye.getWorld() == null) throw new IllegalArgumentException("Menu eye location needs a world");
        SceneBounds scaledBounds = bounds.scaled(interfaceScale);
        Vector baseline = horizontalUnit(requestedForward);
        Selection bestComfortable = null;
        Selection bestFallback = null;
        for (Candidate candidate : CANDIDATES) {
            Vector forward = rotateHorizontal(baseline, candidate.yawDegrees());
            Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
            double distance = safeDistance(eye, forward, right,
                    candidate.verticalOffset() * interfaceScale, scaledBounds);
            Selection selection = new Selection(candidate, forward, right, distance,
                    ergonomicScore(candidate, distance));
            if (bestFallback == null || selection.score() > bestFallback.score()) {
                bestFallback = selection;
            }
            if (distance >= MIN_COMFORTABLE_DISTANCE
                    && (bestComfortable == null || selection.score() > bestComfortable.score())) {
                bestComfortable = selection;
            }
            // The centered candidate has no ergonomic penalty and cannot be
            // outscored once it reaches the preferred distance.
            if (candidate.yawDegrees() == 0.0 && candidate.verticalOffset() == 0.0
                    && distance >= PREFERRED_DISTANCE - 1.0E-6) {
                break;
            }
        }
        Selection selected = bestComfortable == null ? bestFallback : bestComfortable;
        if (selected == null) throw new IllegalStateException("No menu anchor candidate was evaluated");
        double distance = Math.max(MIN_FALLBACK_DISTANCE,
                Math.min(PREFERRED_DISTANCE, selected.distance()));
        // Keep angular size stable even in the last-resort tight fallback. A
        // minimum visual scale would make the measured scene grow back into
        // the obstruction that forced it closer.
        // First find the scene's automatic fit at the neutral preference, then
        // apply the player's choice. Using scaledBounds for comfortScale here
        // would mathematically cancel the preference on every view-limited menu.
        double automaticScale = Math.min(
                Math.min(HIGH_FOV_BASE_SCALE, distance / REFERENCE_LAYOUT_DISTANCE),
                bounds.comfortScale(distance));
        double spatialScale = automaticScale * interfaceScale;
        Location origin = eye.clone()
                .add(selected.forward().clone().multiply(distance))
                .add(0.0, selected.candidate().verticalOffset() * spatialScale, 0.0);
        return new Anchor(origin, selected.forward(), selected.right(), spatialScale,
                distance, selected.candidate().yawDegrees());
    }

    static SceneBounds measure(FloatingMenuDefinition definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(layoutPoses, "layoutPoses");
        Objects.requireNonNull(layoutSizes, "layoutSizes");
        List<FloatingMenuPoint> points = new ArrayList<>();
        for (java.util.Map.Entry<String, FloatingMenuPose> entry : layoutPoses.entrySet()) {
            FloatingMenuPose pose = entry.getValue();
            FloatingMenuSize size = Objects.requireNonNull(layoutSizes.get(entry.getKey()),
                    "Missing layout size for " + entry.getKey());
            addFootprint(points, pose, size.width(), size.height());
        }
        for (FloatingMenuDecoration decoration : definition.decorations().values()) {
            if (decoration.placement() instanceof FloatingMenuPlacement.Local local) {
                FloatingMenuSize size = switch (decoration.content()) {
                    case FloatingMenuDecoration.Text text -> new FloatingMenuSize(
                            text.displayWidth() * text.scale(),
                            text.displayHeight() * text.scale());
                    case FloatingMenuDecoration.Visual visual -> new FloatingMenuSize(
                            visual.scale(), visual.scale());
                };
                addFootprint(points, local.pose(), size.width(), size.height());
            }
        }
        if (points.isEmpty()) points.add(FloatingMenuPoint.ORIGIN);
        if (definition.titleVisible()) {
            double top = points.stream().mapToDouble(FloatingMenuPoint::up).max().orElse(0.0);
            points.add(new FloatingMenuPoint(0.0, top + 0.55, 0.0));
        }
        return SceneBounds.around(points, definition.framing());
    }

    private static void addFootprint(List<FloatingMenuPoint> points,
                                     FloatingMenuPose pose,
                                     double width, double height) {
        points.add(pose.point());
        for (double right : new double[]{pose.right() - width * 0.5,
                pose.right() + width * 0.5}) {
            for (double up : new double[]{pose.up() - height * 0.5,
                    pose.up() + height * 0.5}) {
                points.add(new FloatingMenuPoint(right, up, pose.forward()));
            }
        }
    }

    private static double safeDistance(Location eye, Vector forward, Vector right,
                                       double verticalOffset, SceneBounds bounds) {
        double maximumCenterDistance = PREFERRED_DISTANCE;
        for (FloatingMenuPoint probe : bounds.probes()) {
            Vector rayPerCenterDistance = forward.clone()
                    .multiply(1.0 - probe.forward() / REFERENCE_LAYOUT_DISTANCE)
                    .add(right.clone().multiply(probe.right() / REFERENCE_LAYOUT_DISTANCE))
                    .add(new Vector(0.0,
                            (probe.up() + verticalOffset) / REFERENCE_LAYOUT_DISTANCE, 0.0));
            double distanceRatio = rayPerCenterDistance.length();
            if (!Double.isFinite(distanceRatio) || distanceRatio < 1.0E-8) continue;
            Vector direction = rayPerCenterDistance.multiply(1.0 / distanceRatio);
            double maximumRayDistance = PREFERRED_DISTANCE * distanceRatio + WALL_CLEARANCE;
            RayTraceResult hit = eye.getWorld().rayTraceBlocks(
                    eye, direction, maximumRayDistance, FluidCollisionMode.NEVER, true);
            if (hit == null) continue;
            double hitDistance = hit.getHitPosition().distance(eye.toVector());
            double allowedCenterDistance = Math.max(0.0,
                    (hitDistance - WALL_CLEARANCE) / distanceRatio);
            maximumCenterDistance = Math.min(maximumCenterDistance, allowedCenterDistance);
        }
        return maximumCenterDistance;
    }

    private static double ergonomicScore(Candidate candidate, double distance) {
        return distance
                - Math.abs(candidate.yawDegrees()) * YAW_PENALTY_PER_DEGREE
                - Math.abs(candidate.verticalOffset()) * VERTICAL_PENALTY;
    }

    private static Vector horizontalUnit(Vector direction) {
        Objects.requireNonNull(direction, "requestedForward");
        Vector horizontal = direction.clone().setY(0.0);
        if (!Double.isFinite(horizontal.getX()) || !Double.isFinite(horizontal.getZ())
                || horizontal.lengthSquared() < 1.0E-8) {
            throw new IllegalArgumentException("Requested menu direction must be horizontal and finite");
        }
        return horizontal.normalize();
    }

    private static Vector rotateHorizontal(Vector direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Vector(direction.getX() * cosine - direction.getZ() * sine, 0.0,
                direction.getX() * sine + direction.getZ() * cosine).normalize();
    }

    record Anchor(Location origin, Vector forward, Vector right,
                  double spatialScale, double distance, double yawOffsetDegrees) {
        Anchor {
            origin = Objects.requireNonNull(origin, "origin").clone();
            forward = Objects.requireNonNull(forward, "forward").clone();
            right = Objects.requireNonNull(right, "right").clone();
        }

        @Override public Location origin() { return origin.clone(); }
        @Override public Vector forward() { return forward.clone(); }
        @Override public Vector right() { return right.clone(); }
    }

    record SceneBounds(List<FloatingMenuPoint> probes, FloatingMenuFraming framing) {
        SceneBounds(List<FloatingMenuPoint> probes) {
            this(probes, FloatingMenuFraming.COMFORTABLE);
        }

        SceneBounds {
            probes = List.copyOf(Objects.requireNonNull(probes, "probes"));
            framing = Objects.requireNonNull(framing, "framing");
            if (probes.isEmpty()) throw new IllegalArgumentException("Scene bounds need probes");
        }

        static SceneBounds around(Collection<FloatingMenuPoint> points) {
            return around(points, FloatingMenuFraming.COMFORTABLE);
        }

        static SceneBounds around(Collection<FloatingMenuPoint> points,
                                  FloatingMenuFraming framing) {
            if (points == null || points.isEmpty()) {
                throw new IllegalArgumentException("Scene bounds need points");
            }
            double minRight = Double.POSITIVE_INFINITY;
            double maxRight = Double.NEGATIVE_INFINITY;
            double minUp = Double.POSITIVE_INFINITY;
            double maxUp = Double.NEGATIVE_INFINITY;
            double minForward = Double.POSITIVE_INFINITY;
            double maxForward = Double.NEGATIVE_INFINITY;
            for (FloatingMenuPoint point : points) {
                Objects.requireNonNull(point, "point");
                minRight = Math.min(minRight, point.right());
                maxRight = Math.max(maxRight, point.right());
                minUp = Math.min(minUp, point.up());
                maxUp = Math.max(maxUp, point.up());
                minForward = Math.min(minForward, point.forward());
                maxForward = Math.max(maxForward, point.forward());
            }
            minRight -= HORIZONTAL_PADDING;
            maxRight += HORIZONTAL_PADDING;
            minUp -= VERTICAL_PADDING;
            maxUp += VERTICAL_PADDING;
            minForward -= DEPTH_PADDING;
            maxForward += DEPTH_PADDING;
            double centerRight = (minRight + maxRight) * 0.5;
            double centerUp = (minUp + maxUp) * 0.5;
            double centerForward = (minForward + maxForward) * 0.5;
            List<FloatingMenuPoint> probes = new ArrayList<>(points.size() + 16);
            // Preserve every real node center so a narrow pillar cannot hide a
            // middle control while all outer envelope probes remain clear.
            probes.addAll(points);
            probes.add(FloatingMenuPoint.ORIGIN);
            probes.add(new FloatingMenuPoint(centerRight, centerUp, centerForward));
            for (double right : new double[]{minRight, maxRight}) {
                for (double up : new double[]{minUp, maxUp}) {
                    for (double forward : new double[]{minForward, maxForward}) {
                        probes.add(new FloatingMenuPoint(right, up, forward));
                    }
                }
            }
            probes.add(new FloatingMenuPoint(minRight, centerUp, centerForward));
            probes.add(new FloatingMenuPoint(maxRight, centerUp, centerForward));
            probes.add(new FloatingMenuPoint(centerRight, minUp, centerForward));
            probes.add(new FloatingMenuPoint(centerRight, maxUp, centerForward));
            return new SceneBounds(probes, framing);
        }

        double comfortScale(double distance) {
            double maximumRight = probes.stream().mapToDouble(point ->
                    Math.abs(point.right())).max().orElse(0.0);
            double maximumUp = probes.stream().mapToDouble(point ->
                    Math.abs(point.up())).max().orElse(0.0);
            double horizontal = maximumRight < 1.0E-6 ? Double.POSITIVE_INFINITY
                    : distance * framing.horizontalTangent() / maximumRight;
            double vertical = maximumUp < 1.0E-6 ? Double.POSITIVE_INFINITY
                    : distance * framing.verticalTangent() / maximumUp;
            return Math.min(horizontal, vertical);
        }

        SceneBounds scaled(double factor) {
            if (!Double.isFinite(factor) || factor <= 0.0) {
                throw new IllegalArgumentException("Scene scale must be positive and finite");
            }
            if (factor == 1.0) return this;
            return new SceneBounds(probes.stream()
                    .map(point -> new FloatingMenuPoint(point.right() * factor,
                            point.up() * factor, point.forward() * factor))
                    .toList(), framing);
        }
    }

    private record Candidate(double yawDegrees, double verticalOffset) {
    }

    private record Selection(Candidate candidate, Vector forward, Vector right,
                             double distance, double score) {
    }
}
