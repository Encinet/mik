package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuBalancedLayoutTest {
    @Test
    void tallNarrowCardsUseMoreColumnsInsteadOfAnUnnecessaryTallList() {
        FloatingMenuLayout layout = FloatingMenuLayouts.balancedCards("cards", 3, 4).layout();
        List<FloatingMenuLayout.Node> nodes = nodes(8, 0.8, 1.2);
        var columns = nodes.stream().collect(java.util.stream.Collectors.groupingBy(node ->
                pose(layout, nodes, nodes.indexOf(node)).right()));
        assertEquals(3, columns.size());
        assertTrue(columns.values().stream().allMatch(column -> column.size() <= 3));
        assertSeparated(layout, nodes);
    }

    @Test
    void wideLabelsPreferFewerColumnsAndRetainColumnMajorOrder() {
        FloatingMenuLayout layout = FloatingMenuLayouts.balancedCards("cards", 3, 4).layout();
        List<FloatingMenuLayout.Node> nodes = nodes(8, 5, 1.2);
        assertEquals(pose(layout, nodes, 0).right(), pose(layout, nodes, 3).right());
        assertTrue(pose(layout, nodes, 0).up() > pose(layout, nodes, 1).up());
        assertTrue(pose(layout, nodes, 4).right() > pose(layout, nodes, 0).right());
        assertSeparated(layout, nodes);
    }

    @Test
    void cachedLayoutIsRemeasuredWhenFootprintsOrPageContentsChange() {
        FloatingMenuLayout layout = FloatingMenuLayouts.balancedCards("cards", 3, 4).layout();
        var small = nodes(8, 0.8, 1.2);
        var wide = nodes(8, 5, 1.2);
        FloatingMenuPose initial = pose(layout, small, 0);
        assertTrue(!initial.equals(pose(layout, wide, 0)));
        assertEquals(0, pose(layout, nodes(1, 2, 1), 0).right());
        assertEquals(initial, pose(layout, small, 0));
        assertThrows(IllegalArgumentException.class, () -> pose(layout, nodes(13, 1, 1), 0));
    }

    private static void assertSeparated(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes) {
        for (int first = 0; first < nodes.size(); first++) {
            for (int second = first + 1; second < nodes.size(); second++) {
                FloatingMenuPose firstPose = pose(layout, nodes, first);
                FloatingMenuPose secondPose = pose(layout, nodes, second);
                double width = (nodes.get(first).size().width() + nodes.get(second).size().width()) / 2;
                double height = (nodes.get(first).size().height() + nodes.get(second).size().height()) / 2;
                assertTrue(Math.abs(firstPose.right() - secondPose.right()) >= width + 0.08
                        || Math.abs(firstPose.up() - secondPose.up()) >= height + 0.08);
            }
        }
    }

    private static FloatingMenuPose pose(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes, int index) {
        return layout.pose(new FloatingMenuLayout.Context(index, nodes));
    }

    private static List<FloatingMenuLayout.Node> nodes(int count, double width, double height) {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            nodes.add(new FloatingMenuLayout.Node("card:" + index, FloatingMenuElementStyle.TEXT,
                    FloatingMenuNodeRole.CONTROL, "cards", new FloatingMenuSize(width, height)));
        }
        return nodes;
    }
}
