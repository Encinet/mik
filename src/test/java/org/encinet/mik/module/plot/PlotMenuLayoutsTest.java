package org.encinet.mik.module.plot;

import net.kyori.adventure.text.Component;
import org.encinet.mik.module.menu.FloatingMenuAnchorMode;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuNodeRole;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.encinet.mik.module.menu.FloatingMenuSpatialFrame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotMenuLayoutsTest {

    @Test
    void allPlotScreensUseAStableViewerCenteredBroadFrame() {
        var builder = PlotMenuLayouts.screen("plot-test", FloatingMenuAppearance.SURVEY,
                PlotMenuLayouts.browser("plots", "summary"));
        builder.information("heading", Component.text("Projects")).region("heading");
        var definition = builder.build();
        assertEquals("plot-test", definition.screenId());
        assertEquals(FloatingMenuAnchorMode.FIXED_FOR_SESSION, definition.anchorMode());
        assertEquals(FloatingMenuSpatialFrame.FRONT_ARC, definition.spatialFrame());
        assertEquals(82, definition.framing().horizontalHalfAngleDegrees());
    }

    @Test
    void dashboardSeparatesOwnedProjectsCurrentLocationAndActions() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "summary", "here", "pagination", "navigation");
        add(nodes, "plots", 6, 5, 1);
        add(nodes, "actions", 5, 3, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.browser("plots", "summary");
        assertSide(layout, nodes, "plots", -1);
        assertEquals(0, pose(layout, nodes, "here").right(), 1.0E-6);
        assertSide(layout, nodes, "actions", 1);
        assertFrontArc(layout, nodes);
    }

    @Test
    void browsersHandleFullPagesAndEmptyListsForEveryEntryType() {
        for (String entries : List.of("plots", "members", "choices", "records")) {
            String summary = entries.equals("members") ? "hint" : "summary";
            FloatingMenuLayout layout = PlotMenuLayouts.browser(entries, summary);
            List<FloatingMenuLayout.Node> nodes = scene("heading", summary, "pagination", "navigation");
            add(nodes, entries, 8, 5, 1.2);
            assertFrontArc(layout, nodes);
            assertFrontArc(layout, scene("heading", "navigation"));
            assertEquals(0, pose(layout, scene("heading", "navigation"), "heading").right(), 1.0E-6);
        }
    }

    @Test
    void detailSeparatesConstructionSubplotsSettingsArrivalAndDeletion() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "summary", "notice", "navigation",
                "building-heading", "selection", "subplots-heading", "settings-heading", "arrival-heading");
        add(nodes, "construction", 3, 4, 1);
        add(nodes, "subplots", 5, 4, 1);
        add(nodes, "settings", 4, 4, 1);
        add(nodes, "ownership", 1, 4, 1);
        add(nodes, "arrival-actions", 4, 4, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.detail();
        assertSide(layout, nodes, "construction", -1);
        assertSide(layout, nodes, "selection", -1);
        assertSide(layout, nodes, "subplots", -1);
        assertEquals(0, pose(layout, nodes, "summary").right(), 1.0E-6);
        assertSide(layout, nodes, "settings", 1);
        assertSide(layout, nodes, "ownership", 1);
        assertSide(layout, nodes, "arrival-actions", 1);
        assertTrue(pose(layout, nodes, "ownership").up() < pose(layout, nodes, "settings").up());
        assertFrontArc(layout, nodes);
    }

    @Test
    void readOnlyDetailCollapsesManagementPanelsWithoutLosingItsCenter() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "summary", "notice", "navigation",
                "arrival-heading", "arrival-actions");
        FloatingMenuLayout layout = PlotMenuLayouts.detail();
        assertEquals(0, pose(layout, nodes, "summary").right(), 1.0E-6);
        assertFrontArc(layout, nodes);
    }

    @Test
    void editorSeparatesPositionSelectionFromSaving() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "selection", "hint", "navigation");
        add(nodes, "points", 6, 4, 1);
        add(nodes, "operations", 4, 4, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.editor();
        assertSide(layout, nodes, "points", -1);
        assertEquals(0, pose(layout, nodes, "selection").right(), 1.0E-6);
        assertSide(layout, nodes, "operations", 1);
        assertWorkbench(layout, nodes);
    }

    @Test
    void oneEditorKeepsMultiRegionActionsBesidePositionControls() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "selection", "hint", "navigation");
        add(nodes, "composition", 4, 4, 1);
        add(nodes, "points", 6, 4, 1);
        add(nodes, "view", 8, 3, 1);
        add(nodes, "operations", 1, 4, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.editor();
        assertSide(layout, nodes, "composition", -1);
        assertSide(layout, nodes, "operations", 1);
        assertEquals(0, pose(layout, nodes, "selection").right(), 1.0E-6);
        assertSide(layout, nodes, "points", -1);
        assertSide(layout, nodes, "view", 1);
        assertWorkbench(layout, nodes);
    }

    @Test
    void rectangleManagementKeepsItsNumberedListLeftAndSelectedActionsRight() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "selection", "hint", "navigation",
                "region-heading", "region-pagination");
        add(nodes, "regions", 6, 4, 1);
        add(nodes, "region-selection", 1, 5, 2);
        add(nodes, "region-actions", 2, 4, 1);
        add(nodes, "operations", 3, 4, 1);
        add(nodes, "view", 8, 3, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.editor();
        assertSide(layout, nodes, "regions", -1);
        assertSide(layout, nodes, "region-heading", -1);
        assertSide(layout, nodes, "region-pagination", -1);
        assertSide(layout, nodes, "region-selection", 1);
        assertSide(layout, nodes, "region-actions", 1);
        assertWorkbench(layout, nodes);
    }

    @Test
    void rectangleAdjustmentKeepsCornerControlsAwayFromTheSandbox() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "selection", "hint", "navigation", "region-heading");
        add(nodes, "points", 2, 4, 1);
        add(nodes, "region-selection", 1, 5, 2);
        add(nodes, "region-actions", 2, 4, 1);
        add(nodes, "operations", 2, 4, 1);
        add(nodes, "view", 4, 3, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.editor();
        assertSide(layout, nodes, "points", -1);
        assertSide(layout, nodes, "region-actions", 1);
        assertWorkbench(layout, nodes);
    }

    @Test
    void permissionRowsFormOneVerticalListRatherThanSurroundingPanels() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "navigation");
        add(nodes, "rows", 6, 7, 0.6);
        FloatingMenuLayout layout = PlotMenuLayouts.access();
        double previous = Double.POSITIVE_INFINITY;
        for (int index = 0; index < nodes.size(); index++) {
            if (!nodes.get(index).region().equals("rows")) continue;
            FloatingMenuPose row = layout.pose(new FloatingMenuLayout.Context(index, nodes));
            assertEquals(0, row.right(), 1.0E-6);
            assertTrue(row.up() < previous);
            previous = row.up();
        }
    }

    @Test
    void atmosphereSeparatesTimeAndWeatherWithoutAnInstructionPanel() {
        List<FloatingMenuLayout.Node> nodes = scene("time-heading", "weather-heading", "navigation");
        add(nodes, "times", 6, 4, 1);
        add(nodes, "weather-options", 3, 4, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.atmosphere();
        assertSide(layout, nodes, "times", -1);
        assertEquals(0, pose(layout, nodes, "weather-heading").right(), 1.0E-6);
        assertSide(layout, nodes, "weather-options", 1);
        assertFrontArc(layout, nodes);
    }

    @Test
    void memberPageKeepsIdentityCenterAndRoleActionsOnTheRight() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "identity", "navigation");
        add(nodes, "actions", 5, 4, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.member();
        assertEquals(0, pose(layout, nodes, "identity").right(), 1.0E-6);
        assertSide(layout, nodes, "actions", 1);
        assertFrontArc(layout, nodes);
    }

    @Test
    void communityRecordKeepsLongTextCenterMetadataLeftAndActionsRight() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "metadata", "pagination", "navigation");
        add(nodes, "detail", 1, 8, 5);
        add(nodes, "actions", 4, 4, 1);
        FloatingMenuLayout layout = PlotMenuLayouts.record();
        assertSide(layout, nodes, "metadata", -1);
        assertEquals(0, pose(layout, nodes, "detail").right(), 1.0E-6);
        assertSide(layout, nodes, "actions", 1);
        assertFrontArc(layout, nodes);
    }

    @Test
    void editorNeedsNoPermanentInstructionPanel() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "selection");
        add(nodes, "points", 3, 3, 1);
        add(nodes, "operations", 2, 3, 1);
        add(nodes, "navigation", 2, 3, 0.8);
        assertWorkbench(PlotMenuLayouts.editor(), nodes);
    }

    @Test
    void contextualHelpUsesOneReadableColumnWithReturnBelowInstructions() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "navigation");
        add(nodes, "instructions", 1, 8, 5);
        FloatingMenuLayout layout = PlotMenuLayouts.help();
        assertEquals(0, pose(layout, nodes, "instructions").right(), 1.0E-6);
        assertTrue(pose(layout, nodes, "heading").up() > pose(layout, nodes, "instructions").up());
        assertTrue(pose(layout, nodes, "instructions").up() > pose(layout, nodes, "navigation").up());
    }

    private static List<FloatingMenuLayout.Node> scene(String... regions) {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        for (String region : regions) add(nodes, region, 1, 4, 0.8);
        return nodes;
    }

    private static void add(List<FloatingMenuLayout.Node> nodes, String region, int count,
                            double width, double height) {
        for (int index = 0; index < count; index++) {
            nodes.add(new FloatingMenuLayout.Node(region + ":" + index, FloatingMenuElementStyle.TEXT,
                    FloatingMenuNodeRole.CONTROL, region, new FloatingMenuSize(width, height)));
        }
    }

    private static FloatingMenuPose pose(FloatingMenuLayout layout,
                                         List<FloatingMenuLayout.Node> nodes, String region) {
        int index = java.util.stream.IntStream.range(0, nodes.size())
                .filter(candidate -> nodes.get(candidate).region().equals(region)).findFirst().orElseThrow();
        return layout.pose(new FloatingMenuLayout.Context(index, nodes));
    }

    private static void assertSide(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes,
                                   String region, int direction) {
        for (int index = 0; index < nodes.size(); index++) {
            if (!nodes.get(index).region().equals(region)) continue;
            assertTrue(layout.pose(new FloatingMenuLayout.Context(index, nodes)).right() * direction > 0, region);
        }
    }

    private static void assertFrontArc(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes) {
        FloatingMenuPose first = layout.pose(new FloatingMenuLayout.Context(0, nodes));
        double radius = Math.hypot(first.right(), first.forward());
        for (int index = 0; index < nodes.size(); index++) {
            FloatingMenuPose pose = layout.pose(new FloatingMenuLayout.Context(index, nodes));
            assertEquals(radius, Math.hypot(pose.right(), pose.forward()), 1.0E-6);
            assertTrue(pose.forward() < 0, nodes.get(index).elementId());
            assertTrue(Math.abs(pose.yawDegrees()) < 80, nodes.get(index).elementId());
            assertEquals(Math.toDegrees(Math.atan2(pose.right(), -pose.forward())), pose.yawDegrees(), 1.0E-6);
        }
    }

    private static void assertWorkbench(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            var node = nodes.get(index);
            FloatingMenuPose pose = layout.pose(new FloatingMenuLayout.Context(index, nodes));
            assertTrue(pose.forward() < 0, node.elementId());
            assertTrue(Math.abs(pose.yawDegrees()) <= 45, node.elementId());
            boolean outsideModel = Math.abs(pose.right()) - node.size().width() / 2 >= 1.6 + 0.29
                    || pose.up() - node.size().height() / 2 >= 0.75 + 0.24
                    || pose.up() + node.size().height() / 2 <= -1.45 - 0.24;
            assertTrue(outsideModel, node.elementId());
        }
    }
}
