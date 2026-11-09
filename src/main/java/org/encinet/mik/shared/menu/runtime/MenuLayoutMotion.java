package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.DoubleFunction;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** Validates remaining layout motion without changing target measurement, pagination or the reading frame. */
final class MenuLayoutMotion {
    static final int MAX_SURFACES = 128;
    private static final double ARC_STEP = Math.PI / 12;
    private SceneGeometry checkedLayout;
    private FloatingMenuAnchorResolver.Anchor checkedAnchor;
    private UiVector3 checkedEye;
    private long checkedEpoch;
    private boolean allowed;

    record Move(FloatingMenuPose from, FloatingMenuPose to, FloatingMenuSize size, double depth, MenuPoseTransition.Path path) {
        Move {
            java.util.Objects.requireNonNull(from);
            java.util.Objects.requireNonNull(to);
            java.util.Objects.requireNonNull(size);
            java.util.Objects.requireNonNull(path);
            if (!Double.isFinite(depth) || depth < 0) throw new IllegalArgumentException("Invalid motion depth");
        }
    }

    void clear() { checkedLayout = null; checkedAnchor = null; checkedEye = null; }

    /** Static frames do no additional world work; a new snapshot also rechecks the remaining path of an active transition. */
    boolean permits(SceneGeometry layout, Location eye, FloatingMenuAnchorResolver.Anchor anchor, long epoch,
                    Supplier<List<Move>> sample, DoubleFunction<FloatingMenuWorldSpace> world) {
        var point = MenuSurfaceAttachment.point(eye.toVector());
        if (checkedLayout == layout && anchor.equals(checkedAnchor) && point.equals(checkedEye) && checkedEpoch == epoch) return allowed;
        var moves = sample.get();
        allowed = moves.stream().allMatch(move -> move.from().equals(move.to())) || envelope(moves).map(bounds -> {
            double range = range(eye, anchor, bounds);
            return Double.isFinite(range) && range <= FloatingMenuWorldSpace.MAX_TRACE_DISTANCE
                    && FloatingMenuAnchorResolver.fitFactor(eye, anchor.origin(), anchor.forward(), anchor.right(),
                    anchor.spatialScale(), bounds, world.apply(range)) >= 1 - 1e-9;
        }).orElse(false);
        checkedLayout = layout; checkedAnchor = anchor; checkedEye = point; checkedEpoch = epoch;
        return allowed;
    }

    /** Conservative boxes cover the entire path, including rotation and scale between samples. */
    static Optional<FloatingMenuAnchorResolver.SceneBounds> envelope(List<Move> moves) {
        var surfaces = new ArrayList<FloatingMenuAnchorResolver.Surface>();
        for (var move : moves) {
            if (move.from().equals(move.to())) continue;
            double radius = Math.hypot(Math.hypot(move.size().width(), move.size().height()), Math.max(.28, move.depth()))
                    * Math.max(move.from().scale(), move.to().scale()) / 2;
            if (!Double.isFinite(radius) || radius <= 0 || !Double.isFinite(Math.hypot(move.from().right(), move.from().forward()))
                    || !Double.isFinite(Math.hypot(move.to().right(), move.to().forward()))) return Optional.empty();
            if (move.path() == MenuPoseTransition.Path.LINEAR) {
                if (!append(surfaces, List.of(move.from().point(), move.to().point()), extent(move))) return Optional.empty();
                continue;
            }
            double start = move.path() == MenuPoseTransition.Path.ORBIT ? Math.toRadians(move.from().yawDegrees())
                    : Math.atan2(move.from().right(), -move.from().forward());
            double delta = move.path() == MenuPoseTransition.Path.ORBIT ? Math.toRadians(move.to().yawDegrees() - move.from().yawDegrees())
                    : Math.IEEEremainder(Math.atan2(move.to().right(), -move.to().forward()) - start, 2 * Math.PI);
            if (!Double.isFinite(delta)) return Optional.empty();
            int parts = (int) Math.max(1, Math.min(24, Math.ceil(Math.abs(delta) / ARC_STEP)));
            double radiusFrom = Math.hypot(move.from().right(), move.from().forward());
            double radiusTo = Math.hypot(move.to().right(), move.to().forward());
            var extent = sphere(move);
            for (int part = 0; part < parts; part++) {
                double a = (double) part / parts, b = (double) (part + 1) / parts;
                double r1 = interpolate(radiusFrom, radiusTo, a), r2 = interpolate(radiusFrom, radiusTo, b);
                var sine = trigRange(start + delta * a, start + delta * b, false);
                var cosine = trigRange(start + delta * a, start + delta * b, true);
                double[] x = product(sine, r1, r2), z = product(cosine, -r1, -r2);
                double y1 = interpolate(move.from().up(), move.to().up(), a), y2 = interpolate(move.from().up(), move.to().up(), b);
                // Exact endpoints also cover a retarget or a degenerate orbit that immediately adopts its target.
                var points = new ArrayList<FloatingMenuPoint>(List.of(
                        new FloatingMenuPoint(x[0], Math.min(y1, y2), z[0]),
                        new FloatingMenuPoint(x[1], Math.max(y1, y2), z[1])));
                if (part == 0) points.add(move.from().point());
                if (part == parts - 1) points.add(move.to().point());
                if (!append(surfaces, points, extent)) return Optional.empty();
            }
        }
        if (surfaces.isEmpty()) return Optional.empty();
        var probes = new ArrayList<FloatingMenuPoint>(surfaces.size() * 8);
        for (var surface : surfaces) for (int corner = 0; corner < 8; corner++) {
            probes.add(surface.pose().offset((corner & 1) == 0 ? -surface.size().width() / 2 : surface.size().width() / 2,
                    (corner & 2) == 0 ? -surface.size().height() / 2 : surface.size().height() / 2,
                    (corner & 4) == 0 ? -surface.depth() / 2 : surface.depth() / 2).point());
        }
        return Optional.of(new FloatingMenuAnchorResolver.SceneBounds(probes, FloatingMenuFraming.COMFORTABLE, surfaces));
    }

