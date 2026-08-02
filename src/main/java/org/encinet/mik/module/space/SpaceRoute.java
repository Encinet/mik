package org.encinet.mik.module.space;

import java.util.Optional;

/** One directed traversal across a reversible spatial link. */
final class SpaceRoute {

    private static final double PLANE_EPSILON = 1.0E-7;

    private final String id;
    private final SpaceLink link;
    private final SpaceSurface source;
    private final SpaceSurface destination;

    SpaceRoute(
            String id,
            SpaceLink link,
            SpaceSurface source,
            SpaceSurface destination
    ) {
        this.id = id;
        this.link = link;
        this.source = source;
        this.destination = destination;
    }

    String id() {
        return id;
    }

    SpaceLink link() {
        return link;
    }

    SpaceSurface source() {
        return source;
    }

    Optional<SpaceTransition> trace(
            SpaceVector from,
            SpaceVector to,
            SpaceVector lookDirection,
            SpaceVector velocity
    ) {
        Crossing crossing = crossing(from, to);
        if (crossing == null) {
            return Optional.empty();
        }

        SpaceVector localEntry = new SpaceVector(crossing.localX(), crossing.localY(), 0.0);
        SpaceVector localArrival = source.frame().toLocalPoint(to);
        SpaceTransform transform = new SpaceTransform(source.frame(), destination.frame());
        SpaceVector mappedLook = transform.mapDirection(lookDirection);
        SpaceVector mappedVelocity = transform.mapVector(velocity);
        return Optional.of(new SpaceTransition(
                link.id(),
                id,
                source.id(),
                destination.id(),
                destination.world(),
                transform,
                destination.aperture(),
                destination.frame().fromLocalPoint(localEntry),
                destination.frame().fromLocalPoint(localArrival),
                mappedLook,
                mappedVelocity));
    }

    private Crossing crossing(SpaceVector from, SpaceVector to) {
        SpaceVector localFrom = source.frame().toLocalPoint(from);
        SpaceVector localTo = source.frame().toLocalPoint(to);
        double forwardDelta = localTo.z() - localFrom.z();
        if (forwardDelta <= PLANE_EPSILON
                || localFrom.z() > PLANE_EPSILON
                || localTo.z() <= PLANE_EPSILON) {
            return null;
        }
        double progress = -localFrom.z() / forwardDelta;
        if (progress < -PLANE_EPSILON || progress > 1.0 + PLANE_EPSILON) {
            return null;
        }
        double localX = localFrom.x() + (localTo.x() - localFrom.x()) * progress;
        double localY = localFrom.y() + (localTo.y() - localFrom.y()) * progress;
        return source.aperture().contains(localX, localY)
                ? new Crossing(localX, localY) : null;
    }

    private record Crossing(double localX, double localY) {
    }
}
