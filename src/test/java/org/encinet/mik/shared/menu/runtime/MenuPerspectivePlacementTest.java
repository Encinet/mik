package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import java.lang.reflect.Proxy;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPerspectivePlacementTest {
    @Test void aFourBlockSurfaceRemainsRaySelectableAndDuplicateClickSignalsExecuteOnlyOnce() {
        var world = new FloatingMenuTestWorld(); var eye = new Location(world.world(),0,65.62,0);
        var surface = new MenuSurfaceAttachment.Surface(new UiVector3(0,65.62,4),UiRotation.degrees(180,0,0),new FloatingMenuSize(1,1),1);
        assertEquals(4,surface.intersect(eye,0).orElseThrow().distance(),1e-8);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var scene = MenuScene.screen("distant-click").content(Ui.button(Component.text("Action")).key("action").primary((_,_) -> calls.incrementAndGet()).build()).build();
        var action = org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(scene,"action");
        var gate = new MenuInputGate(() -> 0);
        for (int signal = 0; signal < 2; signal++) if (gate.accept(action.id(),FloatingMenuInteraction.PRIMARY))
            gate.dispatch(() -> action.trigger(FloatingMenuInteraction.PRIMARY).execute(null,null,FloatingMenuInteraction.PRIMARY));
        assertEquals(1,calls.get());
    }
    @Test void recoveryPreservesPerspectiveFromVerySmallWorldFramesToTheNominalFrame() {
        var world = new FloatingMenuTestWorld(); var eye = new Location(world.world(),0,65.62,0);
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN));
        var target = MenuAnchorTestSupport.front(eye,new Vector(0,0,1),bounds);
        double scale = .0005;
        int steps = 0;
        while (scale < target.spatialScale() && steps++ < 512) {
            var eased = FloatingMenuAnchorMotion.recover(eye, target, scale);
            assertTrue(eased.spatialScale() > scale && eased.spatialScale() <= target.spatialScale());
            assertEquals(target.spatialScale() / target.distance(), eased.spatialScale() / eased.distance(), 1e-9);
            scale = eased.spatialScale();
        }
        assertEquals(target.spatialScale(), scale);
    }
    @Test void aVerticalClearanceRayAtABlockBoundaryFindsTheActualFloorRatherThanAZeroDistanceHit() {
        var world = new FloatingMenuTestWorld();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) world.block(x,63,z,new org.bukkit.util.BoundingBox(0,0,0,1,1,1));
        var eye = new Location(world.world(),0,65.62,0);
        assertEquals(1.62,new FloatingMenuWorldSpace(world.world()).firstHit(eye,new Vector(0,-1,0),4),1e-8);
    }
    @Test void fieldOfViewDoesNotInflateThePhysicalPageOrTypography() {
        var world = new FloatingMenuTestWorld(); var eye = new Location(world.world(),0,65.62,0);
        var bounds = new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN));
        for (int fov : new int[]{30,70,110}) {
            var field = new FloatingMenuFieldOfView(fov);
            var actual = MenuAnchorTestSupport.front(eye,new Vector(0,0,1),bounds,1,field);
            assertEquals(MenuViewportLayout.distance(field),actual.distance(),1e-8);
            assertEquals(.25,actual.spatialScale(),1e-8,
                    "FOV preserves the nominal physical scale");
            var estimated = MenuReadingParameters.estimated(bounds,field,1);
            assertEquals(actual.distance(),-estimated.origin().forward(),1e-8);
            assertEquals(actual.spatialScale(),estimated.scale(),1e-8);
        }
    }

    @Test void tightTerrainAndEntitiesKeepInitialAndLoadingPagesVisibleAtTheSameApparentSize() {
        for (var frame : new FloatingMenuSpatialFrame[]{FloatingMenuSpatialFrame.IN_FRONT, FloatingMenuSpatialFrame.FRONT_ARC})
            for (boolean entity : new boolean[]{false, true}) for (double gap : new double[]{.05, .2, .8}) {
                var world = new FloatingMenuTestWorld(); var eye = new Location(world.world(), .5, 65.62, .5);
                Player viewer = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                        (_, method, _) -> switch (method.getName()) {
                            case "canSee" -> true;
                            default -> throw new UnsupportedOperationException(method.getName());
                        });
                var builder = MenuScene.screen("tight-space").content(UiTemplates.window("reader",
                        Ui.button(Component.text("Current task")).key("action").primary((_,_) -> {}).build()));
                if (frame == FloatingMenuSpatialFrame.FRONT_ARC) builder.frontArc();
                var scene = builder.build();
                var clear = prepare(new MenuPresentation(), viewer, eye, scene);
                double wall = eye.getZ() + gap;
                if (entity) {
                    var box = new BoundingBox(-4, 62, wall, 4, 70, wall + 1);
                    world.entity((LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                            (_, method, _) -> switch (method.getName()) {
                                case "getBoundingBox" -> box.clone();
                                case "isValid" -> true;
                                case "isInvisible" -> false;
                                default -> throw new UnsupportedOperationException(method.getName());
                            }));
                } else {
                    int z = (int) Math.floor(wall);
                    for (int x = -4; x <= 4; x++) for (int y = 62; y <= 69; y++)
                        world.block(x,y,z,new BoundingBox(0,0,wall-z,1,1,wall-z+1));
                }
                for (boolean loading : new boolean[]{false, true}) {
                    var fitted = prepare(new MenuPresentation(), viewer, eye, scene.toBuilder().loading(loading).build());
                    var anchor = fitted.anchor().orElseThrow();
                    assertTrue(anchor.distance() < 1.5, frame + " / entity=" + entity + " / gap=" + gap);
                    assertEquals(clear.geometry().frame().size(), fitted.geometry().frame().size());
                    assertPerspective(clear.anchor().orElseThrow(), anchor, eye, fitted.geometry().bounds());
                    SceneGeometryTestSupport.assertReadingCone(fitted.geometry(), anchor, eye, FloatingMenuFieldOfView.DEFAULT, "tight space");
                    var space = new FloatingMenuWorldSpace(eye, MenuEntityObstacles.capture(viewer, eye, 8));
                    assertWorldClear(space, eye, anchor, fitted.geometry().bounds());
                }
            }
    }

    @Test void movingObstaclesContractTheSameViewAndRecoveryRestoresItsOriginalFrame() {
        var world = new FloatingMenuTestWorld(); var eye = new Location(world.world(),.5,65.62,0);
        var scene = MenuScene.screen("perspective").withoutTitle().stableAnchor()
                .content(UiTemplates.window("reader",Ui.button(Component.text("Current task")).key("action").primary((_,_) -> {}).build())).build();
        var view = new MenuPresentation();
        var clear = view.prepare(null,eye,new Vector(0,0,1),scene,1,1,FloatingMenuFieldOfView.DEFAULT,UiPalette.DEFAULT);
        assertEquals(2.8,clear.anchor().orElseThrow().distance(),1e-8);
        for (int x = -4; x <= 4; x++) for (int y = 62; y <= 69; y++) world.block(x,y,1,new org.bukkit.util.BoundingBox(0,0,0,1,1,1));
        var contracted = view.prepare(null,eye,new Vector(0,0,1),scene,1,1,FloatingMenuFieldOfView.DEFAULT,UiPalette.DEFAULT);
        assertEquals(clear.geometry().frame().size(),contracted.geometry().frame().size());
        assertPerspective(clear.anchor().orElseThrow(), contracted.anchor().orElseThrow(), eye, contracted.geometry().bounds());
        assertWorldClear(new FloatingMenuWorldSpace(eye, List.of()), eye, contracted.anchor().orElseThrow(), contracted.geometry().bounds());
        for (int x = -4; x <= 4; x++) for (int y = 62; y <= 69; y++) world.block(x,y,1);
        var restored = view.prepare(null,eye,new Vector(0,0,1),scene,1,1,FloatingMenuFieldOfView.DEFAULT,UiPalette.DEFAULT);
        assertEquals(clear.anchor(),restored.anchor());
    }

    private static MenuPresentation.Frame prepare(MenuPresentation view, Player viewer, Location eye, MenuScene scene) {
        return view.prepare(viewer, eye, new Vector(0,0,1), scene, 1, 1, FloatingMenuFieldOfView.DEFAULT, UiPalette.DEFAULT);
    }

    private static void assertPerspective(FloatingMenuAnchorResolver.Anchor expected, FloatingMenuAnchorResolver.Anchor actual,
                                          Location eye, FloatingMenuAnchorResolver.SceneBounds bounds) {
        assertEquals(expected.spatialScale() / expected.distance(), actual.spatialScale() / actual.distance(), 1e-8);
        for (var point : bounds.probes()) {
            var first = projected(expected, eye, point).normalize();
            var next = projected(actual, eye, point).normalize();
            assertEquals(first.getX(), next.getX(), 1e-7);
            assertEquals(first.getY(), next.getY(), 1e-7);
            assertEquals(first.getZ(), next.getZ(), 1e-7);
        }
    }

    private static Vector projected(FloatingMenuAnchorResolver.Anchor anchor, Location eye, FloatingMenuPoint point) {
        return anchor.origin().toVector().subtract(eye.toVector())
                .add(anchor.right().multiply(point.right() * anchor.spatialScale()))
                .add(new Vector(0,point.up() * anchor.spatialScale(),0))
                .add(anchor.forward().multiply(-point.forward() * anchor.spatialScale()));
    }

    private static void assertWorldClear(FloatingMenuWorldSpace space, Location eye, FloatingMenuAnchorResolver.Anchor anchor,
                                        FloatingMenuAnchorResolver.SceneBounds bounds) {
        for (var surface : bounds.surfaces()) {
            var pose = surface.pose(); var rotation = pose.rotation();
            var r = rotation.apply(new UiVector3(1,0,0)); var u = rotation.apply(new UiVector3(0,1,0));
            var right = anchor.right().multiply(r.right()).add(new Vector(0,r.up(),0)).add(anchor.forward().multiply(-r.forward()));
            var up = anchor.right().multiply(u.right()).add(new Vector(0,u.up(),0)).add(anchor.forward().multiply(-u.forward()));
            double scale = pose.scale() * anchor.spatialScale();
            assertEquals(1, space.limitSurface(eye, eye.toVector().add(projected(anchor,eye,pose.point())), right, up,
                    surface.size().width() * scale / 2, surface.size().height() * scale / 2,
                    Math.max(.14,surface.depth() / 2) * scale), 1e-6);
        }
    }
}
