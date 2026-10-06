package org.encinet.mik.module.plot.board;

import org.encinet.mik.module.plot.Plot;
import org.encinet.mik.module.plot.PlotGeometry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlotNoticeRecipientsTest {
    @Test
    void findsNearestDistinctOwnersOfBuiltPlotsInTheSameWorld() {
        UUID world = UUID.randomUUID(), otherWorld = UUID.randomUUID();
        UUID author = UUID.randomUUID(), near = UUID.randomUUID(), edge = UUID.randomUUID();
        List<Plot> plots = List.of(
                plot(world, author, 0),
                plot(world, near, 20),
                plot(world, near, 40),
                plot(world, edge, 64),
                plot(world, UUID.randomUUID(), 68),
                plot(otherWorld, UUID.randomUUID(), 1));

        assertEquals(List.of(near, edge),
                PlotNoticeRecipients.nearbyOwners(plots, world, 0, 0, author, 64));
    }

    @Test
    void returnsEveryNearbyOwnerWithoutARecipientCap() {
        UUID world = UUID.randomUUID(), author = UUID.randomUUID();
        List<Plot> plots = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> plot(world, UUID.randomUUID(), index * 4))
                .toList();
        assertEquals(12, PlotNoticeRecipients.nearbyOwners(plots, world, 0, 0, author, 64).size());
    }

    @Test
    void reservationUsesItsFullFootprintInsteadOfThePlayersPosition() {
        UUID world = UUID.randomUUID(), author = UUID.randomUUID();
        UUID byFarEdge = UUID.randomUUID();
        List<Plot> plots = List.of(plot(world, byFarEdge, 120));

        assertEquals(List.of(byFarEdge), PlotNoticeRecipients.nearbyOwners(
                plots, world, 0, 0, 100, 0, author, 20));
        assertEquals(List.of(), PlotNoticeRecipients.nearbyOwners(
                plots, world, 0, 0, author, 20));
    }

    private static Plot plot(UUID world, UUID owner, int x) {
        return new Plot(UUID.randomUUID(), world, owner, "Build", false, 0,
                Set.of(PlotGeometry.Cell.at(x, 0, 0)), Map.of(), Map.of(), null);
    }
}
