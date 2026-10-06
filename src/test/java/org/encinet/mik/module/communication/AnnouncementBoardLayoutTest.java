package org.encinet.mik.module.communication;

import org.encinet.mik.module.menu.FloatingMenuElementStyle;
import org.encinet.mik.module.menu.FloatingMenuLayout;
import org.encinet.mik.module.menu.FloatingMenuNodeRole;
import org.encinet.mik.module.menu.FloatingMenuPose;
import org.encinet.mik.module.menu.FloatingMenuSize;
import org.encinet.mik.module.menu.FloatingMenuTextScale;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnnouncementBoardLayoutTest {
    @Test
    void fragmentAnchorsAccountForTextDisplaysBottomOriginAndStayInsideTheReadingArea() {
        for (var scale : FloatingMenuTextScale.values()) {
            double typography = scale.factor();
            double halfWidth = AnnouncementViewport.WIDTH * AnnouncementBoardLayout.PIXEL_SIZE * typography / 2;
            double halfHeight = AnnouncementBoardLayout.BODY_HEIGHT * typography / 2;
            for (int row = 0; row < AnnouncementViewport.ROWS; row++) {
                for (double left : new double[] {2, 170, 341}) {
                    var fragment = new AnnouncementViewport.Fragment(0, row, "甲", left, 9);
                    var pose = AnnouncementBoardLayout.fragmentPose(fragment, typography);
                    assertEquals(0, pose.yawDegrees());
                    assertEquals(0, pose.pitchDegrees());
                    assertTrue(pose.up() > -halfHeight);
                    assertTrue(pose.up() + 9 * AnnouncementBoardLayout.PIXEL_SIZE * typography < halfHeight);
                    assertTrue(Math.abs(pose.right()) + 4.5 * AnnouncementBoardLayout.PIXEL_SIZE * typography < halfWidth);
                }
            }
        }
    }

    @Test
    void wallIsCoplanarAndControlsStayFixedForShortAndLongContent() {
        for (var scale : FloatingMenuTextScale.values()) {
            double typography = scale.factor();
            var layout = AnnouncementBoardLayout.create(typography);
            var shortScene = scene(0.4, typography);
            var longScene = scene(AnnouncementBoardLayout.BODY_HEIGHT, typography);
            for (int index = 0; index < shortScene.size(); index++) {
                var pose = pose(layout, shortScene, index);
                assertEquals(pose, pose(layout, longScene, index));
                assertEquals(0, pose.forward());
                assertEquals(0, pose.yawDegrees());
                assertEquals(0, pose.pitchDegrees());
            }
            assertTrue(pose(layout, shortScene, 2).right() < 0);
            assertTrue(pose(layout, shortScene, 3).right() > 0);
        }
    }

    @Test
    void headingAndNavigationStayOutsideTheFixedBodyBandAtEveryTextScale() {
        for (var scale : FloatingMenuTextScale.values()) {
            double typography = scale.factor();
            var nodes = scene(AnnouncementBoardLayout.BODY_HEIGHT, typography);
            var layout = AnnouncementBoardLayout.create(typography);
            double bodyTop = AnnouncementBoardLayout.BODY_HEIGHT * typography / 2;
            assertTrue(pose(layout, nodes, 0).up() - nodes.getFirst().size().height() / 2 > bodyTop);
            assertTrue(pose(layout, nodes, 2).up() + nodes.get(2).size().height() / 2 < -bodyTop);
            assertTrue(pose(layout, nodes, 4).up() + nodes.get(4).size().height() / 2
                    < pose(layout, nodes, 2).up() - nodes.get(2).size().height() / 2);
            for (int index = 0; index < nodes.size(); index++) {
                var pose = pose(layout, nodes, index);
                assertTrue(Math.abs(pose.up()) + nodes.get(index).size().height() / 2
                        < AnnouncementBoardLayout.HEIGHT * typography / 2);
                assertTrue(Math.abs(pose.right()) + nodes.get(index).size().width() / 2
                        < AnnouncementBoardLayout.WIDTH * typography / 2);
            }
        }
    }

    @Test
    void announcementBodiesAreTrackingContentWithStaticNativeFallback() throws Exception {
        String source = Files.readString(Path.of("src/main/java/org/encinet/mik/module/communication/AnnouncementModule.java"));
        assertTrue(source.contains("AnnouncementViewport.fragments("));
        assertTrue(source.contains("FloatingMenuDecoration.volume(\"wall\""));
        assertTrue(source.contains(".tracking()"));
        assertTrue(source.contains("menu.information(\"body\""));
        assertTrue(source.contains("Component.text(page.text()"));
        assertTrue(source.contains("FloatingMenus.supportsSpatialScenes(player)"));
        assertTrue(source.contains(".stableAnchor()"));
        assertFalse(source.contains(".frontArc()"));
        assertFalse(source.contains("AnnouncementReading.preview"));
        assertFalse(source.contains(".aroundViewer()"));
        assertFalse(source.contains("runTaskTimer"));
    }

    @Test
    void invalidTypographyIsRejected() {
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> AnnouncementBoardLayout.create(invalid));
    }

    private static List<FloatingMenuLayout.Node> scene(double bodyHeight, double typography) {
        List<FloatingMenuLayout.Node> nodes = new ArrayList<>();
        nodes.add(node("heading", "heading", 4, 0.8, typography));
        nodes.add(node("body", "body", 7.4, bodyHeight, typography));
        nodes.add(node("newer", "browse", 2, 0.56, typography));
        nodes.add(node("older", "browse", 2.5, 0.56, typography));
        nodes.add(node("reload", "footer", 2.4, 0.56, typography));
        nodes.add(node("close", "footer", 1.8, 0.56, typography));
        return nodes;
    }

    private static FloatingMenuLayout.Node node(String id, String region, double width, double height, double typography) {
        return new FloatingMenuLayout.Node(id, FloatingMenuElementStyle.TEXT,
                FloatingMenuNodeRole.INFORMATION, region, new FloatingMenuSize(width * typography, height * typography));
    }

    private static FloatingMenuPose pose(FloatingMenuLayout layout, List<FloatingMenuLayout.Node> nodes, int index) {
        return layout.pose(new FloatingMenuLayout.Context(index, nodes));
    }
}
