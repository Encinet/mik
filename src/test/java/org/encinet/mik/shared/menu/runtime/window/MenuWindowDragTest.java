package org.encinet.mik.shared.menu.runtime.window;

import org.encinet.mik.shared.menu.runtime.layout.SceneGeometry;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuWindowDragTest {
    @Test void capturesTheCurrentPoseAndFollowsHeadRotationWithoutAccumulatingError() {
        var workspace = UiTransform.IDENTITY.translated(4,2,8).rotated(35,0,0);
        var original = UiTransform.IDENTITY.translated(30,12,32).rotated(-20,15,0);
        var pivot = new UiVector3(-70,40,0);
        double spatialScale = .12, unit = spatialScale * UiSceneContent.WORLD_PER_UI;
        var window = new UiTransform(workspace.apply(pivot.add(original.translation()).multiply(unit)),
                workspace.rotation().compose(original.rotation()),1);
        var eye = UiTransform.IDENTITY.translated(1,2,3).rotated(70,-12,0);
        var move = new MenuWindowDrag(eye,window,workspace,spatialScale,original);
        var first = move.sample(eye,workspace,spatialScale);
        vector(original.translation(),first.translation());
        rotation(original.rotation(),first.rotation());
        var turned = eye.translated(.2,.1,-.3).rotated(95,-30,0);
        var expected = turned.compose(eye.inverse()).compose(window);
        for (int pass = 0; pass < 100; pass++) {
            var sample = move.sample(turned,workspace,spatialScale);
            vector(expected.translation(),workspace.apply(pivot.add(sample.translation()).multiply(unit)));
            rotation(expected.rotation(),workspace.rotation().compose(sample.rotation()));
        }
        vector(original.translation(),move.sample(eye,workspace,spatialScale).translation());
    }

    @Test void reprojectsTheOwnedSurfaceAndDecorationsFromTheSameMeasuredFrame() {
        var scene = MenuScene.screen("test",Component.text("Window")).decorationScope(UiKey.named("window"))
                .content(Ui.group(Ui.surface(Ui.button(Component.text("Body")).key("body").primary((_,_) -> {}).build()).key("surface").build())
                        .key("window").build())
                .decoration(FloatingMenuDecoration.text("local",FloatingMenuPoint.ORIGIN,Component.text("Owned"))).build();
        var base = SceneGeometry.measure(scene,1,FloatingMenuFieldOfView.DEFAULT,1);
        var group = base.frame().groups().getFirst();
        var transform = UiTransform.IDENTITY.translated(40,20,0).rotated(30,15,0);
        var movedFrame = MenuWindowDrag.frame(base.frame(),group.path(),transform);
        var moved = base.reposition(movedFrame,1,FloatingMenuFieldOfView.DEFAULT,1,UiPalette.DEFAULT);
        String body = base.frame().elements().getFirst().path();
        assertEquals(base.frame().elements(),moved.frame().elements());
        assertSame(base.measurements().get(body),moved.measurements().get(body));
        assertNotEquals(base.poses().get(body),moved.poses().get(body));
        assertNotEquals(base.definition().decorations().get("local"),moved.definition().decorations().get("local"));
        assertEquals(moved.poses().get(body).rotation(),moved.panels().getFirst().pose().rotation());
        var restored = base.reposition(MenuWindowDrag.frame(base.frame(),group.path(),UiTransform.IDENTITY),1,FloatingMenuFieldOfView.DEFAULT,1,UiPalette.DEFAULT);
        assertEquals(base.poses(),restored.poses());
        assertEquals(base.definition().decorations(),restored.definition().decorations());
    }

    private static void vector(UiVector3 expected, UiVector3 actual) {
        assertEquals(expected.right(),actual.right(),1e-8);
        assertEquals(expected.up(),actual.up(),1e-8);
        assertEquals(expected.forward(),actual.forward(),1e-8);
    }
    private static void rotation(UiRotation expected, UiRotation actual) {
        vector(expected.apply(new UiVector3(1,0,0)),actual.apply(new UiVector3(1,0,0)));
        vector(expected.apply(new UiVector3(0,1,0)),actual.apply(new UiVector3(0,1,0)));
    }
}
