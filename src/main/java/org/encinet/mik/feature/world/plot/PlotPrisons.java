package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.i18n.Message;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** Publishes prison plots from the plot catalog and owns their add-on metadata. */
public final class PlotPrisons {
    static final String FLAG = "addon.prison";

    public record Region(Plot plot, List<Plot> cells) {
        public Region {
            cells = List.copyOf(cells);
        }
    }

    private final PlotRegistry registry;
    private volatile Snapshot snapshot;
    private Predicate<UUID> mayRemove = _ -> true;

    PlotPrisons(PlotRegistry registry) {
        this.registry = registry;
    }

    /** A single immutable catalog snapshot for both prison plots and their cells. */
    public List<Region> regions() {
        PlotCatalog catalog = registry.catalog();
        Snapshot cached = snapshot;
        if (cached == null || cached.catalog() != catalog) {
            cached = new Snapshot(catalog, catalog.all().stream().filter(PlotPrisons::enabled)
                    .sorted(Comparator.comparing(Plot::id))
                    .map(plot -> new Region(plot, catalog.childrenOf(plot.id()).stream()
                            .sorted(Comparator.comparing(Plot::id)).toList()))
                    .toList());
            snapshot = cached;
        }
        return cached.regions();
    }

    public Plot at(UUID world, int x, int y, int z) {
        Plot plot = registry.at(world, x, y, z);
        if (plot != null && plot.subPlot()) plot = registry.parentOf(plot);
        return enabled(plot) ? plot : null;
    }

    static boolean enabled(Plot plot) {
        return plot != null && !plot.subPlot() && Boolean.TRUE.equals(plot.flags().get(FLAG));
    }

    void toggle(UUID id, UUID actor, boolean staff) throws SQLException {
        registry.writeBehind(staged -> {
            Plot plot = staged.byId(id);
            if (plot == null || plot.subPlot()) throw new PlotProblem(Message.PLOT_ERROR_ID);
            if (!staff || !staged.permitted(plot, actor, true, PlotPermission.MANAGE_SETTINGS))
                throw new PlotProblem(Message.PLOT_ERROR_OWNER_ONLY);
            requireRemovable(plot);
            Map<String, Boolean> flags = new HashMap<>(plot.flags());
            if (enabled(plot)) flags.remove(FLAG);
            else flags.put(FLAG, true);
            staged.put(new Plot(plot.id(), plot.world(), plot.owner(), plot.name(), plot.createdAt(),
                    plot.cells(), plot.members(), flags, plot.parentId()));
            return null;
        });
    }

    void removalGuard(Predicate<UUID> guard) {
        mayRemove = guard == null ? _ -> true : guard;
    }

    void requireRemovable(Plot plot) {
        if (enabled(plot) && !mayRemove.test(plot.id()))
            throw new PlotProblem(Message.PLOT_ERROR_PRISON_ADDON_INMATES);
    }

    private record Snapshot(PlotCatalog catalog, List<Region> regions) { }
}
