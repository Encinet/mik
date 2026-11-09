package org.encinet.mik.shared.menu.runtime;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.element;
import static org.junit.jupiter.api.Assertions.*;

class MenuMetadataStateTest {
    private final FloatingMenuTestWorld world = new FloatingMenuTestWorld();

    @Test void repeatedLiveResizingUpdatesTheClientBackgroundAndTextEvenWhenTheLogicalPanelIsUnchanged() {
        var scene = MenuScene.screen("settings").content(UiTemplates.window("panel",
                Ui.text(Component.text("Value")).key("value").build())
                .withSizing(UiSizing.atLeast(new UiSize(240,120)))).build();
        var presentation = new MenuPresentation();
        var backgroundState = new MenuMetadataState();
        var textState = new MenuMetadataState();
        var clientBackground = new AtomicReference<Transformation>();
        var clientText = new AtomicReference<Transformation>();
        var eye = new Location(world.world(),0,64,0);
        FloatingMenuDecoration.Surface original = null;
        for (int fov : new int[]{70,30,110,70}) for (double typography : new double[]{1,1.8,.7,1})
            for (double scale : new double[]{1,1.3,.7,1.3,1}) {
                var frame = presentation.prepare(null,eye,new Vector(0,0,1),scene,typography,scale,
                        new FloatingMenuFieldOfView(fov),UiPalette.DEFAULT);
                var geometry = frame.geometry();
                double parentScale = frame.anchor().orElseThrow().spatialScale();
                var panel = geometry.frame().panels().getFirst();
                var background = geometry.generatedDecorations().get("$surface/"+panel.path());
                var content = (FloatingMenuDecoration.Surface) background.content();
                if (original == null) original = content;
                assertEquals(original,content,"The regression requires identical logical background content");
                var pose = ((FloatingMenuPlacement.Local)background.placement()).pose();
                var backgroundContext = MenuMetadataState.Context.decoration(background,pose,pose.rotation(),parentScale,typography,UiPalette.DEFAULT);
                if (backgroundState.needsUpdate(backgroundContext)) backgroundState.publish(backgroundContext,() ->
                        clientBackground.set(MenuDisplayTransforms.surface((float)(content.width()*backgroundContext.coordinateScale()),
                                (float)(content.height()*backgroundContext.coordinateScale()),backgroundContext.rotation())));
                var node = element(geometry.frame(),"value");
                var textPose = geometry.poses().get(node.path());
                var textContext = MenuMetadataState.Context.node(parentScale,textPose,typography*node.node().style().textScale(),UiPalette.DEFAULT);
                if (textState.needsUpdate(textContext)) textState.publish(textContext,() -> clientText.set(MenuDisplayTransforms.text(
                        (float)(FloatingMenuNodeSizing.TEXT_SCALE*textContext.coordinateScale()*textContext.typographyScale()),textContext.rotation())));
                var expectedBackground = MenuDisplayTransforms.surface((float)(panel.size().width()*UiSceneContent.WORLD_PER_UI*parentScale*pose.scale()),
                        (float)(panel.size().height()*UiSceneContent.WORLD_PER_UI*parentScale*pose.scale()),pose.rotation());
                assertTransform(expectedBackground,clientBackground.get());
                assertTransform(MenuDisplayTransforms.text((float)(FloatingMenuNodeSizing.TEXT_SCALE*parentScale*textPose.scale()
                        *typography*node.node().style().textScale()),textPose.rotation()),clientText.get());
                assertFalse(backgroundState.needsUpdate(backgroundContext));
                assertFalse(textState.needsUpdate(textContext));
            }
    }

    @Test void obstacleContractionAndRecoveryRepaintTheSameCachedBackground() {
        var scene = MenuScene.screen("cached").content(UiTemplates.window("panel",Ui.text(Component.text("Value")).build())).build();
        var geometry = SceneGeometry.measure(scene,1,FloatingMenuFieldOfView.DEFAULT,1);
        var background = geometry.generatedDecorations().values().stream()
                .filter(value -> value.content() instanceof FloatingMenuDecoration.Surface).findFirst().orElseThrow();
        var content = (FloatingMenuDecoration.Surface)background.content();
        var pose = ((FloatingMenuPlacement.Local)background.placement()).pose();
        var presentation = new MenuPresentation();
        var metadata = new MenuMetadataState();
        var client = new AtomicReference<Transformation>();
        var eye = new Location(world.world(),.5,64.5,0);
        var scales = new java.util.ArrayList<Double>();
        var sends = new AtomicInteger();
        for (boolean obstructed : new boolean[]{false,true,false}) {
            for (int x=-2;x<=2;x++) for (int y=62;y<=67;y++) {
                if (obstructed) world.block(x,y,2,new org.bukkit.util.BoundingBox(0,0,0,1,1,1));
                else world.block(x,y,2);
            }
            var anchor = presentation.resolve(eye,new Vector(0,0,1),scene,geometry,1,1,FloatingMenuFieldOfView.DEFAULT);
            scales.add(anchor.spatialScale());
            var context = MenuMetadataState.Context.decoration(background,pose,pose.rotation(),anchor.spatialScale(),1,UiPalette.DEFAULT);
            if (metadata.needsUpdate(context)) metadata.publish(context,() -> {
                client.set(MenuDisplayTransforms.surface((float)(content.width()*context.coordinateScale()),
                        (float)(content.height()*context.coordinateScale()),context.rotation()));
                sends.incrementAndGet();
            });
            assertTransform(MenuDisplayTransforms.surface((float)(content.width()*anchor.spatialScale()*pose.scale()),
                    (float)(content.height()*anchor.spatialScale()*pose.scale()),pose.rotation()),client.get());
        }
        assertTrue(scales.get(1)<scales.getFirst());
        assertEquals(scales.getFirst(),scales.getLast());
        assertEquals(3,sends.get(),"The unchanged cached geometry still needs each new world transform");
    }

