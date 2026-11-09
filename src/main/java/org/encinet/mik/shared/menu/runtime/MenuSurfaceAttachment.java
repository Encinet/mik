package org.encinet.mik.shared.menu.runtime;

import java.util.Optional;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.ui.*;

/** Captures the pointer once; the resulting overlay follows its source rather than chasing the reader. */
record MenuSurfaceAttachment(UiVector3 offset, UiRotation rotation, double readingScale) {
    MenuSurfaceAttachment {
        Objects.requireNonNull(offset); Objects.requireNonNull(rotation);
        if (!Double.isFinite(readingScale) || readingScale <= 0 || readingScale > 1)
            throw new IllegalArgumentException("Invalid attachment reading scale");
    }
    record Surface(UiVector3 center, UiRotation rotation, FloatingMenuSize size, double scale, double depth) {
        Surface(UiVector3 center, UiRotation rotation, FloatingMenuSize size, double scale) { this(center, rotation, size, scale, 0); }
        Surface {
            Objects.requireNonNull(center); Objects.requireNonNull(rotation); Objects.requireNonNull(size);
            if (!Double.isFinite(scale) || scale <= 0 || !Double.isFinite(depth) || depth < 0)
                throw new IllegalArgumentException("Invalid surface scale or depth");
        }
        Surface translated(UiVector3 delta) { return new Surface(center.add(delta), rotation, size, scale, depth); }

        Optional<FloatingMenuSurfaceGeometry.Hit> intersect(Location eye, double margin) {
            var angles = rotation.angles();
            return FloatingMenuSurfaceGeometry.intersect(eye.toVector(), eye.getDirection(), vector(center),
                    (float) angles.yaw(), (float) angles.pitch(), (float) angles.roll(),
                    size.width() * scale, size.height() * scale, margin * scale);
        }
    }
    record Pointer(UiVector3 center, UiRotation rotation, UiSize viewport, double gap, UiVector3 eye) {
        UiSize available() { return new UiSize(viewport.width() / 2 - gap, viewport.height() / 2 - gap); }
    }
    record Placement(UiVector3 center, UiRotation rotation) { }

    static Optional<Pointer> pointer(Location eye, Surface source, FloatingMenuFieldOfView fieldOfView) {
        var hit = source.intersect(eye, .04);
        if (hit.isEmpty()) return Optional.empty();
        double depth = hit.get().distance() - Math.min(.12 * source.scale(), hit.get().distance() * .1);
        var framing = fieldOfView.readingFraming();
        var viewport = new UiSize(2 * depth * framing.horizontalTangent() * .9, 2 * depth * framing.verticalTangent() * .9);
        double gap = Math.min(.08 * source.scale(), Math.min(viewport.width(), viewport.height()) / 16);
        return Optional.of(new Pointer(point(eye.toVector().add(eye.getDirection().multiply(depth))),
                UiRotation.degrees(eye.getYaw() + 180, -eye.getPitch(), 0), viewport, gap, point(eye.toVector())));
    }

    static Optional<MenuSurfaceAttachment> attach(Pointer pointer, Surface source, FloatingMenuSize size) {
        return attach(pointer, source, size, List.of());
    }

    static Optional<MenuSurfaceAttachment> attach(Pointer pointer, Surface source, FloatingMenuSize size, List<Surface> surfaces) {
        var viewport = pointer.viewport();
        var position = new UiLayout.Position(viewport.width() / 2, viewport.height() / 2);
        var plane = new MenuViewProjection(pointer.eye(), pointer.center(), pointer.rotation());
        List<UiAttachmentPlacement.Rect> obstacles = new ArrayList<>();
        for (Surface surface : surfaces) plane.project(surface).ifPresent(projected -> {
            var rect = projected.rect();
            double left = Math.max(0, rect.x() + viewport.width() / 2), top = Math.max(0, rect.y() + viewport.height() / 2);
            double right = Math.min(viewport.width(), rect.x() + rect.width() + viewport.width() / 2);
            double bottom = Math.min(viewport.height(), rect.y() + rect.height() + viewport.height() / 2);
            if (right <= left || bottom <= top) return;
            var clipped = new UiAttachmentPlacement.Rect(left, top, right - left, bottom - top);
            // The host surrounds the source; avoiding its entire panel would detach the hint.
            // It still participates in the depth check, as do every neighbour and model volume.
            if (!clipped.contains(position)) obstacles.add(clipped);
        });
        return UiAttachmentPlacement.place(position, new UiSize(size.width(), size.height()), viewport, pointer.gap(), obstacles).map(rect -> {
            UiVector3 offset = new UiVector3(rect.centerX() - viewport.width() / 2, viewport.height() / 2 - rect.centerY(), 0);
            UiVector3 center = pointer.center().add(pointer.rotation().apply(offset));
            return relative(source, center, pointer.rotation(), 1).inFront(pointer.eye(), source, size, surfaces);
        });
    }

