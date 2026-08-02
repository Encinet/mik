package org.encinet.mik.module.space;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceNetworkTest {

    private static final SpaceAperture DOOR = new SpaceAperture(4.0, 4.0);
    private static final SpaceSurface A = surface(
            "a", new SpaceVector(0.0, 64.0, 0.0), 0.0, DOOR);
    private static final SpaceSurface B = surface(
            "b", new SpaceVector(100.0, 70.0, 20.0), 90.0, DOOR);

    @Test
    void crossingPreservesLocalPositionAndRotatesLookAndVelocity() {
        SpaceNetwork network = SpaceNetwork.of(List.of(new SpaceLink(
                "corner", A, B, SpaceLinkEntrances.FIRST)));

        SpaceTransition transition = trace(network,
                A.frame().fromLocalPoint(new SpaceVector(1.0, 0.5, -0.2)),
                A.frame().fromLocalPoint(new SpaceVector(1.0, 0.5, 0.2)),
                A.frame().forward(), A.frame().forward().multiply(0.4)).orElseThrow();

        assertEquals("corner", transition.linkId());
        assertEquals("corner:first-forward-to-second-forward", transition.routeId());
        assertEquals("a", transition.sourceSurfaceId());
        assertEquals("b", transition.destinationSurfaceId());
        assertVector(B.frame().fromLocalPoint(new SpaceVector(1.0, 0.5, 0.0)),
                transition.entryPosition());
        assertVector(B.frame().fromLocalPoint(new SpaceVector(1.0, 0.5, 0.2)),
                transition.position());
        assertVector(B.frame().forward(), transition.lookDirection());
        assertVector(B.frame().forward().multiply(0.4), transition.velocity());
    }

    @Test
    void crossingRequiresForwardPlaneIntersectionInsideTheAperture() {
        SpaceNetwork network = SpaceNetwork.of(List.of(new SpaceLink(
                "bounded", A, B, SpaceLinkEntrances.FIRST)));

        assertTrue(trace(network,
                A.frame().fromLocalPoint(new SpaceVector(0.0, 0.0, 0.2)),
                A.frame().fromLocalPoint(new SpaceVector(0.0, 0.0, -0.2)),
                A.frame().forward(), SpaceVector.ZERO).isEmpty());
        assertTrue(trace(network,
                A.frame().fromLocalPoint(new SpaceVector(2.1, 0.0, -0.2)),
                A.frame().fromLocalPoint(new SpaceVector(2.1, 0.0, 0.2)),
                A.frame().forward(), SpaceVector.ZERO).isEmpty());
        assertTrue(trace(network,
                A.frame().fromLocalPoint(new SpaceVector(0.0, 2.1, -0.2)),
                A.frame().fromLocalPoint(new SpaceVector(0.0, 2.1, 0.2)),
                A.frame().forward(), SpaceVector.ZERO).isEmpty());
        assertTrue(trace(network,
                B.frame().fromLocalPoint(new SpaceVector(0.0, 0.0, -0.2)),
                B.frame().fromLocalPoint(new SpaceVector(0.0, 0.0, 0.2)),
                B.frame().forward(), SpaceVector.ZERO).isEmpty());
    }

    @Test
    void reverseRouteIsTheExactGeometricInverse() {
        SpaceNetwork network = SpaceNetwork.of(List.of(new SpaceLink(
                "two-way", A, B, SpaceLinkEntrances.FIRST)));
        SpaceVector offset = new SpaceVector(0.75, -0.4, 0.2);

        SpaceTransition forward = trace(network,
                A.frame().fromLocalPoint(new SpaceVector(offset.x(), offset.y(), -0.2)),
                A.frame().fromLocalPoint(offset), A.frame().forward(),
                A.frame().forward().multiply(0.6)).orElseThrow();
        SpaceTransition reverse = trace(network,
                B.frame().fromLocalPoint(offset),
                B.frame().fromLocalPoint(new SpaceVector(offset.x(), offset.y(), -0.2)),
                B.frame().forward().multiply(-1.0),
                B.frame().forward().multiply(-0.6)).orElseThrow();

        assertEquals("two-way:first-forward-to-second-forward", forward.routeId());
        assertEquals("two-way:second-reverse-to-first-reverse", reverse.routeId());
        assertVector(A.frame().fromLocalPoint(
                new SpaceVector(offset.x(), offset.y(), -0.2)), reverse.position());
        assertVector(A.frame().forward().multiply(-1.0), reverse.lookDirection());
        assertVector(A.frame().forward().multiply(-0.6), reverse.velocity());
    }

    @Test
    void bothEntrancesAddASecondRoutePairWithItsExactInverse() {
        SpaceNetwork network = SpaceNetwork.of(List.of(new SpaceLink(
                "all-faces", A, B, SpaceLinkEntrances.BOTH)));
        SpaceVector offset = new SpaceVector(0.75, -0.4, 0.2);

        SpaceTransition secondForward = trace(network,
                B.frame().fromLocalPoint(new SpaceVector(offset.x(), offset.y(), -0.2)),
                B.frame().fromLocalPoint(offset), B.frame().forward(),
                B.frame().forward().multiply(0.6)).orElseThrow();
        SpaceTransition firstReverse = trace(network,
                A.frame().fromLocalPoint(offset),
                A.frame().fromLocalPoint(new SpaceVector(offset.x(), offset.y(), -0.2)),
                A.frame().forward().multiply(-1.0),
                A.frame().forward().multiply(-0.6)).orElseThrow();

        assertEquals("all-faces:second-forward-to-first-forward",
                secondForward.routeId());
        assertVector(A.frame().fromLocalPoint(offset), secondForward.position());
        assertVector(A.frame().forward(), secondForward.lookDirection());
        assertVector(A.frame().forward().multiply(0.6), secondForward.velocity());

        assertEquals("all-faces:first-reverse-to-second-reverse",
                firstReverse.routeId());
        assertVector(B.frame().fromLocalPoint(
                new SpaceVector(offset.x(), offset.y(), -0.2)), firstReverse.position());
        assertVector(B.frame().forward().multiply(-1.0), firstReverse.lookDirection());
        assertVector(B.frame().forward().multiply(-0.6), firstReverse.velocity());
    }

    @Test
    void verticalLinkKeepsFallingDirectionAndSpeed() {
        SpaceFrame downwardBottom = SpaceFrame.fromAxes(
                new SpaceVector(0.0, 40.0, 0.0),
                new SpaceVector(0.0, -1.0, 0.0),
                new SpaceVector(0.0, 0.0, -1.0));
        SpaceFrame downwardTop = SpaceFrame.fromAxes(
                new SpaceVector(0.0, 72.0, 0.0),
                new SpaceVector(0.0, -1.0, 0.0),
                new SpaceVector(0.0, 0.0, -1.0));
        SpaceNetwork network = SpaceNetwork.of(List.of(new SpaceLink("fall",
                new SpaceSurface("bottom", "world", downwardBottom, DOOR),
                new SpaceSurface("top", "world", downwardTop, DOOR),
                SpaceLinkEntrances.FIRST)));

        SpaceTransition transition = trace(network,
                new SpaceVector(0.5, 40.2, -0.25),
                new SpaceVector(0.5, 39.7, -0.25),
                new SpaceVector(0.0, -1.0, 0.0),
                new SpaceVector(0.0, -1.4, 0.0)).orElseThrow();

        assertVector(new SpaceVector(0.5, 71.7, -0.25), transition.position());
        assertVector(new SpaceVector(0.0, -1.0, 0.0), transition.lookDirection());
        assertVector(new SpaceVector(0.0, -1.4, 0.0), transition.velocity());
    }

    @Test
    void reverseVerticalRouteKeepsUpwardDirectionAndSpeed() {
        SpaceFrame downwardBottom = SpaceFrame.fromAxes(
                new SpaceVector(0.0, 40.0, 0.0),
                new SpaceVector(0.0, -1.0, 0.0),
                new SpaceVector(0.0, 0.0, -1.0));
        SpaceFrame downwardTop = SpaceFrame.fromAxes(
                new SpaceVector(0.0, 72.0, 0.0),
                new SpaceVector(0.0, -1.0, 0.0),
                new SpaceVector(0.0, 0.0, -1.0));
        SpaceNetwork network = SpaceNetwork.of(List.of(new SpaceLink("fall",
                new SpaceSurface("bottom", "world", downwardBottom, DOOR),
                new SpaceSurface("top", "world", downwardTop, DOOR),
                SpaceLinkEntrances.FIRST)));

        SpaceTransition transition = trace(network,
                new SpaceVector(0.5, 71.8, -0.25),
                new SpaceVector(0.5, 72.3, -0.25),
                new SpaceVector(0.0, 1.0, 0.0),
                new SpaceVector(0.0, 1.2, 0.0)).orElseThrow();

        assertEquals("fall:second-reverse-to-first-reverse", transition.routeId());
        assertVector(new SpaceVector(0.5, 40.3, -0.25), transition.position());
        assertVector(new SpaceVector(0.0, 1.0, 0.0), transition.lookDirection());
        assertVector(new SpaceVector(0.0, 1.2, 0.0), transition.velocity());
    }

    @Test
    void wallToFloorTurnsHorizontalPlayerMomentumIntoDownwardMomentum() {
        SpaceFrame wall = SpaceFrame.fromAxes(
                new SpaceVector(0.0, 64.0, 0.0),
                new SpaceVector(0.0, 0.0, 1.0),
                new SpaceVector(0.0, 1.0, 0.0));
        SpaceFrame floor = SpaceFrame.fromAxes(
                new SpaceVector(30.0, 40.0, 10.0),
                new SpaceVector(0.0, -1.0, 0.0),
                new SpaceVector(0.0, 0.0, -1.0));
        SpaceNetwork network = SpaceNetwork.of(List.of(new SpaceLink("wall-floor",
                new SpaceSurface("wall", "world", wall, DOOR),
                new SpaceSurface("floor", "world", floor, DOOR),
                SpaceLinkEntrances.FIRST)));
        SpaceVector velocity = wall.forward().multiply(1.25)
                .add(wall.up().multiply(0.30))
                .add(wall.right().multiply(0.20));

        SpaceTransition transition = trace(network,
                wall.fromLocalPoint(new SpaceVector(0.5, -0.25, -0.2)),
                wall.fromLocalPoint(new SpaceVector(0.5, -0.25, 0.2)),
                wall.forward(), velocity).orElseThrow();

        SpaceVector expected = floor.forward().multiply(1.25)
                .add(floor.up().multiply(0.30))
                .add(floor.right().multiply(0.20));
        assertVector(expected, transition.velocity());
        assertEquals(velocity.length(), transition.velocity().length(), 1.0E-9);
        assertEquals(-1.25, transition.velocity().y(), 1.0E-9);
    }

    @Test
    void floorToWallTurnsFallingPlayerMomentumIntoHorizontalMomentum() {
        SpaceFrame floor = SpaceFrame.fromAxes(
                new SpaceVector(0.0, 50.0, 0.0),
                new SpaceVector(0.0, -1.0, 0.0),
                new SpaceVector(0.0, 0.0, -1.0));
        SpaceFrame wall = SpaceFrame.fromAxes(
                new SpaceVector(20.0, 65.0, 20.0),
                new SpaceVector(1.0, 0.0, 0.0),
                new SpaceVector(0.0, 1.0, 0.0));
        SpaceNetwork network = SpaceNetwork.of(List.of(new SpaceLink("floor-wall",
                new SpaceSurface("floor", "world", floor, DOOR),
                new SpaceSurface("wall", "world", wall, DOOR),
                SpaceLinkEntrances.FIRST)));
        SpaceVector fallingVelocity = new SpaceVector(0.0, -1.6, 0.0);

        SpaceTransition transition = trace(network,
                floor.fromLocalPoint(new SpaceVector(0.0, 0.0, -0.2)),
                floor.fromLocalPoint(new SpaceVector(0.0, 0.0, 0.2)),
                floor.forward(), fallingVelocity).orElseThrow();

        assertVector(new SpaceVector(1.6, 0.0, 0.0), transition.velocity());
        assertEquals(fallingVelocity.length(), transition.velocity().length(), 1.0E-9);
    }

    @Test
    void networkRejectsAnAmbiguousSurfaceOwner() {
        SpaceSurface C = surface(
                "c", new SpaceVector(20.0, 64.0, 0.0), 0.0, DOOR);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SpaceNetwork.of(List.of(
                        new SpaceLink("first", A, B, SpaceLinkEntrances.FIRST),
                        new SpaceLink("second", A, C, SpaceLinkEntrances.FIRST))));

        assertTrue(error.getMessage().contains("already connected"));
    }

    private Optional<SpaceTransition> trace(
            SpaceNetwork network,
            SpaceVector from,
            SpaceVector to,
            SpaceVector look,
            SpaceVector velocity
    ) {
        return network.trace("world", from, to, look, velocity);
    }

    private static SpaceSurface surface(
            String id,
            SpaceVector origin,
            double yaw,
            SpaceAperture aperture
    ) {
        return new SpaceSurface(
                id, "world", SpaceFrame.oriented(origin, yaw, 0.0, 0.0), aperture);
    }

    private void assertVector(SpaceVector expected, SpaceVector actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9);
        assertEquals(expected.y(), actual.y(), 1.0E-9);
        assertEquals(expected.z(), actual.z(), 1.0E-9);
    }
}
