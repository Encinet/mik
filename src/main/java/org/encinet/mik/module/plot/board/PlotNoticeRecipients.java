package org.encinet.mik.module.plot.board;

import org.encinet.mik.module.plot.Plot;
import org.encinet.mik.module.plot.PlotGeometry;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Finds owners of built plots near a notice location without loading world chunks. */
final class PlotNoticeRecipients {
    private PlotNoticeRecipients() { }

    static List<UUID> nearbyOwners(List<Plot> plots, UUID world, int x, int z,
                                   UUID author, int radius) {
        return nearbyOwners(plots, world, x, z, x, z, author, radius);
    }

    static List<UUID> nearbyOwners(List<Plot> plots, UUID world,
                                   int fromX, int fromZ, int toX, int toZ,
                                   UUID author, int radius) {
        long radiusSquared = (long) radius * radius;
        Map<UUID, Long> closest = new HashMap<>();
        for (Plot plot : plots) {
            if (!plot.world().equals(world) || plot.owner().equals(author)) continue;
            for (PlotGeometry.Cell cell : plot.cells()) {
                long minX = (long) cell.x() * PlotGeometry.CELL;
                long minZ = (long) cell.z() * PlotGeometry.CELL;
                long maxX = minX + PlotGeometry.CELL - 1;
                long maxZ = minZ + PlotGeometry.CELL - 1;
                long dx = Math.max(0L, Math.max(minX - toX, fromX - maxX));
                long dz = Math.max(0L, Math.max(minZ - toZ, fromZ - maxZ));
                long distanceSquared = dx * dx + dz * dz;
                if (distanceSquared <= radiusSquared)
                    closest.merge(plot.owner(), distanceSquared, Math::min);
            }
        }
        return closest.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<UUID, Long> entry) -> entry.getValue())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).toList();
    }
}
