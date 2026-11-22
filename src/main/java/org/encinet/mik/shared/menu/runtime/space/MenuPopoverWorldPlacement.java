package org.encinet.mik.shared.menu.runtime.space;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.ui.UiVector3;

/** Projects the entire companion into one eye-relative world envelope without changing logical reading geometry. */
public final class MenuPopoverWorldPlacement {
    private MenuPopoverWorldPlacement() { }

    record Envelope(Location eye, FloatingMenuAnchorResolver.SceneBounds bounds, boolean possible) {
        Envelope { eye = eye.clone(); }
        @Override public Location eye() { return eye.clone(); }

        double fit(MenuCollisionSpace space) {
            if (!possible) return 0;
            // These bounds are already world-aligned relative to the eye, so this basis is the identity.
            return FloatingMenuAnchorResolver.fitFactor(eye, eye, new Vector(0, 0, -1), new Vector(1, 0, 0), 1, bounds, space);
        }
    }

    public static MenuSpaceRefits.Plan<Double> plan(UiVector3 eye, MenuSurfaceAttachment.Placement placement,
                                            FloatingMenuAnchorResolver.SceneBounds bounds, double scale) {
        var origin = new Location(null, 0, 0, 0);
        var relative = new MenuSurfaceAttachment.Placement(placement.center().subtract(eye), placement.rotation());
        var envelope = project(origin, relative, bounds, scale);
        if (!envelope.possible()) return new MenuSpaceRefits.Plan<>(new BoundingBox(-.01, -.01, -.01, .01, .01, .01), _ -> 0.0);
        var anchor = new FloatingMenuAnchorResolver.Anchor(origin, new Vector(0, 0, -1), new Vector(1, 0, 0), 1, 0, 0);
        return MenuSpaceRefits.Plan.bounded(FloatingMenuAnchorResolver.samplingBounds(anchor, envelope.bounds()),
                (space, factor) -> factor * envelope.fit(space), () -> 0.0);
    }

    public static BoundingBox localEnvelope(FloatingMenuAnchorResolver.SceneBounds bounds) {
        var origin = new Location(null, 0, 0, 0);
        return FloatingMenuAnchorResolver.samplingBounds(new FloatingMenuAnchorResolver.Anchor(origin,
                new Vector(0, 0, -1), new Vector(1, 0, 0), 1, 0, 0), bounds);
    }

    public static boolean clear(MenuSpaceRefits.Evidence evidence, long tick, Location eye,
                                MenuSurfaceAttachment.Placement placement, BoundingBox local, double scale) {
        if (evidence == null || !evidence.fresh(tick)) return false;
        var relative = placement.center().subtract(MenuSurfaceAttachment.point(eye.toVector()));
        double margin = FloatingMenuAnchorResolver.SURFACE_CLEARANCE * 2 + 1e-6;
        if (-placement.rotation().inverse().apply(relative).forward() <= local.getMaxZ() * scale + margin) return false;
        var region = new BoundingBox();
        for (double right : new double[]{local.getMinX(), local.getMaxX()}) {
            for (double up : new double[]{local.getMinY(), local.getMaxY()}) {
                for (double forward : new double[]{local.getMinZ(), local.getMaxZ()}) {
                    var corner = placement.rotation().apply(new UiVector3(right, up, forward).multiply(scale)).add(relative);
                    region.union(corner.right(), corner.up(), corner.forward());
                }
            }
        }
        return evidence.clear(eye, region.expand(margin));
    }

    static Envelope project(Location eye, MenuSurfaceAttachment.Placement placement,
                            FloatingMenuAnchorResolver.SceneBounds local, double scale) {
        if (!Double.isFinite(scale) || scale <= 0) throw new IllegalArgumentException("Invalid companion scale");
        UiVector3 origin = placement.center().subtract(MenuSurfaceAttachment.point(eye.toVector()));
        UiVector3 reader = placement.rotation().inverse().apply(origin.multiply(-1));
        boolean inFront = reader.forward() > 1e-6;
        var points = new ArrayList<FloatingMenuPoint>(local.probes().size());
        for (var point : local.probes()) {
            var projected = origin.add(placement.rotation().apply(new UiVector3(point.right(), point.up(), point.forward()).multiply(scale)));
            points.add(new FloatingMenuPoint(projected.right(), projected.up(), projected.forward()));
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
        return new Envelope(eye, bounds, inFront);
    }
}
