package org.encinet.mik.module.space;

import java.util.Objects;

/**
 * Rigid transform between the local coordinate frames of two connected surfaces.
 * Points include the surface origins; directions and velocities only rotate.
 */
public record SpaceTransform(
        SpaceFrame source,
        SpaceFrame destination
) {

    public SpaceTransform {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
    }

    public SpaceVector mapPoint(SpaceVector point) {
        Objects.requireNonNull(point, "point");
        return destination.fromLocalPoint(source.toLocalPoint(point));
    }

    public SpaceVector mapVector(SpaceVector vector) {
        Objects.requireNonNull(vector, "vector");
        return destination.fromLocalVector(source.toLocalVector(vector));
    }

    public SpaceVector mapDirection(SpaceVector direction) {
        return mapVector(direction).normalized();
    }

    public SpaceTransform inverse() {
        return new SpaceTransform(destination, source);
    }
}
