package org.encinet.mik.feature.world.buildings;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Immutable index prepared by the catalog worker, not by game query handlers. */
record BuildingCatalogSnapshot(BuildingCatalog catalog, Map<String, List<SearchEntry>> worlds,
                               boolean verified, long verifiedAt) {
    static final int REFRESH_MINUTES = 5;
    private static final long MAX_AGE_NANOS = TimeUnit.MINUTES.toNanos(REFRESH_MINUTES + 1);
    private static final Comparator<Match> NEAREST = Comparator.comparingDouble(Match::distanceSquared)
            .thenComparing(match -> match.entry().id());

    BuildingCatalogSnapshot(BuildingCatalog catalog) {
        this(catalog, Map.copyOf(catalog.entries().stream().map(SearchEntry::new).collect(Collectors.groupingBy(
                entry -> entry.building().world(), Collectors.toUnmodifiableList()))), false, 0);
    }

    BuildingCatalogSnapshot verified(long now) { return new BuildingCatalogSnapshot(catalog, worlds, true, now); }

    BuildingCatalogSnapshot unverified() {
        return verified ? new BuildingCatalogSnapshot(catalog, worlds, false, 0) : this;
    }

    Optional<NearbyBuildings> nearby(String world, double x, double y, double z, boolean chinese,
                                     NearbyBuildings.Search query, long now) {
        // Cached entries may have been withdrawn while we could not reach the public catalog.
        if (!verified || now - verifiedAt >= MAX_AGE_NANOS) return Optional.empty();
        String filter = query.filter().toLowerCase(Locale.ROOT);
        boolean exact = BuildingCatalog.isId(filter);
        var candidates = worlds.getOrDefault(world, List.of());
        int end = Math.min(candidates.size(), query.offset() + query.limit());
        var nearest = new PriorityQueue<Match>(Math.max(1, end), NEAREST.reversed());
        int matches = 0;
        for (var candidate : candidates) {
            var entry = candidate.building();
            boolean matched = exact ? entry.id().equals(filter) : filter.isEmpty()
                    || candidate.nameZh().contains(filter) || candidate.nameEn().contains(filter) || entry.type().equals(filter);
            if (!matched) continue;
            matches++;
            double dx = x - entry.x(), dy = y - entry.y(), dz = z - entry.z();
            double squared = dx * dx + dy * dy + dz * dz;
            if (nearest.size() >= end && squared > nearest.element().distanceSquared()) continue;
            nearest.add(new Match(entry, squared));
            if (nearest.size() > end) nearest.remove();
        }
        var page = nearest.stream().sorted(NEAREST).skip(query.offset()).map(match -> match.view(chinese)).toList();
        return Optional.of(new NearbyBuildings(world, query.filter(), query.offset(), matches, page,
                end < matches ? end : null));
    }

    private record SearchEntry(BuildingCatalog.Entry building, String nameZh, String nameEn) {
        SearchEntry(BuildingCatalog.Entry building) {
            this(building, building.nameZh().toLowerCase(Locale.ROOT), building.nameEn().toLowerCase(Locale.ROOT));
        }
    }

    private record Match(BuildingCatalog.Entry entry, double distanceSquared) {
        NearbyBuildings.Building view(boolean chinese) {
            return new NearbyBuildings.Building(entry.id(), chinese ? entry.nameZh() : entry.nameEn(),
                    entry.type(), entry.x(), entry.y(), entry.z(), (int) Math.round(Math.sqrt(distanceSquared)));
        }
    }
}
