package org.encinet.mik.feature.server.prison;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.encinet.mik.shared.menu.runtime.space.WallPanelPlacement;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.encinet.mik.feature.world.plot.PlotGeometry.CELL;

record PrisonPanelSource(Plot cell, List<Block> blocks, int height, double centerX, double centerZ)
        implements WallPanelPlacement.Source {

    static PrisonPanelSource forCell(PrisonAssignment inmate) {
        var entrance = inmate.entrance();
        var world = Bukkit.getWorld(entrance.world());
        if (world == null) return null;
        int x = (int) entrance.x() - CELL / 2;
        int z = (int) entrance.z() - CELL / 2;
        int y = entrance.y() - 1;
        int lastX = x + CELL - 1;
        int lastZ = z + CELL - 1;
        return new PrisonPanelSource(inmate.cell(), List.of(world.getBlockAt(x, y, z), world.getBlockAt(lastX, y, z),
                world.getBlockAt(x, y, lastZ), world.getBlockAt(lastX, y, lastZ)), y, entrance.x(), entrance.z());
    }

    @Override public boolean contains(int x, int z) {
        return cell.cells().contains(Cell.at(x, height, z));
    }

    @Override public UUID worldUid() { return cell.world(); }

    @Override public Predicate<Material> baseMaterial() { return Material::isSolid; }
}
