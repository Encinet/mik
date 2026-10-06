package org.encinet.mik.module.plot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.encinet.mik.module.menu.FloatingMenuDecoration;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuPage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.module.i18n.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class PlotMiniaturePreview {
    private static final Comparator<PlotSelectionRegions.Region> REGION_ORDER = Comparator
            .comparingInt((PlotSelectionRegions.Region region) -> region.bounds().minimumY())
            .thenComparingInt(region -> region.bounds().minimumZ())
            .thenComparingInt(region -> region.bounds().minimumX());
    private final PlotPreviewCache cache;
    private final PlotMiniatureSampler sampler;
    private final Map<UUID, View> views = new HashMap<>();
    private final Map<Material, BlockData> palette = new HashMap<>();

    record Stamp(long view, long sample, int blockX, int blockY, int blockZ, int heading) { }
    record Outline(String id, List<PlotPreviewGeometry.Edge> edges, Material material, int budget, boolean limited) { }
    record Scene(Stamp stamp, PlotMiniatureGeometry.Projection projection,
                 PlotMiniatureSampler.Snapshot snapshot, List<Outline> outlines,
                 PlotSelectionState.Points points, boolean sliced, boolean playerFocus, boolean limited, boolean spatial,
                 Regions regions, PlotMiniatureGeometry.Grid grid, boolean terrain, boolean topDown,
                 boolean preparing) { }

    record Regions(List<PlotSelectionRegions.Region> items, PlotSelectionRegions.Region selected,
                   int selectedNumber, boolean open, boolean editing, FloatingMenuPage page) { }

    PlotMiniaturePreview(PlotPreviewCache cache) {
        this.cache = cache;
        sampler = new PlotMiniatureSampler(cache);
    }

    void enable(org.bukkit.plugin.Plugin plugin) { sampler.enable(plugin); }
    void disable() { sampler.disable(); views.clear(); palette.clear(); }
    void forget(UUID player) { sampler.forget(player); views.remove(player); }

    void rotate(Player player, UUID plotId, double degrees) {
        View view = view(player, plotId);
        view.rotation = (view.rotation + degrees + 360) % 360;
        view.revision++;
    }

    void slice(Player player, UUID plotId) {
        View view = view(player, plotId);
        view.sliced = !view.sliced;
        view.sliceY = Math.clamp((long) player.getLocation().getBlockY() + 2,
                player.getWorld().getMinHeight(), player.getWorld().getMaxHeight() - 1);
        view.revision++;
    }

    void layer(Player player, UUID plotId, int direction) {
        View view = view(player, plotId);
        if (!view.sliced) view.sliceY = player.getLocation().getBlockY() + 2;
        else if (view.bounds != null) view.sliceY = view.bounds.maximumY();
        view.sliced = true;
        view.sliceY = Math.clamp((long) view.sliceY + direction,
                player.getWorld().getMinHeight(), player.getWorld().getMaxHeight() - 1);
        view.revision++;
    }

    void topDown(Player player, UUID plotId) {
        View view = view(player, plotId);
        view.topDown = !view.topDown;
        view.revision++;
    }

    void terrain(Player player, UUID plotId) {
        View view = view(player, plotId);
        view.terrain = !view.terrain;
        if (!view.terrain) sampler.forget(player.getUniqueId());
        view.revision++;
    }

    void focus(Player player, UUID plotId) {
        View view = view(player, plotId);
        if (view.regionFocus) {
            view.regionFocus = false;
            view.playerFocus = false;
        } else view.playerFocus = !view.playerFocus;
        view.focus = player.getLocation();
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
                view.regionFocus = false;
            }
            view.revision++;
        }
        PlotSelectionRegions.Region selected = view.selectedRegion;
        if (draft.base().edit() != null) {
            view.regionsOpen = true;
            if (view.selectedNumber == 0 || !draft.base().edit().region().equals(view.selectedRegion)) {
                view.selectedRegion = draft.base().edit().region();
                view.selectedNumber = regionIndex(cache.regions(draft.base().edit().original().candidate()),
                        view.selectedRegion) + 1;
                view.regionFocus = true;
            }
            var points = draft.base().points();
            selected = points.first() != null && points.second() != null
                    ? new PlotSelectionRegions.Region(PlotSelectionShape.of(points.first(), points.second()).alignedBounds())
                    : draft.base().edit().region();
        }
        int number = draft.base().edit() == null ? selected == null ? 0 : regionIndex(view.regions, selected) + 1 : view.selectedNumber;
        FloatingMenuPage page = new FloatingMenuPage(view.regionPage, view.regions.size(), 6);
        view.regionPage = page.index();
        return new Regions(view.regions, selected, number, view.regionsOpen || draft.base().edit() != null,
                draft.base().edit() != null, page);
    }

    void openRegions(Player player, UUID plotId, boolean open) {
        View view = view(player, plotId);
        view.regionsOpen = open;
        if (!open) view.regionFocus = false;
        view.revision++;
    }

    void regionPage(Player player, UUID plotId, int page) {
        View view = view(player, plotId);
        view.regionPage = page;
        view.selectedRegion = null;
        view.selectedNumber = 0;
        view.regionFocus = false;
        view.revision++;
    }

    void selectRegion(Player player, UUID plotId, PlotSelectionRegions.Region region) {
        View view = view(player, plotId);
        int index = region == null ? -1 : regionIndex(view.regions, region);
        if (index < 0) throw new PlotProblem(Message.PLOT_REGION_CHANGED);
        view.selectedRegion = region;
        view.selectedNumber = index + 1;
        view.regionFocus = true;
        view.revision++;
    }

    private View view(Player player, UUID plotId) {
        View view = views.get(player.getUniqueId());
        UUID world = player.getWorld().getUID();
        if (view == null || !Objects.equals(view.plotId, plotId) || !view.world.equals(world)) {
            view = new View(plotId, world, player.getLocation());
            views.put(player.getUniqueId(), view);
            sampler.forget(player.getUniqueId());
        }
        return view;
    }

    Scene scene(Player player, Plot plot, PlotSelectionState.Draft draft, boolean spatial) {
        View view = view(player, plot == null ? null : plot.id());
        Regions regions = regions(player, plot == null ? null : plot.id(), draft);
        Set<PlotGeometry.Cell> saved = plot == null ? Set.of() : plot.cells();
        var prepared = cache.request(saved);
        PlotSelection.Bounds bounds = bounds(view, draft, prepared == null ? null : prepared.bounds());
        var outlines = spatial ? outlines(draft, prepared, bounds, regions.open()) : List.<Outline>of();
        boolean preparing = !saved.isEmpty() && prepared == null || preparing(draft.base().candidate());
        if (view.draftRevision != draft.revision() || view.saved != saved
                || view.outlineSpatial != spatial || !view.outlines.equals(outlines) || !bounds.equals(view.bounds)
                || view.preparing != preparing) {
            view.outlines = outlines;
            view.draftRevision = draft.revision();
            view.saved = saved;
            view.outlineSpatial = spatial;
            view.bounds = bounds;
            view.preparing = preparing;
            view.revision++;
        }
        var grid = PlotMiniatureGeometry.Grid.withinHeight(bounds,
                player.getWorld().getMinHeight(), player.getWorld().getMaxHeight());
        var snapshot = spatial && view.terrain ? sampler.request(player.getUniqueId(), player.getWorld(), grid)
                : new PlotMiniatureSampler.Snapshot(0, true, 0, List.of(), List.of(), false);
        var projection = new PlotMiniatureGeometry.Projection(bounds, view.topDown ? 0 : view.rotation,
                view.topDown ? 90 : PlotMiniatureGeometry.TILT);
        boolean limited = snapshot.limited();
        for (Outline outline : outlines) {
            limited |= outline.limited();
            int visible = 0;
            for (var edge : outline.edges()) {
                if (PlotMiniatureGeometry.clipped(edge, bounds) != null && ++visible > outline.budget()) {
                    limited = true;
                    break;
                }
            }
        }
        Location position = player.getLocation();
        return new Scene(new Stamp(view.revision, snapshot.revision(), (int) Math.floor(position.getX() * 4),
                (int) Math.floor(position.getY() * 4), (int) Math.floor(position.getZ() * 4),
                Math.round(position.getYaw() / 15)), projection, snapshot,
                outlines, draft.base().points(), view.sliced, view.playerFocus || view.regionFocus, limited, spatial,
                regions, grid, view.terrain, view.topDown, preparing);
    }

    void decorate(FloatingMenuDefinition.Builder menu, Player player, Scene scene) {
        if (!scene.spatial()) return;
        var projection = scene.projection();
        for (var voxel : scene.snapshot().terrain())
            volume(menu, "terrain:" + voxel.index(), projection, voxel.box(), voxel.block(), 0);
        for (int index = 0; index < scene.snapshot().unknownBoxes().size(); index++)
            volume(menu, "unknown:" + index, projection, scene.snapshot().unknownBoxes().get(index),
                    block(Material.GRAY_STAINED_GLASS), 0);
        for (Outline outline : scene.outlines()) {
            int visible = 0;
            int index = 0;
            for (var edge : outline.edges()) {
                var box = PlotMiniatureGeometry.clipped(edge, projection.bounds());
                if (box != null && visible++ < outline.budget())
                    volume(menu, outline.id() + ":" + index, projection, box, block(outline.material()), 0.012);
                index++;
            }
        }
        if (scene.regions().open()) {
            if (!scene.regions().editing()) {
                for (int index = scene.regions().page().fromIndex(); index < scene.regions().page().toIndex(); index++) {
                    var region = scene.regions().items().get(index);
                    if (!region.equals(scene.regions().selected())) {
                        regionOutline(menu, "region:" + index, projection, region, Material.LIGHT_GRAY_CONCRETE, 0.008);
                        regionNumber(menu, "region-number:" + index, projection, region, index + 1, NamedTextColor.GRAY);
                    }
                }
            }
            var selected = scene.regions().selected();
            if (selected != null) {
                regionOutline(menu, "selected-region", projection, selected, Material.LIME_CONCRETE, 0.025);
                regionNumber(menu, "region-number", projection, selected, scene.regions().selectedNumber(), NamedTextColor.GREEN);
            }
        }
        marker(menu, "first", projection, scene.points().first(), Material.LIME_CONCRETE);
        marker(menu, "second", projection, scene.points().second(), Material.YELLOW_CONCRETE);
        Location playerAt = player.getLocation();
        if (projection.contains(playerAt.getX(), playerAt.getY(), playerAt.getZ())) {
            var body = new PlotMiniatureGeometry.Box(playerAt.getX(), playerAt.getY() + 0.3,
                    playerAt.getZ(), 0.4, 0.6, 0.4);
            volume(menu, "player", projection, body, block(Material.WHITE_CONCRETE), 0.045);
            var facing = playerAt.getDirection().setY(0).normalize();
            var nose = new PlotMiniatureGeometry.Box(playerAt.getX() + facing.getX() * 0.8,
                    playerAt.getY() + 0.3, playerAt.getZ() + facing.getZ() * 0.8, 0.2, 0.2, 0.2);
            volume(menu, "player-facing", projection, nose, block(Material.RED_CONCRETE), 0.025);
        }
        menu.decoration(FloatingMenuDecoration.volume("model-base", FloatingMenuPose.at(
                new org.encinet.mik.module.menu.FloatingMenuPoint(0, -1.42, 0.65)),
                block(Material.TINTED_GLASS), 3.1F, 0.025F, 3.1F));
        var bounds = projection.bounds();
        double centerX = (bounds.minimumX() + (double) bounds.maximumX() + 1) / 2;
        double centerZ = (bounds.minimumZ() + (double) bounds.maximumZ() + 1) / 2;
        menu.textDecoration("model-north", projection.point(centerX, bounds.maximumY() + 1.0, bounds.minimumZ()),
                Component.text("N −Z", NamedTextColor.AQUA), 0xA0182420, 0.6F, 0.25F, 0.25F);
        menu.textDecoration("model-east", projection.point(bounds.maximumX() + 1.0, bounds.maximumY() + 1.0, centerZ),
                Component.text("E +X", NamedTextColor.AQUA), 0xA0182420, 0.6F, 0.25F, 0.25F);
    }

    private void marker(FloatingMenuDefinition.Builder menu, String id, PlotMiniatureGeometry.Projection projection,
                        PlotPosition point, Material material) {
        if (point != null && projection.contains(point.x(), point.y(), point.z()))
            volume(menu, id, projection, new PlotMiniatureGeometry.Box(point.x() + 0.5, point.y() + 0.5,
                    point.z() + 0.5, 0, 0, 0), block(material), 0.035);
    }

    private void regionOutline(FloatingMenuDefinition.Builder menu, String id,
                               PlotMiniatureGeometry.Projection projection, PlotSelectionRegions.Region region,
                               Material material, double minimum) {
        int index = 0;
        for (var edge : PlotPreviewGeometry.box(region.bounds())) {
            var box = PlotMiniatureGeometry.clipped(edge, projection.bounds());
            if (box != null) volume(menu, id + ":" + index, projection, box, block(material), minimum);
            index++;
        }
    }

    private void regionNumber(FloatingMenuDefinition.Builder menu, String id,
                              PlotMiniatureGeometry.Projection projection, PlotSelectionRegions.Region region,
                              int number, NamedTextColor color) {
        var bounds = region.bounds();
        if (bounds.minimumY() > projection.bounds().maximumY() + 1.0) return;
        double horizontal = (bounds.minimumX() + (double) bounds.maximumX() + 1) / 2;
        double depth = (bounds.minimumZ() + (double) bounds.maximumZ() + 1) / 2;
        double height = Math.min(bounds.maximumY() + 1.0, projection.bounds().maximumY() + 1.0);
        if (projection.contains(horizontal, height, depth))
            menu.textDecoration(id, projection.point(horizontal, height, depth), Component.text("#" + number, color),
                    0xA0182420, 0.5F, 0.25F, 0.2F);
    }

    private void volume(FloatingMenuDefinition.Builder menu, String id, PlotMiniatureGeometry.Projection projection,
                        PlotMiniatureGeometry.Box box, BlockData data, double minimum) {
        menu.decoration(FloatingMenuDecoration.volume("miniature:" + id, projection.pose(box), data,
                (float) Math.max(minimum, box.width() * projection.scale()),
                (float) Math.max(minimum, box.height() * projection.heightScale()),
                (float) Math.max(minimum, box.depth() * projection.scale())));
    }

    private BlockData block(Material material) { return palette.computeIfAbsent(material, Material::createBlockData); }

    private List<Outline> outlines(PlotSelectionState.Draft draft, PlotPreviewCache.Prepared saved,
                                   PlotSelection.Bounds bounds, boolean regionsOpen) {
        List<Outline> outlines = new ArrayList<>();
        int savedBudget = regionsOpen ? 64 : 96;
        int candidateBudget = regionsOpen ? 96 : 160;
        var savedVisible = saved == null ? new PlotEdgeIndex.Visible(List.of(), false, 0)
                : saved.index().within(bounds, savedBudget + 1);
        List<PlotPreviewGeometry.Edge> savedEdges = savedVisible.edges();
        if (!savedEdges.isEmpty()) outlines.add(new Outline("saved", savedEdges, Material.CYAN_CONCRETE,
                savedBudget, savedVisible.limited()));
        var shape = draft.base().candidate();
        if (shape != null && !shape.empty()) {
            var prepared = shape.composite() ? cache.request(shape.cells()) : null;
            var visible = shape.composite() ? prepared == null ? new PlotEdgeIndex.Visible(List.of(), false, 0)
                    : prepared.index().within(bounds, candidateBudget + 1)
                    : new PlotEdgeIndex.Visible(PlotPreviewGeometry.box(shape.alignedBounds()), false, 0);
            List<PlotPreviewGeometry.Edge> candidate = visible.edges();
            if ((prepared != saved || !shape.composite()) && !candidate.isEmpty()
                    && !PlotPreviewGeometry.sameBoundary(savedEdges, candidate))
                outlines.add(new Outline("candidate", candidate, Material.PURPLE_CONCRETE,
                        candidateBudget, visible.limited()));
        }
        return List.copyOf(outlines);
    }

    private boolean preparing(PlotSelectionShape shape) {
        return shape != null && shape.composite() && !shape.empty() && cache.request(shape.cells()) == null;
    }

    private static int regionIndex(List<PlotSelectionRegions.Region> regions, PlotSelectionRegions.Region region) {
        int index = Collections.binarySearch(regions, region, REGION_ORDER);
        return index >= 0 && regions.get(index).equals(region) ? index : -1;
    }

    private PlotSelection.Bounds bounds(View view, PlotSelectionState.Draft draft,
                                        PlotSelection.Bounds saved) {
        Location focus = view.focus;
        PlotSelection.Bounds bounds = new PlotSelection.Bounds(focus.getBlockX() - 12, focus.getBlockY() - 7,
                focus.getBlockZ() - 12, focus.getBlockX() + 12, focus.getBlockY() + 8, focus.getBlockZ() + 12);
        if (view.regionFocus && (view.regionsOpen || draft.base().edit() != null) && view.selectedRegion != null) {
            var points = draft.base().points();
            var focused = draft.base().edit() != null && points.first() != null && points.second() != null
                    ? PlotSelectionShape.of(points.first(), points.second()).alignedBounds() : view.selectedRegion.bounds();
            bounds = expanded(focused);
        } else if (!view.playerFocus) {
            PlotSelection.Bounds selected = saved;
            PlotSelectionShape shape = draft.base().shape();
            PlotSelection.Bounds proposed = cache.bounds(shape);
            if (proposed != null) selected = union(selected, proposed);
            var points = draft.base().points();
            if (points.first() != null && points.second() != null)
                selected = union(selected, PlotSelectionShape.of(points.first(), points.second()).alignedBounds());
            if (selected != null) bounds = expanded(selected);
        }
        if (view.sliced) {
            int ceiling = Math.max(bounds.minimumY(), Math.min(bounds.maximumY(), view.sliceY));
            bounds = new PlotSelection.Bounds(bounds.minimumX(), bounds.minimumY(), bounds.minimumZ(),
                    bounds.maximumX(), ceiling, bounds.maximumZ());
        }
        return bounds;
    }

    private static PlotSelection.Bounds expanded(PlotSelection.Bounds bounds) {
        return new PlotSelection.Bounds(lower(bounds.minimumX()), lower(bounds.minimumY()), lower(bounds.minimumZ()),
                upper(bounds.maximumX()), upper(bounds.maximumY()), upper(bounds.maximumZ()));
    }

    private static int lower(int coordinate) { return (int) Math.max(Integer.MIN_VALUE, (long) coordinate - 2); }
    private static int upper(int coordinate) { return (int) Math.min(Integer.MAX_VALUE, (long) coordinate + 2); }

    private static PlotSelection.Bounds union(PlotSelection.Bounds first, PlotSelection.Bounds second) {
        if (first == null) return second;
        return new PlotSelection.Bounds(Math.min(first.minimumX(), second.minimumX()),
                Math.min(first.minimumY(), second.minimumY()), Math.min(first.minimumZ(), second.minimumZ()),
                Math.max(first.maximumX(), second.maximumX()), Math.max(first.maximumY(), second.maximumY()),
                Math.max(first.maximumZ(), second.maximumZ()));
    }

    private static final class View {
        private final UUID plotId;
        private final UUID world;
        private Location focus;
        private double rotation;
        private boolean sliced;
        private int sliceY;
        private boolean topDown;
        private boolean terrain = true;
        private boolean playerFocus;
        private boolean regionFocus;
        private long revision;
        private long draftRevision = Long.MIN_VALUE;
        private Set<PlotGeometry.Cell> saved = Set.of();
        private boolean outlineSpatial;
        private List<Outline> outlines = List.of();
        private PlotSelection.Bounds bounds;
        private boolean preparing;
        private long regionRevision = Long.MIN_VALUE;
        private List<PlotSelectionRegions.Region> regions = List.of();
        private PlotSelectionRegions.Region selectedRegion;
        private int selectedNumber;
        private int regionPage;
        private boolean regionsOpen;

        private View(UUID plotId, UUID world, Location focus) {
            this.plotId = plotId;
            this.world = world;
            this.focus = focus.clone();
        }
    }
}
