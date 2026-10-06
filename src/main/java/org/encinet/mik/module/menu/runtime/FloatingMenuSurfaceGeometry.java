package org.encinet.mik.module.menu.runtime;


import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Optional;

/** Exact oriented surface model shared by hover and click selection. */
final class FloatingMenuSurfaceGeometry {
    private static final double PARALLEL_EPSILON = 1.0E-6;

    private FloatingMenuSurfaceGeometry() {
    }

    static Optional<Hit> intersect(Vector rayOrigin, Vector rayDirection,
                                   Vector surfaceCenter, float yawDegrees, float pitchDegrees,
                                   double width, double height, double margin) {
        Vector origin = finiteVector(rayOrigin, "rayOrigin");
        Vector ray = finiteVector(rayDirection, "rayDirection");
        Vector center = finiteVector(surfaceCenter, "surfaceCenter");
        if (ray.lengthSquared() < PARALLEL_EPSILON
                || !Double.isFinite(width) || width <= 0.0
                || !Double.isFinite(height) || height <= 0.0
                || !Double.isFinite(margin) || margin < 0.0) {
            throw new IllegalArgumentException("Invalid menu surface geometry");
        }
        ray.normalize();
        Vector normal = facing(yawDegrees, pitchDegrees);
        double denominator = ray.dot(normal);
        if (Math.abs(denominator) < PARALLEL_EPSILON) return Optional.empty();

        double distance = center.clone().subtract(origin).dot(normal) / denominator;
        if (!Double.isFinite(distance) || distance <= 0.0) return Optional.empty();
        Vector local = origin.clone().add(ray.clone().multiply(distance)).subtract(center);
        Vector up = panelUp(yawDegrees, pitchDegrees);
        Vector right = up.clone().crossProduct(normal).normalize();
        double rightDistance = local.dot(right);
        double upDistance = local.dot(up);
        double halfWidth = width * 0.5 + margin;
        double halfHeight = height * 0.5 + margin;
        if (Math.abs(rightDistance) > halfWidth || Math.abs(upDistance) > halfHeight) {
            return Optional.empty();
        }
        double normalizedCenterDistance = square(rightDistance / halfWidth)
                + square(upDistance / halfHeight);
        return Optional.of(new Hit(distance, normalizedCenterDistance));
    }

    static Vector facing(float yawDegrees, float pitchDegrees) {
        if (!Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("Menu surface rotation must be finite");
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double horizontal = Math.cos(pitch);
        return new Vector(-Math.sin(yaw) * horizontal,
                -Math.sin(pitch), Math.cos(yaw) * horizontal);
    }

    static Vector panelUp(float yawDegrees, float pitchDegrees) {
        if (!Float.isFinite(yawDegrees) || !Float.isFinite(pitchDegrees)) {
            throw new IllegalArgumentException("Menu surface rotation must be finite");
        }
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        return new Vector(-Math.sin(yaw) * Math.sin(pitch),
                Math.cos(pitch), Math.cos(yaw) * Math.sin(pitch));
    }

    private static Vector finiteVector(Vector value, String name) {
        Vector vector = Objects.requireNonNull(value, name).clone();
        if (!Double.isFinite(vector.getX()) || !Double.isFinite(vector.getY())
                || !Double.isFinite(vector.getZ())) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return vector;
    }

    private static double square(double value) {
        return value * value;
    }

    record Hit(double distance, double normalizedCenterDistance) {
    }
}
