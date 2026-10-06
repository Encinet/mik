package org.encinet.mik.module.menu.runtime;

import org.bukkit.Location;
import org.bukkit.util.Vector;

final class FloatingMenuAnchorMotion {
    static boolean translate(Location previous, Location current, Location openedAt, Location view, Location origin) {
        if (!java.util.Objects.equals(previous.getWorld(), current.getWorld())) return false;
        Vector displacement = current.toVector().subtract(previous.toVector());
        if (displacement.lengthSquared() < 1.0E-12) return false;
        openedAt.add(displacement);
        view.add(displacement);
        origin.add(displacement);
        previous.setX(current.getX());
        previous.setY(current.getY());
        previous.setZ(current.getZ());
        return true;
    }
}
