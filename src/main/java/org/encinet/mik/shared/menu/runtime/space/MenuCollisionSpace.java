package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.Location;
import org.bukkit.util.Vector;

public interface MenuCollisionSpace {
    double firstHit(Location eye, Vector direction, double distance);

    double limitSurface(Location eye, Vector center, Vector right, Vector up,
                        double halfWidth, double halfHeight, double clearance);
}
