package org.encinet.mik.module.plot;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

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
    private static final Particle.DustOptions COMPOSED =
            new Particle.DustOptions(Color.fromRGB(205, 125, 255), 1.0F);
    private static final int MAX_PARTICLES = 256;
    private static final double VIEW_DISTANCE = 24;
    private final PlotPreviewCache cache;

    PlotSpatialPreview(PlotPreviewCache cache) { this.cache = cache; }

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
        if (end != null && end.world().equals(world)) remaining -= point(player, end, SELECTION);
        if (shape == null && start != null && end != null
                && start.world().equals(world) && end.world().equals(world))
            shape = PlotSelectionShape.of(start, end);
        Location eye = player.getEyeLocation();
        var viewer = new PlotPreviewGeometry.Point(eye.getX(), eye.getY(), eye.getZ());
        var prepared = plot != null && plot.world().equals(world) ? cache.request(plot.cells()) : null;
        List<PlotPreviewGeometry.Edge> saved = prepared == null ? List.of()
                : prepared.index().nearby(viewer, VIEW_DISTANCE, 64).edges();
        List<PlotPreviewGeometry.Edge> candidate = shape != null && shape.world().equals(world) && !shape.empty()
                ? selection(shape, viewer) : List.of();
        if (!PlotPreviewGeometry.sameBoundary(saved, candidate))
            remaining -= render(player, candidate, COMPOSED, Math.min(160, remaining));
        render(player, saved, CORE, remaining);
    }

    private List<PlotPreviewGeometry.Edge> selection(PlotSelectionShape shape, PlotPreviewGeometry.Point viewer) {
        if (!shape.composite()) return PlotPreviewGeometry.box(shape.alignedBounds());
        var prepared = cache.request(shape.cells());
        return prepared == null ? List.of() : prepared.index().nearby(viewer, VIEW_DISTANCE, 64).edges();
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
