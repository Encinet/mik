package org.encinet.mik.feature.server.ban.prison;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.encinet.mik.shared.menu.runtime.space.WallPanelPlacement;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.encinet.mik.feature.world.plot.PlotGeometry.CELL;

record PrisonPanelSource(UUID room, UUID worldUid, Set<Cell> cells, Cell sector, int height, List<Block> blocks)
        implements WallPanelPlacement.Source {

    static PrisonPanelSource forCell(Plot room, Location anchor) {
        var world = anchor.getWorld();
        Cell sector = Cell.at(anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ());
        int x = sector.x() * CELL;
        int z = sector.z() * CELL;
        int y = anchor.getBlockY() - 1;
        int lastX = x + CELL - 1;
        int lastZ = z + CELL - 1;
        return new PrisonPanelSource(room.id(), room.world(), room.cells(), sector, y,
                List.of(world.getBlockAt(x, y, z), world.getBlockAt(lastX, y, z),
                        world.getBlockAt(x, y, lastZ), world.getBlockAt(lastX, y, lastZ)));
    }

    boolean matches(Plot plot, Location anchor) {
        return anchor != null && room.equals(plot.id()) && worldUid.equals(plot.world()) && cells == plot.cells()
                && height == anchor.getBlockY() - 1
                && sector.protects(anchor.getBlockX(), anchor.getBlockY(), anchor.getBlockZ());
    }

    @Override public boolean contains(int x, int z) {
        return cells.contains(new Cell(Math.floorDiv(x, CELL), sector.y(), Math.floorDiv(z, CELL)));
    }

    @Override public double centerX() { return sector.x() * CELL + CELL / 2.0; }
    @Override public double centerZ() { return sector.z() * CELL + CELL / 2.0; }
    @Override public Predicate<Material> baseMaterial() { return Material::isSolid; }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof PrisonPanelSource source && cells == source.cells
                && room.equals(source.room) && worldUid.equals(source.worldUid) && sector.equals(source.sector)
                && height == source.height;
    }

    @Override public int hashCode() { return Objects.hash(room, worldUid, sector, height, System.identityHashCode(cells)); }
}
