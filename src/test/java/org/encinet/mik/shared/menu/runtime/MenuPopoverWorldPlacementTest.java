package org.encinet.mik.shared.menu.runtime;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuPopoverWorldPlacementTest {
    @Test void nearbyWallsAndEntitiesContractTheCompanionWithoutChangingPerspectiveOrDisablingItsHitSurface() {
        for (boolean entity : new boolean[]{false,true}) for (double gap : new double[]{.05,.2,.8}) {
            var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), .5, 64.5, .5);
            var placed = new MenuSurfaceAttachment.Placement(new UiVector3(.5,64.5,3.3), UiRotation.degrees(180,0,0));
            double wall = eye.getZ() + gap;
            var entities = entity ? List.of(MenuOcclusionGeometry.box(new BoundingBox(-4,62,wall,4,68,wall+1),eye.toVector()))
                    : List.<MenuOcclusionGeometry.Box>of();
            if (!entity) {
                int z = (int) Math.floor(wall);
                for (int x = -4; x <= 4; x++) for (int y = 62; y <= 67; y++)
                    fixture.block(x,y,z,new BoundingBox(0,0,wall-z,1,1,wall-z+1));
            }
            var space = new FloatingMenuWorldSpace(eye, entities);
            double factor = MenuPopoverWorldPlacement.project(eye,placed,panel(.9,.6,.04),1).fit(space);
            assertTrue(factor > 0 && factor < 1);
            var safe = contract(eye,placed,factor);
            double distance = eye.toVector().distance(MenuSurfaceAttachment.vector(safe.center()));
            assertTrue(distance < 1.5);
            assertEquals(.9 / 2.8, .9 * factor / distance, 1e-8);
            assertTrue(clear(space,eye,safe,.9*factor,.6*factor,.02*factor));
            var surface = new MenuSurfaceAttachment.Surface(safe.center(),safe.rotation(),new FloatingMenuSize(.9,.6),factor);
            assertEquals(distance,surface.intersect(eye,0).orElseThrow().distance(),1e-8);
            var proxy = MenuInteractionProxy.project(MenuSurfaceAttachment.point(eye.toVector()),safe.center(),
                    new FloatingMenuSize(.9*factor,.6*factor));
            assertEquals(.9*factor,proxy.width(),1e-8);
        }
    }

    @Test void aSafeParentCannotLeaveItsCompanionInsideTerrain() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var placed = new MenuSurfaceAttachment.Placement(new UiVector3(-.53, 63.62, 3.88), UiRotation.degrees(180, 0, 0));
        fixture.block(-1, 63, 3, new BoundingBox(.43, .58, .84, .51, .66, .92));
        var space = new FloatingMenuWorldSpace(eye, List.of());
        assertEquals(1, space.limitSurface(eye, new Vector(0, 64, 2), new Vector(1, 0, 0), new Vector(0, 1, 0), .1, .1, .04), 1e-7);
        assertFalse(clear(space, eye, placed, .9, .6, .04));
        var envelope = MenuPopoverWorldPlacement.project(eye, placed, panel(.9, .6, .04), 1);
        double factor = envelope.fit(space);
        assertTrue(factor > 0 && factor < 1);
        var safe = contract(eye, placed, factor);
        assertTrue(clear(space, eye, safe, .9 * factor, .6 * factor, .04 * factor));
        assertEquals(.9 / eye.toVector().distance(MenuSurfaceAttachment.vector(placed.center())),
                .9 * factor / eye.toVector().distance(MenuSurfaceAttachment.vector(safe.center())), 1e-9);
    }

    @Test void floorAndNearbyEntitiesConstrainTheFullCompanionWithoutChangingItsReadingAngle() {
        for (boolean entity : new boolean[]{false, true}) {
            var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64.5, 0);
            var placed = new MenuSurfaceAttachment.Placement(new UiVector3(-.53, 64.12, 3.88), UiRotation.degrees(180, 0, 0));
            var box = new BoundingBox(-1, 63, 1, 0, 64, 4.5);
            if (!entity) fixture.block(-1, 63, 1, new BoundingBox(0, 0, 0, 1, 1, 3.5));
            var space = new FloatingMenuWorldSpace(eye, entity ? List.of(MenuOcclusionGeometry.box(box, eye.toVector())) : List.of());
            double factor = MenuPopoverWorldPlacement.project(eye, placed, panel(.9, .6, .04), 1).fit(space);
            assertTrue(factor > 0 && factor < 1, "entity=" + entity);
            var safe = contract(eye, placed, factor);
            var right = safe.rotation().apply(new UiVector3(1, 0, 0)); var up = safe.rotation().apply(new UiVector3(0, 1, 0));
            assertEquals(1, space.limitSurface(eye, MenuSurfaceAttachment.vector(safe.center()), MenuSurfaceAttachment.vector(right),
                    MenuSurfaceAttachment.vector(up), .45 * factor, .3 * factor, .02 * factor), 1e-7);
        }
    }

    @Test void modelDepthIsCheckedEvenWhenTheFlatReadingPlaneIsClear() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        fixture.block(0, 64, 4, new BoundingBox(.01, .01, .30, .05, .05, .38));
        var space = new FloatingMenuWorldSpace(eye, List.of());
        var placed = new MenuSurfaceAttachment.Placement(new UiVector3(0, 64, 4), UiRotation.degrees(180, 0, 0));
        assertTrue(clear(space, eye, placed, .2, .2, .02));
        double factor = MenuPopoverWorldPlacement.project(eye, placed, panel(.2, .2, 1.2), 1).fit(space);
        assertTrue(factor > 0 && factor < 1);
        assertTrue(clear(space, eye, contract(eye, placed, factor), .2 * factor, .2 * factor, .6 * factor));
    }

    @Test void geometryBehindTheEyeAndUnboundedTraceRangesFailBeforeWorldSampling() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var space = new FloatingMenuWorldSpace(eye, List.of());
        var backwards = new MenuSurfaceAttachment.Placement(new UiVector3(0, 64, -2), UiRotation.degrees(180, 0, 0));
        var far = new MenuSurfaceAttachment.Placement(new UiVector3(0, 64, 128), UiRotation.degrees(180, 0, 0));
        assertEquals(0, MenuPopoverWorldPlacement.project(eye, backwards, panel(1, 1, .1), 1).fit(space));
        assertEquals(0, MenuPopoverWorldPlacement.project(eye, far, panel(1, 1, .1), 1).fit(space));
        assertEquals(0, fixture.rayReads()); assertEquals(0, fixture.blockReads());
    }

    @Test void oversizedCompanionVolumesCannotExceedThePlacementSamplingBudget() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), 0, 64, 0);
        var placed = new MenuSurfaceAttachment.Placement(new UiVector3(0, 64, 30), UiRotation.degrees(180, 0, 0));
        double factor = MenuPopoverWorldPlacement.project(eye, placed, panel(45, 45, .1), 1)
                .fit(new FloatingMenuWorldSpace(eye, List.of()));
        assertEquals(0, factor); assertTrue(fixture.blockReads() <= 32768);
    }

    @Test void companionsReuseTheParentsBlockSamplesAndNeverReadUnloadedChunks() {
        var fixture = new FloatingMenuTestWorld(); var eye = new Location(fixture.world(), .5, 64, .5);
        var parent = new FloatingMenuWorldSpace(eye, List.of()); var center = new Vector(.5, 64, 2.5);
        parent.limitSurface(eye, center, new Vector(1, 0, 0), new Vector(0, 1, 0), .3, .3, .14);
        int reads = fixture.blockReads();
        parent.forCompanion(eye, List.of()).limitSurface(eye, center, new Vector(1, 0, 0), new Vector(0, 1, 0), .3, .3, .14);
        assertEquals(reads, fixture.blockReads());
        fixture.loaded((x, z) -> x == 0 && z == 0);
        var edge = new Location(fixture.world(), 15, 64, 1);
        var placed = new MenuSurfaceAttachment.Placement(new UiVector3(16.3, 64, 3), UiRotation.degrees(180, 0, 0));
        double factor = MenuPopoverWorldPlacement.project(edge, placed, panel(.9, .6, .04), 1)
                .fit(new FloatingMenuWorldSpace(edge, List.of()));
        assertTrue(factor > 0 && factor < 1, "Keep the companion in loaded space by contracting its whole viewing frame");
        assertTrue(clear(new FloatingMenuWorldSpace(edge, List.of()), edge, contract(edge, placed, factor),
                .9 * factor, .6 * factor, .04 * factor));
    }

    private static FloatingMenuAnchorResolver.SceneBounds panel(double width, double height, double depth) {
        var pose = FloatingMenuPose.ORIGIN;
        var surface = new FloatingMenuAnchorResolver.Surface(pose, new FloatingMenuSize(width, height), depth);
        var points = new java.util.ArrayList<FloatingMenuPoint>();
        for (double x : new double[]{-width / 2, width / 2}) for (double y : new double[]{-height / 2, height / 2})
            for (double z : new double[]{-depth / 2, depth / 2}) points.add(new FloatingMenuPoint(x, y, z));
        var bounds = FloatingMenuAnchorResolver.SceneBounds.around(points);
        return new FloatingMenuAnchorResolver.SceneBounds(bounds.probes(), bounds.framing(), List.of(surface));
    }

    private static MenuSurfaceAttachment.Placement contract(Location eye, MenuSurfaceAttachment.Placement source, double factor) {
        var origin = MenuSurfaceAttachment.point(eye.toVector());
        return new MenuSurfaceAttachment.Placement(origin.add(source.center().subtract(origin).multiply(factor)), source.rotation());
    }

    private static boolean clear(FloatingMenuWorldSpace space, Location eye, MenuSurfaceAttachment.Placement placed, double width, double height, double halfDepth) {
        return space.limitSurface(eye, MenuSurfaceAttachment.vector(placed.center()), MenuSurfaceAttachment.vector(placed.rotation().apply(new UiVector3(1, 0, 0))),
                MenuSurfaceAttachment.vector(placed.rotation().apply(new UiVector3(0, 1, 0))), width / 2, height / 2, halfDepth) >= 1 - 1e-7;
    }
}
