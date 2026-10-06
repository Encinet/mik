package org.encinet.mik.module.plot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PlotCatalog {
    private static final Comparator<Plot> ORDER = Comparator.comparing(Plot::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Plot::id);
    private final Map<UUID, Plot> source;
    private final List<Plot> all;
    private final Map<UUID, List<Plot>> children;

    PlotCatalog(Map<UUID, Plot> source) {
        this.source = source;
        all = List.copyOf(source.values());
        Map<UUID, List<Plot>> groups = new HashMap<>();
        for (Plot plot : all) {
            if (plot.parentId() != null)
                groups.computeIfAbsent(plot.parentId(), ignored -> new ArrayList<>()).add(plot);
        }
        groups.replaceAll((parent, plots) -> {
            plots.sort(ORDER);
            return List.copyOf(plots);
        });
        children = Map.copyOf(groups);
    }

    boolean matches(Map<UUID, Plot> plots) { return source == plots; }

    List<Plot> all() { return all; }

    List<Plot> childrenOf(UUID parent) { return children.getOrDefault(parent, List.of()); }
}
