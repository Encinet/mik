package org.encinet.mik.module.vehicle;

import org.joml.Quaterniond;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VehicleStoreTest {
    @TempDir Path directory;

    @Test void replacementAndDeletionCompareThePersistedModel() throws IOException {
        var store = new VehicleStore(directory);
        var original = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        store.saveDefinition(original);
        var draft = new VehicleModelDraft(original);
        draft.set("mass", "1800");
        var replacement = draft.validate();
        store.replaceDefinition(original, replacement);
        assertEquals(replacement, store.loadDefinitions().get(original.id()));
        assertThrows(IOException.class, () -> store.replaceDefinition(original, original));
        assertThrows(IOException.class, () -> store.deleteDefinition(original));
        assertEquals(replacement, store.loadDefinitions().get(original.id()));
        store.deleteDefinition(replacement);
        assertTrue(store.loadDefinitions().isEmpty());
    }

    @Test void externalEditsAndIdChangesCannotBeOverwritten() throws IOException {
        var store = new VehicleStore(directory);
        var original = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        store.saveDefinition(original);
        Path file = directory.resolve("definitions").resolve(original.id() + ".yml");
        String external = Files.readString(file).replace("mass: 1200.0", "mass: 1600.0");
        Files.writeString(file, external);
        assertThrows(IOException.class, () -> store.replaceDefinition(original, original));
        assertThrows(IOException.class, () -> store.deleteDefinition(original));
        assertEquals(external, Files.readString(file));
        var renamed = VehicleModelDraft.copy(original, "new_id").validate();
        assertThrows(IllegalArgumentException.class, () -> store.replaceDefinition(original, renamed));
    }

    @Test void oversizedDefinitionCannotBeWrittenOrReplaceAnExistingModel() throws IOException {
        var store = new VehicleStore(directory);
        var original = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        store.saveDefinition(original);
        var config = VehicleStore.encodeDefinition(original);
        var large = VehicleModelDraft.part(VehicleDefinition.PartKind.TEXT, "a".repeat(131072));
        config.set("parts", null);
        for (int index = 0; index < 65; index++) VehicleStore.encodePart(config.createSection("parts." + index), large);
        var oversized = VehicleStore.decodeDefinition(config);
        assertThrows(IOException.class, () -> store.replaceDefinition(original, oversized));
        assertEquals(original, store.loadDefinitions().get(original.id()));
    }

    @Test void definitionRoundTripsTransformsAndPhysics() throws IOException {
        var definition = VehicleFixtures.definition(VehicleDefinition.Kind.PLANE);
        var store = new VehicleStore(directory);
        store.saveDefinition(definition);
        var loaded = store.loadDefinitions().get(definition.id());
        assertEquals(definition.mass(), loaded.mass());
        assertEquals(definition.kind(), loaded.kind());
        assertEquals(definition.seats(), loaded.seats());
        assertEquals(definition.parts().getFirst().transform(), loaded.parts().getFirst().transform());
        assertEquals(definition.engine(), loaded.engine());
    }

    @Test void emptyInstanceFileRoundTripsWithoutCreatingPhantomVehicles() throws IOException {
        var store = new VehicleStore(directory);
        store.saveInstances(List.of());
        assertEquals(List.of(), store.loadInstances());
    }

    @Test void snapshotRetainsAirborneMomentumButDoesNotRestartEngine() throws IOException {
        var definition = VehicleFixtures.definition(VehicleDefinition.Kind.PLANE);
        var body = new VehicleBody(definition, new VehicleVector(20, 300, -40), new Quaterniond().rotateXYZ(0.1, 0.2, -0.3));
        body.velocity = new VehicleVector(1, -2, 30);
        body.angularVelocity = new VehicleVector(0.1, 0.2, 0.3);
        body.engineRunning = true;
        body.throttle = 0.7;
        body.transmission.mode = VehicleTransmission.Mode.MANUAL;
        var instance = new VehicleInstance(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), body);
        instance.publicAccess = true;
        var store = new VehicleStore(directory);
        store.saveInstances(List.of(instance.snapshot()));
        var restored = store.loadInstances().getFirst().restore(definition);
        assertEquals(body.velocity, restored.body.velocity);
        assertEquals(body.angularVelocity, restored.body.angularVelocity);
        assertEquals(body.origin().coordinateX(), restored.body.origin().coordinateX(), 1.0E-9);
        assertEquals(body.origin().coordinateY(), restored.body.origin().coordinateY(), 1.0E-9);
        assertEquals(body.orientation, restored.body.orientation);
        assertFalse(restored.body.engineRunning);
        assertTrue(restored.publicAccess);
        assertEquals(VehicleTransmission.Mode.MANUAL, restored.body.transmission.mode);
    }

    @Test void corruptFilesAreNotSilentlyReplacedAndExistingModelsCannotBeOverwritten() throws IOException {
        var store = new VehicleStore(directory);
        var definition = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        store.saveDefinition(definition);
        assertThrows(IOException.class, () -> store.saveDefinition(definition));
        Path file = directory.resolve("instances.yml");
        Files.writeString(file, "version: 1\ninstances: [broken");
        String original = Files.readString(file);
        assertThrows(IOException.class, store::loadInstances);
        assertEquals(original, Files.readString(file));
    }

    @Test void invalidDefinitionFailsWholeLoad() throws IOException {
        var store = new VehicleStore(directory);
        var definition = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        store.saveDefinition(definition);
        Path file = directory.resolve("definitions").resolve(definition.id() + ".yml");
        Files.writeString(file, Files.readString(file).replace("mass: 1200.0", "mass: .NaN"));
        assertThrows(IOException.class, store::loadDefinitions);
    }

    @Test void rejectsNonFiniteMatricesAndMutableMatrixEscape() {
        var matrix = new org.joml.Matrix4f().translation(1, 2, 3);
        var part = VehicleFixtures.part(matrix);
        matrix.translation(100, 200, 300);
        assertEquals(1, part.transform().m30());
        ((org.joml.Matrix4f) part.transform()).translation(100, 200, 300);
        assertEquals(1, part.transform().m30());
        assertThrows(IllegalArgumentException.class, () -> VehicleFixtures.part(new org.joml.Matrix4f().m00(Float.NaN)));
    }
}
