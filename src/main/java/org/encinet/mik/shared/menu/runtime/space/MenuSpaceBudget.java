package org.encinet.mik.shared.menu.runtime.space;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

record MenuSpaceBudget(double factor, BoundingBox region) {
    private static final int TARGET_BLOCKS = 4096;
    private static final double TRACE_MARGIN = 1;
    private static final double MIN_FACTOR = 1e-5;

    static MenuSpaceBudget forRegion(BoundingBox nominal) {
        double radius = Math.hypot(Math.hypot(Math.max(-nominal.getMinX(), nominal.getMaxX()),
                Math.max(-nominal.getMinY(), nominal.getMaxY())), Math.max(-nominal.getMinZ(), nominal.getMaxZ()));
        double high = Math.min(1, (FloatingMenuWorldSpace.MAX_TRACE_DISTANCE - TRACE_MARGIN) / radius);
        if (cells(nominal, high) <= TARGET_BLOCKS) return new MenuSpaceBudget(high, scaled(nominal, high));
        double low = 0;
        for (int step = 0; step < 20; step++) {
            double middle = (low + high) * .5;
            if (cells(nominal, middle) <= TARGET_BLOCKS) low = middle;
            else high = middle;
        }
        return new MenuSpaceBudget(low, scaled(nominal, low));
    }

    boolean usable() { return factor >= MIN_FACTOR; }

    private static double cells(BoundingBox box, double factor) {
        return cells(box.getWidthX() * factor, MenuSpaceRefits.HORIZONTAL_RESERVE)
                * cells(box.getHeight() * factor, MenuSpaceRefits.VERTICAL_RESERVE)
                * cells(box.getWidthZ() * factor, MenuSpaceRefits.HORIZONTAL_RESERVE);
    }

    private static double cells(double width, double reserve) {
        return Math.ceil(width + 2 * (reserve + MenuBlockCollisionSnapshot.SHAPE_PADDING)) + 1;
    }

    private static BoundingBox scaled(BoundingBox box, double factor) {
        return new BoundingBox(box.getMinX() * factor, box.getMinY() * factor, box.getMinZ() * factor,
                box.getMaxX() * factor, box.getMaxY() * factor, box.getMaxZ() * factor);
    }

    BoundingBox scale(BoundingBox box) { return scaled(box, factor); }

    MenuCollisionSpace units(MenuCollisionSpace sampled) {
        if (factor == 1) return sampled;
        return new MenuCollisionSpace() {
            @Override public double firstHit(Location eye, Vector direction, double distance) {
                return sampled.firstHit(eye, direction, distance * factor) / factor;
            }

            @Override public double limitSurface(Location eye, Vector center, Vector right, Vector up,
                                                 double halfWidth, double halfHeight, double clearance) {
                var adjusted = center.clone().subtract(eye.toVector()).multiply(factor).add(eye.toVector());
                return sampled.limitSurface(eye, adjusted, right, up, halfWidth * factor, halfHeight * factor, clearance * factor);
            }
        };
    }
}
