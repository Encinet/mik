package org.encinet.mik.module.space;

import java.util.Objects;
import java.util.Optional;

/** Fits an upright entity collision box inside the destination surface aperture. */
final class SpaceAperturePlacement {

    private static final double EDGE_MARGIN = 0.02;
    private static final double FIT_EPSILON = 1.0E-7;

    private SpaceAperturePlacement() {
    }

    static Optional<Result> fit(
            SpaceFrame frame,
            SpaceAperture aperture,
            SpaceVector entry,
            SpaceVector desired,
            SpaceEntityShape shape
    ) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(aperture, "aperture");
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(desired, "desired");
        Objects.requireNonNull(shape, "shape");

        SpaceEntityShape.Projection horizontal = shape.projectOnto(frame.right());
        SpaceEntityShape.Projection vertical = shape.projectOnto(frame.up());
        Optional<Range> horizontalRange = anchorRange(
                aperture.halfWidth(), horizontal);
        Optional<Range> verticalRange = anchorRange(
                aperture.halfHeight(), vertical);
        if (horizontalRange.isEmpty() || verticalRange.isEmpty()) {
            return Optional.empty();
        }

        SpaceVector localEntry = frame.toLocalPoint(entry);
        double fittedX = horizontalRange.get().clamp(localEntry.x());
        double fittedY = verticalRange.get().clamp(localEntry.y());
        SpaceVector adjustment = frame.right().multiply(fittedX - localEntry.x())
                .add(frame.up().multiply(fittedY - localEntry.y()));
        return Optional.of(new Result(
                entry.add(adjustment),
                desired.add(adjustment),
                adjustment));
    }

    private static Optional<Range> anchorRange(
            double halfAperture,
            SpaceEntityShape.Projection projection
    ) {
        double margin = projection.size() + EDGE_MARGIN * 2.0
                <= halfAperture * 2.0 + FIT_EPSILON ? EDGE_MARGIN : 0.0;
        double minimum = -halfAperture - projection.minimum() + margin;
        double maximum = halfAperture - projection.maximum() - margin;
        return minimum <= maximum + FIT_EPSILON
                ? Optional.of(new Range(minimum, maximum)) : Optional.empty();
    }

    record Result(
            SpaceVector entry,
            SpaceVector desired,
            SpaceVector adjustment
    ) {
    }

    private record Range(double minimum, double maximum) {

        double clamp(double value) {
            return Math.clamp(value, minimum, maximum);
        }
    }
}
