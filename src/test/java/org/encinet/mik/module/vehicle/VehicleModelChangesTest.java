package org.encinet.mik.module.vehicle;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VehicleModelChangesTest {
    @Test void anOccupiedVehicleCannotBeRebuiltEvenIfItsEngineIsOff() {
        var body = VehicleFixtures.body(VehicleDefinition.Kind.CAR, VehicleVector.ZERO);
        assertFalse(body.engineRunning);
        assertThrows(IllegalArgumentException.class, () -> VehicleModelChanges.requireStopped(body, true));
        assertDoesNotThrow(() -> VehicleModelChanges.requireStopped(body, false));
    }
    @Test void updatesOnlyAffectedStoppedVehiclesAndPreservesInstanceState() {
        var original = instance(VehicleDefinition.Kind.CAR);
        var unrelated = instance(VehicleDefinition.Kind.BOAT);
        original.publicAccess = true;
        original.body.health = 80;
        original.body.fuel = 20;
        original.body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        var draft = new VehicleModelDraft(original.body.definition);
        draft.set("mass", "1800");
        var proposed = VehicleModelChanges.replace(Map.of(original.id, original, unrelated.id, unrelated), draft.validate());
        assertSame(unrelated, proposed.get(unrelated.id));
        var replacement = proposed.get(original.id);
        assertNotSame(original, replacement);
        assertEquals(original.owner, replacement.owner);
        assertEquals(original.worldId, replacement.worldId);
        assertEquals(original.body.origin(), replacement.body.origin());
        assertEquals(20, replacement.body.fuel);
        assertEquals(80, replacement.body.health);
        assertTrue(replacement.publicAccess);
        assertEquals(VehicleTransmission.Mode.MANUAL, replacement.body.transmission.mode);
        assertEquals(1800, replacement.body.definition.mass());
        assertEquals(1200, original.body.definition.mass());
    }

    @Test void refusesRunningMovingOrRotatingAffectedVehiclesWithoutMutatingThem() {
        var original = instance(VehicleDefinition.Kind.CAR);
        var map = Map.of(original.id, original);
        original.body.engineRunning = true;
        assertThrows(IllegalArgumentException.class, () -> VehicleModelChanges.replace(map, original.body.definition));
        original.body.engineRunning = false;
        original.body.velocity = new VehicleVector(0, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> VehicleModelChanges.replace(map, original.body.definition));
        original.body.velocity = VehicleVector.ZERO;
        original.body.angularVelocity = new VehicleVector(0, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> VehicleModelChanges.replace(map, original.body.definition));
        assertSame(original, map.get(original.id));
    }

    @Test void unrelatedRunningVehicleDoesNotBlockModelSave() {
        var original = instance(VehicleDefinition.Kind.BOAT);
        original.body.engineRunning = true;
        assertSame(original, VehicleModelChanges.replace(Map.of(original.id, original), VehicleFixtures.definition(VehicleDefinition.Kind.CAR)).get(original.id));
    }

    @Test void incompatibleFuelCapacityRejectsReplacementWithoutStateLoss() {
        var original = instance(VehicleDefinition.Kind.CAR);
        var draft = new VehicleModelDraft(original.body.definition);
        draft.set("fuel.capacity", "1");
        assertThrows(IllegalArgumentException.class, () -> VehicleModelChanges.replace(Map.of(original.id, original), draft.validate()));
        assertEquals(original.body.definition.fuelCapacity(), original.body.fuel);
    }

    @Test void deletionRejectsAnyReferencingInstanceIncludingInactiveOnes() {
        var original = instance(VehicleDefinition.Kind.PLANE);
        assertNull(original.entities);
        assertThrows(IllegalArgumentException.class, () -> VehicleModelChanges.requireUnused(java.util.List.of(original), original.body.definition.id()));
        assertDoesNotThrow(() -> VehicleModelChanges.requireUnused(java.util.List.of(original), "unused"));
    }

    private VehicleInstance instance(VehicleDefinition.Kind kind) {
        return new VehicleInstance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), VehicleFixtures.body(kind, new VehicleVector(1, 2, 3)));
    }
}
