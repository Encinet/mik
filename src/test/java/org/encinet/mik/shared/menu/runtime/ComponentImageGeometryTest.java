package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.image.UiImage;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ComponentImageGeometryTest {
    @Test void layeredSurfacesPreservePlanarLayoutAndPublishTheirRealDepth() {
        var button = Ui.button(Component.text("Play")).primary((_, _) -> { }).build();
        var scene = MenuScene.screen("layers").frontArc().content(Ui.stack()
                .child(Ui.surface(button).key("base").build())
                .child(Ui.surface(button).key("front").elevation(UiLength.ui(6)).build()).build()).build();
        var geometry = SceneGeometry.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var base = geometry.poses().get("root/base/content");
        var front = geometry.poses().get("root/front/content");
        assertEquals(base.right(), front.right());
        assertEquals(base.up(), front.up());
        assertEquals(0.12, front.forward() - base.forward(), 1e-6);
        assertTrue(geometry.bounds().surfaces().stream().anyMatch(surface -> surface.pose().forward() == front.forward()));
    }
    private MenuScene scene(double background) {
        var image = UiImage.remote("https://example.org/cover.png", 128, 64);
        return MenuScene.screen("art").frontArc().content(Ui.row()
                .child(UiTemplates.sidebar("sidebar", Ui.text(Component.text("sidebar")).build()))
                .child(Ui.surface(Ui.column()
                        .child(Ui.image(image, Component.text("Cover")).key("image").size(new UiSize(80, 40))
                                .style(UiStyle.PLAIN.withContentAlpha(0.5)).build())
                        .child(Ui.button(Component.text("Play")).key("play").primary((_, _) -> { }).build()).build())
                        .key("main").style(UiStyle.PANEL.withBackgroundAlpha(background)).facing(UiSurfaceFacing.VIEWER).build()).build()).build();
    }
    @Test void imageUsesItsRigidPanelAndIndependentAlphaWithoutProducingFakeActions() {
        var scene = scene(0.75);
        var geometry = ComponentSceneLayout.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertEquals(2, scene.entries().size());
        assertTrue(scene.entries().values().stream().noneMatch(entry -> entry.label().equals(Component.text("Cover"))));
        var images = geometry.generatedDecorations().values().stream().filter(value -> value.content() instanceof FloatingMenuDecoration.Image).toList();
        assertEquals(1, images.size());
        assertTrue(((FloatingMenuPlacement.Local) images.getFirst().placement()).pose().yawDegrees() > 0);
        var image = (FloatingMenuDecoration.Image) images.getFirst().content();
        assertEquals(.5f, image.opacity());
        assertEquals(80 * UiSceneContent.WORLD_PER_UI, image.width(), 1e-6);
        assertEquals(40 * UiSceneContent.WORLD_PER_UI, image.height(), 1e-6);
    }

    @Test void proportionalQuadFitsAndCentersInBothWideAndTallBoxes() {
        assertEquals(new UiSize(100, 50), ComponentImageLayout.contain(1920, 960, new UiSize(100, 100)));
        assertEquals(new UiSize(25, 100), ComponentImageLayout.contain(480, 1920, new UiSize(100, 100)));
        assertEquals(new UiSize(200, 100), ComponentImageLayout.contain(1920, 960, new UiSize(200, 100)));
        var content = Ui.image(UiImage.remote("https://example.org/cover.png", 1920, 960), Component.text("Cover"))
                .size(new UiSize(100, 100)).build();
        var geometry = ComponentSceneLayout.measure(MenuScene.screen("image-fit").content(content).build(),
                1, FloatingMenuFieldOfView.DEFAULT, 1);
        var decoration = geometry.generatedDecorations().values().stream()
                .filter(value -> value.content() instanceof FloatingMenuDecoration.Image).findFirst().orElseThrow();
        var image = (FloatingMenuDecoration.Image) decoration.content();
        assertEquals(2, image.width(), 1e-6);
        assertEquals(1, image.height(), 1e-6);
        var pose = ((FloatingMenuPlacement.Local) decoration.placement()).pose();
        assertEquals(geometry.surfaceBounds().getFirst().pose().right(), pose.right(), 1e-6);
        assertEquals(geometry.surfaceBounds().getFirst().pose().up(), pose.up(), 1e-6);
    }

    @Test void makingABackgroundTransparentDoesNotTurnChildrenOrRemoveTheirPlane() {
        var visible = ComponentSceneLayout.measure(scene(0.75), 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var transparent = ComponentSceneLayout.measure(scene(0), 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertEquals(visible.poses(), transparent.poses());
        assertEquals(visible.frame().panels(), transparent.frame().panels());
        assertEquals(visible.surfaceBounds(), transparent.surfaceBounds());
        assertEquals(1, transparent.generatedDecorations().keySet().stream().filter(key -> key.startsWith("$surface/")).count());
    }
}
