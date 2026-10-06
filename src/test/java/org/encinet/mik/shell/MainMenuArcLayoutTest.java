package org.encinet.mik.shell;

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

class MainMenuArcLayoutTest {

    @Test
    void keepsPlotsOnLeftAndVotesOnRightInsteadOfUnderTheHub() {
        List<FloatingMenuLayout.Node> nodes = scene(true, 1);
        MainMenuArcLayout layout = new MainMenuArcLayout();

        assertTrue(pose(layout, nodes, "plots").right() < 0);
        assertTrue(pose(layout, nodes, "plot-notice").right() < 0);
        assertTrue(pose(layout, nodes, "governance").right() > 0);
        assertTrue(pose(layout, nodes, "current-vote").right() > 0);
        assertTrue(pose(layout, nodes, "result").right() > 0);
        assertTrue(pose(layout, nodes, "plots").up() >= 0);
        assertTrue(pose(layout, nodes, "governance").up() >= 0);
        assertEquals(0, pose(layout, nodes, "heading").right(), 1.0E-6);
        assertEquals(0, pose(layout, nodes, "close").right(), 1.0E-6);
    }

    @Test
    void wholeMenuSharesOneBroadFrontArcAndFacesTheViewer() {
        List<FloatingMenuLayout.Node> nodes = scene(true, 1);
        MainMenuArcLayout layout = new MainMenuArcLayout();
        double radius = radius(pose(layout, nodes, "heading"));
        double minimumAngle = 0;
        double maximumAngle = 0;
        for (FloatingMenuLayout.Node node : nodes) {
            FloatingMenuPose pose = pose(layout, nodes, node.elementId());
            assertEquals(radius, radius(pose), 1.0E-6);
            assertEquals(Math.toDegrees(Math.atan2(pose.right(), -pose.forward())),
                    pose.yawDegrees(), 1.0E-6);
            assertTrue(pose.forward() < 0, node.elementId());
            assertTrue(Math.abs(pose.yawDegrees()) < 80, node.elementId());
            minimumAngle = Math.min(minimumAngle, pose.yawDegrees());
            maximumAngle = Math.max(maximumAngle, pose.yawDegrees());
        }
        assertTrue(maximumAngle - minimumAngle > 110);
    }

    @Test
    void neighboringPanelsHaveClearAngularSeparationEvenWithLargeText() {
        List<FloatingMenuLayout.Node> nodes = scene(true, 1.4);
        MainMenuArcLayout layout = new MainMenuArcLayout();
        List<Set<String>> panels = List.of(
                Set.of("profile-heading", "profile"),
                Set.of("plot-actions", "plot-notice"),
                Set.of("heading", "destinations", "quick", "more", "footer"),
                Set.of("governance-actions", "governance-heading", "governance-vote", "governance-pages", "governance-ballot",
                        "governance-results-heading", "governance-results"),
                Set.of("links-heading", "links"));
        double previousEnd = Double.NEGATIVE_INFINITY;
        for (Set<String> panel : panels) {
            double minimum = Double.POSITIVE_INFINITY;
            double maximum = Double.NEGATIVE_INFINITY;
            for (FloatingMenuLayout.Node node : nodes) {
                if (!panel.contains(node.region())) {
                    continue;
                }
                FloatingMenuPose pose = pose(layout, nodes, node.elementId());
                double halfAngle = Math.toDegrees(Math.atan(node.size().width() / (2 * radius(pose))));
                minimum = Math.min(minimum, pose.yawDegrees() - halfAngle);
                maximum = Math.max(maximum, pose.yawDegrees() + halfAngle);
            }
            assertTrue(minimum > previousEnd, panel.toString());
            previousEnd = maximum;
        }
    }

    @Test
    void currentVoteAndRecentResultsShareATopAlignedGroupAwayFromLinks() {
        MainMenuArcLayout layout = new MainMenuArcLayout();
        for (double scale : new double[]{0.8, 1.0, 1.8}) {
            List<FloatingMenuLayout.Node> nodes = new ArrayList<>(scene(true, scale));
            add(nodes, "result:second", "governance-results", 5, 1.8, scale);
            var currentHeading = pose(layout, nodes, "governance-heading");
            var recentHeading = pose(layout, nodes, "results-heading");
            var first = pose(layout, nodes, "result");
            var second = pose(layout, nodes, "result:second");

            assertEquals(currentHeading.up(), recentHeading.up(), 1.0E-6);
            assertTrue(currentHeading.yawDegrees() < recentHeading.yawDegrees());
            assertTrue(recentHeading.yawDegrees() < pose(layout, nodes, "links-heading").yawDegrees());
            assertEquals(first.right(), second.right(), 1.0E-6);
            assertTrue(first.up() > second.up());
            assertTrue(pose(layout, nodes, "governance").up() > currentHeading.up());
        }
    }

