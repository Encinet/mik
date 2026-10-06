package org.encinet.mik.module.plot;

import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuNodeRole;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotPermissionMenuLayoutTest {
    @Test
    void operationAndStatusColumnsShareLeftEdgesAndRowTops() {
        List<FloatingMenuLayout.Node> nodes = scene(6);
        nodes.set(index(nodes, "permission-2-group-heading"), node("permission-2-group-heading", 3.8, 1.4));
        FloatingMenuLayout layout = PlotMenuLayouts.permissionTable();
        double operationLeft = left(layout, nodes, "operation-heading");
        double statusLeft = left(layout, nodes, "result-heading");
        for (int row = 0; row < 6; row++) {
            String label = "permission-" + row + "-group-heading";
            String value = "permission-" + row;
            assertEquals(operationLeft, left(layout, nodes, label), 1.0E-9);
            assertEquals(statusLeft, left(layout, nodes, value), 1.0E-9);
            assertEquals(top(layout, nodes, label), top(layout, nodes, value), 1.0E-9);
        }
        assertEquals(left(layout, nodes, "subject"), left(layout, nodes, "category-0"), 1.0E-9);
        assertTrue(left(layout, nodes, "section") > left(layout, nodes, "subject"));
        assertSeparated(layout, nodes);
    }

    @Test
    void twoFourAndSixRowCategoriesKeepNavigationAndTableAnchorsStable() {
        FloatingMenuLayout layout = PlotMenuLayouts.permissionTable();
        List<FloatingMenuLayout.Node> full = scene(6);
        for (int count : List.of(2, 4, 6)) {
            List<FloatingMenuLayout.Node> nodes = scene(count);
            for (String id : List.of("subject", "section", "operation-heading", "result-heading",
                    "permission-0", "category-0", "category-3", "hint", "back")) {
                assertEquals(pose(layout, full, id), pose(layout, nodes, id), id);
            }
            assertSeparated(layout, nodes);
        }
    }

    @Test
    void permissionRowsAndNavigationNeedNoInstructionFooterOrPlaceholder() {
        FloatingMenuLayout layout = PlotMenuLayouts.permissionTable();
        List<FloatingMenuLayout.Node> full = scene(6);
        full.removeIf(node -> node.elementId().equals("hint"));
        for (int count : List.of(2, 4, 6)) {
            List<FloatingMenuLayout.Node> nodes = scene(count);
            nodes.removeIf(node -> node.elementId().equals("hint"));
            for (String id : List.of("subject", "section", "operation-heading", "result-heading",
                    "permission-0", "category-0", "back"))
                assertEquals(pose(layout, full, id), pose(layout, nodes, id), id);
            assertSeparated(layout, nodes);
        }
    }

    @Test
    void oversizedFooterAndWrappedSubjectStayOutsideTheTable() {
        List<FloatingMenuLayout.Node> nodes = scene(6);
        nodes.set(index(nodes, "subject"), node("subject", 5, 2));
        nodes.set(index(nodes, "heading"), node("heading", 18, 2.4));
        nodes.set(index(nodes, "hint"), node("hint", 14, 1.6));
        nodes.add(node("reset", "navigation", 12, 1.3));
        assertSeparated(PlotMenuLayouts.permissionTable(), nodes);
    }

    @Test
    void sceneSizeChangesInvalidateCachedColumnPositions() {
        FloatingMenuLayout layout = PlotMenuLayouts.permissionTable();
        List<FloatingMenuLayout.Node> nodes = scene(6);
        FloatingMenuPose previous = pose(layout, nodes, "result-heading");
        List<FloatingMenuLayout.Node> expanded = new ArrayList<>(nodes);
        expanded.set(index(expanded, "permission-0-group-heading"),
                node("permission-0-group-heading", 7, 2));
        assertNotEquals(previous, pose(layout, expanded, "result-heading"));
        assertSeparated(layout, expanded);
        assertEquals(previous, pose(layout, nodes, "result-heading"));
    }

    @Test
    void stateInsertionOrderDoesNotBreakLabelPairing() {
        List<FloatingMenuLayout.Node> nodes = scene(6);
        var first = nodes.remove(index(nodes, "permission-0"));
        nodes.add(first);
        FloatingMenuLayout layout = PlotMenuLayouts.permissionTable();
        assertEquals(top(layout, nodes, "permission-0-group-heading"),
                top(layout, nodes, "permission-0"), 1.0E-9);
        assertSeparated(layout, nodes);
    }

    @Test
    void incompleteDuplicateOrUnknownRegionsFailInsteadOfSilentlyDroppingNodes() {
        for (String missing : List.of("heading", "subject", "section", "operation-heading",
                "result-heading", "permission-0", "permission-0-group-heading", "back")) {
            List<FloatingMenuLayout.Node> nodes = scene(6);
            nodes.remove(index(nodes, missing));
            assertInvalid(nodes);
        }
        List<FloatingMenuLayout.Node> noCategories = scene(6);
        noCategories.removeIf(node -> node.region().equals("categories"));
        assertInvalid(noCategories);
        for (String duplicate : List.of("heading", "permission-0", "permission-0-group-heading", "back")) {
            List<FloatingMenuLayout.Node> nodes = scene(6);
            nodes.add(nodes.get(index(nodes, duplicate)));
            assertInvalid(nodes);
        }
        List<FloatingMenuLayout.Node> unknown = scene(6);
        unknown.add(node("unknown", 2, 1));
        assertInvalid(unknown);
        assertInvalid(scene(8));
        assertInvalid(scene(0));
    }

    @Test
    void independentSubplotPermissionFitsWithAllManagementRows() {
        int count = PlotPermission.Category.MANAGEMENT.permissions().size();
        assertEquals(7, count);
        List<FloatingMenuLayout.Node> nodes = scene(count);
        FloatingMenuLayout layout = new PlotPermissionMenuLayout();
        assertSeparated(layout, nodes);
        assertTrue(pose(layout, nodes, "permission-6").up() > pose(layout, nodes, "back").up());
    }

    private static void assertInvalid(List<FloatingMenuLayout.Node> nodes) {
        assertThrows(IllegalArgumentException.class,
                () -> PlotMenuLayouts.permissionTable().pose(new FloatingMenuLayout.Context(0, nodes)));
    }

    private static List<FloatingMenuLayout.Node> scene(int rows) {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        nodes.add(node("heading", 4, 0.5));
        nodes.add(node("subject", 2.5, 0.7));
        nodes.add(node("section", 3, 0.6));
        nodes.add(node("operation-heading", 1.5, 0.4));
        nodes.add(node("result-heading", 1.5, 0.4));
        for (int category = 0; category < 4; category++)
            nodes.add(node("category-" + category, "categories", 2.5, 0.6));
        for (int row = 0; row < rows; row++) {
            nodes.add(node("permission-" + row + "-group-heading", 3, 0.6));
            nodes.add(node("permission-" + row, 2.5, 1));
        }
        nodes.add(node("hint", 5, 0.6));
        nodes.add(node("back", "navigation", 1.5, 0.5));
        return nodes;
    }

    private static FloatingMenuLayout.Node node(String region, double width, double height) {
        return node(region, region, width, height);
    }

    private static FloatingMenuLayout.Node node(String id, String region, double width, double height) {
        return new FloatingMenuLayout.Node(id, FloatingMenuElementStyle.TEXT,
                FloatingMenuNodeRole.CONTROL, region, new FloatingMenuSize(width, height));
    }

    private static int index(List<FloatingMenuLayout.Node> nodes, String id) {
        return java.util.stream.IntStream.range(0, nodes.size())
                .filter(candidate -> nodes.get(candidate).elementId().equals(id)).findFirst().orElseThrow();
    }

    private static FloatingMenuPose pose(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes, String id) {
        return layout.pose(new FloatingMenuLayout.Context(index(nodes, id), nodes));
    }

    private static double left(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes, String id) {
        return pose(layout, nodes, id).right() - nodes.get(index(nodes, id)).size().width() / 2;
    }

    private static double top(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes, String id) {
        return pose(layout, nodes, id).up() + nodes.get(index(nodes, id)).size().height() / 2;
    }

    private static void assertSeparated(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes) {
        for (int first = 0; first < nodes.size(); first++) {
            var firstNode = nodes.get(first);
            var firstPose = layout.pose(new FloatingMenuLayout.Context(first, nodes));
            assertEquals(-0.65, firstPose.forward(), 1.0E-9);
            assertEquals(0, firstPose.yawDegrees(), 1.0E-9);
            assertEquals(0, firstPose.pitchDegrees(), 1.0E-9);
            for (int second = first + 1; second < nodes.size(); second++) {
                var secondNode = nodes.get(second);
                var secondPose = layout.pose(new FloatingMenuLayout.Context(second, nodes));
                boolean separateHorizontal = Math.abs(firstPose.right() - secondPose.right())
                        >= (firstNode.size().width() + secondNode.size().width()) / 2 - 1.0E-9;
                boolean separateVertical = Math.abs(firstPose.up() - secondPose.up())
                        >= (firstNode.size().height() + secondNode.size().height()) / 2 - 1.0E-9;
                assertTrue(separateHorizontal || separateVertical,
                        firstNode.elementId() + " overlaps " + secondNode.elementId());
            }
        }
    }
}
