package org.encinet.mik.module.space;

import java.util.Objects;

/** Fully transformed result of crossing one invisible spatial seam. */
public record SpaceTransition(
        String linkId,
        String routeId,
        String sourceSurfaceId,
        String destinationSurfaceId,
        String destinationWorld,
        SpaceTransform transform,
        SpaceAperture destinationAperture,
        SpaceVector entryPosition,
        SpaceVector position,
        SpaceVector lookDirection,
        SpaceVector velocity
) {

    public SpaceTransition {
        Objects.requireNonNull(linkId, "linkId");
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(sourceSurfaceId, "sourceSurfaceId");
        Objects.requireNonNull(destinationSurfaceId, "destinationSurfaceId");
        Objects.requireNonNull(destinationWorld, "destinationWorld");
        Objects.requireNonNull(transform, "transform");
        Objects.requireNonNull(destinationAperture, "destinationAperture");
        Objects.requireNonNull(entryPosition, "entryPosition");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(lookDirection, "lookDirection");
        Objects.requireNonNull(velocity, "velocity");
    }
}