    @Test
    void missingPlotNoticeDoesNotMoveItsEntryBackToTheCenterOrBottom() {
        List<FloatingMenuLayout.Node> nodes = scene(false, 1);
        MainMenuArcLayout layout = new MainMenuArcLayout();

        assertTrue(pose(layout, nodes, "plots").right() < 0);
        assertEquals(0, pose(layout, nodes, "plots").up(), 1.0E-6);
        assertTrue(pose(layout, nodes, "governance").right() > 0);
        assertEquals(0, pose(layout, nodes, "heading").right(), 1.0E-6);
    }

    @Test
    void remeasuresWhenContentFootprintsChangeWithoutLosingFrontCenter() {
        MainMenuArcLayout layout = new MainMenuArcLayout();
        List<FloatingMenuLayout.Node> normal = scene(false, 1);
        double normalRadius = radius(pose(layout, normal, "heading"));
        List<FloatingMenuLayout.Node> large = scene(true, 1.4);

        assertTrue(radius(pose(layout, large, "heading")) > normalRadius);
        assertEquals(0, pose(layout, large, "heading").right(), 1.0E-6);
        assertEquals(normalRadius, radius(pose(layout, normal, "heading")), 1.0E-6);
    }

    private static List<FloatingMenuLayout.Node> scene(boolean plotNotice, double textScale) {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        add(nodes, "profile-heading", "profile-heading", 2.4, 0.4, textScale);
        add(nodes, "profile", "profile", 3.4, 1.8, textScale);
        add(nodes, "plots", "plot-actions", 1.4, 0.8, textScale);
        if (plotNotice) {
            add(nodes, "plot-notice", "plot-notice", 3.6, 1.6, textScale);
        }
        add(nodes, "heading", "heading", 1.8, 0.4, textScale);
        add(nodes, "home", "destinations", 1.2, 0.8, textScale);
        add(nodes, "music", "destinations", 1.2, 0.8, textScale);
        add(nodes, "announcements", "destinations", 2, 0.8, textScale);
        add(nodes, "afk", "quick", 1.5, 0.8, textScale);
        add(nodes, "pvp", "quick", 1.5, 0.8, textScale);
        add(nodes, "settings", "more", 1.4, 0.8, textScale);
        add(nodes, "close", "footer", 1, 0.42, textScale);
        add(nodes, "governance", "governance-actions", 2, 0.8, textScale);
        add(nodes, "governance-heading", "governance-heading", 2.4, 0.4, textScale);
        add(nodes, "current-vote", "governance-vote", 4.6, 2.2, textScale);
        add(nodes, "pages", "governance-pages", 1.4, 0.42, textScale);
        for (String choice : List.of("yes", "no", "abstain")) {
            add(nodes, choice, "governance-ballot", 1.2, 0.8, textScale);
        }
        add(nodes, "results-heading", "governance-results-heading", 2.4, 0.4, textScale);
        add(nodes, "result", "governance-results", 4.6, 1, textScale);
        add(nodes, "links-heading", "links-heading", 1.6, 0.4, textScale);
        for (String link : List.of("website", "map", "wiki")) {
            add(nodes, link, "links", 1.2, 0.8, textScale);
        }
        return List.copyOf(nodes);
    }

    private static void add(List<FloatingMenuLayout.Node> nodes, String id, String region,
                            double width, double height, double textScale) {
        nodes.add(new FloatingMenuLayout.Node(id, FloatingMenuElementStyle.TEXT,
                FloatingMenuNodeRole.CONTROL, region,
                new FloatingMenuSize(width * textScale, height * textScale)));
    }

    private static FloatingMenuPose pose(MainMenuArcLayout layout, List<FloatingMenuLayout.Node> nodes, String id) {
        for (int index = 0; index < nodes.size(); index++) {
            if (nodes.get(index).elementId().equals(id)) {
                return layout.pose(new FloatingMenuLayout.Context(index, nodes));
            }
        }
        throw new IllegalArgumentException("Unknown test node " + id);
    }

    private static double radius(FloatingMenuPose pose) {
        return Math.hypot(pose.right(), pose.forward());
    }
}
