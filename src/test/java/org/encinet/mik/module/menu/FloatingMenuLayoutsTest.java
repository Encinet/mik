package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuLayoutsTest {

    @Test
    void gridCentersIncompleteRows() {
        FloatingMenuLayout layout = FloatingMenuLayouts.grid(3, 1.0, 1.0);
        FloatingMenuPoint firstOnLastRow = layout.position(context(3, 5));
        FloatingMenuPoint lastOnLastRow = layout.position(context(4, 5));

        assertEquals(-0.5, firstOnLastRow.right());
        assertEquals(0.5, lastOnLastRow.right());
        assertEquals(-0.5, firstOnLastRow.up());
    }

    @Test
    void arcAddsDepthWithoutInventoryCoordinates() {
        FloatingMenuLayout layout = FloatingMenuLayouts.arc(2.0, 90.0);
        FloatingMenuPoint center = layout.position(context(1, 3));
        FloatingMenuPoint edge = layout.position(context(0, 3));
        FloatingMenuPose leftPose = layout.pose(context(0, 3));
        FloatingMenuPose rightPose = layout.pose(context(2, 3));

        assertEquals(0.0, center.forward(), 0.0001);
        assertEquals(0.0, center.right(), 0.0001);
        assertEquals(-Math.sqrt(2), edge.right(), 0.0001);
        assertEquals(-45.0, leftPose.yawDegrees(), 0.0001);
        assertEquals(45.0, rightPose.yawDegrees(), 0.0001);
    }

    @Test
    void rejectsInvalidSpatialConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> FloatingMenuLayouts.grid(0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> FloatingMenuLayouts.grid(1, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> FloatingMenuLayouts.ring(0));
        assertThrows(IllegalArgumentException.class, () ->
                FloatingMenuLayouts.adaptiveDomeGrid(3, 0.2, 0.2,
                        Double.NaN, 0.2));
        assertThrows(IllegalArgumentException.class,
                () -> new FloatingMenuPoint(Double.NaN, 0, 0));
    }

    @Test
    void sparseListsRemainCenteredInsteadOfReservingEmptyColumns() {
        FloatingMenuLayout layout = FloatingMenuLayouts.list(4, 7, 1.2, 0.3);
        assertEquals(0.0, layout.position(context(0, 1)).right(), 0.0001);
        assertEquals(-0.6, layout.position(context(0, 8)).right(), 0.0001);
        assertEquals(0.6, layout.position(context(7, 8)).right(), 0.0001);
    }

    @Test
    void semanticIdsCanOwnExactSpatialPositions() {
        FloatingMenuLayout layout = FloatingMenuLayouts.offset(
                FloatingMenuLayouts.fixed(Map.of("search", new FloatingMenuPoint(1, 2, 3))),
                -1, 0.5, 2);
        FloatingMenuPoint point = layout.position(new FloatingMenuLayout.Context(0, List.of(
                node("search", FloatingMenuNodeRole.CONTROL,
                        FloatingMenuDefinition.DEFAULT_REGION, 1, 1))));
        assertEquals(0, point.right());
        assertEquals(2.5, point.up());
        assertEquals(5, point.forward());
    }

    @Test
    void regionsUseIndependentLocalIndexes() {
        FloatingMenuLayout layout = FloatingMenuLayouts.regions(Map.of(
                "results", FloatingMenuLayouts.grid(2, 1, 1),
                "controls", FloatingMenuLayouts.fixed(Map.of(
                        "close", new FloatingMenuPoint(0, -2, 0)))
        ));
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        for (int index = 0; index < 18; index++) {
            nodes.add(node("result:" + index, FloatingMenuNodeRole.ITEM,
                    "results", 1, 1));
        }
        nodes.add(node("close", FloatingMenuNodeRole.NAVIGATION,
                "controls", 1, 1));
        FloatingMenuPoint point = layout.position(new FloatingMenuLayout.Context(18, nodes));
        assertEquals(0, point.right());
        assertEquals(-2, point.up());
    }

    @Test
    void adaptiveGridMaintainsClearSpaceBetweenMeasuredSurfaces() {
        FloatingMenuLayout layout = FloatingMenuLayouts.adaptiveGrid(2, 0.25, 0.30);
        List<FloatingMenuLayout.Node> nodes = List.of(
                node("wide", FloatingMenuNodeRole.CONTROL, "content", 2.4, 0.5),
                node("narrow", FloatingMenuNodeRole.CONTROL, "content", 0.8, 0.9),
                node("lower", FloatingMenuNodeRole.CONTROL, "content", 1.2, 0.7));
        FloatingMenuPose wide = layout.pose(new FloatingMenuLayout.Context(0, nodes));
        FloatingMenuPose narrow = layout.pose(new FloatingMenuLayout.Context(1, nodes));
        FloatingMenuPose lower = layout.pose(new FloatingMenuLayout.Context(2, nodes));

        assertEquals(2.4 / 2 + 0.25 + 0.8 / 2,
                narrow.right() - wide.right(), 0.0001);
        assertEquals(0.9 / 2 + 0.30 + 0.7 / 2,
                wide.up() - lower.up(), 0.0001);
    }

    @Test
    void adaptiveListUsesEachColumnAndRowsActualFootprints() {
        FloatingMenuLayout layout = FloatingMenuLayouts.adaptiveList(2, 2, 0.4, 0.2);
        List<FloatingMenuLayout.Node> nodes = List.of(
                node("a", FloatingMenuNodeRole.CONTROL, "content", 1.0, 0.4),
                node("b", FloatingMenuNodeRole.CONTROL, "content", 1.8, 0.8),
                node("c", FloatingMenuNodeRole.CONTROL, "content", 0.6, 1.1));
        FloatingMenuPose first = layout.pose(new FloatingMenuLayout.Context(0, nodes));
        FloatingMenuPose second = layout.pose(new FloatingMenuLayout.Context(1, nodes));
        FloatingMenuPose third = layout.pose(new FloatingMenuLayout.Context(2, nodes));

        assertEquals(0.4 / 2 + 0.2 + 0.8 / 2,
                first.up() - second.up(), 0.0001);
        assertEquals(1.8 / 2 + 0.4 + 0.6 / 2,
                third.right() - first.right(), 0.0001);
    }

    @Test
    void verticalRegionsSkipMissingBandsAndCannotOverlap() {
        FloatingMenuLayout layout = FloatingMenuLayouts.verticalRegions(0.35,
                FloatingMenuLayouts.region("header", FloatingMenuLayouts.adaptiveRow(0.2)),
                FloatingMenuLayouts.region("missing", FloatingMenuLayouts.adaptiveRow(0.2)),
                FloatingMenuLayouts.region("content", FloatingMenuLayouts.adaptiveColumn(0.15)));
        List<FloatingMenuLayout.Node> nodes = List.of(
                node("heading", FloatingMenuNodeRole.INFORMATION, "header", 2.0, 0.5),
                node("one", FloatingMenuNodeRole.CONTROL, "content", 1.0, 0.8),
                node("two", FloatingMenuNodeRole.CONTROL, "content", 1.0, 0.6));
        FloatingMenuPose heading = layout.pose(new FloatingMenuLayout.Context(0, nodes));
        FloatingMenuPose one = layout.pose(new FloatingMenuLayout.Context(1, nodes));

        double headingBottom = heading.up() - 0.5 / 2;
        double contentTop = one.up() + 0.8 / 2;
        assertEquals(0.35, headingBottom - contentTop, 0.0001);
    }

    @Test
    void semanticMenuPresetsComposeMeasuredRegionsWithSharedSpacing() {
        FloatingMenuLayout layout = FloatingMenuLayouts.menu(
                FloatingMenuLayouts.heading("heading"),
                FloatingMenuLayouts.actions("actions", 2),
                FloatingMenuLayouts.navigation("navigation"));
        List<FloatingMenuLayout.Node> nodes = List.of(
                node("title", FloatingMenuNodeRole.INFORMATION, "heading", 2.0, 0.4),
                node("one", FloatingMenuNodeRole.ITEM, "actions", 1.0, 0.8),
                node("two", FloatingMenuNodeRole.ITEM, "actions", 1.4, 0.8),
                node("back", FloatingMenuNodeRole.NAVIGATION, "navigation", 0.8, 0.4));

        FloatingMenuPose title = layout.pose(new FloatingMenuLayout.Context(0, nodes));
        FloatingMenuPose firstAction = layout.pose(new FloatingMenuLayout.Context(1, nodes));
        FloatingMenuPose navigation = layout.pose(new FloatingMenuLayout.Context(3, nodes));
        assertEquals(0.28, title.up() - 0.2 - (firstAction.up() + 0.4), 0.0001);
        assertEquals(0.28, firstAction.up() - 0.4 - (navigation.up() + 0.2), 0.0001);
    }

    @Test
    void horizontalPanelsComposeNestedRegionsWithoutOffsets() {
        FloatingMenuLayout browser = FloatingMenuLayouts.verticalRegions(0.20,
                FloatingMenuLayouts.region("header", FloatingMenuLayouts.adaptiveRow(0.0)),
                FloatingMenuLayouts.region("homes", FloatingMenuLayouts.adaptiveGrid(2, 0.25, 0.20)),
                FloatingMenuLayouts.region("pagination", FloatingMenuLayouts.adaptiveRow(0.15)));
        FloatingMenuLayout inspector = FloatingMenuLayouts.verticalRegions(0.20,
                FloatingMenuLayouts.region("detail", FloatingMenuLayouts.adaptiveColumn(0.0)),
                FloatingMenuLayouts.region("actions", FloatingMenuLayouts.adaptiveGrid(2, 0.15, 0.15)));
        FloatingMenuLayout layout = FloatingMenuLayouts.horizontalPanels(0.60,
                FloatingMenuLayouts.panel("browser", browser,
                        "header", "homes", "pagination"),
                FloatingMenuLayouts.panel("inspector", inspector,
                        "detail", "actions"));
        List<FloatingMenuLayout.Node> nodes = List.of(
                node("heading", FloatingMenuNodeRole.INFORMATION, "header", 2.0, 0.5),
                node("home:a", FloatingMenuNodeRole.ITEM, "homes", 1.0, 0.8),
                node("home:b", FloatingMenuNodeRole.ITEM, "homes", 1.3, 0.8),
                node("page", FloatingMenuNodeRole.INFORMATION, "pagination", 0.8, 0.4),
                node("selected-home", FloatingMenuNodeRole.ITEM, "detail", 2.6, 1.5),
                node("teleport", FloatingMenuNodeRole.CONTROL, "actions", 1.0, 0.5),
                node("delete", FloatingMenuNodeRole.CONTROL, "actions", 1.2, 0.5));

        double browserRight = Double.NEGATIVE_INFINITY;
        double inspectorLeft = Double.POSITIVE_INFINITY;
        for (int index = 0; index < nodes.size(); index++) {
            FloatingMenuPose pose = layout.pose(new FloatingMenuLayout.Context(index, nodes));
            double halfWidth = nodes.get(index).size().width() * 0.5;
            if (index < 4) browserRight = Math.max(browserRight, pose.right() + halfWidth);
            else inspectorLeft = Math.min(inspectorLeft, pose.right() - halfWidth);
        }

        assertEquals(0.60, inspectorLeft - browserRight, 0.0001);
    }

    @Test
    void sidecarDoesNotMoveThePrimaryPanelsOrigin() {
        FloatingMenuLayouts.Panel primary = FloatingMenuLayouts.panel("navigation",
                FloatingMenuLayouts.verticalRegions(0.2,
                        FloatingMenuLayouts.region("heading", FloatingMenuLayouts.adaptiveRow(0.0)),
                        FloatingMenuLayouts.region("actions", FloatingMenuLayouts.adaptiveRow(0.2))),
                "heading", "actions");
        FloatingMenuLayouts.Panel profile = FloatingMenuLayouts.panel("profile",
                FloatingMenuLayouts.adaptiveColumn(0.0), "profile");
        FloatingMenuLayout layout = FloatingMenuLayouts.sidecar(primary, profile,
                FloatingMenuLayouts.Side.LEFT, 0.45);
        List<FloatingMenuLayout.Node> nodes = List.of(
                node("profile", FloatingMenuNodeRole.INFORMATION, "profile", 2.4, 1.2),
                node("heading", FloatingMenuNodeRole.INFORMATION, "heading", 1.8, 0.4),
                node("one", FloatingMenuNodeRole.CONTROL, "actions", 1.0, 0.5),
                node("two", FloatingMenuNodeRole.CONTROL, "actions", 1.0, 0.5));

        FloatingMenuLayout.Context headingContext = new FloatingMenuLayout.Context(1, nodes);
        FloatingMenuPose expectedHeading = primary.layout().pose(
                headingContext.inRegions(primary.regions()));
        FloatingMenuPose actualHeading = layout.pose(headingContext);
        assertEquals(expectedHeading.right(), actualHeading.right(), 0.0001);
        assertEquals(expectedHeading.up(), actualHeading.up(), 0.0001);

        FloatingMenuPose profilePose = layout.pose(new FloatingMenuLayout.Context(0, nodes));
        double profileRightEdge = profilePose.right() + 2.4 / 2.0;
        double primaryLeftEdge = Double.POSITIVE_INFINITY;
        for (int index = 1; index < nodes.size(); index++) {
            FloatingMenuPose pose = layout.pose(new FloatingMenuLayout.Context(index, nodes));
            primaryLeftEdge = Math.min(primaryLeftEdge,
                    pose.right() - nodes.get(index).size().width() / 2.0);
        }
        assertEquals(0.45, primaryLeftEdge - profileRightEdge, 0.0001);
    }

    @Test
    void curvedGridKeepsCenterBackAndBringsEdgesForward() {
        FloatingMenuLayout layout = FloatingMenuLayouts.curvedGrid(3, 1.0, 0.8, 0.3);
        FloatingMenuPoint left = layout.position(context(0, 3));
        FloatingMenuPoint center = layout.position(context(1, 3));
        FloatingMenuPoint right = layout.position(context(2, 3));

        assertEquals(0.3, left.forward(), 0.0001);
        assertEquals(0.0, center.forward(), 0.0001);
        assertEquals(left.forward(), right.forward(), 0.0001);
        assertEquals(-layout.pose(context(0, 3)).yawDegrees(),
                layout.pose(context(2, 3)).yawDegrees(), 0.0001);
        assertEquals(0.0, layout.pose(context(1, 3)).yawDegrees(), 0.0001);
    }

    @Test
    void curvedListPreservesVerticalScanningAndAddsColumnDepth() {
        FloatingMenuLayout layout = FloatingMenuLayouts.curvedList(3, 3, 2.0, 0.4, 0.25);
        FloatingMenuPoint firstColumn = layout.position(context(0, 9));
        FloatingMenuPoint centerColumn = layout.position(context(3, 9));
        FloatingMenuPoint lastColumn = layout.position(context(6, 9));

        assertEquals(0.25, firstColumn.forward(), 0.0001);
        assertEquals(0.0, centerColumn.forward(), 0.0001);
        assertEquals(firstColumn.forward(), lastColumn.forward(), 0.0001);
        assertEquals(0.4, firstColumn.up() - layout.position(context(1, 9)).up(), 0.0001);
        assertEquals(-layout.pose(context(0, 9)).yawDegrees(),
                layout.pose(context(6, 9)).yawDegrees(), 0.0001);
    }

    @Test
    void adaptiveDomePreservesMeasuredGridWhileFacingEverySurfaceInward() {
        FloatingMenuLayout planar = FloatingMenuLayouts.adaptiveGrid(3, 0.25, 0.20);
        FloatingMenuLayout dome = FloatingMenuLayouts.adaptiveDomeGrid(
                3, 0.25, 0.20, 0.48, 0.22);

        FloatingMenuPose topLeft = dome.pose(context(0, 9));
        FloatingMenuPose center = dome.pose(context(4, 9));
        FloatingMenuPose bottomRight = dome.pose(context(8, 9));
        FloatingMenuPose planarTopLeft = planar.pose(context(0, 9));

        assertEquals(planarTopLeft.right(), topLeft.right(), 0.0001);
        assertEquals(planarTopLeft.up(), topLeft.up(), 0.0001);
        assertEquals(0.0, center.forward(), 0.0001);
        assertTrue(topLeft.forward() > center.forward());
        assertEquals(topLeft.forward(), bottomRight.forward(), 0.0001);
        assertTrue(topLeft.yawDegrees() < 0.0);
        assertTrue(topLeft.pitchDegrees() > 0.0);
        assertTrue(bottomRight.yawDegrees() > 0.0);
        assertTrue(bottomRight.pitchDegrees() < 0.0);
    }

    @Test
    void sphericalGridProvidesPositionYawAndPitchAsOnePose() {
        FloatingMenuLayout layout = FloatingMenuLayouts.sphericalGrid(3, 2.6, 50.0, 38.0);
        FloatingMenuPose topLeft = layout.pose(context(0, 9));
        FloatingMenuPose center = layout.pose(context(4, 9));

        assertEquals(-25.0, topLeft.yawDegrees(), 0.0001);
        assertEquals(19.0, topLeft.pitchDegrees(), 0.0001);
        assertEquals(0.0, center.right(), 0.0001);
        assertEquals(0.0, center.up(), 0.0001);
        assertEquals(0.0, center.forward(), 0.0001);
        assertEquals(0.0, center.yawDegrees(), 0.0001);
        assertEquals(0.0, center.pitchDegrees(), 0.0001);
    }

    @Test
    void transformsPreserveAndComposePanelOrientation() {
        FloatingMenuLayout layout = FloatingMenuLayouts.orient(
                FloatingMenuLayouts.offset(FloatingMenuLayouts.arc(2.0, 60.0), 1, 2, 3),
                4.0, 5.0);
        FloatingMenuPose pose = layout.pose(context(0, 3));

        assertEquals(-26.0, pose.yawDegrees(), 0.0001);
        assertEquals(5.0, pose.pitchDegrees(), 0.0001);
        assertEquals(1.0 + Math.sin(Math.toRadians(-30.0)) * 2.0, pose.right(), 0.0001);
        assertEquals(2.0, pose.up(), 0.0001);
    }

    private static FloatingMenuLayout.Context context(int index, int count) {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        for (int current = 0; current < count; current++) {
            nodes.add(node("item:" + current, FloatingMenuNodeRole.ITEM,
                    FloatingMenuDefinition.DEFAULT_REGION, 1, 1));
        }
        return new FloatingMenuLayout.Context(index, nodes);
    }

    private static FloatingMenuLayout.Node node(String id, FloatingMenuNodeRole role,
                                                String region, double width, double height) {
        return new FloatingMenuLayout.Node(id, role.visualStyle(), role, region,
                new FloatingMenuSize(width, height));
    }
}
