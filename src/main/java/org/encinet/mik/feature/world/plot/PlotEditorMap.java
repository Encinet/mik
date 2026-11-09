package org.encinet.mik.feature.world.plot;

import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.shared.i18n.Message;

/** Owns editor navigation independently of the selection draft and its undo history. */
final class PlotEditorMap {
    private static final Comparator<PlotSelectionRegions.Region> REGION_ORDER = Comparator
            .comparingInt((PlotSelectionRegions.Region region) -> region.bounds().minimumY())
            .thenComparingInt(region -> region.bounds().minimumZ())
            .thenComparingInt(region -> region.bounds().minimumX());
    private final PlotPreviewCache cache;
    private final PlotMapTerrain terrain = new PlotMapTerrain();
    private final Map<UUID, View> views = new HashMap<>();

    record Regions(List<PlotSelectionRegions.Region> items, PlotSelectionRegions.Region selected,
                   int selectedNumber, boolean open, boolean editing, int sourceIndex) { }
    record Stamp(long view, long terrain, boolean ready, int playerX, int playerZ) { }
    record Pixels(boolean[] saved, boolean[] candidate) { }
    record Scene(Stamp stamp, PlotMapViewport viewport, int layer, boolean first,
                 Regions regions, PlotSelectionState.Points points, PlotMapTerrain.Snapshot terrain,
                 Pixels pixels, boolean preparing) { }

    PlotEditorMap(PlotPreviewCache cache) { this.cache = cache; }
    void enable(Plugin plugin) { terrain.enable(plugin); }
    void disable() {
        terrain.disable();
        views.values().forEach(this::cancel);
        views.clear();
    }
    void release(UUID player) {
        terrain.forget(player);
        View view = views.get(player);
        if (view != null) cancel(view);
    }
    void forget(UUID player) { release(player); views.remove(player); }
    private void cancel(View view) {
        if (view.job != null) cache.cancel(view.job);
        view.job = null;
    }

