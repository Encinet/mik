package org.encinet.mik.shared.menu.runtime;

import java.util.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;

/** Projects rigid layout planes first, then composes their owned spatial groups exactly once. */
final class ComponentSceneProjector {
    private static final double UNIT = UiSceneContent.WORLD_PER_UI;
    private static final double MAX_YAW = 28, MAX_PITCH = 22;
    private static final double ORBIT_RADIUS = 4.6;
    private final MenuScene scene;
    private final UiFrame frame;
    private final FloatingMenuFieldOfView fov;
    private final double scale;
    private final UiFrame.Panel readingPanel;
    private final UiLayout.Position presentationCenter;
    private final double readingRowLeft, readingRowRight, readingColumnTop, readingColumnBottom;
    private final Map<String, UiFrame.Panel> panels = new HashMap<>();
    private final Map<String, Plane> planes = new HashMap<>();
    private final Map<String, UiTransform> transforms = new HashMap<>();
    private final Map<String, Double> elevations = new HashMap<>();
    private final Map<String, UiFrame.Group> groups = new HashMap<>();

    ComponentSceneProjector(MenuScene scene, UiFrame frame, FloatingMenuFieldOfView fov, double scale) {
        this.scene = scene; this.frame = frame; this.fov = fov; this.scale = scale;
        presentationCenter = scene.spatialFrame() == FloatingMenuSpatialFrame.IN_FRONT
                ? new UiLayout.Position(frame.size().width() / 2, frame.size().height() / 2) : frame.focus();
        var readingPanels = frame.panels().stream().filter(panel -> panel.facing() != UiSurfaceFacing.INHERIT)
                .filter(panel -> panel.size().width() > 0 && panel.size().height() > 0)
                .filter(panel -> panel.path().equals(frame.readingAnchor().path())
                        || panel.path().startsWith(frame.readingAnchor().path() + "/")).toList();
        readingPanel = readingPanels.stream()
                .filter(panel -> frame.focus().x() >= panel.x() && frame.focus().x() <= panel.x() + panel.size().width()
                        && frame.focus().y() >= panel.y() && frame.focus().y() <= panel.y() + panel.size().height())
                .min(Comparator.comparingDouble(panel -> panel.size().width() * panel.size().height()))
                .orElseGet(() -> frame.readingAnchor().declared() ? readingPanels.stream()
                        .min(Comparator.comparingDouble(UiFrame.Panel::y).thenComparingDouble(UiFrame.Panel::x)).orElse(null) : null);
        frame.panels().stream().filter(panel -> panel.facing() != UiSurfaceFacing.INHERIT)
                .forEach(panel -> panels.put(panel.path(), panel));
        var row = new DoubleSummaryStatistics();
        var column = new DoubleSummaryStatistics();
        if (readingPanel != null) for (var panel : panels.values()) {
            if (overlaps(panel.y(), panel.size().height(), readingPanel.y(), readingPanel.size().height())) {
                row.accept(panel.x()); row.accept(panel.x() + panel.size().width());
            }
            if (overlaps(panel.x(), panel.size().width(), readingPanel.x(), readingPanel.size().width())) {
                column.accept(panel.y()); column.accept(panel.y() + panel.size().height());
            }
        }
        readingRowLeft = row.getMin(); readingRowRight = row.getMax();
        readingColumnTop = column.getMin(); readingColumnBottom = column.getMax();
        var operations = new TreeMap<String, UiFrame.Spatial>(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        frame.panels().forEach(panel -> operations.put(panel.path(), panel));
        frame.groups().forEach(group -> { operations.put(group.path(), group); groups.put(group.path(), group); });
        operations.values().forEach(this::compile);
    }

    private void compile(UiFrame.Spatial operation) {
        String path = operation.path();
        try {
            Plane plane = plane(path);
            UiTransform local = switch (operation) {
                case UiFrame.Panel panel -> new UiTransform(plane.rotation.apply(new UiVector3(0, 0, panel.elevation().value() * UNIT)), UiRotation.IDENTITY, 1);
                case UiFrame.Group group -> groupTransform(plane, group);
            };
            double elevation = ancestor(elevations, parent(path), 0.0)
                    + (operation instanceof UiFrame.Panel panel ? panel.elevation().value() : 0);
            if (elevation > 64) throw new UiOverflowException("Nested surface elevation exceeds 64 UI units");
            UiTransform combined = ancestor(transforms, parent(path), UiTransform.IDENTITY).compose(local);
            if (combined.scale() < 1.0 / 64 || combined.scale() > 64)
                throw new IllegalArgumentException("Inherited spatial scale exceeds 1/64–64");
            transforms.put(path, combined);
            elevations.put(path, elevation);
        } catch (IllegalArgumentException error) { throw invalid(path, error); }
    }

    private UiTransform groupTransform(Plane plane, UiFrame.Group group) {
        UiVector3 pivot = plane.point(group.x() + group.size().width() * group.pivot().xFraction(),
                group.y() + group.size().height() * group.pivot().yFraction(), group.pivot().depth());
        UiRotation rotation = plane.rotation.compose(group.transform().rotation()).compose(plane.rotation.inverse());
        return new UiTransform(plane.rotation.apply(group.transform().translation().multiply(UNIT)), rotation,
                group.transform().scale()).around(pivot);
    }

    FloatingMenuPose project(String path, double x, double y, UiSize size) {
        try {
            Plane plane = plane(path);
            UiTransform transform = ancestor(transforms, path, UiTransform.IDENTITY);
            UiVector3 point = transform.apply(plane.point(x + size.width() / 2, y + size.height() / 2, 0));
            if (Math.max(Math.abs(point.right()), Math.max(Math.abs(point.up()), Math.abs(point.forward())))
                    + Math.hypot(size.width(), size.height()) * transform.scale() * UNIT / 2 > 8192 * UNIT)
                throw new IllegalArgumentException("Spatial coordinate budget exceeded");
            var angles = transform.rotation().compose(plane.rotation).angles();
            double yaw = plane.yaw + Math.IEEEremainder(angles.yaw() - plane.yaw, 360);
            return new FloatingMenuPose(new FloatingMenuPoint(point.right(), point.up(), point.forward()), yaw, angles.pitch(), angles.roll(), transform.scale());
        } catch (IllegalArgumentException error) { throw invalid(path, error); }
    }

    private static IllegalArgumentException invalid(String path, IllegalArgumentException error) {
        String message = "Invalid spatial geometry at " + path + ": " + error.getMessage();
        return error instanceof UiOverflowException ? new UiOverflowException(message, error) : new IllegalArgumentException(message, error);
    }

    boolean transformed(String path) { return ancestor(groups, path, null) != null || ancestor(panels, path, null) != null; }

    private Plane plane(String path) {
        UiFrame.Panel panel = ancestor(panels, path, null);
        String key = panel == null ? "" : panel.path();
        return planes.computeIfAbsent(key, _ -> panel == null || panel.facing() == UiSurfaceFacing.FORWARD
                ? referencePlane(presentationCenter.x()) : facingPlane(panel));
    }

    private Plane facingPlane(UiFrame.Panel panel) {
        if (panel == readingPanel && frame.readingAnchor().declared()) return referencePlane(presentationCenter.x());
        double centerX = panel.x() + panel.size().width() / 2;
        double centerY = panel.y() + panel.size().height() / 2;
        double right = (centerX - presentationCenter.x()) * UNIT;
        double up = (presentationCenter.y() - centerY) * UNIT;
        FoldAxis axis = switch (panel.facing()) {
            case HORIZONTAL -> FoldAxis.HORIZONTAL;
            case VERTICAL -> FoldAxis.VERTICAL;
            default -> foldAxis(panel);
        };
        Plane base = referencePlane(scene.spatialFrame().viewerCentered() && axis == FoldAxis.HORIZONTAL ? centerX : presentationCenter.x());
        double distance = scene.spatialFrame().viewerCentered() ? Math.hypot(base.center.right(), base.center.forward())
                : MenuViewportLayout.referenceDistance(fov, scale);
        double yaw = 0, pitch = 0;
        if (axis == FoldAxis.HORIZONTAL) {
            yaw = scene.spatialFrame().viewerCentered() ? base.yaw
                    : Math.clamp(Math.toDegrees(Math.atan2(right, distance)), -MAX_YAW, MAX_YAW);
        } else {
            pitch = Math.clamp(Math.toDegrees(Math.atan2(up, distance)), -MAX_PITCH, MAX_PITCH);
        }
        // Keep the adjoining edge in place. Rotating about the center can put a side panel in front of the reading area.
        double pivotX = Math.clamp(presentationCenter.x(), panel.x(), panel.x() + panel.size().width());
        double pivotY = Math.clamp(presentationCenter.y(), panel.y(), panel.y() + panel.size().height());
        return new Plane(base.point(pivotX, pivotY, 0), UiRotation.degrees(yaw, pitch, 0), pivotX, pivotY, yaw);
    }

    private enum FoldAxis { HORIZONTAL, VERTICAL }

    private FoldAxis foldAxis(UiFrame.Panel panel) {
        if (readingPanel != null) {
            // Compare whole rectangles: a short, top-aligned sidebar is still beside a tall reader.
            boolean beside = panel.x() + panel.size().width() <= readingPanel.x() + 1e-6
                    || panel.x() >= readingPanel.x() + readingPanel.size().width() - 1e-6;
            boolean aboveOrBelow = panel.y() + panel.size().height() <= readingPanel.y() + 1e-6
                    || panel.y() >= readingPanel.y() + readingPanel.size().height() - 1e-6;
            // A footer can sit under a row of reading panes without overlapping the focused pane itself.
            if (aboveOrBelow && overlaps(panel.x(), panel.size().width(), readingRowLeft, readingRowRight - readingRowLeft))
                return FoldAxis.VERTICAL;
            if (beside && overlaps(panel.y(), panel.size().height(), readingColumnTop, readingColumnBottom - readingColumnTop))
                return FoldAxis.HORIZONTAL;
        }
        // Corners and layouts without an owning reading panel use the dominant gap to the focus.
        double horizontal = Math.abs(presentationCenter.x() - Math.clamp(presentationCenter.x(), panel.x(), panel.x() + panel.size().width()));
        double vertical = Math.abs(presentationCenter.y() - Math.clamp(presentationCenter.y(), panel.y(), panel.y() + panel.size().height()));
        if (horizontal == 0 && vertical == 0) {
            horizontal = Math.abs(panel.x() + panel.size().width() / 2 - presentationCenter.x());
            vertical = Math.abs(panel.y() + panel.size().height() / 2 - presentationCenter.y());
        }
        return horizontal >= vertical ? FoldAxis.HORIZONTAL : FoldAxis.VERTICAL;
    }

    private static boolean overlaps(double start, double size, double otherStart, double otherSize) {
        return start + size > otherStart + 1e-6 && otherStart + otherSize > start + 1e-6;
    }

    private Plane referencePlane(double centerX) {
        double right = (centerX - presentationCenter.x()) * UNIT;
        FloatingMenuPose pose = FloatingMenuPose.at(new FloatingMenuPoint(right, 0, 0));
        if (scene.spatialFrame().viewerCentered()) {
            double angle = right / ORBIT_RADIUS;
            pose = FloatingMenuViewProjection.pose(scene.spatialFrame(), fov, scale,
                    FloatingMenuPose.oriented(new FloatingMenuPoint(ORBIT_RADIUS * Math.sin(angle), 0,
                            -ORBIT_RADIUS * Math.cos(angle)), Math.toDegrees(angle), 0));
        }
        return new Plane(new UiVector3(pose.right(), pose.up(), pose.forward()), pose.rotation(), centerX,
                presentationCenter.y(), pose.yawDegrees());
    }

    private final class Plane {
        final UiVector3 center;
        final UiRotation rotation;
        final double centerX, centerY;
        final double yaw;
        Plane(UiVector3 center, UiRotation rotation, double centerX, double centerY, double yaw) {
            this.center = center; this.rotation = rotation; this.centerX = centerX; this.centerY = centerY; this.yaw = yaw;
        }
        UiVector3 point(double x, double y, double depth) {
            return center.add(rotation.apply(new UiVector3((x - centerX) * UNIT, (centerY - y) * UNIT, depth * UNIT)));
        }
    }
    private static String parent(String path) { int index = path.lastIndexOf('/'); return index < 0 ? "" : path.substring(0, index); }
    private static <T> T ancestor(Map<String, T> values, String path, T fallback) {
        for (String current = path; !current.isEmpty(); current = parent(current)) {
            T value = values.get(current);
            if (value != null) return value;
        }
        return fallback;
    }
}
