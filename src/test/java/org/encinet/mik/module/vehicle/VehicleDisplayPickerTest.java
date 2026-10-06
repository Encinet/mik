package org.encinet.mik.module.vehicle;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleDisplayPickerTest {
    @Test void surfaceHitsProvideActualWorldPointsAndNormalizedRayDistances() {
        var world = VehicleGroupFixtures.world();
        var eye = new Location(world, 20000000.125, 80.5, -20000000.375);
        var display = VehicleGroupFixtures.block(world);
        display.location = eye.clone().add(0, -0.5, 8);
        display.transformation = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(2, 1, 3), new Quaternionf());
        var hit = VehicleDisplayPicker.hit(eye, new Vector(0, 0, 10), List.of(display.entity), entity -> false, 32);
        assertNotNull(hit);
        assertSame(display.entity, hit.display());
        assertEquals(8, hit.distance(), 1.0E-6);
        assertEquals(eye.getX(), hit.position().coordinateX(), 1.0E-6);
        assertEquals(eye.getY(), hit.position().coordinateY(), 1.0E-6);
        assertEquals(eye.getZ() + 8, hit.position().coordinateZ(), 1.0E-6);
        assertNull(VehicleDisplayPicker.hit(eye, new Vector(0, 0, 1), List.of(display.entity), entity -> false, 7));
        assertNull(VehicleDisplayPicker.hit(eye, new Vector(), List.of(display.entity), entity -> false, 32));
        assertNull(VehicleDisplayPicker.hit(eye, new Vector(0, 0, 1), List.of(display.entity), entity -> false, Double.NaN));
    }

    @Test void translatedDisplaysArePickedByGeometryNotZeroEntityHitboxesAtFarCoordinates() {
        var world = VehicleGroupFixtures.world();
        var eye = new Location(world, 20000000.125, 80.5, -20000000.375);
        var display = VehicleGroupFixtures.block(world);
        display.location = eye.clone().add(3, 0, 8);
        display.transformation = new Transformation(new Vector3f(-3, -0.5f, 0), new Quaternionf(), new Vector3f(2, 1, 1), new Quaternionf());
        assertSame(display.entity, VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), List.of(display.entity), entity -> false));
        assertNull(VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), List.of(display.entity), entity -> false, 7));
    }

    @Test void pitchYawBothQuaternionRotationsAndNonUniformScaleRemainTargetable() {
        var world = VehicleGroupFixtures.world();
        var eye = new Location(world, 0, 0, 0);
        var display = VehicleGroupFixtures.block(world);
        display.location = new Location(world, 0, 0, 8, 55, -15);
        display.transformation = new Transformation(new Vector3f(0.2f, 0.1f, 0), new Quaternionf().rotateZ(0.3f), new Vector3f(2, 0.5f, 1), new Quaternionf().rotateY(0.2f));
        var center = VehicleModel.relativeTransform(eye, (org.bukkit.entity.Display) display.entity).transformPosition(new Vector3f(0.5f));
        assertSame(display.entity, VehicleDisplayPicker.pick(eye, new Vector(center.x, center.y, center.z), List.of(display.entity), entity -> false));
    }

    @Test void nearestEligibleDisplayWinsAndForeignInvalidAndExcludedCandidatesAreSkipped() {
        var world = VehicleGroupFixtures.world();
        var eye = new Location(world, 0, 0, 0);
        var near = VehicleGroupFixtures.block(world);
        var far = VehicleGroupFixtures.block(world);
        var foreign = VehicleGroupFixtures.block(VehicleGroupFixtures.world());
        near.location.add(0, 0, 3);
        far.location.add(0, 0, 8);
        var candidates = List.of(far.entity, foreign.entity, near.entity);
        assertSame(near.entity, VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), candidates, entity -> false));
        assertSame(far.entity, VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), candidates, entity -> entity.getUniqueId().equals(near.id)));
        near.valid = false;
        assertSame(far.entity, VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), candidates, entity -> false));
    }

    @Test void singularInvisibleTransformsAndTargetsBehindThePlayerAreNotPicked() {
        var world = VehicleGroupFixtures.world();
        var eye = new Location(world, 0, 0, 0);
        var display = VehicleGroupFixtures.block(world);
        display.location.add(0, 0, 5);
        assertNull(VehicleDisplayPicker.pick(eye, new Vector(0, 0, -1), List.of(display.entity), entity -> false));
        display.transformation = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0), new Quaternionf());
        assertNull(VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), List.of(display.entity), entity -> false));
    }

    @Test void itemGeometryIsCenteredAndRangeAndObstructionDistanceAreHonored() {
        var world = VehicleGroupFixtures.world();
        var eye = new Location(world, 0, 0, 0);
        var item = new VehicleGroupFixtures.Node(ItemDisplay.class, world);
        item.location.add(0, 0, 5);
        assertSame(item.entity, VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), List.of(item.entity), entity -> false));
        assertNull(VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), List.of(item.entity), entity -> false, 4));
        item.location.add(0, 0, 30);
        assertNull(VehicleDisplayPicker.pick(eye, new Vector(0, 0, 1), List.of(item.entity), entity -> false));
    }

    @Test void parallelAndInsideBoxRaysHaveFiniteBoundedIntersections() {
        assertEquals(5, VehicleDisplayPicker.intersection(new Vector3f(0.5f, 0.5f, -5), new Vector3f(0, 0, 1), 0, 1));
        assertEquals(0, VehicleDisplayPicker.intersection(new Vector3f(0.5f), new Vector3f(0, 0, 1), 0, 1));
        assertEquals(-1, VehicleDisplayPicker.intersection(new Vector3f(2, 0, -5), new Vector3f(0, 0, 1), 0, 1));
        assertEquals(-1, VehicleDisplayPicker.intersection(new Vector3f(), new Vector3f(Float.NaN), 0, 1));
    }
}
