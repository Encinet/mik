package org.encinet.mik.module.governance.platform.paper.command;

import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuNodeRole;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceArcLayoutsTest {

    @Test
    void dashboardKeepsVoteListsTogetherLeftOfTheCentralDetailAndBallot() {
        FloatingMenuLayout layout = GovernanceArcLayouts.dashboard();
        List<FloatingMenuLayout.Node> nodes = dashboard(1);
        assertEquals(0, pose(layout, nodes, "heading").right(), 1.0E-6);
        assertEquals(0, pose(layout, nodes, "vote-detail").right(), 1.0E-6);
        for (String region : List.of("status", "open-votes", "results")) {
            assertTrue(pose(layout, nodes, region).right() < 0, region);
        }
        assertTrue(pose(layout, nodes, "actions").right() > 0);
        assertTrue(pose(layout, nodes, "ballot").up() < pose(layout, nodes, "vote-detail").up());
        assertTrue(pose(layout, nodes, "navigation").up() < pose(layout, nodes, "ballot").up());
        assertFrontArc(layout, nodes);
    }

    @Test
    void dashboardKeepsPanelGapsWithLargeTranslatedText() {
        List<FloatingMenuLayout.Node> nodes = dashboard(1.8);
        FloatingMenuLayout layout = GovernanceArcLayouts.dashboard();
        double previousEnd = Double.NEGATIVE_INFINITY;
        for (Set<String> panel : List.of(
                Set.of("status-heading", "status"),
                Set.of("votes-heading", "open-votes", "vote-pagination",
                        "results-heading", "results", "results-navigation"),
                Set.of("heading", "vote-detail", "ballot", "navigation"),
                Set.of("actions-heading", "actions"))) {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (FloatingMenuLayout.Node node : nodes) {
                if (!panel.contains(node.region())) continue;
                FloatingMenuPose pose = pose(layout, nodes, node.region());
                double halfAngle = Math.toDegrees(Math.atan(node.size().width() / (2 * radius(pose))));
                minimum = Math.min(minimum, pose.yawDegrees() - halfAngle);
                maximum = Math.max(maximum, pose.yawDegrees() + halfAngle);
            }
            assertTrue(minimum > previousEnd, panel.toString());
            previousEnd = maximum;
        }
    }

    @Test
    void multipleVotesStayInTheirColumnsWithAlignedHeadingsAndNoUnrelatedPanelBetweenThem() {
        for (double scale : new double[]{0.8, 1, 1.8}) {
            FloatingMenuLayout layout = GovernanceArcLayouts.dashboard();
            List<FloatingMenuLayout.Node> nodes = new ArrayList<>(dashboard(scale));
            nodes.add(new FloatingMenuLayout.Node("vote:second", FloatingMenuElementStyle.ITEM,
                    FloatingMenuNodeRole.ITEM, "open-votes", new FloatingMenuSize(4 * scale, 1.2 * scale)));
            nodes.add(new FloatingMenuLayout.Node("vote:third", FloatingMenuElementStyle.ITEM,
                    FloatingMenuNodeRole.ITEM, "open-votes", new FloatingMenuSize(5 * scale, 1.6 * scale)));
            nodes.add(new FloatingMenuLayout.Node("result:second", FloatingMenuElementStyle.ITEM,
                    FloatingMenuNodeRole.ITEM, "results", new FloatingMenuSize(5 * scale, 1.4 * scale)));

            assertEquals(pose(layout, nodes, "votes-heading").up(),
                    pose(layout, nodes, "results-heading").up(), 1.0E-6);
            assertEquals(pose(layout, nodes, "open-votes").right(),
                    pose(layout, nodes, "vote:second").right(), 1.0E-6);
            assertEquals(pose(layout, nodes, "vote:second").right(),
                    pose(layout, nodes, "vote:third").right(), 1.0E-6);
            assertEquals(pose(layout, nodes, "results").right(),
                    pose(layout, nodes, "result:second").right(), 1.0E-6);
            assertTrue(pose(layout, nodes, "status").yawDegrees()
                    < pose(layout, nodes, "results-heading").yawDegrees());
            assertTrue(pose(layout, nodes, "results-heading").yawDegrees()
                    < pose(layout, nodes, "votes-heading").yawDegrees());
            assertTrue(pose(layout, nodes, "votes-heading").yawDegrees()
                    < pose(layout, nodes, "heading").yawDegrees());
            assertFrontArc(layout, nodes);
        }
    }

    @Test
    void emptyDashboardStillHasAFrontFacingReadingPanel() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "navigation",
                "status", "open-votes", "results", "actions");
        FloatingMenuLayout layout = GovernanceArcLayouts.dashboard();
        assertEquals(0, pose(layout, nodes, "heading").right(), 1.0E-6);
        assertFrontArc(layout, nodes);
    }

    @Test
    void historyKeepsListLeftDetailCenterAndNavigationRight() {
        List<FloatingMenuLayout.Node> nodes = scene("votes", "pagination", "heading", "detail", "navigation");
        FloatingMenuLayout layout = GovernanceArcLayouts.history();
        assertTrue(pose(layout, nodes, "votes").right() < 0);
        assertEquals(0, pose(layout, nodes, "detail").right(), 1.0E-6);
        assertTrue(pose(layout, nodes, "navigation").right() > 0);
        assertFrontArc(layout, nodes);
    }

    @Test
    void petitionListsSurroundTheCentralHeadingAndControlsWithoutInstructions() {
        List<FloatingMenuLayout.Node> nodes = scene("petitions-left", "heading",
                "pagination", "navigation", "petitions-right");
        FloatingMenuLayout layout = GovernanceArcLayouts.petitions();
        assertTrue(pose(layout, nodes, "petitions-left").right() < 0);
        assertEquals(0, pose(layout, nodes, "heading").right(), 1.0E-6);
        assertTrue(pose(layout, nodes, "petitions-right").right() > 0);
        assertFrontArc(layout, nodes);
        List<FloatingMenuLayout.Node> sparse = scene("heading", "navigation", "petitions-left");
        assertEquals(0, pose(layout, sparse, "heading").right(), 1.0E-6);
    }

    @Test
    void playerReportStaysCenterAndManagementActionsStayRight() {
        List<FloatingMenuLayout.Node> nodes = scene("heading", "report", "actions", "navigation");
        FloatingMenuLayout layout = GovernanceArcLayouts.player();
        assertEquals(0, pose(layout, nodes, "report").right(), 1.0E-6);
        assertTrue(pose(layout, nodes, "actions").right() > 0);
        assertFrontArc(layout, nodes);
    }

    @Test
    void loadingAndFailureAreCenteredAndRemeasureAfterStatusChanges() {
        FloatingMenuLayout layout = GovernanceArcLayouts.status();
        List<FloatingMenuLayout.Node> nodes = scene("heading", "status", "navigation");
        assertFrontArc(layout, nodes);
        assertEquals(0, pose(layout, nodes, "status").right(), 1.0E-6);
        List<FloatingMenuLayout.Node> large = List.of(node("heading", 3, 0.4),
                node("status", 10, 3), node("navigation", 2, 0.6));
        assertEquals(0, pose(layout, large, "status").right(), 1.0E-6);
        assertFrontArc(layout, large);
    }

    private static List<FloatingMenuLayout.Node> dashboard(double scale) {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        for (String region : List.of("status-heading", "status", "votes-heading", "open-votes",
                "vote-pagination", "heading", "vote-detail", "ballot", "navigation",
                "results-heading", "results", "results-navigation", "actions-heading", "actions")) {
            nodes.add(node(region, (region.equals("vote-detail") ? 7 : 4) * scale,
                    (region.equals("vote-detail") || region.equals("status") ? 3 : 0.8) * scale));
        }
        return List.copyOf(nodes);
    }

    private static List<FloatingMenuLayout.Node> scene(String... regions) {
        return java.util.Arrays.stream(regions).map(region -> node(region, 4, 1)).toList();
    }

    private static FloatingMenuLayout.Node node(String region, double width, double height) {
        return new FloatingMenuLayout.Node(region, FloatingMenuElementStyle.TEXT,
                FloatingMenuNodeRole.CONTROL, region, new FloatingMenuSize(width, height));
    }

    private static FloatingMenuPose pose(FloatingMenuLayout layout,
                                         List<FloatingMenuLayout.Node> nodes, String elementId) {
        int index = java.util.stream.IntStream.range(0, nodes.size())
                .filter(candidate -> nodes.get(candidate).elementId().equals(elementId)).findFirst().orElseThrow();
        return layout.pose(new FloatingMenuLayout.Context(index, nodes));
    }

    private static void assertFrontArc(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes) {
        double radius = radius(layout.pose(new FloatingMenuLayout.Context(0, nodes)));
        for (int index = 0; index < nodes.size(); index++) {
            FloatingMenuPose pose = layout.pose(new FloatingMenuLayout.Context(index, nodes));
            assertEquals(radius, radius(pose), 1.0E-6);
            assertTrue(pose.forward() < 0);
            assertTrue(Math.abs(pose.yawDegrees()) < 80);
            assertEquals(Math.toDegrees(Math.atan2(pose.right(), -pose.forward())), pose.yawDegrees(), 1.0E-6);
        }
    }

    private static double radius(FloatingMenuPose pose) {
        return Math.hypot(pose.right(), pose.forward());
    }
}