    private View view(Player player, UUID plotId) {
        View view = views.get(player.getUniqueId());
        UUID world = player.getWorld().getUID();
        if (view == null || !Objects.equals(view.plotId, plotId) || !view.world.equals(world)) {
            forget(player.getUniqueId());
            view = new View(plotId, world);
            view.viewport = PlotMapViewport.near(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            view.layer = alignedLayer(player, player.getLocation().getBlockY());
            views.put(player.getUniqueId(), view);
        }
        return view;
    }

    static int alignedLayer(Player player, long y) {
        return Math.clamp(Math.floorDiv(y, PlotGeometry.CELL) * PlotGeometry.CELL,
                player.getWorld().getMinHeight(), player.getWorld().getMaxHeight() - PlotGeometry.CELL);
    }

    void corner(Player player, UUID plotId, boolean first) {
        View view = view(player, plotId); view.first = first; view.revision++;
    }
    void focus(Player player, UUID plotId, PlotPosition point) {
        View view = view(player, plotId);
        view.viewport = PlotMapViewport.near(point.x(), point.z());
        view.layer = alignedLayer(player, point.y());
        view.fit = false;
        view.revision++;
    }
    void layer(Player player, UUID plotId, long y) {
        View view = view(player, plotId); view.layer = alignedLayer(player, y); view.revision++;
    }
    void zoom(Player player, UUID plotId, int direction) {
        View view = view(player, plotId); view.viewport = view.viewport.zoom(direction); view.revision++;
    }
    void pan(Player player, UUID plotId, int east, int south) {
        View view = view(player, plotId); view.viewport = view.viewport.pan(east, south); view.revision++;
    }
    void fit(Player player, UUID plotId) { View view = view(player, plotId); view.fit = true; view.focusSelected = false; view.revision++; }
    void near(Player player, UUID plotId) {
        View view = view(player, plotId);
        view.viewport = PlotMapViewport.near(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        view.layer = alignedLayer(player, player.getLocation().getBlockY());
        view.fit = false;
        view.revision++;
    }
    Regions regions(Player player, UUID plotId, PlotSelectionState.Draft draft) {
        View view = view(player, plotId);
        var candidate = draft.base().candidate();
        var preparedRegions = cache.regions(candidate);
        boolean regionsChanged = candidate != null && candidate.composite()
                ? view.regions != preparedRegions : !view.regions.equals(preparedRegions);
        if (view.regionRevision != draft.revision() || regionsChanged) {
            view.regions = preparedRegions;
            view.regionRevision = draft.revision();
            if (draft.base().edit() == null && view.selectedRegion != null && regionIndex(view.regions, view.selectedRegion) < 0) {
                view.selectedRegion = null;
                view.selectedNumber = 0;
                view.fit = true;
            }
            view.revision++;
        }
        PlotSelectionRegions.Region selected = view.selectedRegion;
        if (draft.base().edit() != null) {
            view.regionsOpen = true;
            if (view.selectedNumber == 0 || !draft.base().edit().region().equals(view.selectedRegion)) {
                view.selectedRegion = draft.base().edit().region();
                view.focusSelected = true;
                view.selectedNumber = regionIndex(cache.regions(draft.base().edit().original().candidate()),
                        view.selectedRegion) + 1;
                view.fit = true;
            }
            var points = draft.base().points();
            selected = points.first() != null && points.second() != null
                    ? new PlotSelectionRegions.Region(PlotSelectionShape.of(points.first(), points.second()).alignedBounds())
                    : draft.base().edit().region();
        }
        int number = draft.base().edit() == null ? selected == null ? 0 : regionIndex(view.regions, selected) + 1 : view.selectedNumber;
        int anchor = view.firstRegion == null ? -1 : regionIndex(view.regions,view.firstRegion);
        int sourceIndex = anchor >= 0 ? anchor : Math.clamp(view.regionIndex,0,Math.max(0,view.regions.size()-1));
        return new Regions(view.regions, selected, number, view.regionsOpen || draft.base().edit() != null,
                draft.base().edit() != null, sourceIndex);
    }

    void openRegions(Player player, UUID plotId, boolean open) {
        View view = view(player, plotId);
        view.regionsOpen = open;
        if (!open) { view.selectedRegion = null; view.fit = true; }
        view.revision++;
    }

    void regionPosition(Player player, UUID plotId, int index) {
        View view = view(player, plotId);
        view.regionIndex = Math.clamp(index,0,Math.max(0,view.regions.size()-1));
        view.firstRegion = view.regions.isEmpty() ? null : view.regions.get(view.regionIndex);
        view.revision++;
    }

    void selectRegion(Player player, UUID plotId, PlotSelectionRegions.Region region) {
        View view = view(player, plotId);
        int index = region == null ? -1 : regionIndex(view.regions, region);
        if (index < 0) throw new PlotProblem(Message.PLOT_REGION_CHANGED);
        view.selectedRegion = region;
        view.focusSelected = true;
        view.selectedNumber = index + 1;
        view.fit = true;
        view.revision++;
    }


    Scene scene(Player player, Plot plot, PlotSelectionState.Draft draft) {
        UUID plotId = plot == null ? null : plot.id();
        View view = view(player, plotId);
        Regions regions = regions(player, plotId, draft);
        Set<PlotGeometry.Cell> saved = plot == null ? Set.of() : plot.cells();
        var prepared = cache.request(saved);
        var shape = draft.base().candidate();
        List<PlotSelectionRegions.Region> candidate = cache.regions(shape);
        boolean preparing = !saved.isEmpty() && prepared == null
                || shape != null && !shape.empty() && candidate.isEmpty();
        if (view.fit && !preparing) {
            PlotSelection.Bounds bounds = view.focusSelected && regions.open() && regions.selected() != null ? regions.selected().bounds()
                    : cache.bounds(shape);
            if (bounds == null && prepared != null) bounds = prepared.bounds();
            if (bounds != null) {
                view.viewport = PlotMapViewport.fit(bounds);
                if (view.layer < bounds.minimumY() || view.layer > bounds.maximumY())
                    view.layer = alignedLayer(player, bounds.minimumY());
            }
            view.fit = false;
            view.revision++;
        }
        List<PlotSelectionRegions.Region> savedRegions = prepared == null ? List.of() : prepared.regions();
        var key = new RasterKey(view.viewport, view.layer, savedRegions, candidate);
        if (!key.equals(view.rasterKey)) {
            cancel(view);
            view.rasterKey = key;
            view.pixels = null;
            view.revision++;
        }
        if (view.job == null && view.pixels == null && !preparing) {
            View target = view;
            view.job = new FutureTask<>(() -> {
                try {
                    return new Pixels(PlotMapRaster.layer(key.viewport(), key.layer(), key.saved()),
                            PlotMapRaster.layer(key.viewport(), key.layer(), key.candidate()));
                } catch (CancellationException ignored) { return null; }
            });
            try { cache.execute(view.job); }
            catch (RejectedExecutionException rejected) { cancel(target); }
        }
        if (view.job != null && view.job.isDone()) {
            try { view.pixels = view.job.get(); }
            catch (Exception failure) {
                if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
                System.getLogger(PlotEditorMap.class.getName()).log(System.Logger.Level.WARNING,
                        "Could not prepare plot map", failure);
            }
            view.job = null;
        }
        var snapshot = terrain.request(player.getUniqueId(), player.getWorld(), view.viewport);
        var location = player.getLocation();
        return new Scene(new Stamp(view.revision, snapshot.revision(), view.pixels != null,
                location.getBlockX(), location.getBlockZ()), view.viewport, view.layer, view.first,
                regions, draft.base().points(), snapshot, view.pixels, preparing || view.pixels == null);
    }

    private static int regionIndex(List<PlotSelectionRegions.Region> regions, PlotSelectionRegions.Region region) {
        int index = Collections.binarySearch(regions, region, REGION_ORDER);
        return index >= 0 && regions.get(index).equals(region) ? index : -1;
    }

    // Lists from the preview cache are immutable and reused across player motion and menu redraws.
    private record RasterKey(PlotMapViewport viewport, int layer, List<PlotSelectionRegions.Region> saved,
                             List<PlotSelectionRegions.Region> candidate) { }
    private static final class View {
        final UUID plotId, world;
        PlotMapViewport viewport;
        int layer;
        boolean first = true, fit = true, focusSelected;
        long revision;
        RasterKey rasterKey;
        Pixels pixels;
        FutureTask<Pixels> job;
        long regionRevision = Long.MIN_VALUE;
        List<PlotSelectionRegions.Region> regions = List.of();
        PlotSelectionRegions.Region selectedRegion;
        int selectedNumber, regionIndex;
        PlotSelectionRegions.Region firstRegion;
        boolean regionsOpen;
        View(UUID plotId, UUID world) { this.plotId = plotId; this.world = world; }
    }
}
