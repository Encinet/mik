package org.encinet.mik.module.plot;

import org.encinet.mik.module.plot.PlotPreviewGeometry.Edge;
import org.encinet.mik.module.plot.PlotPreviewGeometry.Point;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlotEdgeIndexTest {
    @Test
    void tensOfThousandsOfEdgesNeverMakeAFrameVisitTheWholeIndex() {
        List<Edge> edges = new ArrayList<>();
        for (int horizontal = -100; horizontal < 100; horizontal++)
            for (int forward = -100; forward < 100; forward++)
                edges.add(new Edge(new Point(horizontal, 0, forward), new Point(horizontal, 4, forward)));
        var index = new PlotEdgeIndex(edges);
        var visible = index.within(new PlotSelection.Bounds(-200, -1, -200, 200, 10, 200), 160);
        assertEquals(160, visible.edges().size());
        assertTrue(visible.limited());
        assertTrue(visible.visits() <= PlotEdgeIndex.MAX_VISITS);
        var nearby = index.nearby(new Point(0, 2, 0), 24, 64);
        assertEquals(64, nearby.edges().size());
        assertTrue(nearby.visits() <= PlotEdgeIndex.MAX_VISITS);
        var sampled = PlotPreviewGeometry.sample(nearby.edges(), new Point(0, 2, 0), 24, 256);
        assertTrue(sampled.size() <= 256);
        assertTrue(sampled.stream().allMatch(point -> point.distanceSquared(new Point(0, 2, 0)) <= 24 * 24 + 1.0E-6));
    }

    @Test
    void longEdgesCrossingTheViewerAreKeptEvenWhenBothEndpointsAreFarAway() {
        Edge longEdge = new Edge(new Point(-1_000_000, 10, 0), new Point(1_000_000, 10, 0));
        var index = new PlotEdgeIndex(List.of(longEdge));
        assertEquals(List.of(longEdge), index.nearby(new Point(0, 0, 0), 24, 64).edges());
        assertEquals(List.of(longEdge), index.within(new PlotSelection.Bounds(-12, 0, -12, 12, 15, 12), 160).edges());
        assertTrue(index.nearby(new Point(0, 100, 0), 24, 64).edges().isEmpty());
    }

    @Test
    void unclippedSmallShapesKeepExactConcaveEdgesAndCanonicalOrdering() {
        var edges = PlotPreviewGeometry.core(Set.of(new PlotGeometry.Cell(-1, 16, -1),
                new PlotGeometry.Cell(0, 16, -1), new PlotGeometry.Cell(-1, 16, 0)));
        var visible = new PlotEdgeIndex(edges).within(new PlotSelection.Bounds(-8, 60, -8, 8, 75, 8), 160);
        assertEquals(edges, visible.edges());
        assertFalse(visible.limited());
        assertEquals(18, visible.edges().size());
    }

    @Test
    void emptyAndZeroBudgetQueriesReturnWithoutTraversal() {
        assertTrue(new PlotEdgeIndex(List.of()).nearby(new Point(0, 0, 0), 24, 64).edges().isEmpty());
        var edges = PlotPreviewGeometry.box(new PlotSelection.Bounds(0, 0, 0, 3, 3, 3));
        var visible = new PlotEdgeIndex(edges).nearby(new Point(0, 0, 0), 24, 0);
        assertTrue(visible.edges().isEmpty());
        assertEquals(0, visible.visits());
        assertTrue(visible.limited());
    }
}