    private static double range(Location eye, FloatingMenuAnchorResolver.Anchor anchor, FloatingMenuAnchorResolver.SceneBounds bounds) {
        Vector offset = anchor.origin().toVector().subtract(eye.toVector());
        double maximum = 0;
        for (var point : bounds.probes()) {
            var relative = offset.clone().add(anchor.right().multiply(point.right() * anchor.spatialScale()))
                    .add(new Vector(0, point.up() * anchor.spatialScale(), 0))
                    .add(anchor.forward().multiply(-point.forward() * anchor.spatialScale()));
            maximum = Math.max(maximum, relative.length());
        }
        return maximum + .5;
    }

    private static UiVector3 extent(Move move) {
        if (!move.from().rotation().equals(move.to().rotation())) return sphere(move);
        double scale = Math.max(move.from().scale(), move.to().scale());
        var rotation = move.from().rotation();
        return absolute(rotation.apply(new UiVector3(1, 0, 0))).multiply(move.size().width() * scale / 2)
                .add(absolute(rotation.apply(new UiVector3(0, 1, 0))).multiply(move.size().height() * scale / 2))
                .add(absolute(rotation.apply(new UiVector3(0, 0, 1))).multiply(Math.max(.28, move.depth()) * scale / 2));
    }

    private static UiVector3 sphere(Move move) {
        double radius = Math.hypot(Math.hypot(move.size().width(), move.size().height()), Math.max(.28, move.depth()))
                * Math.max(move.from().scale(), move.to().scale()) / 2;
        return new UiVector3(radius, radius, radius);
    }

    private static UiVector3 absolute(UiVector3 value) {
        return new UiVector3(Math.abs(value.right()), Math.abs(value.up()), Math.abs(value.forward()));
    }

    private static boolean append(List<FloatingMenuAnchorResolver.Surface> surfaces, List<FloatingMenuPoint> points, UiVector3 extent) {
        if (surfaces.size() >= MAX_SURFACES) return false;
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (var point : points) {
            minX = Math.min(minX, point.right()); maxX = Math.max(maxX, point.right());
            minY = Math.min(minY, point.up()); maxY = Math.max(maxY, point.up());
            minZ = Math.min(minZ, point.forward()); maxZ = Math.max(maxZ, point.forward());
        }
        double width = maxX - minX + 2 * extent.right(), height = maxY - minY + 2 * extent.up(), depth = maxZ - minZ + 2 * extent.forward();
        if (!Double.isFinite(width + height + depth) || !Double.isFinite(minX - extent.right()) || !Double.isFinite(maxX + extent.right())
                || !Double.isFinite(minY - extent.up()) || !Double.isFinite(maxY + extent.up())
                || !Double.isFinite(minZ - extent.forward()) || !Double.isFinite(maxZ + extent.forward())) return false;
        surfaces.add(new FloatingMenuAnchorResolver.Surface(FloatingMenuPose.at(new FloatingMenuPoint(
                minX / 2 + maxX / 2, minY / 2 + maxY / 2, minZ / 2 + maxZ / 2)),
                new FloatingMenuSize(Math.max(1e-12, width), Math.max(1e-12, height)), Math.max(1e-12, depth)));
        return true;
    }

    private static double interpolate(double a, double b, double fraction) { return a * (1 - fraction) + b * fraction; }

    private static double[] trigRange(double a, double b, boolean cosine) {
        if (Math.max(Math.abs(a), Math.abs(b)) > 1e12) return new double[]{-1, 1};
        double low = Math.min(a, b), high = Math.max(a, b);
        if (high - low >= 2 * Math.PI) return new double[]{-1, 1};
        double min = Math.min(cosine ? Math.cos(a) : Math.sin(a), cosine ? Math.cos(b) : Math.sin(b));
        double max = Math.max(cosine ? Math.cos(a) : Math.sin(a), cosine ? Math.cos(b) : Math.sin(b));
        double offset = cosine ? 0 : Math.PI / 2;
        double first = Math.ceil((low - offset) / Math.PI), last = Math.floor((high - offset) / Math.PI);
        // There are at most three extrema in an interval shorter than a full turn.
        for (int index = 0; index < 3 && first + index <= last; index++) {
            double value = (first + index) % 2 == 0 ? 1 : -1;
            min = Math.min(min, value); max = Math.max(max, value);
        }
        return new double[]{min, max};
    }

    private static double[] product(double[] unit, double a, double b) {
        return new double[]{Math.min(Math.min(unit[0] * a, unit[0] * b), Math.min(unit[1] * a, unit[1] * b)),
                Math.max(Math.max(unit[0] * a, unit[0] * b), Math.max(unit[1] * a, unit[1] * b))};
    }
}
