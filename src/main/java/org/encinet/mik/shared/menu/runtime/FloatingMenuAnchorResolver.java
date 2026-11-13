package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuPlacement;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuSize;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.ui.UiVector3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Finds a visible, reachable frame for a complete floating-menu scene. */
final class FloatingMenuAnchorResolver {
    /** Beyond this envelope even a small head turn is insufficient to read a scene. */
    static final FloatingMenuFraming MAX_READING_FRAMING =
            new FloatingMenuFraming(62.0, 35.0);

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

    /** Selects the reading frame once; current content and world fitting do not own its lifetime. */
    static Anchor reference(Location eye, Vector requestedForward, SceneBounds bounds,
                            double interfaceScale, FloatingMenuFieldOfView fieldOfView) {
        return reference(eye, requestedForward, bounds, interfaceScale, fieldOfView, new FloatingMenuWorldSpace(eye.getWorld()));
    }

    static Anchor reference(Location eye, Vector requestedForward, SceneBounds bounds,
                            double interfaceScale, FloatingMenuFieldOfView fieldOfView, FloatingMenuWorldSpace space) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(bounds, "bounds");
        FloatingMenuFraming reading = Objects.requireNonNull(fieldOfView, "fieldOfView").readingFraming();
        double preferredDistance = MenuViewportLayout.distance(fieldOfView);
        if (!Double.isFinite(interfaceScale) || interfaceScale <= 0.0) {
            throw new IllegalArgumentException("Interface scale must be positive and finite");
        }
        if (eye.getWorld() == null) throw new IllegalArgumentException("Menu eye location needs a world");
        Vector baseline = horizontalUnit(requestedForward);
        Selection best = null;
        var candidates = new ArrayList<>(CANDIDATES);
        double nominalScale = referenceScale(bounds,interfaceScale,reading,preferredDistance,0);
        // A complete page can extend below the eye or towards a ceiling. Fit its full height
        // above the real floor (or below a ceiling) before contracting the whole page towards the eye.
        for (int direction : new int[]{-1,1}) {
            double hit = space.firstHit(eye,new Vector(0,direction,0),preferredDistance);
            if (!Double.isFinite(hit)) continue;
            double edge = bounds.probes().stream().mapToDouble(point -> direction * point.up()).max().orElse(0);
            double offset = -direction * Math.max(0,edge - Math.max(0,hit - SURFACE_CLEARANCE) / nominalScale);
            if (Math.abs(offset) > 1e-6) candidates.add(new Candidate(0,offset));
        }
        for (Candidate candidate : candidates) {
            Vector forward = rotateHorizontal(baseline, candidate.yawDegrees());
            Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
            Anchor reference = referenceCandidate(eye, bounds, interfaceScale, reading,
                    preferredDistance, candidate, forward, right);
            double factor = fitFactor(eye, reference.origin(), forward, right, reference.spatialScale(), bounds, space);
            if (factor <= 1e-6) continue;
            double distance = preferredDistance * factor;
            Selection selection = new Selection(reference, ergonomicScore(candidate, distance));
            if (best == null || selection.score() > best.score()) best = selection;
            // The centered candidate has no ergonomic penalty and cannot be
            // outscored once it reaches the preferred distance.
            if (candidate.yawDegrees() == 0.0 && candidate.verticalOffset() == 0.0
                    && distance >= preferredDistance - 1.0E-6) {
                break;
            }
        }
        if (best == null) throw new MenuSpaceUnavailableException();
        return best.reference();
    }

    private static Anchor referenceCandidate(Location eye, SceneBounds bounds, double interfaceScale,
                                              FloatingMenuFraming reading, double distance,
                                              Candidate candidate, Vector forward, Vector right) {
        double spatialScale = referenceScale(bounds,interfaceScale,reading,distance,candidate.verticalOffset());
        Location origin = eye.clone().add(forward.clone().multiply(distance))
                .add(0, candidate.verticalOffset() * spatialScale, 0);
        return new Anchor(origin, forward, right, spatialScale, distance, candidate.yawDegrees());
    }

    private static double referenceScale(SceneBounds bounds, double interfaceScale, FloatingMenuFraming reading,
                                         double distance, double verticalOffset) {
        double maximum = bounds.readableScale(distance,verticalOffset,reading);
        return Math.min(MenuViewportLayout.scale(interfaceScale), maximum);
    }

    /** Uniform contraction preserves apparent size while bringing the complete scene before obstructions. */
    static Anchor fit(Location eye, Anchor reference, SceneBounds bounds) {
        return fit(eye, reference, bounds, new FloatingMenuWorldSpace(eye.getWorld()));
    }

    static Anchor fit(Location eye, Anchor reference, SceneBounds bounds, FloatingMenuWorldSpace space) {
        double factor = fitFactor(eye, reference.origin(), reference.forward(), reference.right(), reference.spatialScale(), bounds, space);
        if (factor <= 1.0E-6) throw new MenuSpaceUnavailableException();
        return reference.contract(eye, factor);
    }

    static Anchor frontArcReference(Location eye, Vector requestedForward, double interfaceScale, SceneBounds bounds,
                                    FloatingMenuFieldOfView fieldOfView) {
        return frontArcReference(eye, requestedForward, interfaceScale, bounds, fieldOfView,
                new FloatingMenuWorldSpace(eye.getWorld()));
    }

    static Anchor frontArcReference(Location eye, Vector requestedForward, double interfaceScale, SceneBounds bounds,
                                    FloatingMenuFieldOfView fieldOfView, FloatingMenuWorldSpace space) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(bounds, "bounds");
        double verticalTangent = Objects.requireNonNull(fieldOfView, "fieldOfView").readingFraming().verticalTangent();
        if (eye.getWorld() == null || !Double.isFinite(interfaceScale) || interfaceScale <= 0) {
            throw new IllegalArgumentException("Invalid front-arc anchor");
        }
        double depth = 0;
        for (FloatingMenuPoint point : bounds.probes()) {
            double neededRadius = Math.abs(point.up()) / verticalTangent;
            double neededForward = Math.sqrt(Math.max(0, neededRadius * neededRadius
                    - point.right() * point.right()));
            depth = Math.max(depth, neededForward + point.forward());
        }
        double scale = MenuViewportLayout.scale(interfaceScale);
        depth = Math.max(depth, MenuViewportLayout.distance(fieldOfView) / scale);
        double verticalOffset = 0;
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
            double factor = fitFactor(eye, origin, forward, right, scale, bounds, space);
            double score = factor - Math.abs(yaw) * 0.002;
            if (score > bestScore) {
                bestScore = score;
                best = new Anchor(origin, forward, right, scale, depth * scale, yaw);
            }
            if (factor >= 1 - 1.0E-6) break;
        }
        return Objects.requireNonNull(best);
    }

    static double fitFactor(Location eye, Location origin, Vector forward, Vector right,
                              double scale, SceneBounds bounds, FloatingMenuWorldSpace space) {
        Vector offset = origin.toVector().subtract(eye.toVector());
        double factor = 1;
        for (FloatingMenuPoint point : bounds.probes()) {
            Vector target = offset.clone().add(localVector(point, forward, right).multiply(scale));
            double distance = target.length();
            if (distance < 1.0E-8) continue;
            double hit = space.firstHit(eye, target.clone().multiply(1 / distance), distance + SURFACE_CLEARANCE);
            double clearance = Math.min(SURFACE_CLEARANCE, hit * 0.2);
            factor = Math.min(factor, Math.max(0, (hit - clearance) / distance));
        }
        if (factor <= 1.0E-6) return 0;
        double surfaceFactor = 1;
        for (Surface surface : bounds.surfaces()) {
            Vector center = eye.toVector().add(offset.clone().add(
                    localVector(surface.pose().point(), forward, right).multiply(scale)).multiply(factor));
            var rotation = surface.pose().rotation();
            UiVector3 r = rotation.apply(new UiVector3(1, 0, 0)), u = rotation.apply(new UiVector3(0, 1, 0));
            Vector panelRight = localVector(new FloatingMenuPoint(r.right(), r.up(), r.forward()), forward, right);
            Vector panelUp = localVector(new FloatingMenuPoint(u.right(), u.up(), u.forward()), forward, right);
            double localScale = surface.pose().scale() * scale * factor;
            surfaceFactor = Math.min(surfaceFactor, space.limitSurface(eye, center, panelRight, panelUp,
                    surface.size().width() * localScale / 2,
                    surface.size().height() * localScale / 2,
                    (Math.max(0.14, surface.depth() / 2) * surface.pose().scale() * scale + SURFACE_CLEARANCE) * factor));
            if (surfaceFactor <= 1.0E-6) return 0;
        }
        return factor * surfaceFactor;
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
                               double interfaceScale, MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> poses,
                               java.util.Map<String, FloatingMenuSize> sizes, Optional<FloatingMenuPose> title) {
        return aroundViewer(eye, requestedForward, interfaceScale, definition, poses, sizes, title,
                new FloatingMenuWorldSpace(eye.getWorld()));
    }

    static Anchor aroundViewer(Location eye, Vector requestedForward, double interfaceScale, MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> poses, java.util.Map<String, FloatingMenuSize> sizes,
                               Optional<FloatingMenuPose> title, FloatingMenuWorldSpace space) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(poses, "poses");
        Objects.requireNonNull(sizes, "sizes");
        List<FloatingMenuPoint> essential = new ArrayList<>();
        List<Surface> surfaces = new ArrayList<>();
        for (MenuScene.Entry entry : definition.entries().values()) {
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
            FloatingMenuPose pose = title.orElseThrow(() -> new IllegalArgumentException("Missing published title geometry"));
            FloatingMenuSize size = titleSize(1);
            addFootprint(essential, pose, size.width(), size.height());
            surfaces.add(new Surface(pose, size));
        }
        return aroundViewer(eye, requestedForward, interfaceScale, essential, surfaces, space);
    }

    private static Anchor aroundViewer(Location eye, Vector requestedForward,
                                       double interfaceScale,
                                       List<FloatingMenuPoint> essential) {
        return aroundViewer(eye, requestedForward, interfaceScale, essential, List.of(), new FloatingMenuWorldSpace(eye.getWorld()));
    }

    private static Anchor aroundViewer(Location eye, Vector requestedForward,
                                       double interfaceScale, List<FloatingMenuPoint> essential,
                                       List<Surface> surfaces, FloatingMenuWorldSpace space) {
        Objects.requireNonNull(eye, "eye");
        if (eye.getWorld() == null || !Double.isFinite(interfaceScale)
                || interfaceScale <= 0.0) {
            throw new IllegalArgumentException("Invalid player-centered menu anchor");
        }
        Vector forward = horizontalUnit(requestedForward);
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
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
            fittedScale *= fitFactor(eye, eye, forward, right, fittedScale,
                    new SceneBounds(essential, FloatingMenuFraming.COMFORTABLE, surfaces), space);
        }
        if (fittedScale <= 1.0E-6) throw new MenuSpaceUnavailableException();
        return new Anchor(eye, forward, right, fittedScale, 0.0, 0.0);
    }

    static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes) {
        return measure(definition, layoutPoses, layoutSizes, 1.0);
    }

    static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale) {
        return measure(definition, layoutPoses, layoutSizes, typographyScale, FloatingMenuFieldOfView.DEFAULT);
    }

    static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale, FloatingMenuFieldOfView fieldOfView) {
        return measure(definition, layoutPoses, layoutSizes, typographyScale, fieldOfView, 1);
    }

    static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale, FloatingMenuFieldOfView fieldOfView, double interfaceScale) {
        return measure(definition, layoutPoses, layoutSizes, typographyScale, fieldOfView, interfaceScale, List.of());
    }

    static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale, FloatingMenuFieldOfView fieldOfView, double interfaceScale,
                               List<Surface> componentSurfaces) {
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
            MenuScene.Entry node = definition.entries().get(entry.getKey());
            double depth = node == null ? 0 : FloatingMenuNodeGeometry.surfaceDepth(node.style());
            if (depth > 0) addVolumeCorners(points, pose, size.width(), size.height(), depth);
            surfaces.add(new Surface(pose, new FloatingMenuSize(size.width() + 0.16, size.height() + 0.16),
                    depth));
        }
        for (Surface surface : componentSurfaces) {
            addFootprint(points, surface.pose(), surface.size().width(), surface.size().height());
            surfaces.add(surface);
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
                var surface = decorationSurface(decoration, pose, typographyScale);
                addFootprint(points, pose, surface.size().width(), surface.size().height());
                if (surface.depth() > 0) addVolumeCorners(points, pose, surface.size().width(), surface.size().height(), surface.depth());
                surfaces.add(surface);
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
        Optional<FloatingMenuPose> title = definition.titleVisible() ? Optional.of(titlePose(definition, surfaces, typographyScale)) : Optional.empty();
        if (title.isPresent()) {
            FloatingMenuPose pose = title.get();
            FloatingMenuSize size = titleSize(typographyScale);
            addFootprint(points, pose, size.width(), size.height());
            surfaces.add(new Surface(pose, size));
        }
        SceneBounds bounds = SceneBounds.around(points, definition.framing());
        return new SceneBounds(bounds.probes(), bounds.framing(), surfaces, title);
    }

    /** Target fitting and path validation use the same physical decoration dimensions. */
    static Surface decorationSurface(FloatingMenuDecoration decoration, FloatingMenuPose pose, double typographyScale) {
        FloatingMenuSize size = switch (decoration.content()) {
            case FloatingMenuDecoration.Text text -> new FloatingMenuSize(text.displayWidth() * text.scale() * typographyScale,
                    text.displayHeight() * text.scale() * typographyScale);
            case FloatingMenuDecoration.Visual visual -> new FloatingMenuSize(visual.scale(), visual.scale());
            case FloatingMenuDecoration.Surface surface -> new FloatingMenuSize(surface.width(), surface.height());
            case FloatingMenuDecoration.Image image -> new FloatingMenuSize(image.width(), image.height());
            case FloatingMenuDecoration.RangePart range -> new FloatingMenuSize(range.geometry().width(), range.geometry().height());
            case FloatingMenuDecoration.IconPart icon -> new FloatingMenuSize(icon.size(), icon.size());
            case FloatingMenuDecoration.BlockVolume volume -> new FloatingMenuSize(volume.width(), volume.height());
            case FloatingMenuDecoration.BlockOutline outline -> new FloatingMenuSize(outline.size(),outline.size());
        };
        double depth = switch (decoration.content()) {
            case FloatingMenuDecoration.BlockVolume volume -> volume.depth();
            case FloatingMenuDecoration.BlockOutline outline -> outline.size();
            case FloatingMenuDecoration.Visual _ -> size.width();
            default -> 0;
        };
        return new Surface(pose, size, depth);
    }

    static FloatingMenuSize titleSize(double typographyScale) {
        return new FloatingMenuSize(5 * 0.86 * typographyScale, 1.5 * 0.86 * typographyScale);
    }

    private static FloatingMenuPose titlePose(MenuScene definition, List<Surface> content, double typographyScale) {
        List<FloatingMenuPoint> corners = new ArrayList<>();
        for (Surface surface : content) {
            if (surface.depth() > 0) addVolumeCorners(corners, surface.pose(), surface.size().width(), surface.size().height(), surface.depth());
            else addFootprint(corners, surface.pose(), surface.size().width(), surface.size().height());
        }
        double top = corners.stream().mapToDouble(FloatingMenuPoint::up).max().orElse(0);
        double forward = definition.spatialFrame().viewerCentered()
                ? corners.stream().mapToDouble(FloatingMenuPoint::forward).min()
                .orElse(-MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1)) : 0;
        return FloatingMenuPose.at(new FloatingMenuPoint(0, top + 0.22 + titleSize(typographyScale).height() / 2, forward));
    }

    private static void addFootprint(List<FloatingMenuPoint> points,
                                     FloatingMenuPose pose,
                                     double width, double height) {
        points.add(pose.point());
        var rotation = pose.rotation();
        for (double horizontal : new double[]{-width / 2, 0, width / 2})
            for (double vertical : new double[]{-height / 2, 0, height / 2}) {
                UiVector3 offset = rotation.apply(new UiVector3(horizontal, vertical, 0).multiply(pose.scale()));
                points.add(new FloatingMenuPoint(pose.right() + offset.right(), pose.up() + offset.up(), pose.forward() + offset.forward()));
            }
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

        Anchor contract(Location eye, double factor) {
            if (!Double.isFinite(factor) || factor <= 0) throw new MenuSpaceUnavailableException();
            factor = Math.min(factor, 1);
            return new Anchor(eye.clone().add(origin.toVector().subtract(eye.toVector()).multiply(factor)),
                    forward, right, spatialScale * factor, distance * factor, yawOffsetDegrees);
        }

        ReadingFrame relativeTo(Location eye) {
            return new ReadingFrame(spatialScale, distance, origin.getY() - eye.getY(), yawOffsetDegrees);
        }
    }

    /** Eye-relative reading geometry survives content updates, movement and obstacle recovery. */
    record ReadingFrame(double spatialScale, double distance, double verticalOffset, double yawOffsetDegrees) {
        ReadingFrame fittedTo(SceneBounds bounds, FloatingMenuFraming reading) {
            return new ReadingFrame(bounds.readableScale(this, reading), distance, verticalOffset, yawOffsetDegrees);
        }

        Anchor at(Location eye, Vector requestedForward) {
            Vector forward = rotateHorizontal(horizontalUnit(requestedForward), yawOffsetDegrees);
            Vector right = new Vector(-forward.getZ(), 0, forward.getX());
            return new Anchor(eye.clone().add(forward.clone().multiply(distance)).add(0, verticalOffset, 0),
                    forward, right, spatialScale, distance, yawOffsetDegrees);
        }
    }

    private static void addVolumeCorners(List<FloatingMenuPoint> points, FloatingMenuPose pose,
                                         FloatingMenuDecoration.BlockVolume volume) {
        addVolumeCorners(points, pose, volume.width(), volume.height(), volume.depth());
    }

    private static void addVolumeCorners(List<FloatingMenuPoint> points, FloatingMenuPose pose,
                                         double width, double height, double depth) {
        var rotation = pose.rotation();
        for (double horizontal : new double[]{-width / 2, width / 2})
            for (double vertical : new double[]{-height / 2, height / 2})
                for (double normal : new double[]{-depth / 2, depth / 2}) {
                    UiVector3 offset = rotation.apply(new UiVector3(horizontal, vertical, normal).multiply(pose.scale()));
                    points.add(new FloatingMenuPoint(pose.right() + offset.right(), pose.up() + offset.up(), pose.forward() + offset.forward()));
                }
    }

    record Surface(FloatingMenuPose pose, FloatingMenuSize size, double depth) {
        Surface(FloatingMenuPose pose, FloatingMenuSize size) { this(pose, size, 0); }

        /** Physical rear extent, independent of the viewing envelope's safety padding. */
        double minimumForward() {
            var axis = pose.rotation().inverse().apply(new UiVector3(0,0,1));
            double extent = (Math.abs(axis.right()) * size.width() + Math.abs(axis.up()) * size.height()
                    + Math.abs(axis.forward()) * depth) * pose.scale() / 2;
            return pose.forward() - extent;
        }
    }

    record SceneBounds(List<FloatingMenuPoint> probes, FloatingMenuFraming framing, List<Surface> surfaces,
                       Optional<FloatingMenuPose> titlePose) {
        SceneBounds(List<FloatingMenuPoint> probes, FloatingMenuFraming framing, List<Surface> surfaces) {
            this(probes, framing, surfaces, Optional.empty());
        }
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
            titlePose = Objects.requireNonNull(titlePose);
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
                maximum = limitScale(maximum, horizontalExtent, distance * horizontalTangent);
                maximum = limitScale(maximum, verticalExtent, distance * verticalTangent);
                maximum = limitScale(maximum, point.forward(), distance);
            }
            return maximum;
        }

        /** Fit current content at a retained position, without changing the session's reference scale. */
        double readableScale(ReadingFrame reference, FloatingMenuFraming reading) {
            double maximum = reference.spatialScale();
            double horizontal = reading.horizontalTangent(), vertical = reading.verticalTangent();
            for (FloatingMenuPoint point : probes) {
                maximum = limitScale(maximum, Math.abs(point.right()) + point.forward() * horizontal,
                        reference.distance() * horizontal);
                maximum = limitScale(maximum, point.up() + point.forward() * vertical,
                        reference.distance() * vertical - reference.verticalOffset());
                maximum = limitScale(maximum, -point.up() + point.forward() * vertical,
                        reference.distance() * vertical + reference.verticalOffset());
                maximum = limitScale(maximum, point.forward(), reference.distance());
            }
            return maximum;
        }

        private static double limitScale(double maximum, double extent, double available) {
            return extent > 1.0E-6 ? Math.min(maximum, available / extent) : maximum;
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
                            new FloatingMenuPose(new FloatingMenuPoint(surface.pose().right() * factor,
                                    surface.pose().up() * factor, surface.pose().forward() * factor),
                                    surface.pose().yawDegrees(), surface.pose().pitchDegrees(), surface.pose().rollDegrees(), surface.pose().scale()),
                            new FloatingMenuSize(surface.size().width() * factor, surface.size().height() * factor), surface.depth() * factor))
                    .toList(), titlePose.map(pose -> new FloatingMenuPose(new FloatingMenuPoint(pose.right() * factor, pose.up() * factor,
                            pose.forward() * factor), pose.yawDegrees(), pose.pitchDegrees(), pose.rollDegrees(), pose.scale())));
        }
    }

    private record Candidate(double yawDegrees, double verticalOffset) {
    }

    private record Selection(Anchor reference, double score) {
    }
}
