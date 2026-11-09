package org.encinet.mik.feature.world.plot;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import net.kyori.adventure.text.format.NamedTextColor;

/** Immutable, bounded presentation plan; geometry can be verified without a running server. */
record PlotBoundaryFrame(List<Line> lines, List<Label> labels) {
    static final int MAX_LINES = 48;
    static final double DISTANCE = 32;
    record Line(PlotPreviewGeometry.Edge edge, Material material) { }
    record Label(PlotPosition point, String name, NamedTextColor color) { }
    PlotBoundaryFrame { lines = List.copyOf(lines); labels = List.copyOf(labels); }

    static List<Line> lines(List<PlotPreviewGeometry.Edge> edges, PlotPreviewGeometry.Point viewer,
                            Material material, int budget) {
        return edges.stream().map(edge -> PlotPreviewGeometry.clip(edge, viewer, DISTANCE))
                .filter(Objects::nonNull).filter(edge -> edge.length() > .001)
                .sorted(Comparator.comparingDouble(edge -> edge.at(.5).distanceSquared(viewer)))
                .limit(Math.clamp(budget, 0, MAX_LINES)).map(edge -> new Line(edge, material)).toList();
    }
}
