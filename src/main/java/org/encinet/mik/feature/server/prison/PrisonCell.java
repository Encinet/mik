package org.encinet.mik.feature.server.prison;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.encinet.mik.feature.world.plot.PrisonPlotHost;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;

import java.util.List;
import java.util.UUID;

/**
 * Stateless cell assignment for prison plots: an inmate's cell is a deterministic hash of the
 * player and plot UUIDs over the plot's sub-plots, so nothing needs to be stored per inmate.
 */
public final class PrisonCell {
    private PrisonCell() { }

    /**
     * Deterministic cell index in {@code [0, count)}; identical for every JVM and version.
     * Uses the splitmix64 finalizer on each of the four UUID words — never {@code UUID.hashCode()}.
     */
    public static int index(UUID playerId, UUID plotId, int count) {
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        long mixed = mix64(playerId.getMostSignificantBits())
                ^ Long.rotateLeft(mix64(playerId.getLeastSignificantBits()), 1)
                ^ Long.rotateLeft(mix64(plotId.getMostSignificantBits()), 2)
                ^ Long.rotateLeft(mix64(plotId.getLeastSignificantBits()), 3);
        return Math.floorMod(mixed, count);
    }

    /** The inmate's cell region: one sub-plot per cell, or the whole plot when it has none. */
    public static Plot cellOf(PrisonPlotHost host, UUID playerId, Plot prisonPlot) {
        List<Plot> subplots = host.subplots(prisonPlot.id());
        return subplots.isEmpty() ? prisonPlot : subplots.get(index(playerId, prisonPlot.id(), subplots.size()));
    }

    /**
     * A standing spot on the top band of the region's cells: the horizontal centroid of the top
     * cell band, one block above the band, clamped to the world's height. Null when the world is
     * not loaded or the region is empty.
     */
    public static Location standing(Plot region) {
        World world = Bukkit.getWorld(region.world());
        if (world == null || region.cells().isEmpty()) return null;
        int top = region.cells().stream().mapToInt(Cell::y).max().orElse(0);
        double centerX = 0;
        double centerZ = 0;
        int bandCells = 0;
        for (Cell cell : region.cells()) if (cell.y() == top) {
            centerX += cell.x() * 4 + 2;
            centerZ += cell.z() * 4 + 2;
            bandCells++;
        }
        if (bandCells == 0) return null;
        centerX /= bandCells;
        centerZ /= bandCells;
        int y = Math.max(world.getMinHeight() + 1, Math.min(top * 4 + 4, world.getMaxHeight()));
        return new Location(world, centerX, y, centerZ);
    }

    /** Exact membership test on block coordinates, the same primitive as {@link Plot#protects}. */
    public static boolean inside(Plot region, int blockX, int blockY, int blockZ) {
        return region != null && region.cells().contains(Cell.at(blockX, blockY, blockZ));
    }

    /** Exact membership test for a location in the region's world. */
    public static boolean inside(Plot region, Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().getUID().equals(region.world())
                && inside(region, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static long mix64(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
