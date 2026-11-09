package org.encinet.mik.feature.world.plot;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Viewer-only construction-core and XYZ selection cues while a plot menu is visible. */
final class PlotSpatialPreview {
    private static final Particle.DustOptions CORE =
            new Particle.DustOptions(Color.fromRGB(66, 202, 221), 0.8F);
    private static final Particle.DustOptions FIRST_POINT =
            new Particle.DustOptions(Color.fromRGB(96, 220, 125), 1.0F);
    private static final Particle.DustOptions SELECTION =
            new Particle.DustOptions(Color.fromRGB(255, 192, 75), 1.0F);
    private static final int MAX_PARTICLES = 96;
    private static final double VIEW_DISTANCE = 24;
    private final PlotPreviewCache cache;
    private final PlotBoundarySlices slices;
    private PlotBoundaryDisplays displays;
    private final java.util.function.BiConsumer<Player, PlotBoundaryFrame> output;

    PlotSpatialPreview(PlotPreviewCache cache) { this(cache, null); }
    PlotSpatialPreview(PlotPreviewCache cache, java.util.function.BiConsumer<Player, PlotBoundaryFrame> output) {
        this.cache = cache;
        slices = new PlotBoundarySlices(cache);
        this.output = output == null ? (viewer, frame) -> {
            if (displays != null) displays.show(viewer, frame.lines(), frame.labels());
        } : output;
    }

    void enable(Plugin plugin) { displays = new PlotBoundaryDisplays(); displays.enable(plugin); }
    void forget(UUID player) { if (displays != null) displays.forget(player); }
    void disable() {
        if (displays != null) displays.disable();
        displays = null;
        slices.close();
    }

    void show(Player player, Plot plot, Map<UUID, PlotPosition> first,
                     Map<UUID, PlotPosition> second) {
        show(player, plot, first.get(player.getUniqueId()), second.get(player.getUniqueId()));
    }

    void show(Player player, Plot plot, PlotPosition start, PlotPosition end) {
        show(player, plot, start, end, null);
    }

    void show(Player player, Plot plot, PlotPosition start, PlotPosition end, PlotSelectionShape shape) {
        UUID world = player.getWorld().getUID();
        int remaining = MAX_PARTICLES;
        if (start != null && start.world().equals(world)) remaining -= point(player, start, FIRST_POINT);
        boolean samePoint = start != null && start.equals(end);
        if (end != null && !samePoint && end.world().equals(world)) remaining -= point(player, end, SELECTION);
        if (shape == null && start != null && end != null
                && start.world().equals(world) && end.world().equals(world))
            shape = PlotSelectionShape.of(start, end);
        Location eye = player.getEyeLocation();
        var viewer = new PlotPreviewGeometry.Point(eye.getX(), eye.getY(), eye.getZ());
        var prepared = plot != null && plot.world().equals(world) ? cache.request(plot.cells()) : null;
        List<PlotPreviewGeometry.Edge> saved = prepared == null ? List.of()
                : prepared.index().nearby(viewer, PlotBoundaryFrame.DISTANCE, 64).edges();
        List<PlotPreviewGeometry.Edge> candidate = shape != null && shape.world().equals(world) && !shape.empty()
                ? selection(shape, viewer) : List.of();
        List<PlotBoundaryFrame.Line> lines = new ArrayList<>();
        List<PlotBoundaryFrame.Label> labels = new ArrayList<>();
        marker(lines, labels, start, world, viewer, samePoint ? "A/B" : "A", Material.LIME_CONCRETE,
                net.kyori.adventure.text.format.NamedTextColor.GREEN);
        if (!samePoint) marker(lines, labels, end, world, viewer, "B", Material.YELLOW_CONCRETE,
                    net.kyori.adventure.text.format.NamedTextColor.GOLD);
        // Local clipped edges can both be empty halfway up a tall building; compare the full geometry identity.
        boolean same = shape == null || prepared != null && (shape.composite()
                ? cache.request(shape.cells()) == prepared
                : prepared.bounds().equals(shape.alignedBounds()) && PlotPreviewGeometry.solid(plot.cells(), prepared.bounds()));
        PlotSelectionShape sliceShape = shape != null ? shape : prepared != null
                ? new PlotSelectionShape(world, null, plot.cells()) : null;
        var currentSlice = slice(player, sliceShape, viewer);
        Material material = same || shape == null ? Material.CYAN_CONCRETE : Material.PURPLE_CONCRETE;
        lines.addAll(PlotBoundaryFrame.lines(currentSlice, viewer, material,
                Math.min(12, PlotBoundaryFrame.MAX_LINES - lines.size())));
        lines.addAll(PlotBoundaryFrame.lines(candidate, viewer, material,
                PlotBoundaryFrame.MAX_LINES - lines.size()));
        if (candidate.isEmpty()) lines.addAll(PlotBoundaryFrame.lines(saved, viewer, Material.CYAN_CONCRETE,
                PlotBoundaryFrame.MAX_LINES - lines.size()));
        output.accept(player, new PlotBoundaryFrame(lines, labels));
        if (!same && !candidate.isEmpty()) {
            render(player, saved, CORE, Math.min(40, remaining));
            if (plot != null && plot.world().equals(world)) render(player,
                    slice(player, new PlotSelectionShape(world, null, plot.cells()), viewer), CORE, Math.min(24, remaining - 40));
        }

    }

