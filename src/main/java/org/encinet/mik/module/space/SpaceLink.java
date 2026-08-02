package org.encinet.mik.module.space;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A topological stitching between two congruent spatial surfaces.
 * Enabled entrances are always accompanied by their exact inverse routes.
 */
public record SpaceLink(
        String id,
        SpaceSurface first,
        SpaceSurface second,
        SpaceLinkEntrances entrances
) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,63}");

    public SpaceLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(entrances, "entrances");
        id = id.trim();
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Space link id must match " + ID_PATTERN.pattern() + ": " + id);
        }
        if (first.id().equals(second.id())) {
            throw new IllegalArgumentException("A spatial link requires two distinct surfaces");
        }
        if (!first.world().equalsIgnoreCase(second.world())) {
            throw new IllegalArgumentException(
                    "Immersive spatial links must remain in the same world");
        }
        if (!first.aperture().approximatelyEquals(second.aperture())) {
            throw new IllegalArgumentException(
                    "Linked spatial surfaces must have identical aperture dimensions");
        }
    }

    List<SpaceRoute> routes() {
        SpaceRoute firstForward = new SpaceRoute(
                id + ":first-forward-to-second-forward", this, first, second);
        SpaceRoute firstInverse = new SpaceRoute(
                id + ":second-reverse-to-first-reverse",
                this, second.reversed(), first.reversed());
        if (entrances == SpaceLinkEntrances.FIRST) {
            return List.of(firstForward, firstInverse);
        }
        return List.of(
                firstForward,
                firstInverse,
                new SpaceRoute(id + ":second-forward-to-first-forward",
                        this, second, first),
                new SpaceRoute(id + ":first-reverse-to-second-reverse",
                        this, first.reversed(), second.reversed()));
    }
}
