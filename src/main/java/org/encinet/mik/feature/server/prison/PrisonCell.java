package org.encinet.mik.feature.server.prison;

import org.bukkit.Location;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.encinet.mik.feature.world.plot.PlotGeometry.CELL;

final class PrisonCell {
    private static final Comparator<Cell> ENTRANCE_ORDER = Comparator.comparingInt(Cell::y).reversed()
            .thenComparingInt(Cell::x).thenComparingInt(Cell::z);

    private PrisonCell() { }

    static Plot cellOf(Region region, UUID playerId) {
        List<Plot> cells = region.cells();
        return cells.isEmpty() ? region.plot() : cells.get(index(playerId, region.plot().id(), cells.size()));
    }

    static int prisonIndex(UUID playerId, int count) {
        return Math.floorMod(playerHash(playerId), count);
    }

    static int index(UUID playerId, UUID plotId, int count) {
        if (count <= 0) throw new IllegalArgumentException("count must be positive");
        long mixed = playerHash(playerId) ^ Long.rotateLeft(mix64(plotId.getMostSignificantBits()), 2)
                ^ Long.rotateLeft(mix64(plotId.getLeastSignificantBits()), 3);
        return Math.floorMod(mixed, count);
    }

    /** Selects a point with space for feet and head within an actual cell of the plot. */
    static Entrance entrance(Plot region, int worldMin, int worldMax) {
        return region.cells().stream()
                .filter(cell -> low(cell, worldMin) <= high(cell, worldMax))
                .min(ENTRANCE_ORDER)
                .map(cell -> new Entrance(region.world(), cell.x() * CELL + CELL / 2.0,
                        low(cell, worldMin), cell.z() * CELL + CELL / 2.0))
                .orElse(null);
    }

    private static int low(Cell cell, int worldMin) {
        return Math.max(cell.y() * CELL + 1, worldMin + 1);
    }

    private static int high(Cell cell, int worldMax) {
        return Math.min(cell.y() * CELL + CELL - 2, worldMax - 2);
    }

    static boolean inside(Plot region, Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().getUID().equals(region.world())
                && region.protects(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    record Entrance(UUID world, double x, int y, double z) { }

    private static long playerHash(UUID playerId) {
        return mix64(playerId.getMostSignificantBits()) ^ Long.rotateLeft(mix64(playerId.getLeastSignificantBits()), 1);
    }

    private static long mix64(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
