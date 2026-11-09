package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.joml.Matrix4d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Converts local model bounds to eye-relative geometry without reading entities or the world. */
public final class MenuModelGeometry {
    private static final double PLANE_THICKNESS = 1.0E-5;

    private MenuModelGeometry() { }

    public static MenuOcclusionGeometry.Box transform(BoundingBox local, Matrix4d transform, double maximumDistance) {
        if (!transform.isFinite()) return null;
        List<Vector> vertices = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) {
            var vertex = transform.transformPosition(new Vector3d(
                    (corner & 1) == 0 ? local.getMinX() : local.getMaxX(),
                    (corner & 2) == 0 ? local.getMinY() : local.getMaxY(),
                    (corner & 4) == 0 ? local.getMinZ() : local.getMaxZ()));
            if (!vertex.isFinite()) return null;
            vertices.add(new Vector(vertex.x, vertex.y, vertex.z));
        }
        var envelope = MenuOcclusionGeometry.bounds(vertices);
        if (!inRange(envelope, maximumDistance)) return null;
        var x = vertices.get(1).clone().subtract(vertices.getFirst());
        var y = vertices.get(2).clone().subtract(vertices.getFirst());
        var z = vertices.get(4).clone().subtract(vertices.getFirst());
        if (Math.max(x.clone().crossProduct(y).lengthSquared(), Math.max(x.clone().crossProduct(z).lengthSquared(),
                y.clone().crossProduct(z).lengthSquared())) == 0) return null;

        Matrix4d inverse = new Matrix4d(transform).invert();
        if (inverse.isFinite()) {
            var eye = inverse.transformPosition(new Vector3d());
            return new MenuOcclusionGeometry.Box(vertices, local.contains(eye.x, eye.y, eye.z), envelope);
        }
        // A flattened display still renders a face. Give its conservative envelope a tiny depth
        // so the shared convex intersection code never receives a singular box.
        double padX = Math.max(0, PLANE_THICKNESS - envelope.getWidthX()) / 2;
        double padY = Math.max(0, PLANE_THICKNESS - envelope.getHeight()) / 2;
        double padZ = Math.max(0, PLANE_THICKNESS - envelope.getWidthZ()) / 2;
        return MenuOcclusionGeometry.box(envelope.expand(padX, padY, padZ), new Vector());
    }

    public static boolean inRange(BoundingBox bounds, double maximumDistance) {
        double x = Math.clamp(0, bounds.getMinX(), bounds.getMaxX());
        double y = Math.clamp(0, bounds.getMinY(), bounds.getMaxY());
        double z = Math.clamp(0, bounds.getMinZ(), bounds.getMaxZ());
        return x * x + y * y + z * z <= maximumDistance * maximumDistance;
    }
}
