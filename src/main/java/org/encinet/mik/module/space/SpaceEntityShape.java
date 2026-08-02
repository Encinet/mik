package org.encinet.mik.module.space;

import org.bukkit.util.BoundingBox;

import java.util.Objects;

/** Axis-aligned collision-box offsets relative to an entity's Bukkit location anchor. */
record SpaceEntityShape(
        SpaceVector minimum,
        SpaceVector maximum
) {

    private static final double PORTAL_CLEARANCE_MARGIN = 0.02;

    SpaceEntityShape {
        Objects.requireNonNull(minimum, "minimum");
        Objects.requireNonNull(maximum, "maximum");
        if (minimum.x() > maximum.x()
                || minimum.y() > maximum.y()
                || minimum.z() > maximum.z()) {
            throw new IllegalArgumentException("Entity shape minimum must not exceed maximum");
        }
    }

    static SpaceEntityShape from(BoundingBox bounds, SpaceVector anchor) {
        Objects.requireNonNull(bounds, "bounds");
        Objects.requireNonNull(anchor, "anchor");
        return new SpaceEntityShape(
                new SpaceVector(
                        bounds.getMinX() - anchor.x(),
                        bounds.getMinY() - anchor.y(),
                        bounds.getMinZ() - anchor.z()),
                new SpaceVector(
                        bounds.getMaxX() - anchor.x(),
                        bounds.getMaxY() - anchor.y(),
                        bounds.getMaxZ() - anchor.z()));
    }

    Projection projectOnto(SpaceVector axis) {
        SpaceVector direction = axis.normalized();
        double minimumProjection = contribution(
                direction.x(), minimum.x(), maximum.x(), true)
                + contribution(direction.y(), minimum.y(), maximum.y(), true)
                + contribution(direction.z(), minimum.z(), maximum.z(), true);
        double maximumProjection = contribution(
                direction.x(), minimum.x(), maximum.x(), false)
                + contribution(direction.y(), minimum.y(), maximum.y(), false)
                + contribution(direction.z(), minimum.z(), maximum.z(), false);
        return new Projection(minimumProjection, maximumProjection);
    }

    double entryClearance(SpaceVector exitDirection) {
        Projection projection = projectOnto(exitDirection);
        return Math.max(PORTAL_CLEARANCE_MARGIN,
                -projection.minimum() + PORTAL_CLEARANCE_MARGIN);
    }

    private static double contribution(
            double direction,
            double minimum,
            double maximum,
            boolean chooseMinimum
    ) {
        boolean useMinimum = chooseMinimum == (direction >= 0.0);
        return direction * (useMinimum ? minimum : maximum);
    }

    record Projection(double minimum, double maximum) {

        double size() {
            return maximum - minimum;
        }
    }
}
