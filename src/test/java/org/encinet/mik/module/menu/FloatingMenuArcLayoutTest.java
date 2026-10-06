package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuArcLayoutTest {

    @Test
    void tallPanelsIncreaseRadiusInsteadOfExtendingIntoExtremeVerticalAngles() {
        FloatingMenuLayout layout = layout();
        List<FloatingMenuLayout.Node> nodes = List.of(new FloatingMenuLayout.Node("center", FloatingMenuElementStyle.TEXT,
                FloatingMenuNodeRole.CONTROL, "center", new FloatingMenuSize(2, 12)));
        FloatingMenuPose pose = pose(layout, nodes, 0);
        assertTrue(Math.toDegrees(Math.atan2(6, radius(pose))) <= 30.001);
        assertEquals(0, pose.pitchDegrees());
    }

    @Test
    void centersTheDesignatedPanelAndFacesEverySurfaceTowardTheViewer() {
        FloatingMenuLayout layout = layout();
        List<FloatingMenuLayout.Node> nodes = scene(1);
        double radius = radius(pose(layout, nodes, 1));
        assertEquals(0, pose(layout, nodes, 1).right(), 1.0E-6);
        assertTrue(pose(layout, nodes, 0).right() < 0);
        assertTrue(pose(layout, nodes, 2).right() > 0);
        for (int index = 0; index < nodes.size(); index++) {
            FloatingMenuPose pose = pose(layout, nodes, index);
            assertEquals(radius, radius(pose), 1.0E-6);
            assertEquals(Math.toDegrees(Math.atan2(pose.right(), -pose.forward())),
                    pose.yawDegrees(), 1.0E-6);
            assertTrue(pose.forward() < 0);
            assertTrue(Math.abs(pose.yawDegrees()) < 80);
        }
    }

    @Test
    void remeasuresChangedFootprintsAndDoesNotShareCacheAcrossLayouts() {
        FloatingMenuLayout first = layout();
        FloatingMenuLayout second = layout();
        List<FloatingMenuLayout.Node> small = scene(1);
        List<FloatingMenuLayout.Node> large = scene(3);
        double initialRadius = radius(pose(first, small, 1));
        assertTrue(radius(pose(first, large, 1)) > initialRadius);
        assertEquals(initialRadius, radius(pose(second, small, 1)), 1.0E-6);
        assertEquals(initialRadius, radius(pose(first, small, 1)), 1.0E-6);
    }

    @Test
    void collapsesAbsentSidePanelsWithoutMovingTheCenter() {
        FloatingMenuLayout layout = layout();
        List<FloatingMenuLayout.Node> nodes = List.of(node("center", 4));
        assertEquals(0, pose(layout, nodes, 0).right(), 1.0E-6);
        assertEquals(-3, pose(layout, nodes, 0).forward(), 1.0E-6);
    }

    @Test
    void rejectsUnknownOrAbsentCenterPanelsAndUnassignedNodes() {
        assertThrows(IllegalArgumentException.class, () -> FloatingMenuLayouts.panoramicPanels(
                "missing", 0.6, panel("center")));
        assertThrows(IllegalArgumentException.class,
                () -> pose(layout(), List.of(node("left", 3)), 0));
        assertThrows(IllegalArgumentException.class,
                () -> pose(layout(), List.of(node("center", 3), node("unknown", 3)), 0));
    }

    @Test
    void rejectsDuplicatePanelsOverlappingRegionsAndInvalidGaps() {
        assertThrows(IllegalArgumentException.class, () -> FloatingMenuLayouts.panoramicPanels(
                "center", 0.6, panel("center"), panel("center")));
        assertThrows(IllegalArgumentException.class, () -> FloatingMenuLayouts.panoramicPanels(
                "center", 0.6, panel("center"), FloatingMenuLayouts.panel("other",
                        FloatingMenuLayouts.adaptiveRow(0.2), "center")));
        assertThrows(IllegalArgumentException.class, () -> FloatingMenuLayouts.panoramicPanels(
                "center", Double.NaN, panel("center")));
        assertThrows(IllegalArgumentException.class, () -> FloatingMenuLayouts.panoramicPanels(
                "center", -1, panel("center")));
    }

    private static FloatingMenuLayout layout() {
        return FloatingMenuLayouts.panoramicPanels("center", 0.6,
                panel("left"), panel("center"), panel("right"));
    }

    private static FloatingMenuLayouts.Panel panel(String region) {
        return FloatingMenuLayouts.panel(region, FloatingMenuLayouts.adaptiveRow(0.2), region);
    }

    private static List<FloatingMenuLayout.Node> scene(double scale) {
        return List.of(node("left", 8 * scale), node("center", 4 * scale), node("right", 5 * scale));
    }

    private static FloatingMenuLayout.Node node(String region, double width) {
        return new FloatingMenuLayout.Node(region, FloatingMenuElementStyle.TEXT,
                FloatingMenuNodeRole.CONTROL, region, new FloatingMenuSize(width, 1));
    }

    private static FloatingMenuPose pose(FloatingMenuLayout layout,
                                         List<FloatingMenuLayout.Node> nodes, int index) {
        return layout.pose(new FloatingMenuLayout.Context(index, nodes));
    }

    private static double radius(FloatingMenuPose pose) {
        return Math.hypot(pose.right(), pose.forward());
    }
}