    /** Only advance an obstructed hint; retreating or resampling the gaze would disturb reading. */
    MenuSurfaceAttachment inFront(UiVector3 eye, Surface source, FloatingMenuSize size, List<Surface> surfaces) {
        var placed = place(source);
        var plane = new MenuViewProjection(eye, placed.center(), placed.rotation());
        if (plane.depth <= MenuViewProjection.NEAR) return this;
        var bounds = new UiAttachmentPlacement.Rect(-size.width() * readingScale / 2, -size.height() * readingScale / 2,
                size.width() * readingScale, size.height() * readingScale);
        double depth = plane.depth;
        // The live source includes hover/press displacement, which can differ from its layout pose.
        List<Surface> blockers = new ArrayList<>(surfaces);
        blockers.add(source);
        for (Surface surface : blockers) {
            var projection = plane.project(surface).orElse(null);
            if (projection == null || bounds.intersectionArea(projection.rect()) == 0) continue;
            double clearance = Math.min(.08 * source.scale(), projection.nearestDepth() * .1);
            depth = Math.min(depth, projection.nearestDepth() - clearance);
        }
        double factor = depth / plane.depth;
        if (factor >= 1 - 1e-7) return this;
        return relative(source, eye.add(placed.center().subtract(eye).multiply(factor)), placed.rotation(), readingScale * factor);
    }

    static MenuSurfaceAttachment relative(Surface source, UiVector3 center, UiRotation rotation, double readingScale) {
        return new MenuSurfaceAttachment(source.rotation().inverse().apply(center.subtract(source.center())).multiply(1 / source.scale()),
                source.rotation().inverse().compose(rotation), readingScale);
    }

    static MenuSurfaceAttachment centered(Pointer pointer, Surface source, FloatingMenuSize size, List<Surface> surfaces) {
        return relative(source, pointer.center(), pointer.rotation(), 1).inFront(pointer.eye(), source, size, surfaces);
    }

    MenuSurfaceAttachment retarget(Surface previous, Surface next) {
        var placed = place(previous);
        return relative(next, placed.center(), placed.rotation(), readingScale);
    }

    /** Reflow only as far as necessary inside the captured reading viewport, without sampling a new gaze. */
    MenuSurfaceAttachment constrained(Pointer viewport, Surface source, FloatingMenuSize size) {
        var placed = place(source);
        var inverse = viewport.rotation().inverse();
        double referenceDepth = -inverse.apply(viewport.center().subtract(viewport.eye())).forward();
        double depth = -inverse.apply(placed.center().subtract(viewport.eye())).forward();
        if (referenceDepth <= MenuViewProjection.NEAR || depth <= MenuViewProjection.NEAR) return this;
        double factor = depth / referenceDepth;
        var projected = viewport.eye().add(placed.center().subtract(viewport.eye()).multiply(1 / factor));
        var local = inverse.apply(projected.subtract(viewport.center()));
        double horizontal = Math.max(0, (viewport.viewport().width() - size.width() * readingScale / factor) / 2 - viewport.gap());
        double vertical = Math.max(0, (viewport.viewport().height() - size.height() * readingScale / factor) / 2 - viewport.gap());
        double right = Math.clamp(local.right(), -horizontal, horizontal), up = Math.clamp(local.up(), -vertical, vertical);
        if (Math.abs(right - local.right()) < 1e-8 && Math.abs(up - local.up()) < 1e-8) return this;
        var center = viewport.center().add(viewport.rotation().apply(new UiVector3(right, up, 0)));
        return relative(source, viewport.eye().add(center.subtract(viewport.eye()).multiply(factor)), placed.rotation(), readingScale);
    }

    Placement place(Surface source) {
        return new Placement(source.center().add(source.rotation().apply(offset.multiply(source.scale()))),
                source.rotation().compose(rotation));
    }

    static UiVector3 point(Vector value) { return new UiVector3(value.getX(), value.getY(), value.getZ()); }
    static Vector vector(UiVector3 value) { return new Vector(value.right(), value.up(), value.forward()); }
}