    @Test void worldDecorationsIgnoreMenuScalingButStillRefreshTheirThemeAndRotation() {
        var definition = new FloatingMenuDecoration("world",FloatingMenuPlacement.world(new Location(world.world(),0,64,0)),
                new FloatingMenuDecoration.Surface(2,1,0xff202020),FloatingMenuDecoration.Motion.NONE);
        var state = new MenuMetadataState();
        var initial = MenuMetadataState.Context.decoration(definition,FloatingMenuPose.ORIGIN,UiRotation.IDENTITY,.2,1,UiPalette.DEFAULT);
        var calls = new AtomicInteger();
        state.publish(initial,calls::incrementAndGet);
        for (double scale : new double[]{.1,.2,.7,1.3}) for (double typography : new double[]{.7,1.8}) {
            var same = MenuMetadataState.Context.decoration(definition,FloatingMenuPose.ORIGIN,UiRotation.IDENTITY,scale,typography,UiPalette.DEFAULT);
            assertFalse(state.needsUpdate(same),"World geometry retains its own units");
        }
        assertTrue(state.needsUpdate(MenuMetadataState.Context.decoration(definition,FloatingMenuPose.ORIGIN,UiRotation.IDENTITY,.2,1,UiPalette.FOREST)));
        assertTrue(state.needsUpdate(MenuMetadataState.Context.decoration(definition,FloatingMenuPose.ORIGIN,UiRotation.degrees(0,0,30),.2,1,UiPalette.DEFAULT)));
        assertEquals(1,calls.get());
    }

    @Test void positionOnlyMotionDoesNotResendMetadataWhileSizeRotationAndContentChangesDo() {
        var state = new MenuMetadataState();
        var pose = FloatingMenuPose.ORIGIN;
        var first = MenuMetadataState.Context.node(.2,pose,1,UiPalette.DEFAULT);
        state.publish(first,() -> {});
        assertFalse(state.needsUpdate(MenuMetadataState.Context.node(.2,pose.offset(3,2,1),1,UiPalette.DEFAULT)));
        assertTrue(state.needsUpdate(MenuMetadataState.Context.node(.3,pose,1,UiPalette.DEFAULT)));
        assertTrue(state.needsUpdate(MenuMetadataState.Context.node(.2,pose,1.8,UiPalette.DEFAULT)));
        assertTrue(state.needsUpdate(MenuMetadataState.Context.node(.2,pose.rotate(20,15),1,UiPalette.DEFAULT)));
        state.invalidate();
        assertTrue(state.needsUpdate(first),"New content must publish even in the same paint context");
        state.publish(first,() -> {});
        assertFalse(state.needsUpdate(first));
    }

    @Test void aFailedPublicationCannotAcknowledgeTheNewTransformOrClearPendingContent() {
        var state = new MenuMetadataState();
        var first = MenuMetadataState.Context.node(.2,FloatingMenuPose.ORIGIN,1,UiPalette.DEFAULT);
        var resized = MenuMetadataState.Context.node(.26,FloatingMenuPose.ORIGIN,1,UiPalette.DEFAULT);
        var client = new AtomicReference<>(first);
        state.publish(first,() -> {});
        assertThrows(IllegalStateException.class,() -> state.publish(resized,() -> {
            client.set(resized);
            throw new IllegalStateException("only part of the group was sent");
        }));
        assertTrue(state.needsUpdate(resized));
        assertTrue(state.needsUpdate(first),"Returning to the old context must repair a partially updated client");
        state.publish(first,() -> client.set(first));
        assertEquals(first,client.get());
        assertFalse(state.needsUpdate(first));
        state.invalidate();
        assertThrows(IllegalStateException.class,() -> state.publish(first,() -> { throw new IllegalStateException("send failed"); }));
        assertTrue(state.needsUpdate(first));
        state.publish(resized,() -> {});
        assertFalse(state.needsUpdate(resized));
    }

    private static void assertTransform(Transformation expected, Transformation actual) {
        assertNotNull(actual);
        assertTrue(expected.getScale().equals(actual.getScale(),1e-5f));
        assertTrue(expected.getTranslation().equals(actual.getTranslation(),1e-5f));
        assertTrue(expected.getLeftRotation().equals(actual.getLeftRotation(),1e-5f));
        assertTrue(expected.getRightRotation().equals(actual.getRightRotation(),1e-5f));
    }
}
