package org.encinet.mik.module.vehicle;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorGeometryTest {
    @Test void snappingUsesLocalAxesAndRejectsInvalidGridSizes() {
        var position = new VehicleVector(0.23, -0.38, 1.01);
        assertEquals(new VehicleVector(0.25, -0.5, 1), VehicleEditorGeometry.snap(position, 0.25));
        assertEquals(new VehicleVector(0, 0, 1), VehicleEditorGeometry.snap(position, 1));
        for (double step : new double[]{0, -0.1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> VehicleEditorGeometry.snap(position, step));
        var origin = new Location(world(), 20000000.125, 64, -20000000.375, 37, 0);
        var snapped = VehicleEditorGeometry.snap(VehicleEditorGeometry.local(origin, VehicleEditorGeometry.world(origin, position)), 0.25);
        assertEquals(new VehicleVector(0.25, -0.5, 1), snapped);
    }

    @Test void coordinateDisplayIsCompactWhileCommandSerializationRemainsLossless() {
        var position = new VehicleVector(0.1 + 0.2, -1.23456789, 2);
        assertEquals("0.300 -1.235 2.000", VehicleEditorGeometry.display(position));
        assertEquals(position, VehicleModelDraft.vector(VehicleEditorGeometry.coordinates(position)));
    }

    @Test void currentPositionUsesTheFixedOriginAndItsHeadingNotThePlayersHeading() {
        World world = world();
        Location origin = new Location(world, 100, 64, 200, 90, 35);
        Location feet = new Location(world, 98, 65.25, 203, -45, -80);
        VehicleVector local = VehicleEditorGeometry.local(origin, feet);
        assertEquals(3, local.coordinateX(), 1.0E-9);
        assertEquals(1.25, local.coordinateY(), 1.0E-9);
        assertEquals(2, local.coordinateZ(), 1.0E-9);
        Location restored = VehicleEditorGeometry.world(origin, local);
        assertEquals(0, restored.distance(feet), 1.0E-9);
        assertEquals(100, origin.getX());
        assertEquals(90, origin.getYaw());
    }

    @Test void worldAndLocalCoordinatesRoundTripAtLargeCoordinatesAndArbitraryHeadings() {
        World world = world();
        VehicleVector expected = new VehicleVector(1.125, 0.375, -2.75);
        for (float yaw : new float[]{0, 90, -90, 180, 37, 270}) {
            Location origin = new Location(world, 20000000.125, 80.5, -20000000.375, yaw, 0);
            Location position = VehicleEditorGeometry.world(origin, expected);
            VehicleVector actual = VehicleEditorGeometry.local(origin, position);
            assertEquals(expected.coordinateX(), actual.coordinateX(), 1.0E-8);
            assertEquals(expected.coordinateY(), actual.coordinateY(), 1.0E-8);
            assertEquals(expected.coordinateZ(), actual.coordinateZ(), 1.0E-8);
        }
    }

    @Test void changingWorldOrLeavingTheOriginRangeCannotSilentlyReanchorTheEditor() {
        World world = world();
        Location origin = new Location(world, 0, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorGeometry.local(origin, new Location(world(), 0, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorGeometry.local(origin, new Location(world, 32.01, 64, 0)));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorGeometry.local(new Location(null, 0, 64, 0), origin));
        assertEquals(32, VehicleEditorGeometry.local(origin, new Location(world, 32, 64, 0)).coordinateX());
    }

    @Test void oppositeCornersProduceAnOrderIndependentLocalBoxAndRejectFlatBoxes() {
        VehicleVector first = new VehicleVector(-2, 0, -3);
        VehicleVector second = new VehicleVector(2, 2, 3);
        var box = VehicleEditorGeometry.box(first, second);
        assertEquals(new VehicleVector(0, 1, 0), box.center());
        assertEquals(new VehicleVector(2, 1, 3), box.halfSize());
        assertEquals(box, VehicleEditorGeometry.box(second, first));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorGeometry.box(first, new VehicleVector(2, 0, 3)));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorGeometry.box(first, first));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorGeometry.box(VehicleVector.ZERO, new VehicleVector(40, 2, 2)));
    }

    @Test void wireframeSamplingIsBoundedAndOnlyDrawsEdges() {
        var box = new VehicleDefinition.Collider(new VehicleVector(0, 1, 0), new VehicleVector(2, 1, 3));
        var points = VehicleEditorGeometry.outline(box);
        assertEquals(60, points.size());
        assertEquals(44, points.stream().distinct().count());
        for (VehicleVector point : points) {
            assertTrue(Math.abs(point.coordinateX()) <= 2);
            assertTrue(Math.abs(point.coordinateY() - 1) <= 1);
            assertTrue(Math.abs(point.coordinateZ()) <= 3);
            int boundaries = (Math.abs(point.coordinateX()) == 2 ? 1 : 0)
                    + (Math.abs(point.coordinateY() - 1) == 1 ? 1 : 0) + (Math.abs(point.coordinateZ()) == 3 ? 1 : 0);
            assertTrue(boundaries >= 2);
        }
    }

    private static World world() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, arguments) ->
                switch (method.getName()) {
                    case "equals" -> proxy == arguments[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "editor-world";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
