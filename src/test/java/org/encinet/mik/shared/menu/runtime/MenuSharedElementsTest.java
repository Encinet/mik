package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.world.plot.PlotCardList;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuSharedElementsTest {
    private static final UiKey OBJECT = UiKey.named("plot:one");

    private static SceneGeometry measure(MenuScene scene) {
        return SceneGeometry.measureBase(scene,1,FloatingMenuFieldOfView.DEFAULT,1,UiPalette.DEFAULT);
    }

    @Test void plotCardsAndDetailHeadingsPairOnlyByTheDeclaredObjectWhileActionsRemainInTheirPage() {
        var called = new AtomicInteger();
        var list = PlotCardList.content(List.of(new PlotCardList.Card(OBJECT,Component.text("Garden\nOwner"),(_,_) -> called.incrementAndGet())),
                Component.text("Empty"),0,_ -> {});
        var directory = measure(MenuScene.screen("plots").content(list).sharedElement(OBJECT,OBJECT).build());
        var surface = new MenuSurfaceAttachment.Surface(new UiVector3(1,65,3),UiRotation.degrees(20,10,0),
                new FloatingMenuSize(2,1),.25,.28);
        var captured = MenuSharedElements.capture(directory,_ -> surface);
        assertEquals(java.util.Set.of(OBJECT),captured.keySet());
        var builder = MenuScene.screen("detail").content(Ui.column().child(UiTemplates.information("heading")).build())
                .sharedElement(OBJECT,UiKey.named("heading"));
        builder.information("heading",Component.text("Garden")).region("heading");
        var detail = measure(builder.build());
        var target = MenuSharedElements.targets(detail).get(OBJECT);
        assertNotNull(target);
        assertEquals(UiPointerBehavior.NONE,detail.definition().entries().get(target).pointerBehavior());
        assertEquals(0,called.get(),"Capturing and pairing never invokes or transfers the source action");
        assertEquals(Map.of(OBJECT,UiKey.named("heading")),detail.definition().toBuilder().build().sharedElements());
        assertTrue(MenuSharedElements.targets(measure(MenuScene.screen("unrelated").content(list).build())).isEmpty(),
                "Identical local keys and labels alone cannot pair pages");
    }

    @Test void worldToLocalConversionPreservesThePublishedSurfaceAcrossShrunkenFramesAndReverseNavigation() {
        var worldCenter = new UiVector3(3,65,7);
        var worldRotation = UiRotation.degrees(30,15,5);
        var source = new MenuSharedElements.Source(new MenuSurfaceAttachment.Surface(worldCenter,worldRotation,new FloatingMenuSize(2,1),.03),
                FloatingMenuElementStyle.TEXT);
        var origin = new UiVector3(2,64,6);
        for (float yaw : new float[]{0,60,180}) for (double parentScale : new double[]{.01,.25,1}) {
            var local = MenuSharedElements.local(source,origin,yaw,parentScale,new FloatingMenuSize(2,1));
            var parent = UiRotation.degrees(yaw,0,0);
            var restored = origin.add(parent.apply(new UiVector3(local.right(),local.up(),local.forward())).multiply(parentScale));
            assertEquals(worldCenter.right(),restored.right(),1e-9);
            assertEquals(worldCenter.up(),restored.up(),1e-9);
            assertEquals(worldCenter.forward(),restored.forward(),1e-9);
            assertEquals(.03,local.scale() * parentScale,1e-9);
            var transition = new MenuPoseTransition();
            transition.snap(local,0);
            transition.advance(FloatingMenuPose.ORIGIN,0,6,FloatingMenuEasing.CUBIC_OUT,MenuPoseTransition.Path.LINEAR);
            var midway = transition.sample(2);
            var current = new MenuSharedElements.Source(new MenuSurfaceAttachment.Surface(
                    origin.add(parent.apply(new UiVector3(midway.right(),midway.up(),midway.forward())).multiply(parentScale)),
                    parent.compose(midway.rotation()),new FloatingMenuSize(2,1),midway.scale() * parentScale),FloatingMenuElementStyle.TEXT);
            var reverse = MenuSharedElements.local(current,origin,yaw,parentScale,new FloatingMenuSize(2,1));
            assertEquals(midway.right(),reverse.right(),1e-9);
            assertEquals(midway.up(),reverse.up(),1e-9);
            assertEquals(midway.forward(),reverse.forward(),1e-9);
            assertEquals(midway.scale(),reverse.scale(),1e-9);
        }
    }

    @Test void missingAmbiguousAndUnpublishedSourcesFallBackToOrdinaryNavigation() {
        var key = UiKey.named("value");
        var content = Ui.row().child(Ui.column().key("left").child(Ui.text(Component.text("L")).key(key).build()).build())
                .child(Ui.column().key("right").child(Ui.text(Component.text("R")).key(key).build()).build()).build();
        var ambiguous = measure(MenuScene.screen("ambiguous").content(content).sharedElement(OBJECT,key).build());
        assertTrue(MenuSharedElements.targets(ambiguous).isEmpty());
        var missing = measure(MenuScene.screen("missing").content(Ui.text(Component.text("Text")).key(key).build())
                .sharedElement(OBJECT,UiKey.named("gone")).build());
        assertTrue(MenuSharedElements.targets(missing).isEmpty());
        var notPublished = measure(MenuScene.screen("unpublished").content(Ui.text(Component.text("Text")).key(key).build())
                .sharedElement(OBJECT,key).build());
        assertTrue(MenuSharedElements.capture(notPublished,_ -> null).isEmpty());
        assertThrows(IllegalArgumentException.class,() -> MenuScene.screen("duplicate").sharedElement(OBJECT,key).sharedElement(OBJECT,key));
    }
}
