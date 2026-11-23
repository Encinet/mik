package org.encinet.mik.feature.world.plot;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** {@link PrisonPlotHost} backed by the plot registry; toggles the flag via a metadata-only staged write. */
public final class PlotPrisonHost implements PrisonPlotHost {
    private final PlotRegistry registry;
    private final List<Predicate<Plot>> guards = new CopyOnWriteArrayList<>();

    public PlotPrisonHost(PlotRegistry registry) {
        this.registry = registry;
    }

    @Override public List<Plot> enabled() {
        return registry.all().stream()
                .filter(plot -> enabled(plot))
                .sorted(Comparator.comparing(Plot::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Plot::id))
                .toList();
    }

    @Override public Plot find(UUID plotId) { return registry.byId(plotId); }

    @Override public boolean isEnabled(UUID plotId) {
        Plot plot = registry.byId(plotId);
        return plot != null && enabled(plot);
    }

    @Override public List<Plot> subplots(UUID plotId) { return registry.childrenOf(plotId); }

    @Override public Plot plotAt(UUID world, int blockX, int blockY, int blockZ) {
        Plot plot = registry.at(world, blockX, blockY, blockZ);
        // The add-on flag lives on the top-level plot; climb to it through the sub-plot chain.
        while (plot != null && !enabled(plot)) plot = plot.parentId() == null ? null : registry.byId(plot.parentId());
        return plot;
    }

    @Override public boolean canManage(Plot plot, UUID actor, boolean staff) {
        return registry.canManage(plot, actor, staff);
    }

    @Override public void setEnabled(UUID plotId, boolean enabled, UUID actor, boolean staff) throws SQLException {
        Plot plot = registry.byId(plotId);
        if (plot == null) throw new SQLException("Plot does not exist: " + plotId);
        if (!registry.permitted(plot, actor, staff, PlotPermission.MANAGE_SETTINGS))
            throw new SQLException("Permission required");
        if (enabled == enabled(plot)) return;
        if (!enabled && !mayToggleOff(plot))
            throw new PlotProblem(org.encinet.mik.shared.i18n.Message.PLOT_ERROR_PRISON_ADDON_INMATES);
        registry.writeBehind(staged -> {
            Plot latest = staged.byId(plotId);
            if (latest == null) throw new SQLException("Plot does not exist: " + plotId);
            if (enabled != enabled(latest)) {
                if (!enabled && !mayToggleOff(latest))
                    throw new PlotProblem(org.encinet.mik.shared.i18n.Message.PLOT_ERROR_PRISON_ADDON_INMATES);
                Map<String, Boolean> flags = new HashMap<>(latest.flags());
                if (enabled) flags.put(PRISON_ADDON_FLAG, true); else flags.remove(PRISON_ADDON_FLAG);
                staged.put(new Plot(latest.id(), latest.world(), latest.owner(), latest.name(),
                        latest.createdAt(), latest.cells(), latest.members(), flags, latest.parentId()));
            }
            return null;
        }).join();
    }

    @Override public boolean mayToggleOff(Plot plot) {
        return !enabled(plot) || guards.stream().allMatch(guard -> guard.test(plot));
    }

    @Override public void addToggleGuard(Predicate<Plot> guard) { guards.add(guard); }

    /** Vetoed only while the add-on is on and any guard objects; used by the plot delete path. */
    public boolean mayDelete(Plot plot) {
        return plot == null || !enabled(plot) || guards.stream().allMatch(guard -> guard.test(plot));
    }

    private static boolean enabled(Plot plot) {
        return plot != null && Boolean.TRUE.equals(plot.flags().get(PRISON_ADDON_FLAG));
    }
}
