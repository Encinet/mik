package org.encinet.mik.module.menu.runtime;

import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuFieldOfView;
import org.encinet.mik.module.menu.FloatingMenuPlacement;
import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSize;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** Finds a visible, reachable frame for a complete floating-menu scene. */
final class FloatingMenuAnchorResolver {
    static final double PREFERRED_DISTANCE = 2.80;
    /** Shared size for ordinary screens. Vanilla does not report its FOV to the server. */
    static final double STANDARD_SCENE_SCALE = 1.10;
    static final double REFERENCE_LAYOUT_DISTANCE =
            PREFERRED_DISTANCE / STANDARD_SCENE_SCALE;
    static final double MIN_COMFORTABLE_DISTANCE = 1.25;

    /** Beyond this envelope even a small head turn is insufficient to read a scene. */
    static final FloatingMenuFraming MAX_READING_FRAMING =
            new FloatingMenuFraming(62.0, 35.0);

    private static final double WALL_CLEARANCE = 0.28;
    private static final double MIN_FALLBACK_DISTANCE = 0.08;
    private static final double HORIZONTAL_PADDING = 0.55;
    private static final double VERTICAL_PADDING = 0.48;
    private static final double DEPTH_PADDING = 0.20;
    private static final double YAW_PENALTY_PER_DEGREE = 0.015;
    private static final double VERTICAL_PENALTY = 0.45;
    private static final double ORBIT_WALL_CLEARANCE = 0.12;
    private static final double ORBIT_WALL_CLEARANCE_FRACTION = 0.20;
    // Only guards against zero-sized entity metadata when the eye is inside a block.
    // A visual minimum would leave the reader and controls behind nearby walls.
    private static final double DEGENERATE_ORBIT_SCALE = 0.001;
    private static final double SURFACE_CLEARANCE = 0.08;

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
        return resolve(eye, requestedForward, bounds, interfaceScale, FloatingMenuFieldOfView.DEFAULT);
    }

    static Anchor resolve(Location eye, Vector requestedForward, SceneBounds bounds,
                          double interfaceScale, FloatingMenuFieldOfView fieldOfView) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(bounds, "bounds");
        FloatingMenuFraming reading = Objects.requireNonNull(fieldOfView, "fieldOfView").readingFraming();
        double referenceDistance = REFERENCE_LAYOUT_DISTANCE / fieldOfView.projectionFactor();
        double preferredDistance = Math.min(PREFERRED_DISTANCE, PREFERRED_DISTANCE / fieldOfView.projectionFactor());
        if (!Double.isFinite(interfaceScale) || interfaceScale <= 0.0) {
            throw new IllegalArgumentException("Interface scale must be positive and finite");
        }
        if (eye.getWorld() == null) throw new IllegalArgumentException("Menu eye location needs a world");
        SceneBounds scaledBounds = bounds.scaled(interfaceScale);
        Vector baseline = horizontalUnit(requestedForward);
        FloatingMenuWorldSpace space = new FloatingMenuWorldSpace(eye.getWorld());
        Selection bestComfortable = null;
        Selection bestFallback = null;
        for (Candidate candidate : CANDIDATES) {
            Vector forward = rotateHorizontal(baseline, candidate.yawDegrees());
            Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
            double distance = safeDistance(eye, forward, right,
                    candidate.verticalOffset() * interfaceScale, scaledBounds, space, referenceDistance, preferredDistance);
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
                    && distance >= preferredDistance - 1.0E-6) {
                break;
            }
        }
        Selection selected = bestComfortable == null ? bestFallback : bestComfortable;
        if (selected == null) throw new IllegalStateException("No menu anchor candidate was evaluated");
        double distance = Math.max(MIN_FALLBACK_DISTANCE,
                Math.min(preferredDistance, selected.distance()));
        // Keep the size independent of the current page's entry count. A dense
        // page can extend beyond the central view and be read with a small head
        // turn. Only exceptionally large scenes and nearby obstructions reduce
        // the shared size.
        double automaticScale = Math.min(distance / referenceDistance,
                bounds.readableScale(distance, selected.candidate().verticalOffset(), reading));
        double spatialScale = Math.min(automaticScale * interfaceScale,
                bounds.readableScale(distance, selected.candidate().verticalOffset(), reading));
        Location origin = eye.clone()
                .add(selected.forward().clone().multiply(distance))
                .add(0.0, selected.candidate().verticalOffset() * spatialScale, 0.0);
        double factor = fit(eye, origin, selected.forward(), selected.right(), spatialScale, bounds,
                space);
        origin = eye.clone().add(origin.toVector().subtract(eye.toVector()).multiply(factor));
        spatialScale *= factor;
        distance *= factor;
        return new Anchor(origin, selected.forward(), selected.right(), spatialScale,
                distance, selected.candidate().yawDegrees());
    }

    static Anchor frontArc(Location eye, Vector requestedForward, double interfaceScale, SceneBounds bounds) {
        return frontArc(eye, requestedForward, interfaceScale, bounds, FloatingMenuFieldOfView.DEFAULT);
    }

    static Anchor frontArc(Location eye, Vector requestedForward, double interfaceScale, SceneBounds bounds,
                           FloatingMenuFieldOfView fieldOfView) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(bounds, "bounds");
        double verticalTangent = Objects.requireNonNull(fieldOfView, "fieldOfView").readingFraming().verticalTangent();
        if (eye.getWorld() == null || !Double.isFinite(interfaceScale) || interfaceScale <= 0) {
            throw new IllegalArgumentException("Invalid front-arc anchor");
        }
        FloatingMenuWorldSpace space = new FloatingMenuWorldSpace(eye.getWorld());
        double above = Math.min(PREFERRED_DISTANCE,
                space.firstHit(eye, new Vector(0, 1, 0), PREFERRED_DISTANCE));
        double below = Math.min(PREFERRED_DISTANCE,
                space.firstHit(eye, new Vector(0, -1, 0), PREFERRED_DISTANCE));
        double minimumUp = bounds.probes().stream().mapToDouble(FloatingMenuPoint::up).min().orElse(0);
        double maximumUp = bounds.probes().stream().mapToDouble(FloatingMenuPoint::up).max().orElse(0);
        double depth = 0;
        for (FloatingMenuPoint point : bounds.probes()) {
            double neededRadius = Math.abs(point.up()) / verticalTangent;
            double neededForward = Math.sqrt(Math.max(0, neededRadius * neededRadius
                    - point.right() * point.right()));
            depth = Math.max(depth, neededForward + point.forward());
        }
        double fittedDepth = depth;
        double radius = bounds.probes().stream().mapToDouble(point ->
                Math.hypot(point.right(), fittedDepth - point.forward())).max().orElse(1);
        double scale = Math.min(interfaceScale, PREFERRED_DISTANCE / Math.max(1, radius));
        if (maximumUp > minimumUp) {
            scale = Math.min(scale, Math.max(DEGENERATE_ORBIT_SCALE,
                    (above + below - SURFACE_CLEARANCE * 2) / (maximumUp - minimumUp)));
        }
        double minimumOffset = -below + SURFACE_CLEARANCE - minimumUp * scale;
        double maximumOffset = above - SURFACE_CLEARANCE - maximumUp * scale;
        double verticalOffset = minimumOffset <= maximumOffset
                ? Math.clamp(0, minimumOffset, maximumOffset) : (minimumOffset + maximumOffset) / 2;
        double angularMinimum = Double.NEGATIVE_INFINITY;
        double angularMaximum = Double.POSITIVE_INFINITY;
        for (FloatingMenuPoint point : bounds.probes()) {
            double horizontal = Math.hypot(point.right(), depth - point.forward());
            if (horizontal < 1.0E-6) continue;
            angularMinimum = Math.max(angularMinimum, (-horizontal * verticalTangent - point.up()) * scale);
            angularMaximum = Math.min(angularMaximum, (horizontal * verticalTangent - point.up()) * scale);
        }
        verticalOffset = Math.clamp(verticalOffset, Math.min(0, angularMinimum), Math.max(0, angularMaximum));
        Vector baseline = horizontalUnit(requestedForward);
        Anchor best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double yaw : new double[]{0, -16, 16, -28, 28}) {
            Vector forward = rotateHorizontal(baseline, yaw);
            Vector right = new Vector(-forward.getZ(), 0, forward.getX());
            Location origin = eye.clone().add(forward.clone().multiply(depth * scale)).add(0, verticalOffset, 0);
            double factor = fit(eye, origin, forward, right, scale, bounds, space);
            double score = factor - Math.abs(yaw) * 0.002;
            if (score > bestScore) {
                bestScore = score;
                best = new Anchor(eye.clone().add(forward.clone().multiply(depth * scale * factor))
                        .add(0, verticalOffset * factor, 0), forward, right, scale * factor, depth * scale * factor, yaw);
            }
            if (factor >= 1 - 1.0E-6) break;
        }
        return Objects.requireNonNull(best);
    }

    private static double fit(Location eye, Location origin, Vector forward, Vector right,
                              double scale, SceneBounds bounds, FloatingMenuWorldSpace space) {
        Vector offset = origin.toVector().subtract(eye.toVector());
        double factor = 1;
        for (FloatingMenuPoint point : bounds.probes()) {
            Vector target = offset.clone().add(localVector(point, forward, right).multiply(scale));
            double distance = target.length();
            if (distance < 1.0E-8) continue;
            double hit = space.firstHit(eye, target.clone().multiply(1 / distance), distance + SURFACE_CLEARANCE);
            double clearance = Math.min(SURFACE_CLEARANCE, hit * 0.2);
            factor = Math.min(factor, Math.max(DEGENERATE_ORBIT_SCALE, (hit - clearance) / distance));
        }
        if (!clearSurfaces(eye, offset, forward, right, scale, factor, bounds, space)) {
            double minimum = 0;
            double maximum = factor;
            for (int iteration = 0; iteration < 10; iteration++) {
                double middle = (minimum + maximum) / 2;
                if (clearSurfaces(eye, offset, forward, right, scale, middle, bounds, space)) {
                    minimum = middle;
                } else {
                    maximum = middle;
                }
            }
            factor = Math.max(DEGENERATE_ORBIT_SCALE, minimum);
        }
        return factor;
    }

    private static boolean clearSurfaces(Location eye, Vector offset, Vector forward, Vector right,
                                         double scale, double factor, SceneBounds bounds,
                                         FloatingMenuWorldSpace space) {
        for (Surface surface : bounds.surfaces()) {
            Vector center = eye.toVector().add(offset.clone().add(
                    localVector(surface.pose().point(), forward, right).multiply(scale)).multiply(factor));
            double yaw = Math.toRadians(surface.pose().yawDegrees());
            double pitch = Math.toRadians(surface.pose().pitchDegrees());
            Vector panelRight = right.clone().multiply(Math.cos(yaw))
                    .add(forward.clone().multiply(-Math.sin(yaw)));
            Vector panelUp = new Vector(0, Math.cos(pitch), 0)
                    .add(right.clone().multiply(-Math.sin(yaw) * Math.sin(pitch)))
                    .add(forward.clone().multiply(-Math.cos(yaw) * Math.sin(pitch)));
            if (!space.clearSurface(center, panelRight, panelUp,
                    surface.size().width() * scale * factor / 2,
                    surface.size().height() * scale * factor / 2,
                    (Math.max(0.14, surface.depth() / 2) * scale + SURFACE_CLEARANCE) * factor)) return false;
        }
        return true;
    }

    private static Vector localVector(FloatingMenuPoint point, Vector forward, Vector right) {
        return right.clone().multiply(point.right()).add(new Vector(0, point.up(), 0))
                .add(forward.clone().multiply(-point.forward()));
    }

    /** Keeps an actual circular scene centered on the viewer's opening position. */
    static Anchor aroundViewer(Location eye, Vector requestedForward,
                               double interfaceScale) {
        return aroundViewer(eye, requestedForward, interfaceScale, List.of());
    }

    static Anchor aroundViewer(Location eye, Vector requestedForward,
                               double interfaceScale, FloatingMenuDefinition definition,
                               java.util.Map<String, FloatingMenuPose> poses,
                               java.util.Map<String, FloatingMenuSize> sizes) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(poses, "poses");
        Objects.requireNonNull(sizes, "sizes");
        List<FloatingMenuPoint> essential = new ArrayList<>();
        List<Surface> surfaces = new ArrayList<>();
        for (FloatingMenuDefinition.Entry entry : definition.entries().values()) {
            if (!entry.keepAccessible()) continue;
            FloatingMenuPose pose = Objects.requireNonNull(poses.get(entry.id()),
                    "Missing layout pose for " + entry.id());
            FloatingMenuSize size = Objects.requireNonNull(sizes.get(entry.id()),
                    "Missing layout size for " + entry.id());
            addFootprint(essential, pose, size.width(), size.height());
            surfaces.add(new Surface(pose, new FloatingMenuSize(size.width() + 0.16, size.height() + 0.16),
                    FloatingMenuNodeGeometry.surfaceDepth(entry.style())));
        }
        if (definition.titleVisible()) {
            FloatingMenuPose pose = titlePose(definition, poses, sizes, 1);
            FloatingMenuSize size = titleSize(1);
            addFootprint(essential, pose, size.width(), size.height());
            surfaces.add(new Surface(pose, size));
        }
        return aroundViewer(eye, requestedForward, interfaceScale, essential, surfaces);
    }

    private static Anchor aroundViewer(Location eye, Vector requestedForward,
                                       double interfaceScale,
                                       List<FloatingMenuPoint> essential) {
        return aroundViewer(eye, requestedForward, interfaceScale, essential, List.of());
    }

    private static Anchor aroundViewer(Location eye, Vector requestedForward,
                                       double interfaceScale, List<FloatingMenuPoint> essential,
                                       List<Surface> surfaces) {
        Objects.requireNonNull(eye, "eye");
        if (eye.getWorld() == null || !Double.isFinite(interfaceScale)
                || interfaceScale <= 0.0) {
            throw new IllegalArgumentException("Invalid player-centered menu anchor");
        }
        Vector forward = horizontalUnit(requestedForward);
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
        FloatingMenuWorldSpace space = new FloatingMenuWorldSpace(eye.getWorld());
        double fittedScale = interfaceScale;
        for (FloatingMenuPoint point : essential) {
            Vector direction = right.clone().multiply(point.right())
                    .add(new Vector(0.0, point.up(), 0.0))
                    .add(forward.clone().multiply(-point.forward()));
            double localDistance = direction.length();
            if (localDistance < 1.0E-8) continue;
            direction.multiply(1.0 / localDistance);
            double targetDistance = localDistance * fittedScale;
            double hitDistance = space.firstHit(eye, direction, targetDistance);
            if (!Double.isFinite(hitDistance)) continue;
            // A fixed clearance consumes the entire viewing gap when the player
            // stands close to a wall. Scale the clearance with that gap, while
            // retaining the usual separation in open space. Perspective keeps
            // the apparent size of a player-centered scene unchanged.
            double clearance = Math.min(ORBIT_WALL_CLEARANCE,
                    hitDistance * ORBIT_WALL_CLEARANCE_FRACTION);
            fittedScale = Math.max(DEGENERATE_ORBIT_SCALE,
                    Math.min(fittedScale,
                            (hitDistance - clearance) / localDistance));
        }
        if (!essential.isEmpty()) {
            fittedScale *= fit(eye, eye, forward, right, fittedScale,
                    new SceneBounds(essential, FloatingMenuFraming.COMFORTABLE, surfaces), space);
        }
        return new Anchor(eye, forward, right, fittedScale, 0.0, 0.0);
    }

    static SceneBounds measure(FloatingMenuDefinition definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes) {
        return measure(definition, layoutPoses, layoutSizes, 1.0);
    }

    static SceneBounds measure(FloatingMenuDefinition definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale) {
        return measure(definition, layoutPoses, layoutSizes, typographyScale, FloatingMenuFieldOfView.DEFAULT);
    }

    static SceneBounds measure(FloatingMenuDefinition definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale, FloatingMenuFieldOfView fieldOfView) {
        return measure(definition, layoutPoses, layoutSizes, typographyScale, fieldOfView, 1);
    }

    static SceneBounds measure(FloatingMenuDefinition definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale, FloatingMenuFieldOfView fieldOfView, double interfaceScale) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(layoutPoses, "layoutPoses");
        Objects.requireNonNull(layoutSizes, "layoutSizes");
        if (!Double.isFinite(typographyScale) || typographyScale <= 0.0) {
            throw new IllegalArgumentException("Typography scale must be positive and finite");
        }
        List<FloatingMenuPoint> points = new ArrayList<>();
        List<Surface> surfaces = new ArrayList<>();
        List<FloatingMenuPoint> volumeCorners = new ArrayList<>();
        for (java.util.Map.Entry<String, FloatingMenuPose> entry : layoutPoses.entrySet()) {
            FloatingMenuPose pose = entry.getValue();
            FloatingMenuSize size = Objects.requireNonNull(layoutSizes.get(entry.getKey()),
                    "Missing layout size for " + entry.getKey());
            addFootprint(points, pose, size.width(), size.height());
            FloatingMenuDefinition.Entry node = definition.entries().get(entry.getKey());
            surfaces.add(new Surface(pose, new FloatingMenuSize(size.width() + 0.16, size.height() + 0.16),
                    node == null ? 0.0 : FloatingMenuNodeGeometry.surfaceDepth(node.style())));
        }
        for (FloatingMenuDecoration decoration : definition.decorations().values()) {
            if (!definition.framingDecorations().isEmpty()
                    && !definition.framingDecorations().contains(decoration.id())) continue;
            if (decoration.placement() instanceof FloatingMenuPlacement.Local local) {
                FloatingMenuPose pose = FloatingMenuViewProjection.pose(definition.spatialFrame(), fieldOfView,
                        interfaceScale, local.pose());
                if (decoration.content() instanceof FloatingMenuDecoration.BlockVolume volume) {
                    addVolumeCorners(volumeCorners, pose, volume);
                    continue;
                }
                FloatingMenuSize size = switch (decoration.content()) {
                    case FloatingMenuDecoration.Text text -> new FloatingMenuSize(
                            text.displayWidth() * text.scale() * typographyScale,
                            text.displayHeight() * text.scale() * typographyScale);
                    case FloatingMenuDecoration.Visual visual -> new FloatingMenuSize(
                            visual.scale(), visual.scale());
                    case FloatingMenuDecoration.BlockVolume ignored -> throw new IllegalStateException();
                };
                addFootprint(points, pose, size.width(), size.height());
                surfaces.add(new Surface(pose, size));
            }
        }
        if (!volumeCorners.isEmpty()) {
            double minimumRight = volumeCorners.stream().mapToDouble(FloatingMenuPoint::right).min().orElseThrow();
            double maximumRight = volumeCorners.stream().mapToDouble(FloatingMenuPoint::right).max().orElseThrow();
            double minimumUp = volumeCorners.stream().mapToDouble(FloatingMenuPoint::up).min().orElseThrow();
            double maximumUp = volumeCorners.stream().mapToDouble(FloatingMenuPoint::up).max().orElseThrow();
            double minimumForward = volumeCorners.stream().mapToDouble(FloatingMenuPoint::forward).min().orElseThrow();
            double maximumForward = volumeCorners.stream().mapToDouble(FloatingMenuPoint::forward).max().orElseThrow();
            FloatingMenuPose center = FloatingMenuPose.at(new FloatingMenuPoint(
                    (minimumRight + maximumRight) / 2, (minimumUp + maximumUp) / 2,
                    (minimumForward + maximumForward) / 2));
            double width = maximumRight - minimumRight;
            double height = maximumUp - minimumUp;
            double depth = maximumForward - minimumForward;
            addVolumeCorners(points, center, width, height, depth);
            surfaces.add(new Surface(center, new FloatingMenuSize(width, height), depth));
        }
        if (points.isEmpty()) points.add(FloatingMenuPoint.ORIGIN);
        if (definition.titleVisible()) {
            FloatingMenuPose pose = titlePose(definition, layoutPoses, layoutSizes, typographyScale);
            FloatingMenuSize size = titleSize(typographyScale);
            addFootprint(points, pose, size.width(), size.height());
            surfaces.add(new Surface(pose, size));
        }
        SceneBounds bounds = SceneBounds.around(points, definition.framing());
        return new SceneBounds(bounds.probes(), bounds.framing(), surfaces);
    }

    static FloatingMenuSize titleSize(double typographyScale) {
        return new FloatingMenuSize(5 * 0.86 * typographyScale, 1.5 * 0.86 * typographyScale);
    }

    static FloatingMenuPose titlePose(FloatingMenuDefinition definition,
                                     java.util.Map<String, FloatingMenuPose> poses,
                                     java.util.Map<String, FloatingMenuSize> sizes, double typographyScale) {
        double top = poses.entrySet().stream().mapToDouble(entry ->
                entry.getValue().up() + sizes.get(entry.getKey()).height() / 2).max().orElse(0);
        double forward = definition.spatialFrame().viewerCentered()
                ? poses.values().stream().mapToDouble(FloatingMenuPose::forward).min()
                .orElse(-REFERENCE_LAYOUT_DISTANCE) : 0;
        return FloatingMenuPose.at(new FloatingMenuPoint(0, top + 0.22 + titleSize(typographyScale).height() / 2, forward));
    }

    private static void addFootprint(List<FloatingMenuPoint> points,
                                     FloatingMenuPose pose,
                                     double width, double height) {
        points.add(pose.point());
        double yaw = Math.toRadians(pose.yawDegrees());
        double pitch = Math.toRadians(pose.pitchDegrees());
        double cosine = Math.cos(yaw);
        double sine = Math.sin(yaw);
        double pitchCosine = Math.cos(pitch);
        double pitchSine = Math.sin(pitch);
        for (double horizontal : new double[]{-width * 0.5, 0, width * 0.5}) {
            for (double vertical : new double[]{-height * 0.5, 0, height * 0.5}) {
                points.add(new FloatingMenuPoint(
                        pose.right() + horizontal * cosine - vertical * sine * pitchSine,
                        pose.up() + vertical * pitchCosine,
                        pose.forward() + horizontal * sine + vertical * cosine * pitchSine));
            }
        }
    }

    private static double safeDistance(Location eye, Vector forward, Vector right,
                                       double verticalOffset, SceneBounds bounds, FloatingMenuWorldSpace space,
                                       double referenceDistance, double preferredDistance) {
        double maximumCenterDistance = preferredDistance;
        for (FloatingMenuPoint probe : bounds.probes()) {
            Vector rayPerCenterDistance = forward.clone()
                    .multiply(1.0 - probe.forward() / referenceDistance)
                    .add(right.clone().multiply(probe.right() / referenceDistance))
                    .add(new Vector(0.0,
                            (probe.up() + verticalOffset) / referenceDistance, 0.0));
            double distanceRatio = rayPerCenterDistance.length();
            if (!Double.isFinite(distanceRatio) || distanceRatio < 1.0E-8) continue;
            Vector direction = rayPerCenterDistance.multiply(1.0 / distanceRatio);
            double maximumRayDistance = preferredDistance * distanceRatio + WALL_CLEARANCE;
            double hitDistance = space.firstHit(eye, direction, maximumRayDistance);
            if (!Double.isFinite(hitDistance)) continue;
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

    private static void addVolumeCorners(List<FloatingMenuPoint> points, FloatingMenuPose pose,
                                         FloatingMenuDecoration.BlockVolume volume) {
        addVolumeCorners(points, pose, volume.width(), volume.height(), volume.depth());
    }

    private static void addVolumeCorners(List<FloatingMenuPoint> points, FloatingMenuPose pose,
                                         double width, double height, double depth) {
        double yaw = Math.toRadians(pose.yawDegrees());
        double pitch = Math.toRadians(pose.pitchDegrees());
        for (double horizontal : new double[] {-width / 2, width / 2})
            for (double vertical : new double[] {-height / 2, height / 2})
                for (double normal : new double[] {-depth / 2, depth / 2})
                    points.add(new FloatingMenuPoint(
                            pose.right() + horizontal * Math.cos(yaw)
                                    - vertical * Math.sin(yaw) * Math.sin(pitch)
                                    + normal * Math.sin(yaw) * Math.cos(pitch),
                            pose.up() + vertical * Math.cos(pitch) + normal * Math.sin(pitch),
                            pose.forward() + horizontal * Math.sin(yaw)
                                    + vertical * Math.cos(yaw) * Math.sin(pitch)
                                    - normal * Math.cos(yaw) * Math.cos(pitch)));
    }

    record Surface(FloatingMenuPose pose, FloatingMenuSize size, double depth) {
        Surface(FloatingMenuPose pose, FloatingMenuSize size) { this(pose, size, 0); }
    }

    record SceneBounds(List<FloatingMenuPoint> probes, FloatingMenuFraming framing, List<Surface> surfaces) {
        SceneBounds(List<FloatingMenuPoint> probes, FloatingMenuFraming framing) {
            this(probes, framing, List.of());
        }
        SceneBounds(List<FloatingMenuPoint> probes) {
            this(probes, FloatingMenuFraming.COMFORTABLE);
        }

        SceneBounds {
            probes = List.copyOf(Objects.requireNonNull(probes, "probes"));
            framing = Objects.requireNonNull(framing, "framing");
            surfaces = List.copyOf(surfaces);
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
            return new SceneBounds(List.copyOf(new java.util.LinkedHashSet<>(probes)), framing);
        }

        double readableScale(double distance) {
            return readableScale(distance, 0);
        }

        double readableScale(double distance, double verticalOffset) {
            return readableScale(distance, verticalOffset, MAX_READING_FRAMING);
        }

        double readableScale(double distance, double verticalOffset, FloatingMenuFraming reading) {
            double maximum = Double.POSITIVE_INFINITY;
            double horizontalTangent = Objects.requireNonNull(reading, "reading").horizontalTangent();
            double verticalTangent = reading.verticalTangent();
            for (FloatingMenuPoint point : probes) {
                double horizontalExtent = Math.abs(point.right())
                        + point.forward() * horizontalTangent;
                double verticalExtent = Math.abs(point.up() + verticalOffset)
                        + point.forward() * verticalTangent;
                if (horizontalExtent > 1.0E-6) {
                    maximum = Math.min(maximum,
                            distance * horizontalTangent / horizontalExtent);
                }
                if (verticalExtent > 1.0E-6) {
                    maximum = Math.min(maximum,
                            distance * verticalTangent / verticalExtent);
                }
                if (point.forward() > 1.0E-6) {
                    maximum = Math.min(maximum, distance / point.forward());
                }
            }
            return maximum;
        }

        /** Actual angle of the complete scene, including its forward depth. */
        double angularRadius(double distance, double scale) {
            double radius = 0.0;
            for (FloatingMenuPoint point : probes) {
                double toward = Math.max(0.01, distance - point.forward() * scale);
                radius = Math.max(radius, Math.toDegrees(Math.atan2(
                        Math.hypot(point.right(), point.up()) * scale, toward)));
            }
            return radius;
        }

        FloatingMenuFraming interactionFraming(double distance, double scale) {
            double horizontal = framing.horizontalHalfAngleDegrees();
            double vertical = framing.verticalHalfAngleDegrees();
            for (FloatingMenuPoint point : probes) {
                double toward = Math.max(0.01, distance - point.forward() * scale);
                horizontal = Math.max(horizontal,
                        Math.toDegrees(Math.atan2(Math.abs(point.right()) * scale, toward)) + 3);
                vertical = Math.max(vertical,
                        Math.toDegrees(Math.atan2(Math.abs(point.up()) * scale, toward)) + 3);
            }
            return new FloatingMenuFraming(Math.min(85, horizontal), Math.min(85, vertical));
        }

        SceneBounds scaled(double factor) {
            if (!Double.isFinite(factor) || factor <= 0.0) {
                throw new IllegalArgumentException("Scene scale must be positive and finite");
            }
            if (factor == 1.0) return this;
            return new SceneBounds(probes.stream()
                    .map(point -> new FloatingMenuPoint(point.right() * factor,
                            point.up() * factor, point.forward() * factor))
                    .toList(), framing, surfaces.stream().map(surface -> new Surface(
                            FloatingMenuPose.oriented(new FloatingMenuPoint(surface.pose().right() * factor,
                                    surface.pose().up() * factor, surface.pose().forward() * factor),
                                    surface.pose().yawDegrees(), surface.pose().pitchDegrees()),
                            new FloatingMenuSize(surface.size().width() * factor, surface.size().height() * factor)))
                    .toList());
        }
    }

    private record Candidate(double yawDegrees, double verticalOffset) {
    }

    private record Selection(Candidate candidate, Vector forward, Vector right,
                             double distance, double score) {
    }
}
