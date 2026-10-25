package org.encinet.mik.feature.world.space;

import java.util.Optional;

/** One directed traversal across a reversible spatial link. */
final class SpaceRoute {

    private static final double PLANE_EPSILON = 1.0E-7;

    private final String id;
    private final SpaceLink link;
    private final SpaceSurface source;
    private final SpaceSurface destination;
    private final SpaceTransform transform;

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
        this.transform = new SpaceTransform(source.frame(), destination.frame());
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
        SpaceFrame frame = source.frame();
        double fromX = from.x() - frame.origin().x();
        double fromY = from.y() - frame.origin().y();
        double fromZ = from.z() - frame.origin().z();
        double toX = to.x() - frame.origin().x();
        double toY = to.y() - frame.origin().y();
        double toZ = to.z() - frame.origin().z();
        double localFromZ = dot(fromX, fromY, fromZ, frame.forward());
        double localToZ = dot(toX, toY, toZ, frame.forward());
        double forwardDelta = localToZ - localFromZ;
        if (forwardDelta <= PLANE_EPSILON
                || localFromZ > PLANE_EPSILON
                || localToZ <= PLANE_EPSILON) {
            return null;
        }
        double progress = -localFromZ / forwardDelta;
        if (progress < -PLANE_EPSILON || progress > 1.0 + PLANE_EPSILON) {
            return null;
        }
        double localFromX = dot(fromX, fromY, fromZ, frame.right());
        double localFromY = dot(fromX, fromY, fromZ, frame.up());
        double localX = localFromX + (dot(toX, toY, toZ, frame.right()) - localFromX) * progress;
        double localY = localFromY + (dot(toX, toY, toZ, frame.up()) - localFromY) * progress;
        return source.aperture().contains(localX, localY)
                ? new Crossing(localX, localY) : null;
    }

    private static double dot(double x, double y, double z, SpaceVector axis) {
        return x * axis.x() + y * axis.y() + z * axis.z();
    }

    private record Crossing(double localX, double localY) {
    }
}
