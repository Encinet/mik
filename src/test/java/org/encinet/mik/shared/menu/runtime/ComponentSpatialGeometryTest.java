package org.encinet.mik.shared.menu.runtime;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.image.UiImage;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class ComponentSpatialGeometryTest {
    @Test void compiledOrbitPlanesKeepTheirWindingAcrossTheRearSeamIncludingWithFovProjection() {
        for (int fov : new int[]{70, 110}) {
            var before = SceneGeometry.measure(orbit(179), 1, new FloatingMenuFieldOfView(fov), 1);
            var after = SceneGeometry.measure(orbit(181), 1, new FloatingMenuFieldOfView(fov), 1);
            var a = pose(before, "tool"); var b = pose(after, "tool");
            assertTrue(a.yawDegrees() < 180);
            assertTrue(b.yawDegrees() > 180);
            assertTrue(b.yawDegrees() - a.yawDegrees() < 10);
            assertEquals(180, FloatingMenuMotion.approachOrbit(a, b, .5).yawDegrees(), 1e-8);
        }
    }

    private static MenuScene orbit(double angle) {
        UiNode panel = UiTemplates.window("panel", Ui.button(Component.text("Tool")).key("tool").build());
        UiNode content = Ui.layout((scope, limits) -> {
            UiSize size = scope.measure(0, limits);
            double x = 4.6 * Math.toRadians(angle) / UiSceneContent.WORLD_PER_UI - size.width() / 2;
            return new UiLayout.Result(new UiSize(1800, 200), List.of(new UiLayout.Position(x, 20)),
                    Optional.of(new UiLayout.Position(0, 0)));
        }).child(panel).build();
        return MenuScene.screen("orbit").aroundViewer().viewport(new UiSize(1800, 200)).content(content).build();
    }
    @Test void aSceneTitleUsesThePublishedEnvelopeIncludingTransformedImageOnlyContent() {
        UiNode image = Ui.image(UiImage.remote("https://example.org/cover.png", 64, 64), Component.text("Preview"))
                .size(new UiSize(100, 60)).build();
        var scene = MenuScene.screen("image-title", Component.text("Header"))
                .content(Ui.group(image).transform(UiTransform.IDENTITY.translated(0, 100, 0).rotated(20, 35, 70).scaled(1.5)).build()).build();
        var geometry = SceneGeometry.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var header = geometry.bounds().titlePose().orElseThrow();
        assertTrue(geometry.poses().isEmpty());
        var content = ComponentSceneLayout.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1).surfaceBounds().getFirst();
        double highest = Double.NEGATIVE_INFINITY;
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
            var offset = content.pose().rotation().apply(new UiVector3(x * content.size().width() / 2, y * content.size().height() / 2, 0)
                    .multiply(content.pose().scale()));
            highest = Math.max(highest, content.pose().up() + offset.up());
        }
        assertTrue(header.up() - FloatingMenuAnchorResolver.titleSize(1).height() / 2 >= highest + .22 - 1e-8);
        assertEquals(header.up() * .5, geometry.bounds().scaled(.5).titlePose().orElseThrow().up());
    }
    private static MenuScene scene(UiTransform transform) {
        var bitmap = UiImage.remote("https://example.org/cover.png", 128, 64);
        var content = Ui.surface(Ui.column().gap(UiLength.ui(8))
                .child(Ui.image(bitmap, Component.text("Cover")).key("cover").size(new UiSize(60, 40)).build())
                .child(Ui.button(Component.text("Play")).key("play").primary((_, _) -> { }).build()).build())
                .key("panel").style(UiStyle.PANEL).build();
        return MenuScene.screen("transformed").content(Ui.group(content).key("group").transform(transform).build()).build();
    }
    private static UiVector3 vector(FloatingMenuPoint point) { return new UiVector3(point.right(), point.up(), point.forward()); }
    private static FloatingMenuPose pose(SceneGeometry geometry, String key) {
        return geometry.poses().get(geometry.frame().elements().stream().filter(element -> element.node().key().value().equals(key))
                .findFirst().orElseThrow().path());
    }

    @Test void imagesBackgroundsControlsAndBoundsReceiveTheSameParentTransformOnce() {
        UiTransform declared = UiTransform.IDENTITY.translated(12, 5, 8).rotated(25, 15, 32).scaled(1.5);
        UiTransform world = new UiTransform(declared.translation().multiply(UiSceneContent.WORLD_PER_UI), declared.rotation(), declared.scale());
        var base = SceneGeometry.measure(scene(UiTransform.IDENTITY), 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var transformed = SceneGeometry.measure(scene(declared), 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertEquals(base.frame().size(), transformed.frame().size());
        for (int index = 0; index < base.frame().elements().size(); index++) {
            var a = base.frame().elements().get(index); var b = transformed.frame().elements().get(index);
            assertEquals(a.x(), b.x()); assertEquals(a.y(), b.y()); assertEquals(a.size(), b.size());
        }
        FloatingMenuPose play = pose(transformed, "play");
        assertVector(world.apply(vector(pose(base, "play").point())), vector(play.point()));
        assertRotation(declared.rotation(), play.rotation());
        assertEquals(1.5, play.scale());
        assertEquals(transformed.frame().elements().stream().map(UiFrame.Element::path).collect(java.util.stream.Collectors.toSet()),
                transformed.transformedContents());
        for (var entry : base.generatedDecorations().entrySet()) {
            var a = ((FloatingMenuPlacement.Local) entry.getValue().placement()).pose();
            var b = ((FloatingMenuPlacement.Local) transformed.generatedDecorations().get(entry.getKey()).placement()).pose();
            assertVector(world.apply(vector(a.point())), vector(b.point()));
            assertRotation(play.rotation(), b.rotation());
            assertEquals(play.scale(), b.scale());
            assertEquals(FloatingMenuDecoration.Transition.TRACKING, transformed.generatedDecorations().get(entry.getKey()).transition());
        }
        var path = transformed.poses().keySet().iterator().next();
        var size = transformed.sizes().get(path);
        for (int x : new int[]{-1, 1}) for (int y : new int[]{-1, 1}) {
            UiVector3 corner = vector(play.point()).add(play.rotation().apply(new UiVector3(x * size.width() / 2, y * size.height() / 2, 0).multiply(play.scale())));
            assertTrue(transformed.bounds().probes().stream().anyMatch(point -> vector(point).subtract(corner).dot(vector(point).subtract(corner)) < 1e-14));
        }
    }

    @Test void arcChildrenStayOnOneRigidPlaneAcrossFovAndInterfaceScale() {
        var scene = MenuScene.screen("rigid-arc").frontArc().content(Ui.row().gap(UiLength.ui(16))
                .child(UiTemplates.sidebar("sidebar", Ui.text(Component.text("Navigation sidebar")).build()))
                .child(UiTemplates.window("main", Ui.row().gap(UiLength.ui(20))
                        .child(Ui.button(Component.text("Previous")).key("a").build())
                        .child(Ui.button(Component.text("Next")).key("b").build()).build())).build()).build();
        for (int fov : new int[]{30, 70, 110}) for (double scale : new double[]{.7, 1, 1.4}) {
            var geometry = SceneGeometry.measure(scene, 1, new FloatingMenuFieldOfView(fov), scale);
            var a = pose(geometry, "a"); var b = pose(geometry, "b");
            var frameA = element(geometry.frame(), "a"); var frameB = element(geometry.frame(), "b");
            UiVector3 delta = a.rotation().inverse().apply(vector(b.point()).subtract(vector(a.point())));
            assertVector(new UiVector3((frameB.x() + frameB.size().width() / 2 - frameA.x() - frameA.size().width() / 2) * UiSceneContent.WORLD_PER_UI,
                    (frameA.y() + frameA.size().height() / 2 - frameB.y() - frameB.size().height() / 2) * UiSceneContent.WORLD_PER_UI, 0), delta);
            assertRotation(a.rotation(), b.rotation());
        }
    }

    @Test void wrappingAReadingSheetDoesNotChangeItsLayoutFocusOrAllocation() {
        UiNode content = UiTemplates.sheet(UiTemplates.window("body", Ui.text(Component.text("Read here")).key("read").build()),
                UiTemplates.toolbar("tools", Ui.button(Component.text("Back")).key("back").build()));
        var plain = SceneGeometryTestSupport.measure(MenuScene.screen("plain").content(content).build(), 1);
        var grouped = SceneGeometryTestSupport.measure(MenuScene.screen("grouped").content(Ui.group(content).build()).build(), 1);
        assertEquals(plain.size(), grouped.size()); assertEquals(plain.focus(), grouped.focus());
        assertEquals(element(plain, "read").x(), element(grouped, "read").x());
        assertEquals(element(plain, "back").y(), element(grouped, "back").y());
    }

    @Test void transformBudgetsAreCheckedBeforeAllocatingAnyDisplay() {
        var node = Ui.group(Ui.group(Ui.text(Component.text("X")).build()).transform(UiTransform.IDENTITY.scaled(10)).build())
                .transform(UiTransform.IDENTITY.scaled(10)).build();
        var invalid = MenuScene.screen("huge").content(node).build();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SceneGeometry.measure(invalid, 1, FloatingMenuFieldOfView.DEFAULT, 1))
                .getMessage().contains("spatial scale"));
        assertThrows(IllegalArgumentException.class, () -> SceneGeometry.measure(scene(UiTransform.IDENTITY.translated(100_000, 0, 0)),
                1, FloatingMenuFieldOfView.DEFAULT, 1));
        var invalidPivot = MenuScene.screen("pivot-overflow").content(Ui.group(Ui.text(Component.text("Preview")).build())
                .pivot(new UiPivot(Double.MAX_VALUE, 0, 0)).build()).build();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SceneGeometry.measure(invalidPivot,
                1, FloatingMenuFieldOfView.DEFAULT, 1)).getMessage().contains("root"));
    }

    @Test void fittingAScenePreservesRollLocalScaleAndVolumeDepth() {
        var pose = new FloatingMenuPose(new FloatingMenuPoint(1, 2, 3), 40, 15, 30, 1.7);
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.of(pose.point()), FloatingMenuFraming.COMFORTABLE,
                List.of(new FloatingMenuAnchorResolver.Surface(pose, new FloatingMenuSize(2, .5), .9)));
        var scaled = bounds.scaled(.5).surfaces().getFirst();
        assertVector(new UiVector3(.5, 1, 1.5), vector(scaled.pose().point()));
        assertRotation(pose.rotation(), scaled.pose().rotation());
        assertEquals(pose.scale(), scaled.pose().scale());
        assertEquals(.45, scaled.depth());
    }

}
