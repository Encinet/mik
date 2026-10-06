package org.encinet.mik.shell;

import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuNodeRole;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.encinet.mik.module.i18n.Language;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageRingLayoutTest {
    @Test
    void eightChoicesEncircleThePlayerAndFrontControlsStayReachable() {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        nodes.add(node("reader", "reader", 2.0, 0.9));
        for (int index = 0; index < 8; index++) {
            nodes.add(node("language:" + index,
                    LanguageRingLayout.cardRegion(index), 1.5, 0.7));
        }
        nodes.add(node("automatic", "navigation", 1.2, 0.42));

        FloatingMenuPose front = pose(nodes, 1);
        FloatingMenuPose side = pose(nodes, 2);
        FloatingMenuPose rear = pose(nodes, 5);
        assertEquals(0.0, front.right(), 0.0001);
        assertTrue(front.forward() < 0.0);
        assertTrue(side.right() > 0.0);
        assertTrue(rear.forward() > 0.0);
        assertEquals(180.0, rear.yawDegrees(), 0.0001);
        assertTrue(pose(nodes, 9).up() < pose(nodes, 0).up());
        assertTrue(Math.sqrt(side.right() * side.right() + side.up() * side.up()
                + side.forward() * side.forward()) * 1.1 < 3.0);
    }

    @Test
    void allLanguagesFitOneRingWithoutOverlappingNeighbors() {
        List<FloatingMenuLayout.Node> nodes = allLanguages();
        var layout = new LanguageRingLayout(0);
        assertTrue(layout.continuousOrbit());
        int count = Language.values().length;
        double radius = 0;
        for (int index = 0; index < count; index++) {
            var pose = layout.pose(new FloatingMenuLayout.Context(index + 1, nodes));
            double measuredRadius = Math.hypot(pose.right(), pose.forward());
            if (index == 0) radius = measuredRadius;
            assertEquals(radius, measuredRadius, 1.0E-6);
            assertEquals(index * 360.0 / count, pose.yawDegrees(), 1.0E-6);
        }
        assertTrue(2 * radius * Math.sin(Math.PI / count) >= 1.5 + 0.159);
    }

    @Test
    void crossingTheLastLanguagePreservesOneStepAndUnwrappedAngles() {
        var nodes = allLanguages();
        int count = Language.values().length;
        for (int index = 1; index <= count; index++) {
            var before = new LanguageRingLayout(count - 1).pose(new FloatingMenuLayout.Context(index, nodes));
            var after = new LanguageRingLayout(count).pose(new FloatingMenuLayout.Context(index, nodes));
            assertEquals(-360.0 / count, after.yawDegrees() - before.yawDegrees(), 1.0E-6);
        }
        var first = new LanguageRingLayout(count).pose(new FloatingMenuLayout.Context(1, nodes));
        assertEquals(-360, first.yawDegrees());
        assertEquals(0, first.right(), 1.0E-6);
        assertTrue(first.forward() < 0);
    }

    private List<FloatingMenuLayout.Node> allLanguages() {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        nodes.add(node("reader", "reader", 2, 0.9));
        for (int index = 0; index < Language.values().length; index++)
            nodes.add(node("language:" + index, LanguageRingLayout.cardRegion(index), 1.5, 0.7));
        nodes.add(node("automatic", "navigation", 1.2, 0.42));
        return nodes;
    }

    private static FloatingMenuLayout.Node node(String id, String region,
                                                double width, double height) {
        return new FloatingMenuLayout.Node(id, FloatingMenuElementStyle.TEXT,
                FloatingMenuNodeRole.CONTROL, region, new FloatingMenuSize(width, height));
    }

    private static FloatingMenuPose pose(List<FloatingMenuLayout.Node> nodes, int index) {
        return new LanguageRingLayout(0).pose(new FloatingMenuLayout.Context(index, nodes));
    }
}
