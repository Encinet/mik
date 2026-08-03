package org.encinet.mik.module.skript;

import org.bukkit.Location;
import org.bukkit.World;
import org.encinet.mik.module.space.NonEuclideanSpaceService;
import org.encinet.mik.module.space.SpaceLink;
import org.encinet.mik.module.space.SpaceLinkEntrances;
import org.encinet.mik.module.space.SpaceRegistration;
import org.encinet.mik.module.space.SpaceSurface;
import org.encinet.mik.module.space.SpaceVector;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Owns runtime spatial links created by individual Skript files. */
final class MikSkriptSpaceRegistry {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,56}");

    private final @Nullable NonEuclideanSpaceService service;
    private final Map<OwnedLink, SpaceRegistration> registrations = new LinkedHashMap<>();

    MikSkriptSpaceRegistry(@Nullable NonEuclideanSpaceService service) {
        this.service = service;
    }

    void register(
            String owner,
            String rawId,
            Location firstCornerA,
            Location firstCornerB,
            String firstThrough,
            @Nullable String firstUp,
            Location secondCornerA,
            Location secondCornerB,
            String secondThrough,
            @Nullable String secondUp,
            SpaceLinkEntrances entrances
    ) {
        NonEuclideanSpaceService available = requireService();
        String id = normalizeId(rawId);
        OwnedLink key = new OwnedLink(owner, id);
        if (registrations.containsKey(key)) {
            throw new IllegalArgumentException(
                    "MIK space '" + id + "' is already registered by this script");
        }

        SpaceSurface first = surface(
                id + ".first", firstCornerA, firstCornerB, firstThrough, firstUp);
        SpaceSurface second = surface(
                id + ".second", secondCornerA, secondCornerB, secondThrough, secondUp);
        SpaceLink link = new SpaceLink(id, first, second, entrances);
        SpaceRegistration registration = available.register(link);
        registrations.put(key, registration);
    }

    void unregister(String owner, String rawId) {
        String id = normalizeId(rawId);
        SpaceRegistration registration = registrations.remove(new OwnedLink(owner, id));
        if (registration != null) {
            registration.close();
        }
    }

    boolean registered(String rawId) {
        if (service == null) {
            return false;
        }
        try {
            return service.snapshot().link(normalizeId(rawId)).isPresent();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    void clearOwnedBy(String owner) {
        closeMatching(key -> key.owner().equals(owner));
    }

    void clear() {
        closeMatching(_ -> true);
    }

    private SpaceSurface surface(
            String id,
            Location cornerA,
            Location cornerB,
            String rawThrough,
            @Nullable String rawUp
    ) {
        Objects.requireNonNull(cornerA, "cornerA");
        Objects.requireNonNull(cornerB, "cornerB");
        World firstWorld = cornerA.getWorld();
        World secondWorld = cornerB.getWorld();
        if (firstWorld == null || secondWorld == null) {
            throw new IllegalArgumentException("MIK space corners must include a world");
        }
        if (!firstWorld.getName().equalsIgnoreCase(secondWorld.getName())) {
            throw new IllegalArgumentException(
                    "Both corners of a MIK space surface must be in the same world");
        }

        Orientation orientation = orientation("surface '" + id + "'", rawThrough, rawUp);
        return SpaceSurface.betweenCorners(
                id,
                firstWorld.getName(),
                vector(cornerA),
                vector(cornerB),
                orientation.through(),
                orientation.up());
    }

    private void closeMatching(java.util.function.Predicate<OwnedLink> predicate) {
        RuntimeException failure = null;
        var iterator = registrations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<OwnedLink, SpaceRegistration> entry = iterator.next();
            if (!predicate.test(entry.getKey())) {
                continue;
            }
            iterator.remove();
            try {
                entry.getValue().close();
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private NonEuclideanSpaceService requireService() {
        if (service == null) {
            throw new IllegalStateException("MIK non-Euclidean space service is unavailable");
        }
        return service;
    }

    private static String normalizeId(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("MIK space ID must not be empty");
        }
        String id = raw.trim();
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "MIK space ID must be 1-57 lowercase ASCII letters, numbers, '.', '_' or '-'"
                            + " and must start with a letter or number");
        }
        return id;
    }

    private static SpaceVector direction(String raw, String field) {
        if (raw == null) {
            throw new IllegalArgumentException("MIK space " + field + " direction is missing");
        }
        SpaceVector result = switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "north" -> new SpaceVector(0.0, 0.0, -1.0);
            case "south" -> new SpaceVector(0.0, 0.0, 1.0);
            case "east" -> new SpaceVector(1.0, 0.0, 0.0);
            case "west" -> new SpaceVector(-1.0, 0.0, 0.0);
            case "up" -> new SpaceVector(0.0, 1.0, 0.0);
            case "down" -> new SpaceVector(0.0, -1.0, 0.0);
            default -> null;
        };
        if (result == null) {
            throw new IllegalArgumentException("MIK space " + field
                    + " must be north, south, east, west, up or down");
        }
        return result;
    }

    private static SpaceVector defaultUp(SpaceVector through) {
        return Math.abs(through.normalized().y()) > 0.99
                ? new SpaceVector(0.0, 0.0, -1.0)
                : new SpaceVector(0.0, 1.0, 0.0);
    }

    static void validateOrientation(
            String surface, String rawThrough, @Nullable String rawUp) {
        orientation(surface, rawThrough, rawUp);
    }

    private static Orientation orientation(
            String surface, String rawThrough, @Nullable String rawUp) {
        SpaceVector through = direction(rawThrough, surface + " through");
        SpaceVector up = rawUp == null
                ? defaultUp(through) : direction(rawUp, surface + " up");
        if (Math.abs(through.dot(up)) > 1.0E-9) {
            throw new IllegalArgumentException(
                    "MIK space " + surface
                            + " up direction must be perpendicular to through");
        }
        return new Orientation(through, up);
    }

    private static SpaceVector vector(Location location) {
        return new SpaceVector(location.getX(), location.getY(), location.getZ());
    }

    private record OwnedLink(String owner, String id) {

        private OwnedLink {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(id, "id");
        }
    }

    private record Orientation(SpaceVector through, SpaceVector up) {
    }
}
