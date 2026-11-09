package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.encinet.mik.shared.menu.ui.UiAttachmentPlacement;
import org.encinet.mik.shared.menu.ui.UiRotation;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** Perspective bounds of a rotated rectangle or box, clipped before division at the eye plane. */
final class MenuViewProjection {
    static final double NEAR = 1e-4;
    private final UiVector3 eye, localCenter;
    private final UiRotation inverse;
    final double depth;

    MenuViewProjection(UiVector3 eye, UiVector3 center, UiRotation rotation) {
        this.eye = eye;
        inverse = rotation.inverse();
        localCenter = inverse.apply(center.subtract(eye));
        depth = -localCenter.forward();
    }

    Optional<Projection> project(MenuSurfaceAttachment.Surface surface) {
        if (depth <= NEAR) return Optional.empty();
        List<UiVector3> vertices = new ArrayList<>();
        int layers = surface.depth() > 0 ? 2 : 1;
        for (int layer = 0; layer < layers; layer++) for (int corner = 0; corner < 4; corner++) {
            double x = (corner == 0 || corner == 3 ? -.5 : .5) * surface.size().width();
            double y = (corner < 2 ? .5 : -.5) * surface.size().height();
            double z = layers == 1 ? 0 : (layer == 0 ? -.5 : .5) * surface.depth();
            vertices.add(inverse.apply(surface.center().add(surface.rotation().apply(new UiVector3(x, y, z).multiply(surface.scale())))
                    .subtract(eye)));
        }
        List<UiVector3> visible = new ArrayList<>();
        for (var vertex : vertices) if (-vertex.forward() >= NEAR) visible.add(vertex);
        for (int layer = 0; layer < layers; layer++) for (int corner = 0; corner < 4; corner++)
            clip(vertices.get(layer * 4 + corner), vertices.get(layer * 4 + (corner + 1) % 4), visible);
        if (layers == 2) for (int corner = 0; corner < 4; corner++) clip(vertices.get(corner), vertices.get(corner + 4), visible);
        if (visible.isEmpty()) return Optional.empty();
        double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY, nearest = Double.POSITIVE_INFINITY;
        for (var vertex : visible) {
            double distance = Math.max(NEAR, -vertex.forward());
            double x = vertex.right() * depth / distance - localCenter.right();
            double y = localCenter.up() - vertex.up() * depth / distance;
            left = Math.min(left, x); right = Math.max(right, x);
            top = Math.min(top, y); bottom = Math.max(bottom, y);
            nearest = Math.min(nearest, distance);
        }
        return Optional.of(new Projection(new UiAttachmentPlacement.Rect(left, top, right - left, bottom - top), nearest));
    }

    private static void clip(UiVector3 a, UiVector3 b, List<UiVector3> visible) {
        double first = -a.forward(), second = -b.forward();
        if ((first < NEAR) == (second < NEAR)) return;
        double fraction = (NEAR - first) / (second - first);
        visible.add(a.add(b.subtract(a).multiply(fraction)));
    }

    record Projection(UiAttachmentPlacement.Rect rect, double nearestDepth) { }
}
