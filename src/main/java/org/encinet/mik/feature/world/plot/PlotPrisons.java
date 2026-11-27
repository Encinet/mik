package org.encinet.mik.feature.world.plot;

import org.encinet.mik.shared.i18n.Message;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

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

    PlotPrisons(PlotRegistry registry) {
        this.registry = registry;
    }

    /** A single immutable catalog snapshot for both prison plots and their cells. */
    public List<Region> regions() {
        PlotCatalog catalog = registry.catalog();
        Snapshot cached = snapshot;
        if (cached == null || cached.catalog() != catalog) {
            cached = new Snapshot(catalog, regions(catalog));
            snapshot = cached;
        }
        return cached.regions();
    }

    private static List<Region> regions(PlotCatalog catalog) {
        return catalog.all().stream().filter(PlotPrisons::enabled)
                .sorted(Comparator.comparing(Plot::id))
                .map(plot -> new Region(plot, catalog.childrenOf(plot.id()).stream()
                        .sorted(Comparator.comparing(Plot::id)).toList()))
                .toList();
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
            Map<String, Boolean> flags = new HashMap<>(plot.flags());
            if (enabled(plot)) flags.remove(FLAG);
            else flags.put(FLAG, true);
            staged.put(new Plot(plot.id(), plot.world(), plot.owner(), plot.name(), plot.createdAt(),
                    plot.cells(), plot.members(), flags, plot.parentId()));
            return null;
        });
    }

    public void changeGuard(BiPredicate<List<Region>, List<Region>> guard) {
        registry.beforeCatalogChange(guard == null ? null : next -> {
            List<Region> before = regions();
            List<Region> after = regions(next);
            if (!sameTopology(before, after) && !guard.test(before, after))
                throw new PlotProblem(Message.PLOT_ERROR_PRISON_EDIT_INMATES);
        });
    }

    private static boolean sameTopology(List<Region> before, List<Region> after) {
        if (before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            Region old = before.get(i), next = after.get(i);
            if (!sameGeometry(old.plot(), next.plot()) || old.cells().size() != next.cells().size()) return false;
            for (int j = 0; j < old.cells().size(); j++)
                if (!sameGeometry(old.cells().get(j), next.cells().get(j))) return false;
        }
        return true;
    }

    private static boolean sameGeometry(Plot before, Plot after) {
        return before.id().equals(after.id()) && before.world().equals(after.world()) && before.cells() == after.cells();
    }

    private record Snapshot(PlotCatalog catalog, List<Region> regions) { }
}
