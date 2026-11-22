package org.encinet.mik.shared.menu.runtime.space;

import org.encinet.mik.shared.menu.runtime.layout.FloatingMenuNodeGeometry;
import org.encinet.mik.shared.menu.runtime.layout.FloatingMenuViewProjection;
import org.encinet.mik.shared.menu.runtime.layout.MenuViewportLayout;

import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuFraming;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuPlacement;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuSize;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.ui.UiVector3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Finds a visible, reachable frame for a complete floating-menu scene. */
public final class FloatingMenuAnchorResolver {
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
    static final double SURFACE_CLEARANCE = 0.08;

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
    public static Anchor reference(Location eye, Vector requestedForward, SceneBounds bounds,
                            double interfaceScale, FloatingMenuFieldOfView fieldOfView) {
        return reference(eye, requestedForward, bounds, interfaceScale, fieldOfView, new FloatingMenuWorldSpace(eye.getWorld()));
    }

    public static Anchor reference(Location eye, Vector requestedForward, SceneBounds bounds,
                            double interfaceScale, FloatingMenuFieldOfView fieldOfView, MenuCollisionSpace space) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(bounds, "bounds");
        FloatingMenuFraming reading = Objects.requireNonNull(fieldOfView, "fieldOfView").readingFraming();
        double preferredDistance = MenuViewportLayout.distance(fieldOfView);
        if (!Double.isFinite(interfaceScale) || interfaceScale <= 0.0) {
            throw new IllegalArgumentException("Interface scale must be positive and finite");
        }
        Anchor centered = centeredReference(eye, requestedForward, bounds, interfaceScale, fieldOfView, false);
        Vector baseline = centered.forward();
        double centeredFactor = fitFactor(eye, centered.origin(), baseline, centered.right(), centered.spatialScale(), bounds, space);
        if (centerDominates(centered, centeredFactor, false)) return centered;
        Selection best = centeredFactor <= 1.0E-6 ? null : new Selection(centered, preferredDistance * centeredFactor);
        var candidates = new ArrayList<>(CANDIDATES.subList(1, CANDIDATES.size()));
        double nominalScale = centered.spatialScale();
        // A complete page can extend below the eye or towards a ceiling. Fit its full height
        // above the real floor (or below a ceiling) before contracting the whole page towards the eye.
        for (int direction : new int[]{-1,1}) {
            double edge = bounds.probes().stream().mapToDouble(point -> direction * point.up()).max().orElse(0);
            if (edge <= 0) continue;
            double hit = space.firstHit(eye,new Vector(0,direction,0), Math.min(preferredDistance, edge * nominalScale + SURFACE_CLEARANCE));
            if (!Double.isFinite(hit)) continue;
            double offset = -direction * Math.max(0,edge - Math.max(0,hit - SURFACE_CLEARANCE) / nominalScale);
            if (Math.abs(offset) > 1e-6) candidates.add(new Candidate(0,offset));
        }
        for (Candidate candidate : candidates) {
            if (best != null && ergonomicScore(candidate, preferredDistance) <= best.score()) continue;
            Vector forward = rotateHorizontal(baseline, candidate.yawDegrees());
            Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
            Anchor reference = referenceCandidate(eye, bounds, interfaceScale, reading,
                    preferredDistance, candidate, forward, right);
            double factor = fitFactor(eye, reference.origin(), forward, right, reference.spatialScale(), bounds, space);
            if (factor <= 1e-6) continue;
            double distance = preferredDistance * factor;
            Selection selection = new Selection(reference, ergonomicScore(candidate, distance));
            if (best == null || selection.score() > best.score()) best = selection;
        }
        if (best == null) throw new MenuSpaceUnavailableException();
        return best.reference();
    }

    public static Anchor centeredReference(Location eye, Vector heading, SceneBounds bounds, double interfaceScale,
                                           FloatingMenuFieldOfView fov, boolean arc) {
        var forward = horizontalUnit(heading);
        var right = new Vector(-forward.getZ(), 0, forward.getX());
        if (!arc) return referenceCandidate(eye, bounds, interfaceScale, fov.readingFraming(), MenuViewportLayout.distance(fov),
                CANDIDATES.getFirst(), forward, right);
        double scale = MenuViewportLayout.scale(interfaceScale);
        double distance = frontArcDepth(bounds, fov, scale) * scale;
        return new Anchor(eye.clone().add(forward.clone().multiply(distance)), forward, right, scale, distance, 0);
    }

    public static boolean centerDominates(Anchor centered, double factor, boolean arc) {
        return arc ? factor >= 1 - 1.0E-6 : centered.distance() * factor >= centered.distance() - 1.0E-6;
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
    public static Anchor fit(Location eye, Anchor reference, SceneBounds bounds) {
        return fit(eye, reference, bounds, new FloatingMenuWorldSpace(eye.getWorld()));
    }

    public static Anchor fit(Location eye, Anchor reference, SceneBounds bounds, MenuCollisionSpace space) {
        double factor = fitFactor(eye, reference.origin(), reference.forward(), reference.right(), reference.spatialScale(), bounds, space);
        if (factor <= 1.0E-6) throw new MenuSpaceUnavailableException();
        return reference.contract(eye, factor);
    }

    public static Anchor frontArcReference(Location eye, Vector requestedForward, double interfaceScale, SceneBounds bounds,
                                    FloatingMenuFieldOfView fieldOfView) {
        return frontArcReference(eye, requestedForward, interfaceScale, bounds, fieldOfView,
                new FloatingMenuWorldSpace(eye.getWorld()));
    }

    public static Anchor frontArcReference(Location eye, Vector requestedForward, double interfaceScale, SceneBounds bounds,
                                    FloatingMenuFieldOfView fieldOfView, MenuCollisionSpace space) {
        Objects.requireNonNull(eye, "eye");
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(fieldOfView, "fieldOfView");
        if (!Double.isFinite(interfaceScale) || interfaceScale <= 0) {
            throw new IllegalArgumentException("Invalid front-arc anchor");
        }
        double scale = MenuViewportLayout.scale(interfaceScale);
        double depth = frontArcDepth(bounds, fieldOfView, scale);
        Vector baseline = horizontalUnit(requestedForward);
        Anchor best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double yaw : new double[]{0, -16, 16, -28, 28}) {
            if (1 - Math.abs(yaw) * 0.002 <= bestScore) continue;
            Vector forward = rotateHorizontal(baseline, yaw);
            Vector right = new Vector(-forward.getZ(), 0, forward.getX());
            Location origin = eye.clone().add(forward.clone().multiply(depth * scale));
            double factor = fitFactor(eye, origin, forward, right, scale, bounds, space);
            double score = factor - Math.abs(yaw) * 0.002;
            if (score > bestScore) {
                bestScore = score;
                best = new Anchor(origin, forward, right, scale, depth * scale, yaw);
            }
            if (yaw == 0 && centerDominates(best, factor, true)) break;
        }
        return Objects.requireNonNull(best);
    }

    private static double frontArcDepth(SceneBounds bounds, FloatingMenuFieldOfView fov, double scale) {
        double depth = MenuViewportLayout.distance(fov) / scale;
        double verticalTangent = fov.readingFraming().verticalTangent();
        for (FloatingMenuPoint point : bounds.probes()) {
            double radius = Math.abs(point.up()) / verticalTangent;
            depth = Math.max(depth, Math.sqrt(Math.max(0, radius * radius - point.right() * point.right())) + point.forward());
        }
        return depth;
    }

    public static double fitFactor(Location eye, Location origin, Vector forward, Vector right,
                              double scale, SceneBounds bounds, MenuCollisionSpace space) {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
        Vector offset = origin.toVector().subtract(eye.toVector());
        double factor = 1;
        for (FloatingMenuPoint point : bounds.probes()) {
            Vector target = offset.clone().add(localVector(point, forward, right).multiply(scale));
            double distance = target.length();
            if (distance < 1.0E-8) continue;
            double hit = space.firstHit(eye, target.clone().multiply(1 / distance), distance * factor + SURFACE_CLEARANCE);
            double clearance = Math.min(SURFACE_CLEARANCE, hit * 0.2);
            factor = Math.min(factor, Math.max(0, (hit - clearance) / distance));
            if (factor <= 1.0E-6) return 0;
        }
        double surfaceFactor = 1;
        for (Surface surface : bounds.surfaces()) {
            var panel = panel(offset, forward, right, scale, surface);
            surfaceFactor = Math.min(surfaceFactor, space.limitSurface(eye,
                    eye.toVector().add(panel.center().multiply(factor)), panel.right(), panel.up(),
                    panel.halfWidth() * factor, panel.halfHeight() * factor, panel.halfDepth() * factor));
            if (surfaceFactor <= 1.0E-6) return 0;
        }
        return factor * surfaceFactor;
    }

    private record Panel(Vector center, Vector right, Vector up, double halfWidth, double halfHeight, double halfDepth) { }

    private static Panel panel(Vector offset, Vector forward, Vector right, double scale, Surface surface) {
        var pose = surface.pose();
        var rotation = pose.rotation();
        var across = rotation.apply(new UiVector3(1, 0, 0));
        var above = rotation.apply(new UiVector3(0, 1, 0));
        double localScale = pose.scale() * scale;
        return new Panel(offset.clone().add(localVector(pose.point(), forward, right).multiply(scale)),
                localVector(new FloatingMenuPoint(across.right(), across.up(), across.forward()), forward, right),
                localVector(new FloatingMenuPoint(above.right(), above.up(), above.forward()), forward, right),
                surface.size().width() * localScale / 2, surface.size().height() * localScale / 2,
                Math.max(0.14, surface.depth() / 2) * localScale + SURFACE_CLEARANCE);
    }

    public static BoundingBox samplingBounds(Anchor relative, SceneBounds bounds) {
        var region = new BoundingBox();
        var offset = relative.origin().toVector();
        var forward = relative.forward();
        var right = relative.right();
        double scale = relative.spatialScale();
        for (var point : bounds.probes()) region.union(offset.clone().add(localVector(point, forward, right).multiply(scale)));
        for (var surface : bounds.surfaces()) {
            var panel = panel(offset, forward, right, scale, surface);
            region.union(MenuOcclusionGeometry.surfaceBounds(panel.center(), panel.right(), panel.up(),
                    panel.halfWidth(), panel.halfHeight(), panel.halfDepth()));
        }
        return region.expand(SURFACE_CLEARANCE + 1.0E-6);
    }

    public static BoundingBox searchBounds(SceneBounds bounds, FloatingMenuFieldOfView fov, double interfaceScale,
                                    Vector heading, boolean arc) {
        double scale = MenuViewportLayout.scale(interfaceScale);
        double distance = MenuViewportLayout.distance(fov);
        double depth = arc ? frontArcDepth(bounds, fov, scale) * scale : distance;
        var eye = new Location(null, 0, 0, 0);
        var region = new BoundingBox();
        for (double yaw : new double[]{0, -16, 16, -28, 28}) {
            var facing = new ReadingFrame(scale, depth, 0, yaw).at(eye, heading);
            var base = samplingBounds(facing, bounds);
            if (!arc) {
                var margin = 2 * SURFACE_CLEARANCE + 1.0E-6;
                base.union(BoundingBox.of(facing.origin(), margin, margin, margin));
                var up = Math.max(.55 * scale, -base.getMinY());
                var down = Math.max(.40 * scale, base.getMaxY());
                base.resize(base.getMinX(), base.getMinY() - down, base.getMinZ(),
                        base.getMaxX(), base.getMaxY() + up, base.getMaxZ());
            }
            region.union(base);
        }
        return region;
    }

    private static Vector localVector(FloatingMenuPoint point, Vector forward, Vector right) {
        return right.clone().multiply(point.right()).add(new Vector(0, point.up(), 0))
                .add(forward.clone().multiply(-point.forward()));
    }

    /** Keeps an actual circular scene centered on the viewer's opening position. */
    public static Anchor aroundViewer(Location eye, Vector requestedForward,
                               double interfaceScale) {
        return aroundViewer(eye, requestedForward, interfaceScale, List.of());
    }

    public static Anchor aroundViewer(Location eye, Vector requestedForward,
                               double interfaceScale, MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> poses,
                               java.util.Map<String, FloatingMenuSize> sizes, Optional<FloatingMenuPose> title) {
        return aroundViewer(eye, requestedForward, interfaceScale, definition, poses, sizes, title,
                new FloatingMenuWorldSpace(eye.getWorld()));
    }

    public static Anchor aroundViewer(Location eye, Vector requestedForward, double interfaceScale, MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> poses, java.util.Map<String, FloatingMenuSize> sizes,
                               Optional<FloatingMenuPose> title, MenuCollisionSpace space) {
        return aroundViewer(eye, requestedForward, interfaceScale, orbitalBounds(definition, poses, sizes, title), space);
    }

    public static SceneBounds orbitalBounds(MenuScene definition, java.util.Map<String, FloatingMenuPose> poses,
                                    java.util.Map<String, FloatingMenuSize> sizes, Optional<FloatingMenuPose> title) {
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
        if (essential.isEmpty()) essential.add(FloatingMenuPoint.ORIGIN);
        return new SceneBounds(essential, FloatingMenuFraming.COMFORTABLE, surfaces);
    }

    private static Anchor aroundViewer(Location eye, Vector requestedForward,
                                       double interfaceScale,
                                       List<FloatingMenuPoint> essential) {
        var probes = essential.isEmpty() ? List.of(FloatingMenuPoint.ORIGIN) : essential;
        return aroundViewer(eye, requestedForward, interfaceScale, new SceneBounds(probes), new FloatingMenuWorldSpace(eye.getWorld()));
    }

    public static Anchor aroundViewer(Location eye, Vector requestedForward, double interfaceScale,
                               SceneBounds bounds, MenuCollisionSpace space) {
        Objects.requireNonNull(eye, "eye");
        if (!Double.isFinite(interfaceScale) || interfaceScale <= 0.0) {
            throw new IllegalArgumentException("Invalid player-centered menu anchor");
        }
        Vector forward = horizontalUnit(requestedForward);
        Vector right = new Vector(-forward.getZ(), 0.0, forward.getX());
        double fittedScale = interfaceScale;
        for (FloatingMenuPoint point : bounds.probes()) {
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
        fittedScale *= fitFactor(eye, eye, forward, right, fittedScale, bounds, space);
        if (fittedScale <= 1.0E-6) throw new MenuSpaceUnavailableException();
        return new Anchor(eye, forward, right, fittedScale, 0.0, 0.0);
    }

    public static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes) {
        return measure(definition, layoutPoses, layoutSizes, 1.0);
    }

    public static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale) {
        return measure(definition, layoutPoses, layoutSizes, typographyScale, FloatingMenuFieldOfView.DEFAULT);
    }

    public static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale, FloatingMenuFieldOfView fieldOfView) {
        return measure(definition, layoutPoses, layoutSizes, typographyScale, fieldOfView, 1);
    }

    public static SceneBounds measure(MenuScene definition,
                               java.util.Map<String, FloatingMenuPose> layoutPoses,
                               java.util.Map<String, FloatingMenuSize> layoutSizes,
                               double typographyScale, FloatingMenuFieldOfView fieldOfView, double interfaceScale) {
        return measure(definition, layoutPoses, layoutSizes, typographyScale, fieldOfView, interfaceScale, List.of());
    }

    public static SceneBounds measure(MenuScene definition,
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
    public static Surface decorationSurface(FloatingMenuDecoration decoration, FloatingMenuPose pose, double typographyScale) {
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

    public static FloatingMenuSize titleSize(double typographyScale) {
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

    public record Anchor(Location origin, Vector forward, Vector right,
                  double spatialScale, double distance, double yawOffsetDegrees) {
        public Anchor {
            origin = Objects.requireNonNull(origin, "origin").clone();
            forward = Objects.requireNonNull(forward, "forward").clone();
            right = Objects.requireNonNull(right, "right").clone();
        }

        @Override public Location origin() { return origin.clone(); }
        @Override public Vector forward() { return forward.clone(); }
        @Override public Vector right() { return right.clone(); }

        public Anchor contract(Location eye, double factor) {
            if (!Double.isFinite(factor) || factor <= 0) throw new MenuSpaceUnavailableException();
            factor = Math.min(factor, 1);
            return new Anchor(eye.clone().add(origin.toVector().subtract(eye.toVector()).multiply(factor)),
                    forward, right, spatialScale * factor, distance * factor, yawOffsetDegrees);
        }

        public ReadingFrame relativeTo(Location eye) {
            return new ReadingFrame(spatialScale, distance, origin.getY() - eye.getY(), yawOffsetDegrees);
        }
    }

    /** Eye-relative reading geometry survives content updates, movement and obstacle recovery. */
    public record ReadingFrame(double spatialScale, double distance, double verticalOffset, double yawOffsetDegrees) {
        public ReadingFrame fittedTo(SceneBounds bounds, FloatingMenuFraming reading) {
            double fittedScale = bounds.readableScale(this, reading);
            if (fittedScale > 1e-6) return new ReadingFrame(fittedScale, distance, verticalOffset, yawOffsetDegrees);
            return new ReadingFrame(Math.min(spatialScale, bounds.readableScale(distance, 0, reading)), distance, 0, yawOffsetDegrees);
        }

        public Anchor at(Location eye, Vector requestedForward) {
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

    public record Surface(FloatingMenuPose pose, FloatingMenuSize size, double depth) {
        public Surface(FloatingMenuPose pose, FloatingMenuSize size) { this(pose, size, 0); }

        /** Physical rear extent, independent of the viewing envelope's safety padding. */
        public double minimumForward() {
            var axis = pose.rotation().inverse().apply(new UiVector3(0,0,1));
            double extent = (Math.abs(axis.right()) * size.width() + Math.abs(axis.up()) * size.height()
                    + Math.abs(axis.forward()) * depth) * pose.scale() / 2;
            return pose.forward() - extent;
        }
    }

    public record SceneBounds(List<FloatingMenuPoint> probes, FloatingMenuFraming framing, List<Surface> surfaces,
                       Optional<FloatingMenuPose> titlePose) {
        public SceneBounds(List<FloatingMenuPoint> probes, FloatingMenuFraming framing, List<Surface> surfaces) {
            this(probes, framing, surfaces, Optional.empty());
        }
        public SceneBounds(List<FloatingMenuPoint> probes, FloatingMenuFraming framing) {
            this(probes, framing, List.of());
        }
        public SceneBounds(List<FloatingMenuPoint> probes) {
            this(probes, FloatingMenuFraming.COMFORTABLE);
        }

        public SceneBounds {
            probes = List.copyOf(new LinkedHashSet<>(probes));
            framing = Objects.requireNonNull(framing, "framing");
            surfaces = List.copyOf(new LinkedHashSet<>(surfaces));
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

        public double readableScale(double distance) {
            return readableScale(distance, 0);
        }

        public double readableScale(double distance, double verticalOffset) {
            return readableScale(distance, verticalOffset, MAX_READING_FRAMING);
        }

        public double readableScale(double distance, double verticalOffset, FloatingMenuFraming reading) {
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
        public double readableScale(ReadingFrame reference, FloatingMenuFraming reading) {
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

        public FloatingMenuFraming interactionFraming(double distance, double scale) {
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

        public SceneBounds scaled(double factor) {
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
