package org.encinet.mik.feature.server.ban.prison;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CancellationException;

import static org.encinet.mik.feature.world.plot.PlotGeometry.CELL;

final class PrisonCell {
    private static final int SEARCH_SECTORS = 8;
    private static final Comparator<Cell> SEARCH_ORDER = Comparator.comparingInt(Cell::y).reversed()
            .thenComparingInt(Cell::x).thenComparingInt(Cell::z);

    private PrisonCell() { }

    /** Computes a bounded search near the top of the room without reading Bukkit state. */
    static List<Sector> sectors(Set<Cell> cells, Height height) {
        var best = new PriorityQueue<>(SEARCH_SECTORS, SEARCH_ORDER.reversed());
        for (Cell cell : cells) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            if (!fits(cell, height)) continue;
            if (best.size() < SEARCH_SECTORS) best.add(cell);
            else if (SEARCH_ORDER.compare(cell, best.element()) < 0) {
                best.remove();
                best.add(cell);
            }
        }
        return best.stream().sorted(SEARCH_ORDER).map(cell -> sector(cell, height)).toList();
    }

    static Sector sector(Cell cell, Height height) {
        return fits(cell, height)
                ? new Sector(cell.x() * CELL, cell.z() * CELL, low(cell, height), high(cell, height)) : null;
    }

    private static boolean fits(Cell cell, Height height) { return low(cell, height) <= high(cell, height); }
    private static int low(Cell cell, Height height) { return Math.max(cell.y() * CELL, height.min() + 1); }
    private static int high(Cell cell, Height height) { return Math.min(cell.y() * CELL + CELL - 2, height.max() - 2); }

    static boolean inside(Plot region, Location location) {
        return location != null && location.getWorld() != null
                && location.getWorld().getUID().equals(region.world())
                && region.protects(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    static boolean containsPlayer(Plot room, Location feet, Player player) {
        if (!inside(room, feet)) return false;
        double radius = player.getWidth() / 2;
        double height = player.getHeight();
        Cell min = Cell.at(Location.locToBlock(feet.getX() - radius), feet.getBlockY(),
                Location.locToBlock(feet.getZ() - radius));
        Cell max = Cell.at(Location.locToBlock(radius == 0 ? feet.getX() : Math.nextDown(feet.getX() + radius)),
                Location.locToBlock(height == 0 ? feet.getY() : Math.nextDown(feet.getY() + height)),
                Location.locToBlock(radius == 0 ? feet.getZ() : Math.nextDown(feet.getZ() + radius)));
        for (int x = min.x(); x <= max.x(); x++)
            for (int y = min.y(); y <= max.y(); y++)
                for (int z = min.z(); z <= max.z(); z++)
                    if (!room.cells().contains(new Cell(x, y, z))) return false;
        return true;
    }

    record Sector(int x, int z, int low, int high) { }
    record Height(int min, int max) { }
}
