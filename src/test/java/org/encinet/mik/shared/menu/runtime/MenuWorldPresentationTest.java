package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.assertRotation;
import static org.junit.jupiter.api.Assertions.*;

class MenuWorldPresentationTest {
    private final FloatingMenuTestWorld world = new FloatingMenuTestWorld();

    @Test void planarTextStaysOnItsBackingWhileVolumetricContentKeepsItsRequiredClearance() {
        var text = MenuScene.screen("flat-controls").animation(FloatingMenuAnimation.PLANAR)
                .appearance(FloatingMenuAppearance.NO_BACKGROUNDS)
                .content(Ui.button(Component.text("Floor 1")).primary((_,_) -> {}).build()).build();
        var model = MenuScene.screen("flat-model").animation(FloatingMenuAnimation.PLANAR)
                .content(Ui.group(Ui.item(SceneGeometryTestSupport.item(),Component.text("Model")).build())
                        .transform(UiTransform.IDENTITY.rotated(23,17,38).scaled(1.3)).build()).build();
        for (var rotation : List.of(UiRotation.IDENTITY,UiRotation.degrees(90,0,0),UiRotation.degrees(180,0,0),
                UiRotation.degrees(-90,0,0),UiRotation.degrees(0,-90,0),UiRotation.degrees(0,90,0),UiRotation.degrees(27,-43,61))) {
            var surface = surface(rotation,2,1.5);
            var flat = MenuWorldPresentation.prepare(new MenuPresentation(),surface,text,1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
            assertEquals(surface.center().toVector(),flat.anchor().orElseThrow().origin().toVector());
            var solid = MenuWorldPresentation.prepare(new MenuPresentation(),surface,model,1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
            var normal = rotation.apply(new UiVector3(0,0,1));
            var displacement = solid.anchor().orElseThrow().origin().toVector().subtract(surface.center().toVector());
            assertTrue(displacement.dot(new Vector(normal.right(),normal.up(),normal.forward())) > 0,
                    "Planar animation must preserve the physical depth of models");
            assertMountClearance(surface,solid);
        }
    }

    @Test void wallsFloorsCeilingsAndTiltedHostsShareOneRenderingAndInputFrame() {
        for (var rotation : List.of(UiRotation.IDENTITY,UiRotation.degrees(90,0,0),UiRotation.degrees(180,0,0),
                UiRotation.degrees(-90,0,0),UiRotation.degrees(0,-90,0),UiRotation.degrees(0,90,0),UiRotation.degrees(27,-43,61))) {
            var surface = surface(rotation,2,1);
            var frame = MenuWorldPresentation.prepare(new MenuPresentation(),surface,scene(),1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
            var anchor = frame.anchor().orElseThrow();
            var target = anchor.origin();
            var normal = rotation.apply(new UiVector3(0,0,1));
            var front = target.clone().add(normal.right()*2,normal.up()*2,normal.forward()*2);
            front.setDirection(target.toVector().subtract(front.toVector()));
            var hit = MenuWorldPresentation.hit(surface,target,frame.geometry().frame().size(),front).orElseThrow();
            assertEquals(2,hit.distance(),1e-6);
            assertEquals(.5,hit.horizontalFraction(),1e-6);
            var pose = FloatingMenuPose.transformed(FloatingMenuPoint.ORIGIN,UiRotation.degrees(8,17,22),.9);
            var node = MenuNodeSurface.sample(pose,target.toVector(),rotation,.25,new FloatingMenuSize(1,.5),FloatingMenuAnimation.DEFAULT,
                    new MenuNodeSurface.Feedback(0,0,1,true));
            var paint = MenuMetadataState.Context.node(.25,pose,1,UiPalette.DEFAULT).inFrame(rotation);
            assertRotation(node.rotation(),paint.rotation());
            var client = MenuDisplayTransforms.text((float)paint.coordinateScale(),paint.rotation());
            var right = new org.joml.Vector3f(1,0,0).rotate(client.getLeftRotation());
            var expectedRight = node.rotation().apply(new UiVector3(1,0,0));
            assertEquals(expectedRight.right(),right.x,1e-6);
            assertEquals(expectedRight.up(),right.y,1e-6);
            assertEquals(expectedRight.forward(),right.z,1e-6);
            var back = target.clone().add(-normal.right(),-normal.up(),-normal.forward());
            back.setDirection(target.toVector().subtract(back.toVector()));
            assertTrue(MenuWorldPresentation.hit(surface,target,frame.geometry().frame().size(),back).isEmpty());
        }
    }

    @Test void fixedExtentAndDensitySurviveTypographyChangesAndDoNotUseCameraPreferences() {
        var surface = surface(UiRotation.degrees(65,12,20),2,1.5);
        var presentation = new MenuPresentation();
        for (double typography : List.of(.7,1.,1.8,1.)) {
            var frame = MenuWorldPresentation.prepare(presentation,surface,scene(),typography,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
            var anchor = frame.anchor().orElseThrow();
            assertEquals(surface.scale(),anchor.spatialScale());
            assertTrue(frame.geometry().frame().size().width() <= surface.viewport().width()+1e-6);
            assertTrue(frame.geometry().frame().size().height() <= surface.viewport().height()+1e-6);
            assertEquals(surface.reach(),anchor.distance());
            assertMountClearance(surface,frame);
        }
    }

    @Test void changedSurfaceExtentReflowsImmediatelyAndFailedContentCannotMutateTheSurface() {
        var presentation = new MenuPresentation();
        var wide = surface(UiRotation.IDENTITY,2,1.5); var narrow = surface(UiRotation.IDENTITY,.8,1);
        var first = MenuWorldPresentation.prepare(presentation,wide,scene(),1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
        var second = MenuWorldPresentation.prepare(presentation,narrow,scene(),1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
        assertNotSame(first.geometry(),second.geometry());
        assertTrue(second.geometry().frame().size().width() <= narrow.viewport().width()+1e-6);
        assertThrows(IllegalArgumentException.class,() -> MenuWorldPresentation.prepare(presentation,wide,
                MenuScene.screen("bad",Component.text("External title")).content(Ui.text(Component.text("Body")).build()).build(),1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT));
        var recovered = MenuWorldPresentation.prepare(presentation,narrow,scene(),1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
        assertEquals(second.anchor(),recovered.anchor());
    }

    @Test void retainedLoadingContentReflowsAgainstTheCurrentHostAndSurvivesARejectedResize() {
        var presentation = new MenuPresentation();
        var initial = MenuWorldPresentation.prepare(presentation,surface(UiRotation.IDENTITY,2,1.5),scene(),1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
        var loading = MenuScene.screen("mounted").loading(true).content(Ui.text(Component.text("Loading")).build()).build();
        var narrow = surface(UiRotation.IDENTITY,.8,.7);
        var retained = MenuWorldPresentation.prepare(presentation,narrow,loading,1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
        assertNotSame(initial.geometry(),retained.geometry());
        assertTrue(retained.geometry().frame().size().width() <= narrow.viewport().width()+1e-6);
        assertTrue(retained.geometry().frame().size().height() <= narrow.viewport().height()+1e-6);
        assertTrue(retained.geometry().labels().values().stream().anyMatch(label ->
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(label).startsWith("Floor ")));
        assertFalse(retained.geometry().definition().loading());
        assertThrows(UiOverflowException.class,() -> MenuWorldPresentation.prepare(presentation,
                surface(UiRotation.IDENTITY,.15,.1),loading,1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT));
        var recovered = MenuWorldPresentation.prepare(presentation,narrow,loading,1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
        assertSame(retained.geometry(),recovered.geometry());
        assertEquals(retained.anchor(),recovered.anchor());
    }

    @Test void aBudgetRejectedUpdateCannotBecomeTheNextRetainedLoadingPage() {
        var presentation = new MenuPresentation();
        var surface = surface(UiRotation.IDENTITY,2,1.5);
        int capacity = 200;
        var initial = MenuWorldPresentation.prepare(presentation,surface,scene(),1,UiPalette.DEFAULT,capacity);
        var dense = Ui.stack();
        for (int index=0;index<100;index++) dense.child(Ui.button(Component.text("Rejected"))
                .key("rejected:"+index).primary((_,_) -> {}).build());
        var rejected = MenuScene.screen("mounted").content(dense.build()).build();
        assertThrows(UiOverflowException.class,() -> MenuWorldPresentation.prepare(presentation,surface,rejected,1,UiPalette.DEFAULT,capacity));
        var loading = MenuScene.screen("mounted").loading(true).content(Ui.text(Component.text("Loading")).build()).build();
        var retained = MenuWorldPresentation.prepare(presentation,surface,loading,1,UiPalette.DEFAULT,capacity);
        assertSame(initial.geometry(),retained.geometry());
        assertTrue(retained.geometry().frame().elements().stream().noneMatch(element -> element.node().key().value().startsWith("rejected:")));
    }

    @Test void raysRespectReachActualContentExtentWorldAndSolidObstruction() {
        var surface = surface(UiRotation.IDENTITY,2,1);
        var frame = MenuWorldPresentation.prepare(new MenuPresentation(),surface,scene(),1,UiPalette.DEFAULT,MenuEntityBudget.LIMIT);
        var origin = frame.anchor().orElseThrow().origin(); var size = frame.geometry().frame().size();
        var eye = origin.clone().add(0,0,2).setDirection(new Vector(0,0,-1));
        assertTrue(MenuWorldPresentation.hit(surface,origin,size,eye).isPresent());
        assertTrue(MenuWorldPresentation.hit(surface,origin,size,eye.clone().add(0,0,4)).isEmpty());
        assertTrue(MenuWorldPresentation.hit(surface,origin,size,eye.clone().add(2,0,0)).isEmpty());
        assertTrue(MenuWorldPresentation.hit(surface,origin,size,new Location(new FloatingMenuTestWorld().world(),0,64,2)).isEmpty());
        world.rayHit(eye.toVector().add(new Vector(0,0,-1)));
        assertTrue(MenuWorldPresentation.hit(surface,origin,size,eye).isEmpty());
    }

    @Test void surfaceOwnsItsCoordinatesAndRejectsInvalidDensityAndReach() {
        var location = new Location(world.world(),0,64,0);
        var surface = new WorldMenuSurface(location,UiRotation.IDENTITY,new FloatingMenuSize(2,1),.25,5);
        location.add(100,0,0); surface.center().add(200,0,0);
        assertEquals(0,surface.center().getX());
        assertThrows(IllegalArgumentException.class,() -> new WorldMenuSurface(location,UiRotation.IDENTITY,new FloatingMenuSize(2,1),0,5));
        assertThrows(IllegalArgumentException.class,() -> new WorldMenuSurface(location,UiRotation.IDENTITY,new FloatingMenuSize(2,1),.25,33));
    }

    private static void assertMountClearance(WorldMenuSurface surface, MenuPresentation.Frame frame) {
        var normal = surface.rotation().apply(new UiVector3(0,0,1));
        var displacement = frame.anchor().orElseThrow().origin().toVector().subtract(surface.center().toVector());
        double forward = displacement.dot(new Vector(normal.right(),normal.up(),normal.forward()));
        for (var plane : frame.geometry().bounds().surfaces())
            for (double x : new double[]{-plane.size().width() / 2,plane.size().width() / 2})
                for (double y : new double[]{-plane.size().height() / 2,plane.size().height() / 2})
                    for (double z : new double[]{-plane.depth() / 2,plane.depth() / 2}) {
                        var corner = plane.pose().rotation().apply(new UiVector3(x,y,z).multiply(plane.pose().scale()));
                        assertTrue(forward + (plane.pose().forward() + corner.forward()) * surface.scale() >= -1e-8,
                                "Every physical surface corner must remain in front of the backing");
                    }
    }

    private WorldMenuSurface surface(UiRotation rotation,double width,double height) {
        return new WorldMenuSurface(new Location(world.world(),0,64,0),rotation,new FloatingMenuSize(width,height),.25,5);
    }
    private MenuScene scene() {
        var items = Ui.windowedList(100,Ui.button(Component.text("Floor 100")).build(),index ->
                Ui.button(Component.text("Floor " + index)).key("floor:"+index).primary((_,_) -> {}).build()).key("floors").build();
        return MenuScene.screen("mounted").content(Ui.windowedGrid(items,8,UiLength.ui(90),UiLength.ui(8))).build();
    }
}
