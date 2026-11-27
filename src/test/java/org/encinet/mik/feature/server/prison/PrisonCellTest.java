package org.encinet.mik.feature.server.prison;

import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
        assertEquals(prison, PrisonCell.cellOf(new Region(prison, List.of()), PLAYER),
                "No sub-plots means the plot itself is the cell");
    }

    @Test
    void subplotsAreChosenByHashOrder() {
        Plot prison = plot("aabbccdd-0000-0000-0000-000000000002", Map.of(), Set.of(Cell.at(0, 64, 0)));
        Plot first  = plot("aabbccdd-0000-0000-0000-000000000003", prison.id(), Set.of(Cell.at(4, 64, 0)));
        Plot second = plot("aabbccdd-0000-0000-0000-000000000004", prison.id(), Set.of(Cell.at(8, 64, 0)));
        Region region = new Region(prison, List.of(first, second));
        int index = PrisonCell.index(PLAYER, prison.id(), 2);
        assertEquals(index == 0 ? first : second, PrisonCell.cellOf(region, PLAYER),
                "cellOf must pick the sub-plot at the hash position");
    }

    @Test
    void entranceIsInsideAnActualCellAndBelowTheBuildLimit() {
        Plot region = plot("aabbccdd-0000-0000-0000-000000000005", Map.of(),
                Set.of(Cell.at(0, 64, 0), Cell.at(8, 64, 0), Cell.at(0, 72, 0)));
        var entrance = PrisonCell.entrance(region, -64, 67);
        assertNotNull(entrance);
        assertTrue(region.protects((int) entrance.x(), entrance.y(), (int) entrance.z()));
        assertTrue(region.protects((int) entrance.x(), entrance.y() + 1, (int) entrance.z()));
        assertEquals(65, entrance.y());
        assertNull(PrisonCell.entrance(region, -64, 65));
    }

    @Test
    void choosesAPrisonByHashAndTriesTheNextOneWhenItsCellCannotBeResolved() {
        Plot first = plot("aabbccdd-0000-0000-0000-000000000001", Map.of(), Set.of(Cell.at(0, 64, 0)));
        Plot second = plot("aabbccdd-0000-0000-0000-000000000002", Map.of(), Set.of(Cell.at(4, 64, 0)));
        List<Region> prisons = List.of(new Region(first, List.of()), new Region(second, List.of()));
        int selected = PrisonCell.prisonIndex(PLAYER, prisons.size());
        Plot expected = prisons.get(selected).plot();
        Plot fallback = prisons.get(1 - selected).plot();
        BanRecord ban = new BanRecord(1, PLAYER, "Jailed", "jailed", "reason", "Admin",
                Instant.EPOCH, Instant.EPOCH, null, null, BanRecord.Origin.MIK);

        assertEquals(expected, PrisonAssignment.select(ban, prisons, plot -> PrisonCell.entrance(plot, -64, 320)).plot());
        assertEquals(fallback, PrisonAssignment.select(ban, prisons,
                plot -> plot.equals(expected) ? null : PrisonCell.entrance(plot, -64, 320)).plot());
        assertNull(PrisonAssignment.select(ban, prisons, _ -> null));
    }

    private static final UUID worldId = UUID.fromString("aabbccdd-0000-0000-0000-000000000005");

    private static Plot plot(String id, Map<String, Boolean> flags, Set<Cell> cells) {
        return new Plot(UUID.fromString(id), worldId, UUID.randomUUID(), id, 0L, cells, Map.of(), flags, null);
    }

    private static Plot plot(String id, UUID parentId, Set<Cell> cells) {
        return new Plot(UUID.fromString(id), worldId, UUID.randomUUID(), id, 0L, cells, Map.of(), Map.of(), parentId);
    }
}
