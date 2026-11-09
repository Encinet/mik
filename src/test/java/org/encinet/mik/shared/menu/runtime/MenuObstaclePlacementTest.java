package org.encinet.mik.shared.menu.runtime;

import org.bukkit.Location;
import org.bukkit.entity.Cushion;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.FloatingMenuPoint;
import org.encinet.mik.shared.menu.FloatingMenuPose;
import org.encinet.mik.shared.menu.FloatingMenuSize;
import org.encinet.mik.shared.menu.runtime.space.MenuOcclusionGeometry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MenuObstaclePlacementTest {
    private static final Vector FORWARD = new Vector(0, 0, 1);
    private static final Vector RIGHT = new Vector(1, 0, 0);
    private static final Vector UP = new Vector(0, 1, 0);

    @Test void anOffAxisModelCanCrossTheEyePlaneWithoutContainingTheViewer() {
        var center = new Vector(3, 0, .1);
        assertEquals(1, MenuOcclusionGeometry.limit(center, RIGHT, UP, .5, .5, 1, List.of()));
        var blocker = MenuOcclusionGeometry.box(new BoundingBox(1, -.3, -.2, 1.2, .3, .2), new Vector());
        double factor = MenuOcclusionGeometry.limit(center, RIGHT, UP, .5, .5, 1, List.of(blocker));
        assertTrue(factor > 0 && factor < .5);
        assertEquals(1, MenuOcclusionGeometry.limit(center.clone().multiply(factor), RIGHT, UP,
                .5 * factor, .5 * factor, factor, List.of(blocker)), 1e-6);
    }

    @Test void aThinBlockBetweenAllPointProbesStillLimitsTheWholeReadingSurface() {
        var fixture = new FloatingMenuTestWorld();
        fixture.block(0, 64, 1, new BoundingBox(.22, .41, .4, .28, .49, .48));
        var eye = new Location(fixture.world(), 0, 64, 0);
        var pose = FloatingMenuPose.at(FloatingMenuPoint.ORIGIN);
        var bounds = bounds(pose, 2, 2);
        var anchor = anchor(eye, 0, 0, 2);
        var fitted = FloatingMenuAnchorResolver.fit(eye, anchor, bounds);
        assertTrue(fitted.distance() < 1.4, "A clear center ray must not hide an obstruction between probes");
        assertClear(fixture, eye, fitted, pose, 2, 2);
    }

    @Test void aMenuCannotEscapeAboveALowCeilingEvenWhenItsFinalSurfaceIsOutsideTheBlock() {
        var fixture = new FloatingMenuTestWorld();
        for (int z = 0; z <= 2; z++) fixture.block(0, 65, z, new BoundingBox(0, 0, 0, 1, .3, 1));
        var eye = new Location(fixture.world(), .5, 64.5, 0);
        var pose = FloatingMenuPose.at(FloatingMenuPoint.ORIGIN);
        var fitted = FloatingMenuAnchorResolver.fit(eye, anchor(eye, 0, 2, 3), bounds(pose, 1, 1));
        assertTrue(fitted.origin().getY() < 65, "The menu must remain on the viewer's side of the ceiling");
        assertClear(fixture, eye, fitted, pose, 1, 1);
    }

    @Test void floorAndWallAreAppliedTogetherAndTheFittedSurfaceRemainsAValidFixedPoint() {
        var fixture = new FloatingMenuTestWorld();
        for (int x = -2; x <= 2; x++) for (int z = 0; z <= 3; z++) {
            fixture.block(x, 63, z, new BoundingBox(0, 0, 0, 1, 1, 1));
            if (z == 2) fixture.block(x, 64, z, new BoundingBox(0, 0, 0, 1, 1, 1));
        }
        var eye = new Location(fixture.world(), .5, 65.1, 0);
        var pose = FloatingMenuPose.at(FloatingMenuPoint.ORIGIN);
        var fitted = FloatingMenuAnchorResolver.fit(eye, anchor(eye, 0, -.9, 3), bounds(pose, 2, 2));
        assertTrue(fitted.origin().getY() - fitted.spatialScale() > 64);
        assertTrue(fitted.distance() < 2);
        assertClear(fixture, eye, fitted, pose, 2, 2);
    }

    @Test void rolledAndScaledComponentsUseTheirActualEnvelopeForBothKindsOfObstacle() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        var pose = new FloatingMenuPose(FloatingMenuPoint.ORIGIN, 0, 0, 90, 3);
        var blocker = MenuOcclusionGeometry.box(new BoundingBox(.6, 63.9, 1, .7, 64.1, 1.1), eye.toVector());
        var fitted = FloatingMenuAnchorResolver.fit(eye, anchor(eye, 0, 0, 2), bounds(pose, .2, 1),
                new FloatingMenuWorldSpace(eye, List.of(blocker)));
        assertTrue(fitted.distance() < 1, "The rolled long edge reaches the off-center entity");
        assertEquals(2, FloatingMenuAnchorResolver.fit(eye, anchor(eye, 0, 0, 2),
                bounds(FloatingMenuPose.at(FloatingMenuPoint.ORIGIN), .2, 1),
                new FloatingMenuWorldSpace(eye, List.of(blocker))).distance());
    }

    @Test void beingInsideAnOpaqueObstacleDoesNotPublishADegenerateMenuAsSuccess() {
        var fixture = new FloatingMenuTestWorld();
        fixture.block(0, 64, 0, new BoundingBox(0, 0, 0, 1, 1, 1));
        var eye = new Location(fixture.world(), .5, 64.5, .5);
        assertThrows(MenuSpaceUnavailableException.class, () -> FloatingMenuAnchorResolver.fit(eye,
                anchor(eye, 0, 0, 2), bounds(FloatingMenuPose.at(FloatingMenuPoint.ORIGIN), 1, 1)));
    }

    @Test void nearbyVisibleBodiesAreCapturedAndHiddenOrInvisibleBodiesAreExcluded() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        LivingEntity visible = body(new BoundingBox(-.2, 63, 1, .2, 65, 1.4), false);
        LivingEntity invisible = body(new BoundingBox(-.2, 63, .2, .2, 65, .4), true);
        LivingEntity hidden = body(new BoundingBox(-.2, 63, .1, .2, 65, .2), false);
        fixture.entity(visible); fixture.entity(invisible); fixture.entity(hidden);
        Player viewer = viewer(hidden);
        fixture.entity(viewer);
        var snapshot = MenuEntityObstacles.capture(viewer, eye, 5);
        assertEquals(1, snapshot.size());
        assertEquals(1, MenuOcclusionGeometry.firstHit(new Vector(), FORWARD, 5, snapshot), 1e-8);
    }

    @Test void vanillaCushionsConstrainTheWholeReadingSurfaceAndRecoverWhenTheyLeave() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        var cushionBounds = new BoundingBox(.22, 63.9, 1, .97, 64.1, 1.75);
        fixture.entity(cushion(cushionBounds, true));
        Player viewer = viewer(null);
        var snapshot = MenuEntityObstacles.capture(viewer, eye, 5);
        var space = new FloatingMenuWorldSpace(eye, snapshot);
        var reference = anchor(eye, 0, 0, 2);
        var bounds = bounds(FloatingMenuPose.at(FloatingMenuPoint.ORIGIN), 2, 2);
        var fitted = FloatingMenuAnchorResolver.fit(eye, reference, bounds, space);

        assertTrue(fitted.distance() > 0 && fitted.distance() < 1,
                "A cushion between the center and corner rays must still constrain the reading surface");
        assertEquals(Double.POSITIVE_INFINITY, space.firstHit(eye, FORWARD, 2));
        assertEquals(1, space.limitSurface(eye, fitted.origin().toVector(), RIGHT, UP,
                fitted.spatialScale(), fitted.spatialScale(), .22 * fitted.spatialScale()), 1e-6);

        cushionBounds.shift(0, 0, 10);
        var cleared = new FloatingMenuWorldSpace(eye, MenuEntityObstacles.capture(viewer, eye, 5));
        assertEquals(reference.distance(), FloatingMenuAnchorResolver.fit(eye, reference, bounds, cleared).distance());
    }

    @Test void hiddenAndInvalidCushionsDoNotConstrainTheReadingSurface() {
        var fixture = new FloatingMenuTestWorld();
        var eye = new Location(fixture.world(), 0, 64, 0);
        Cushion hidden = cushion(new BoundingBox(-.2, 63.9, .1, .2, 64.1, .3), true);
        fixture.entity(hidden);
        fixture.entity(cushion(new BoundingBox(-.2, 63.9, .4, .2, 64.1, .6), false));
        fixture.entity(cushion(new BoundingBox(-.2, 63.9, 1, .2, 64.1, 1.2), true));
        var snapshot = MenuEntityObstacles.capture(viewer(hidden), eye, 5);

        assertEquals(1, snapshot.size());
        assertEquals(1, new FloatingMenuWorldSpace(eye, snapshot).firstHit(eye, FORWARD, 5), 1e-8);
    }

    private static Cushion cushion(BoundingBox bounds, boolean valid) {
        return (Cushion) Proxy.newProxyInstance(Cushion.class.getClassLoader(), new Class<?>[]{Cushion.class},
                (_, method, _) -> switch (method.getName()) {
                    case "getBoundingBox" -> bounds.clone();
                    case "isValid" -> valid;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Player viewer(Entity hidden) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (_, method, args) -> switch (method.getName()) {
                    case "canSee" -> args[0] != hidden;
                    case "getBoundingBox" -> new BoundingBox(-.3, 63, -.3, .3, 65, .3);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static LivingEntity body(BoundingBox bounds, boolean invisible) {
        return (LivingEntity) Proxy.newProxyInstance(LivingEntity.class.getClassLoader(), new Class<?>[]{LivingEntity.class},
                (_, method, _) -> switch (method.getName()) {
                    case "getBoundingBox" -> bounds.clone();
                    case "isValid" -> true;
                    case "isInvisible" -> invisible;
                    case "getEquipment" -> null;
                    case "getUniqueId" -> UUID.nameUUIDFromBytes(bounds.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static FloatingMenuAnchorResolver.SceneBounds bounds(FloatingMenuPose pose, double width, double height) {
        return new FloatingMenuAnchorResolver.SceneBounds(List.of(FloatingMenuPoint.ORIGIN),
                org.encinet.mik.shared.menu.FloatingMenuFraming.COMFORTABLE,
                List.of(new FloatingMenuAnchorResolver.Surface(pose, new FloatingMenuSize(width, height))));
    }

    private static FloatingMenuAnchorResolver.Anchor anchor(Location eye, double x, double y, double z) {
        return new FloatingMenuAnchorResolver.Anchor(eye.clone().add(x, y, z), FORWARD, RIGHT, 1, z, 0);
    }

    private static void assertClear(FloatingMenuTestWorld fixture, Location eye,
                                    FloatingMenuAnchorResolver.Anchor anchor, FloatingMenuPose pose, double width, double height) {
        assertEquals(1, new FloatingMenuWorldSpace(fixture.world()).limitSurface(eye, anchor.origin().toVector(), RIGHT, UP,
                width * pose.scale() * anchor.spatialScale() / 2, height * pose.scale() * anchor.spatialScale() / 2,
                .22 * anchor.spatialScale()), 1e-6);
    }
}