    private List<PlotPreviewGeometry.Edge> slice(Player player, PlotSelectionShape shape, PlotPreviewGeometry.Point viewer) {
        if (shape == null || shape.empty() || !shape.world().equals(player.getWorld().getUID())) return List.of();
        Location position = player.getLocation();
        int y = position.getBlockY();
        double height = position.getY() + .06;
        List<PlotPreviewGeometry.Edge> edges;
        if (!shape.composite()) {
            var bounds = shape.alignedBounds();
            if (y < bounds.minimumY() || y > bounds.maximumY()) return List.of();
            edges = PlotPreviewGeometry.box(new PlotSelection.Bounds(bounds.minimumX(), y, bounds.minimumZ(),
                    bounds.maximumX(), y, bounds.maximumZ())).stream()
                    .filter(edge -> edge.first().vertical() == y && edge.second().vertical() == y).toList();
        } else {
            var index = slices.request(shape.cells(), y);
            edges = index == null ? List.of() : index.nearby(new PlotPreviewGeometry.Point(viewer.horizontal(),
                    Math.floorDiv(y, PlotGeometry.CELL) * PlotGeometry.CELL, viewer.forward()), PlotBoundaryFrame.DISTANCE, 24).edges();
        }
        return edges.stream().map(edge -> new PlotPreviewGeometry.Edge(
                new PlotPreviewGeometry.Point(edge.first().horizontal(), height, edge.first().forward()),
                new PlotPreviewGeometry.Point(edge.second().horizontal(), height, edge.second().forward()))).toList();
    }

    private static void marker(List<PlotBoundaryFrame.Line> lines, List<PlotBoundaryFrame.Label> labels,
                               PlotPosition point, UUID world, PlotPreviewGeometry.Point viewer, String name,
                               Material material, net.kyori.adventure.text.format.NamedTextColor color) {
        if (point == null || !world.equals(point.world()) || viewer.distanceSquared(new PlotPreviewGeometry.Point(
                point.x(), point.y(), point.z())) > PlotBoundaryFrame.DISTANCE * PlotBoundaryFrame.DISTANCE) return;
        lines.addAll(PlotBoundaryFrame.lines(PlotPreviewGeometry.box(new PlotSelection(point, point).bounds()),
                viewer, material, Math.min(12, PlotBoundaryFrame.MAX_LINES - lines.size())));
        labels.add(new PlotBoundaryFrame.Label(point, name, color));
    }

    private List<PlotPreviewGeometry.Edge> selection(PlotSelectionShape shape, PlotPreviewGeometry.Point viewer) {
        if (!shape.composite()) return PlotPreviewGeometry.box(shape.alignedBounds());
        var prepared = cache.request(shape.cells());
        return prepared == null ? List.of() : prepared.index().nearby(viewer, PlotBoundaryFrame.DISTANCE, 64).edges();
    }

    private static int render(Player player, List<PlotPreviewGeometry.Edge> edges,
                              Particle.DustOptions options, int budget) {
        Location eye = player.getEyeLocation();
        List<PlotPreviewGeometry.Point> points = PlotPreviewGeometry.sample(edges,
                new PlotPreviewGeometry.Point(eye.getX(), eye.getY(), eye.getZ()), VIEW_DISTANCE, budget);
        for (PlotPreviewGeometry.Point point : points) dust(player, point.horizontal(), point.vertical(), point.forward(), options);
        return points.size();
    }

    private static int point(Player player, PlotPosition point, Particle.DustOptions options) {
        return render(player, PlotPreviewGeometry.box(new PlotSelection(point, point).bounds()), options, 16);
    }

    private static void dust(Player player, double x, double y, double z,
                             Particle.DustOptions options) {
        player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, options);
    }

}
