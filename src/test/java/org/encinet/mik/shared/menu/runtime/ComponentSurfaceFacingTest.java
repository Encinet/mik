package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.image.UiImage;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ComponentSurfaceFacingTest {
    private static final double UNIT = UiSceneContent.WORLD_PER_UI;

    private static UiNode control(String key) {
        return Ui.button(Component.text(key)).key(key).primary((_, _) -> { }).build();
    }

    private static MenuScene workspace() {
        var body = UiTemplates.window("main", control("read")).withSizing(UiSizing.atLeast(new UiSize(240, 160)));
        return MenuScene.screen("folded").viewport(new UiSize(960, 2048))
                .content(UiTemplates.panoramicPanels("sheet", UiLength.ui(16),
                        UiTemplates.sidebar("left", control("left")),
                        UiTemplates.sheet(body, UiTemplates.toolbar("bottom", control("bottom"))),
                        UiTemplates.sidebar("right", control("right")))).build();
    }

    @Test void primaryStaysFlatWhileSideAndBottomPanelsFoldTowardsTheReader() {
        for (double typography : new double[]{.7, 1, 1.8}) for (int fov : new int[]{30, 70, 110})
            for (double scale : new double[]{.7, 1.4}) {
                var geometry = SceneGeometryTestSupport.naturalGeometry(workspace(), typography, new FloatingMenuFieldOfView(fov), scale);
                var main = pose(geometry, "read");
                assertRotation(UiRotation.IDENTITY, main.rotation());
                assertTrue(pose(geometry, "left").yawDegrees() < 0);
                assertTrue(pose(geometry, "right").yawDegrees() > 0);
                assertTrue(pose(geometry, "bottom").pitchDegrees() < 0);
                assertEquals(0, pose(geometry, "left").pitchDegrees(), 1e-8);
                assertEquals(0, pose(geometry, "right").pitchDegrees(), 1e-8);
                assertEquals(0, pose(geometry, "bottom").yawDegrees(), 1e-8);
                for (String key : List.of("left", "right", "bottom")) {
                    var pose = pose(geometry, key);
                    assertTrue(Math.abs(pose.yawDegrees()) <= 28 + 1e-8);
                    assertTrue(Math.abs(pose.pitchDegrees()) <= 22 + 1e-8);
                    assertEquals(0, pose.rollDegrees(), 1e-8);
                    assertTrue(geometry.transformedContents().contains(element(geometry.frame(), key).path()));
                }
            }
    }

    @Test void shortTopAlignedSidebarsOnlyTurnHorizontallyBesideATallReadingPanel() {
        UiNode content = UiTemplates.panoramicPanels("reading", UiLength.ui(16),
                UiTemplates.sidebar("left", control("left")).withSizing(UiSizing.fixed(new UiSize(120, 100))),
                UiTemplates.window("reading", control("read")).withSizing(UiSizing.fixed(new UiSize(280, 900))),
                UiTemplates.sidebar("right", control("right")).withSizing(UiSizing.fixed(new UiSize(120, 160))));
        for (var spatialFrame : FloatingMenuSpatialFrame.values()) {
            var scene = spatialScene(content, spatialFrame);
            for (int fov : new int[]{30, 70, 110}) {
                var geometry = SceneGeometryTestSupport.naturalGeometry(scene, 1, new FloatingMenuFieldOfView(fov), 1);
                assertTrue(pose(geometry, "left").yawDegrees() < 0);
                assertTrue(pose(geometry, "right").yawDegrees() > 0);
                assertEquals(0, pose(geometry, "left").pitchDegrees(), 1e-8, spatialFrame.name());
                assertEquals(0, pose(geometry, "right").pitchDegrees(), 1e-8, spatialFrame.name());
                assertRotation(UiRotation.IDENTITY, pose(geometry, "read").rotation());
            }
        }
    }

    @Test void offCenterTopAndBottomToolbarsOnlyTurnVertically() {
        var content = UiTemplates.focus(Ui.column().gap(UiLength.ui(16))
                .child(UiTemplates.toolbar("top", control("top")).withSizing(UiSizing.fixed(new UiSize(160, 100))),
                        new LinearSlot(0, LinearSlot.Alignment.START))
                .child(UiTemplates.window("reading", control("read")).withSizing(UiSizing.fixed(new UiSize(640, 140))))
                .child(UiTemplates.toolbar("bottom", control("bottom")).withSizing(UiSizing.fixed(new UiSize(160, 100))),
                        new LinearSlot(0, LinearSlot.Alignment.END)).build(), UiKey.named("reading"));
        for (var spatialFrame : FloatingMenuSpatialFrame.values()) {
            var scene = spatialScene(content, spatialFrame);
            for (int fov : new int[]{30, 70, 110}) {
                var geometry = SceneGeometryTestSupport.naturalGeometry(scene, 1, new FloatingMenuFieldOfView(fov), 1);
                assertTrue(pose(geometry, "top").pitchDegrees() > 0);
                assertTrue(pose(geometry, "bottom").pitchDegrees() < 0);
                assertEquals(0, pose(geometry, "top").yawDegrees(), 1e-8, spatialFrame.name());
                assertEquals(0, pose(geometry, "bottom").yawDegrees(), 1e-8, spatialFrame.name());
                assertRotation(UiRotation.IDENTITY, pose(geometry, "read").rotation());
            }
        }
    }

    @Test void aToolbarBelowSeveralReadingPanesUsesTheWholeRowToDetermineItsAxis() {
        var reading = UiTemplates.panoramicPanels("reading", UiLength.ui(16),
                UiTemplates.window("reading", control("read")).withSizing(UiSizing.fixed(new UiSize(120, 140))),
                UiTemplates.window("auxiliary", control("auxiliary")).withSizing(UiSizing.fixed(new UiSize(400, 140))));
        var content = UiTemplates.focus(Ui.column().gap(UiLength.ui(16))
                .child(UiKey.named("row"), reading)
                .child(UiTemplates.toolbar("bottom", control("bottom")).withSizing(UiSizing.fixed(new UiSize(160, 100))),
                        new LinearSlot(0, LinearSlot.Alignment.END)).build(), UiKey.named("row"));
        for (var spatialFrame : FloatingMenuSpatialFrame.values()) {
            var geometry = SceneGeometryTestSupport.naturalGeometry(spatialScene(content, spatialFrame), 1, FloatingMenuFieldOfView.DEFAULT, 1);
            var read = element(geometry.frame(), "read"); var bottom = element(geometry.frame(), "bottom");
            assertTrue(bottom.x() > read.x() + read.size().width());
            assertTrue(pose(geometry, "bottom").pitchDegrees() < 0);
            assertEquals(0, pose(geometry, "bottom").yawDegrees(), 1e-8);
            assertEquals(0, pose(geometry, "auxiliary").pitchDegrees(), 1e-8);
        }
    }

    private static MenuScene spatialScene(UiNode content, FloatingMenuSpatialFrame spatialFrame) {
        var scene = MenuScene.screen("axis-folding").viewport(new UiSize(960, 2048)).content(content);
        return switch (spatialFrame) {
            case IN_FRONT -> scene.build();
            case FRONT_ARC -> scene.frontArc().build();
            case AROUND_VIEWER -> scene.aroundViewer().build();
        };
    }

    @Test void diagonalPanelsChooseOneAxisWithADeterministicTieBreak() {
        var cases = java.util.Map.of(new UiLayout.Position(150, 200), true,
                new UiLayout.Position(200, 150), false, new UiLayout.Position(200, 200), true);
        for (var entry : cases.entrySet()) {
            var positions = java.util.Map.of(UiKey.named("reading"), new UiLayout.Position(400, 400), UiKey.named("corner"), entry.getKey());
            var content = UiTemplates.focus(Ui.layout(UiLayouts.canvas(new UiSize(800, 800), positions))
                    .child(UiTemplates.window("reading", control("read")).withSizing(UiSizing.fixed(new UiSize(200, 200))))
                    .child(UiTemplates.sidebar("corner", control("corner")).withFacing(UiSurfaceFacing.VIEWER).withSizing(UiSizing.fixed(new UiSize(100, 100))))
                    .build(), UiKey.named("reading"));
            var scene = MenuScene.screen("diagonal").viewport(new UiSize(800, 800)).content(content).build();
            var geometry = SceneGeometryTestSupport.naturalGeometry(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
            var corner = pose(geometry, "corner");
            if (entry.getValue()) {
                assertTrue(corner.yawDegrees() < 0);
                assertEquals(0, corner.pitchDegrees(), 1e-8);
            } else {
                assertEquals(0, corner.yawDegrees(), 1e-8);
                assertTrue(corner.pitchDegrees() > 0);
            }
            assertEquals(0, corner.rollDegrees(), 1e-8);
        }
    }

    @Test void foldingKeepsTheAdjoiningEdgeFixedAndOutOfThePrimaryReadingArea() {
        var scene = workspace();
        var geometry = SceneGeometryTestSupport.naturalGeometry(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var frame = geometry.frame();
        var projector = new ComponentSceneProjector(scene, frame, FloatingMenuFieldOfView.DEFAULT, 1);
        double distance = MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1);
        var main = frame.panels().stream().filter(panel -> panel.path().endsWith("/body")).findFirst().orElseThrow();
        for (var panel : frame.panels()) {
            double pivotX = Math.clamp(frame.size().width() / 2, panel.x(), panel.x() + panel.size().width());
            double pivotY = Math.clamp(frame.size().height() / 2, panel.y(), panel.y() + panel.size().height());
            var pivot = projector.project(panel.path(), pivotX, pivotY, UiSize.ZERO);
            var elevation = pivot.rotation().apply(new UiVector3(0, 0, panel.elevation().value() * UNIT));
            assertVector(new UiVector3((pivotX - frame.size().width() / 2) * UNIT,
                    (frame.size().height() / 2 - pivotY) * UNIT, 0).add(elevation), vector(pivot));
            if (panel == main) continue;
            var center = projector.project(panel.path(), panel.x(), panel.y(), panel.size());
            for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
                var corner = vector(center).add(center.rotation().apply(new UiVector3(
                        x * panel.size().width() * UNIT / 2, y * panel.size().height() * UNIT / 2, 0)));
                double projectedX = corner.right() * distance / (distance - corner.forward()) / UNIT + frame.size().width() / 2;
                double projectedY = frame.size().height() / 2 - corner.up() * distance / (distance - corner.forward()) / UNIT;
                assertFalse(projectedX > main.x() && projectedX < main.x() + main.size().width()
                        && projectedY > main.y() && projectedY < main.y() + main.size().height(), panel.path());
            }
        }
    }

    private static MenuScene nested(double alpha) {
        var card = Ui.surface(Ui.column().child(control("nested"))
                        .child(Ui.image(UiImage.remote("https://example.org/cover.png", 128, 64), Component.text("Cover"))
                                .key("cover").size(new UiSize(60, 30)).build()).build())
                .key("card").style(UiStyle.PANEL.withBackgroundAlpha(alpha)).elevation(UiLength.ui(6)).build();
        return MenuScene.screen("nested").content(UiTemplates.split(
                UiTemplates.sidebar("sidebar", Ui.column().child(control("outer")).child(card).build()),
                UiTemplates.window("main", control("read")))).build();
    }

    @Test void nestedCardsImagesAndTransparentBackgroundsInheritOneOwningPlane() {
        var visible = SceneGeometryTestSupport.naturalGeometry(nested(.8), 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var transparent = SceneGeometryTestSupport.naturalGeometry(nested(0), 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertEquals(visible.poses(), transparent.poses());
        assertEquals(visible.frame().panels(), transparent.frame().panels());
        var outer = pose(visible, "outer"); var nested = pose(visible, "nested");
        assertTrue(outer.yawDegrees() < 0);
        assertRotation(outer.rotation(), nested.rotation());
        var delta = outer.rotation().inverse().apply(vector(nested).subtract(vector(outer)));
        assertEquals(6 * UNIT, delta.forward(), 1e-8);
        var images = visible.generatedDecorations().values().stream().filter(value -> value.content() instanceof FloatingMenuDecoration.Image).toList();
        assertEquals(1, images.size());
        for (var decoration : visible.generatedDecorations().values()) {
            if (!decoration.id().contains("/sidebar/")) continue;
            assertRotation(outer.rotation(), ((FloatingMenuPlacement.Local) decoration.placement()).pose().rotation());
            assertEquals(FloatingMenuDecoration.Transition.TRACKING, decoration.transition());
        }
    }

    @Test void aSecondaryPanelCanExplicitlyRemainParallelToThePrimaryPlane() {
        var parallel = UiTemplates.sidebar("sidebar", control("parallel")).withFacing(UiSurfaceFacing.FORWARD);
        var scene = MenuScene.screen("parallel").content(UiTemplates.split(parallel,
                UiTemplates.window("main", control("read")))).build();
        var geometry = SceneGeometryTestSupport.naturalGeometry(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertTrue(pose(geometry, "parallel").right() < 0);
        assertRotation(UiRotation.IDENTITY, pose(geometry, "parallel").rotation());
    }

    @Test void verticalFallbackUsesItsActualPositionInsteadOfKeepingASidewaysTilt() {
        var scene = MenuScene.screen("narrow").viewport(new UiSize(280, 2048)).content(UiTemplates.split(
                UiTemplates.sidebar("sidebar", control("above")).withSizing(UiSizing.atLeast(new UiSize(200, 60))),
                UiTemplates.window("main", control("read")).withSizing(UiSizing.atLeast(new UiSize(240, 120))))).build();
        var geometry = SceneGeometryTestSupport.naturalGeometry(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var above = pose(geometry, "above");
        assertEquals(0, above.yawDegrees(), 1e-8);
        assertTrue(above.pitchDegrees() > 0);
        assertRotation(UiRotation.IDENTITY, pose(geometry, "read").rotation());
    }

    @Test void raySelectionUsesTheSameTiltedControlPlanesAsRendering() {
        var geometry = SceneGeometryTestSupport.naturalGeometry(workspace(), 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var eye = new Vector(0, 0, MenuViewportLayout.referenceDistance(FloatingMenuFieldOfView.DEFAULT,1));
        for (var entry : geometry.poses().entrySet()) {
            var pose = entry.getValue(); var size = geometry.sizes().get(entry.getKey());
            var center = bukkit(vector(pose));
            var hit = FloatingMenuSurfaceGeometry.intersect(eye, center.clone().subtract(eye), center,
                    (float) pose.yawDegrees(), (float) pose.pitchDegrees(), (float) pose.rollDegrees(), size.width(), size.height(), 0).orElseThrow();
            assertEquals(.5, hit.horizontalFraction(), 1e-6);
            var outside = center.clone().add(bukkit(pose.rotation().apply(new UiVector3(size.width() + .1, 0, 0))));
            assertTrue(FloatingMenuSurfaceGeometry.intersect(eye, outside.subtract(eye), center,
                    (float) pose.yawDegrees(), (float) pose.pitchDegrees(), (float) pose.rollDegrees(), size.width(), size.height(), 0).isEmpty());
        }
    }

    private static UiVector3 vector(FloatingMenuPose pose) { return new UiVector3(pose.right(), pose.up(), pose.forward()); }
    private static Vector bukkit(UiVector3 value) { return new Vector(value.right(), value.up(), value.forward()); }
    private static FloatingMenuPose pose(SceneGeometry geometry, String key) {
        return geometry.poses().get(element(geometry.frame(), key).path());
    }
}
