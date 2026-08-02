package org.encinet.mik.module.space;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Immutable spatial topology indexed by the source world of every directed seam. */
public final class SpaceNetwork {

    private static final SpaceNetwork EMPTY = new SpaceNetwork(List.of());

    private final Map<String, SpaceLink> links;
    private final Map<String, List<SpaceRoute>> routesByWorld;

    private SpaceNetwork(Collection<SpaceLink> links) {
        Map<String, SpaceLink> byId = new LinkedHashMap<>();
        Map<String, String> surfaceOwners = new LinkedHashMap<>();
        List<SpaceRoute> routes = new ArrayList<>();
        for (SpaceLink link : links) {
            SpaceLink previous = byId.putIfAbsent(link.id(), link);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate space link id: " + link.id());
            }
            claimSurface(surfaceOwners, link.first(), link.id());
            claimSurface(surfaceOwners, link.second(), link.id());
            routes.addAll(link.routes());
        }
        routes.sort(Comparator
                .comparing((SpaceRoute route) -> route.link().id())
                .thenComparing(SpaceRoute::id));

        Map<String, List<SpaceRoute>> indexed = new LinkedHashMap<>();
        for (SpaceRoute route : routes) {
            indexed.computeIfAbsent(route.source().normalizedWorld(), _ -> new ArrayList<>())
                    .add(route);
        }
        indexed.replaceAll((_, value) -> List.copyOf(value));
        this.links = Collections.unmodifiableMap(new LinkedHashMap<>(byId));
        this.routesByWorld = Map.copyOf(indexed);
    }

    public static SpaceNetwork empty() {
        return EMPTY;
    }

    public static SpaceNetwork of(Collection<SpaceLink> links) {
        return links.isEmpty() ? EMPTY : new SpaceNetwork(List.copyOf(links));
    }

    public Collection<SpaceLink> links() {
        return links.values();
    }

    public Optional<SpaceLink> link(String id) {
        return Optional.ofNullable(links.get(id));
    }

    public Optional<SpaceTransition> trace(
            String sourceWorld,
            SpaceVector from,
            SpaceVector to,
            SpaceVector lookDirection,
            SpaceVector velocity
    ) {
        List<SpaceRoute> routes = routesByWorld.getOrDefault(
                sourceWorld.toLowerCase(Locale.ROOT), List.of());
        for (SpaceRoute route : routes) {
            Optional<SpaceTransition> transition = route.trace(
                    from, to, lookDirection, velocity);
            if (transition.isPresent()) {
                return transition;
            }
        }
        return Optional.empty();
    }

    private static void claimSurface(
            Map<String, String> owners,
            SpaceSurface surface,
            String link
    ) {
        String key = surface.normalizedWorld() + '\u0000' + surface.id();
        String previous = owners.putIfAbsent(key, link);
        if (previous != null) {
            throw new IllegalArgumentException("Space surface '" + surface.id()
                    + "' is already connected by link '" + previous + "'");
        }
    }
}
