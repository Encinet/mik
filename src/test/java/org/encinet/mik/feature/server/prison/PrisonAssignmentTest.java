package org.encinet.mik.feature.server.prison;

import org.encinet.mik.feature.server.ban.BanRecord;
import org.encinet.mik.feature.world.plot.Plot;
import org.encinet.mik.feature.world.plot.PlotGeometry.Cell;
import org.encinet.mik.feature.world.plot.PlotPrisons.Region;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrisonAssignmentTest {
    private static final UUID WORLD = new UUID(3, 5);
    private static final Plot FIRST = plot(2, WORLD, null, "First");
    private static final Plot SECOND = plot(3, WORLD, null, "Second");
    private static final Plot THIRD = plot(4, WORLD, null, "Third");

    @Test
    void addingAndRemovingAPrisonOnlyMovesAccountsAssignedToThatPrison() {
        List<Region> before = List.of(region(FIRST), region(SECOND));
        List<Region> after = List.of(region(THIRD), region(SECOND), region(FIRST));
        int moved = 0;
        for (int i = 0; i < 256; i++) {
            BanRecord ban = ban(new UUID(1, i));
            var original = select(ban, before);
            var expanded = select(ban, after);
            assertEquals(original, select(ban, before.reversed()), "Catalog order does not enter the score");
            if (expanded.plot().equals(THIRD)) moved++;
            else assertEquals(original, expanded);
        }
        assertTrue(moved > 0 && moved < 256);
    }

    @Test
    void addingACellDoesNotReassignAccountsBetweenExistingCells() {
        Plot a = plot(5, WORLD, FIRST.id(), "A");
        Plot b = plot(6, WORLD, FIRST.id(), "B");
        Plot c = plot(7, WORLD, FIRST.id(), "C");
        for (int i = 0; i < 256; i++) {
            BanRecord ban = ban(new UUID(1, i));
            var before = select(ban, List.of(region(FIRST, a, b)));
            var after = select(ban, List.of(region(FIRST, b, c, a)));
            if (!after.cell().equals(c)) assertEquals(before.cell(), after.cell());
        }
    }

    @Test
    void nameAndAvailabilityDoNotChangeAnOtherwiseValidRoom() {
        var ban = ban(new UUID(1, 4));
        assertNull(select(ban(null), List.of(region(FIRST))));
        assertEquals(FIRST, select(ban, List.of(region(FIRST))).cell());
        assertTrue(select(ban, List.of(region(FIRST))).sameRoom(select(ban,
                List.of(region(new Plot(FIRST.id(), WORLD, FIRST.owner(), "Renamed", FIRST.createdAt(),
                        FIRST.cells(), FIRST.members(), FIRST.flags(), null))))));
        Plot unavailable = plot(5, new UUID(3, 10), null, "Unavailable");
        var plots = List.of(region(unavailable), region(FIRST));
        assertEquals(FIRST, PrisonAssignment.select(ban, plots, WORLD::equals).plot());
        assertNull(PrisonAssignment.select(ban, plots, _ -> false));
    }

    private static PrisonAssignment select(BanRecord ban, List<Region> regions) {
        return PrisonAssignment.select(ban, regions, _ -> true);
    }

    private static Region region(Plot plot, Plot... cells) { return new Region(plot, List.of(cells)); }

    private static Plot plot(long id, UUID world, UUID parent, String name) {
        return new Plot(new UUID(2, id), world, new UUID(7, 0), name,
                0, Set.of(Cell.at((int) id * 4, 64, 0)), Map.of(), Map.of(), parent);
    }

    private static BanRecord ban(UUID id) {
        return new BanRecord(1, id, "Jailed", "jailed", "reason", "Admin",
                Instant.EPOCH, Instant.EPOCH, null, null, BanRecord.Origin.MIK);
    }
}
