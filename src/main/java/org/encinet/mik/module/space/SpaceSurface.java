package org.encinet.mik.module.space;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** A named, world-qualified and oriented rectangular spatial seam. */
public record SpaceSurface(
        String id,
        String world,
        SpaceFrame frame,
        SpaceAperture aperture
) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    public SpaceSurface {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(aperture, "aperture");
        id = id.trim();
        world = world.trim();
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Space surface id must match " + ID_PATTERN.pattern() + ": " + id);
        }
        if (world.isEmpty()) {
            throw new IllegalArgumentException("Space surface world must not be blank");
        }
    }

    /**
     * Builds a rectangular seam from two opposite corners. The through vector is
     * perpendicular to the area and points in the accepted crossing direction.
     */
    public static SpaceSurface betweenCorners(
            String id,
            String world,
            SpaceVector cornerA,
            SpaceVector cornerB,
            SpaceVector through,
            SpaceVector upHint
    ) {
        Objects.requireNonNull(cornerA, "cornerA");
        Objects.requireNonNull(cornerB, "cornerB");
        Objects.requireNonNull(through, "through");
        Objects.requireNonNull(upHint, "upHint");
        SpaceVector center = cornerA.add(cornerB).multiply(0.5);
        SpaceFrame frame = SpaceFrame.fromAxes(center, through, upHint);
        SpaceVector diagonal = cornerB.subtract(cornerA);
        double normalDisplacement = Math.abs(diagonal.dot(frame.forward()));
        double tolerance = 1.0E-7 * Math.max(1.0, diagonal.length());
        if (normalDisplacement > tolerance) {
            throw new IllegalArgumentException(
                    "Space area corners must lie on the same plane perpendicular to through");
        }
        double width = Math.abs(diagonal.dot(frame.right()));
        double height = Math.abs(diagonal.dot(frame.up()));
        return new SpaceSurface(id, world, frame, new SpaceAperture(width, height));
    }

    /** Returns the four world-space corners of this rectangular seam. */
    public List<SpaceVector> corners() {
        double halfWidth = aperture.halfWidth();
        double halfHeight = aperture.halfHeight();
        return List.of(
                frame.fromLocalPoint(new SpaceVector(-halfWidth, -halfHeight, 0.0)),
                frame.fromLocalPoint(new SpaceVector(halfWidth, -halfHeight, 0.0)),
                frame.fromLocalPoint(new SpaceVector(halfWidth, halfHeight, 0.0)),
                frame.fromLocalPoint(new SpaceVector(-halfWidth, halfHeight, 0.0)));
    }

    SpaceSurface reversed() {
        return new SpaceSurface(id, world, frame.reversed(), aperture);
    }

    String normalizedWorld() {
        return world.toLowerCase(Locale.ROOT);
    }
}
