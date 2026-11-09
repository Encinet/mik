package org.encinet.mik.shared.menu.runtime;


import org.bukkit.util.Vector;

import java.util.Objects;
import java.util.Optional;
import org.encinet.mik.shared.menu.FloatingMenuSize;

/** Exact oriented surface model shared by hover and click selection. */
final class FloatingMenuSurfaceGeometry {
    private static final double PARALLEL_EPSILON = 1.0E-6;

    private FloatingMenuSurfaceGeometry() {
    }

    static Optional<Hit> intersect(Vector rayOrigin, Vector rayDirection,
                                   Vector surfaceCenter, float yawDegrees, float pitchDegrees,
                                   double width, double height, double margin) {
        return intersect(rayOrigin, rayDirection, surfaceCenter, yawDegrees, pitchDegrees, 0, width, height, margin);
    }

    static Optional<Hit> intersect(Vector rayOrigin, Vector rayDirection,
                                   Vector surfaceCenter, float yawDegrees, float pitchDegrees, float rollDegrees,
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
        Vector up = panelUp(yawDegrees, pitchDegrees, rollDegrees);
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
        double horizontalFraction = Math.clamp(rightDistance / width + 0.5, 0.0, 1.0);
        return Optional.of(new Hit(distance, normalizedCenterDistance, horizontalFraction));
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

    static Vector panelUp(float yaw, float pitch, float roll) {
        if (!Float.isFinite(roll)) throw new IllegalArgumentException("Menu surface roll must be finite");
        Vector up = panelUp(yaw, pitch), right = up.clone().crossProduct(facing(yaw, pitch));
        double angle = Math.toRadians(roll);
        return up.multiply(Math.cos(angle)).subtract(right.multiply(Math.sin(angle)));
    }

    /** Vanilla Interaction entities are axis-aligned; this catcher encloses the exact oriented reading plane. */
    static FloatingMenuSize catcher(float yaw, float pitch, float roll, double width, double height) {
        Vector up = panelUp(yaw, pitch, roll), right = up.clone().crossProduct(facing(yaw, pitch));
        return new FloatingMenuSize(Math.max(.02, Math.max(Math.abs(right.getX()) * width + Math.abs(up.getX()) * height,
                Math.abs(right.getZ()) * width + Math.abs(up.getZ()) * height)),
                Math.max(.02, Math.abs(right.getY()) * width + Math.abs(up.getY()) * height));
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

    record Hit(double distance, double normalizedCenterDistance,
               double horizontalFraction) {
    }
}
