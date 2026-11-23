package org.encinet.mik.feature.server.prison;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.encinet.mik.feature.world.plot.PrisonPlotHost;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.encinet.mik.shared.menu.WallPanelPlacement;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Wall-panel source anchored on the top band of a prison cell region: the four corner blocks
 * of the top cell band, any non-air material as backing.
 */
public final class PrisonPanelSource implements WallPanelPlacement.Source {
    private final UUID worldUid;
    private final List<Block> blocks;
    private final int height;
    private final int minX, maxX, minZ, maxZ;

    private PrisonPanelSource(UUID worldUid, List<Block> blocks, int height, int minX, int maxX, int minZ, int maxZ) {
        this.worldUid = worldUid;
        this.blocks = blocks;
        this.height = height;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    /** Null when the region's world is not loaded or the region is empty. */
    public static PrisonPanelSource forCell(PrisonPlotHost host, UUID playerId, Plot prisonPlot) {
        Plot cell = PrisonCell.cellOf(host, playerId, prisonPlot);
        int top = cell.cells().stream().mapToInt(Cell::y).max().orElse(0);
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Cell cellBox : cell.cells()) if (cellBox.y() == top) {
            minX = Math.min(minX, cellBox.x() * 4);
            maxX = Math.max(maxX, cellBox.x() * 4);
            minZ = Math.min(minZ, cellBox.z() * 4);
            maxZ = Math.max(maxZ, cellBox.z() * 4);
        }
        if (minX > maxX || minZ > maxZ) return null;
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(cell.world());
        if (world == null) return null;
        int y = top * 4 + 1;
        List<Block> anchors = List.of(
                world.getBlockAt(minX, y, minZ),
                world.getBlockAt(maxX, y, minZ),
                world.getBlockAt(minX, y, maxZ),
                world.getBlockAt(maxX, y, maxZ)).stream()
                .distinct().filter(Objects::nonNull).toList();
        if (anchors.isEmpty()) return null;
        return new PrisonPanelSource(world.getUID(), anchors, y, minX, maxX, minZ, maxZ);
    }

    @Override public List<Block> blocks() { return blocks; }

    @Override public int height() { return height; }

    @Override public double centerX() { return (minX + 1 + maxX) / 2.0; }

    @Override public double centerZ() { return (minZ + 1 + maxZ) / 2.0; }

    @Override public boolean contains(int blockX, int blockZ) {
        return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }

    @Override public UUID worldUid() { return worldUid; }

    @Override public Predicate<Material> baseMaterial() { return type -> !type.isAir(); }
}
