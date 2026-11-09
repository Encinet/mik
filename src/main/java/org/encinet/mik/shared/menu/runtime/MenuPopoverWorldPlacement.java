package org.encinet.mik.shared.menu.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** Projects the entire companion into one eye-relative world envelope without changing logical reading geometry. */
final class MenuPopoverWorldPlacement {
    private MenuPopoverWorldPlacement() { }

    record Envelope(Location eye, FloatingMenuAnchorResolver.SceneBounds bounds, double range, boolean possible) {
        Envelope { eye = eye.clone(); }
        @Override public Location eye() { return eye.clone(); }

        double fit(FloatingMenuWorldSpace space) {
            if (!possible) return 0;
            // These bounds are already world-aligned relative to the eye, so this basis is the identity.
            return FloatingMenuAnchorResolver.fitFactor(eye, eye, new Vector(0, 0, -1), new Vector(1, 0, 0), 1, bounds, space);
        }
    }

    static Envelope project(Location eye, MenuSurfaceAttachment.Placement placement,
                            FloatingMenuAnchorResolver.SceneBounds local, double scale) {
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid companion scale");
        UiVector3 origin = placement.center().subtract(MenuSurfaceAttachment.point(eye.toVector()));
        UiVector3 reader = placement.rotation().inverse().apply(origin.multiply(-1));
        boolean inFront = reader.forward() > 1e-6;
        double range = 0;
        var points = new ArrayList<FloatingMenuPoint>(local.probes().size());
        for (var point : local.probes()) {
            var projected = origin.add(placement.rotation().apply(new UiVector3(point.right(), point.up(), point.forward()).multiply(scale)));
            points.add(new FloatingMenuPoint(projected.right(), projected.up(), projected.forward()));
            range = Math.max(range, Math.hypot(Math.hypot(projected.right(), projected.up()), projected.forward()));
            inFront &= point.forward() * scale < reader.forward() - 1e-6;
        }
        var surfaces = new ArrayList<FloatingMenuAnchorResolver.Surface>(local.surfaces().size());
        for (var surface : local.surfaces()) {
            var pose = surface.pose();
            var point = origin.add(placement.rotation().apply(new UiVector3(pose.right(), pose.up(), pose.forward()).multiply(scale)));
            surfaces.add(new FloatingMenuAnchorResolver.Surface(FloatingMenuPose.transformed(
                    new FloatingMenuPoint(point.right(), point.up(), point.forward()), placement.rotation().compose(pose.rotation()), pose.scale() * scale),
                    surface.size(), surface.depth()));
        }
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.copyOf(points), local.framing(), List.copyOf(surfaces), Optional.empty());
        return new Envelope(eye, bounds, Math.min(FloatingMenuWorldSpace.MAX_TRACE_DISTANCE, range + .5),
                inFront && range + .08 <= FloatingMenuWorldSpace.MAX_TRACE_DISTANCE);
    }
}
