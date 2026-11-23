package org.encinet.mik.feature.server.prison;

import org.encinet.mik.feature.world.plot.PrisonPlotHost;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class PrisonCellTest {
    private static final UUID PLAYER = UUID.fromString("0123e608-0000-0000-0000-000000000000");
    private static final UUID PLOT   = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void indexIsPureAndBounded() {
        for (int count : List.of(1, 2, 3, 5, 64)) {
            int a = PrisonCell.index(PLAYER, PLOT, count);
            assertEquals(a, PrisonCell.index(PLAYER, PLOT, count),
                    "index is a pure function of its inputs");
            assertTrue(a >= 0 && a < count, "index must stay in [0, count), got " + a + " for count " + count);
        }
    }

    @Test
    void indexDistinguishesTheTwoUuidRoles() {
        // Swapping the two UUIDs must not always return the same cell index.
        boolean anyDiffer = false;
        for (int count : List.of(2, 3, 7, 11, 64)) {
            if (PrisonCell.index(PLAYER, PLOT, count) != PrisonCell.index(PLOT, PLAYER, count)) {
                anyDiffer = true;
                break;
            }
        }
        assertTrue(anyDiffer, "The hash must distinguish the two UUID roles for at least one cell count");
    }

    @Test
    void rejectsNonPositiveCellCounts() {
        assertThrows(IllegalArgumentException.class, () -> PrisonCell.index(PLAYER, PLOT, 0));
        assertThrows(IllegalArgumentException.class, () -> PrisonCell.index(PLAYER, PLOT, -1));
    }

    @Test
    void zeroSubplotsFallBackToTheWholePlot() {
        Plot prison = plot("aabbccdd-0000-0000-0000-000000000001", Map.of(), Set.of(Cell.at(0, 64, 0)));
        PrisonPlotHost host = fixedHost(List.of(), prison);
        assertEquals(prison, PrisonCell.cellOf(host, PLAYER, prison),
                "No sub-plots means the plot itself is the cell");
    }

    @Test
    void subplotsAreChosenByHashOrder() {
        Plot prison = plot("aabbccdd-0000-0000-0000-000000000002", Map.of(), Set.of(Cell.at(0, 64, 0)));
        Plot first  = plot("aabbccdd-0000-0000-0000-000000000003", prison.id(), Set.of(Cell.at(4, 64, 0)));
        Plot second = plot("aabbccdd-0000-0000-0000-000000000004", prison.id(), Set.of(Cell.at(8, 64, 0)));
        PrisonPlotHost host = fixedHost(List.of(first, second), prison);
        int index = PrisonCell.index(PLAYER, prison.id(), 2);
        assertEquals(index == 0 ? first : second, PrisonCell.cellOf(host, PLAYER, prison),
                "cellOf must pick the sub-plot at the hash position");
    }

    @Test
    void insideIsAnExactCellTest() {
        Plot region = plot("aabbccdd-0000-0000-0000-000000000005", Map.of(), Set.of(Cell.at(0, 64, 0)));
        assertTrue(PrisonCell.inside(region, 1, 64, 1));
        assertTrue(PrisonCell.inside(region, 3, 67, 3), "The whole 4×4×4 cell counts as inside");
        assertFalse(PrisonCell.inside(region, 4, 64, 0), "The neighbouring cell is not inside");
        assertFalse(PrisonCell.inside(region, 0, 68, 0), "Above the cell band is not inside");
        assertFalse(PrisonCell.inside(region, 0, 60, 0), "Below the cell is not inside");
    }

    private static final UUID worldId = UUID.fromString("aabbccdd-0000-0000-0000-000000000005");

    private static PrisonPlotHost fixedHost(List<Plot> subplots, Plot prison) {
        return new PrisonPlotHost() {
            @Override public List<Plot> enabled() { return List.of(prison); }
            @Override public Plot find(UUID plotId) {
                if (plotId.equals(prison.id())) return prison;
                return subplots.stream().filter(p -> p.id().equals(plotId)).findFirst().orElse(null);
            }
            @Override public boolean isEnabled(UUID plotId) { return true; }
            @Override public List<Plot> subplots(UUID plotId) { return subplots; }
            @Override public Plot plotAt(UUID world, int blockX, int blockY, int blockZ) { return null; }
            @Override public boolean canManage(Plot plot, UUID actor, boolean staff) { return staff; }
            @Override public void setEnabled(UUID plotId, boolean enabled, UUID actor, boolean staff) { }
            @Override public boolean mayToggleOff(Plot plot) { return true; }
            @Override public boolean mayDelete(Plot plot) { return true; }
            @Override public void addToggleGuard(Predicate<Plot> guard) { }
        };
    }

    private static Plot plot(String id, Map<String, Boolean> flags, Set<Cell> cells) {
        return new Plot(UUID.fromString(id), worldId, UUID.randomUUID(), id, 0L, cells, Map.of(), flags, null);
    }

    private static Plot plot(String id, UUID parentId, Set<Cell> cells) {
        return new Plot(UUID.fromString(id), worldId, UUID.randomUUID(), id, 0L, cells, Map.of(), Map.of(), parentId);
    }
}
