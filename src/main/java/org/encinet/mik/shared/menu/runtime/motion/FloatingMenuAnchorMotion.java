package org.encinet.mik.shared.menu.runtime.motion;

import org.encinet.mik.shared.menu.runtime.space.FloatingMenuAnchorResolver;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public final class FloatingMenuAnchorMotion {
    /** Dimensions and eye-relative distance recover together, preserving the complete projected frame. */
    public static FloatingMenuAnchorResolver.Anchor recover(Location eye, FloatingMenuAnchorResolver.Anchor target, double previousScale) {
        return target.contract(eye, recoveryScale(previousScale, target.spatialScale()) / target.spatialScale());
    }

    public static double recoveryScale(double previous, double target) {
        if (previous <= 0 || target <= previous) return target;
        return Math.min(target, previous * 1.08);
    }

    static boolean translate(Location previous, Location current, Location openedAt, Location view, Location origin) {
        var before = view.toVector();
        if (!translateView(previous, current, openedAt, view)) return false;
        origin.add(view.toVector().subtract(before));
        return true;
    }

    public static boolean translateView(Location previous, Location current, Location openedAt, Location view) {
        if (!java.util.Objects.equals(previous.getWorld(), current.getWorld())) return false;
        Vector displacement = current.toVector().subtract(previous.toVector());
        if (displacement.lengthSquared() < 1.0E-12) return false;
        openedAt.add(displacement);
        view.add(displacement);
        previous.setX(current.getX());
        previous.setY(current.getY());
        previous.setZ(current.getZ());
        return true;
    }
}
