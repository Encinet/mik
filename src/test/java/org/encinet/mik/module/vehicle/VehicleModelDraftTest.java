package org.encinet.mik.module.vehicle;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleModelDraftTest {
    @Test void copyingDisplayPartsPreservesAllPropertiesAndCreatesAnIndependentUndoableComponent() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.changePart(0, "position", "1 2 3");
        draft.changePart(0, "rotate", "15 30 5");
        draft.changePart(0, "scale", "2 0.5 1.5");
        draft.changePart(0, "animation", "FRONT_WHEEL");
        draft.changePart(0, "option", "block-light 12");
        var original = draft.parts().getFirst();
        int before = draft.count("parts");
        long revision = draft.revision();
        int index = draft.copy("parts", 0);
        assertEquals(before, index);
        assertEquals(before + 1, draft.count("parts"));
        assertEquals(revision + 1, draft.revision());
        assertEquals(original, draft.parts().get(index));
        assertTrue(draft.undo());
        assertEquals(before, draft.count("parts"));
        assertTrue(draft.redo());
        draft.position("parts", index, false, new VehicleVector(4, 2, 3));
        assertEquals(original, draft.parts().getFirst());
        assertNotEquals(original.transform(), draft.parts().get(index).transform());
    }

    @Test void copiedDriverSeatsBecomePassengersWithoutChangingTheOriginalExitOrDriver() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var original = draft.validate().seats().getFirst();
        int index = draft.copy("seats", 0);
        var copied = draft.validate().seats().get(index);
        assertTrue(original.driver());
        assertFalse(copied.driver());
        assertEquals(original.position(), copied.position());
        assertEquals(original.exit(), copied.exit());
        assertEquals(1, draft.validate().seats().stream().filter(VehicleDefinition.Seat::driver).count());
        draft.seat("exit", index, new VehicleVector(3, 0, 0), false);
        assertEquals(original, draft.validate().seats().getFirst());
    }

    @Test void colliderAndSupportCopiesKeepTheirCoordinatesAndRespectComponentLimits() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var collider = draft.collider(0);
        var support = draft.position("supports", 0, false);
        int colliderIndex = draft.copy("colliders", 0);
        int supportIndex = draft.copy("supports", 0);
        assertEquals(collider, draft.collider(colliderIndex));
        assertEquals(support, draft.position("supports", supportIndex, false));
        for (String section : List.of("seats", "colliders", "supports")) {
            while (draft.count(section) < 16) draft.copy(section, 0);
            long revision = draft.revision();
            assertThrows(IllegalArgumentException.class, () -> draft.copy(section, 0));
            assertEquals(revision, draft.revision());
        }
        assertThrows(IllegalArgumentException.class, () -> draft.copy("unknown", 0));
        assertThrows(IllegalArgumentException.class, () -> draft.copy("parts", -1));
        draft.addParts(java.util.Collections.nCopies(127, draft.parts().getFirst()));
        long revision = draft.revision();
        assertThrows(IllegalArgumentException.class, () -> draft.copy("parts", 0));
        assertEquals(revision, draft.revision());
        assertEquals(128, draft.count("parts"));
    }

    @Test void redoRestoresUndoneEditsAndOnlyANewSuccessfulEditClearsTheBranch() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var original = draft.validate();
        draft.set("mass", "2000");
        draft.position("seats", 0, true, new VehicleVector(2, 1, 0));
        var edited = draft.validate();
        long revision = draft.revision();
        assertTrue(draft.undo());
        assertTrue(draft.undo());
        assertEquals(original, draft.validate());
        assertFalse(draft.canUndo());
        assertTrue(draft.canRedo());
        assertThrows(IllegalArgumentException.class, () -> draft.set("mass", "-1"));
        draft.set("mass", String.valueOf(original.mass()));
        assertTrue(draft.canRedo());
        assertTrue(draft.redo());
        assertTrue(draft.redo());
        assertEquals(edited, draft.validate());
        assertEquals(revision + 4, draft.revision());
        assertFalse(draft.redo());
        assertTrue(draft.undo());
        draft.set("mass", "2200");
        assertFalse(draft.canRedo());
        assertTrue(draft.undo());
        assertEquals(2000, draft.validate().mass());
    }

    @Test void undoAndRedoShareTheBoundedHistoryBudget() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        for (int index = 0; index < 25; index++) draft.set("mass", String.valueOf(2000 + index));
        for (int index = 0; index < 20; index++) assertTrue(draft.undo());
        assertFalse(draft.undo());
        assertEquals(2004, draft.validate().mass());
        for (int index = 0; index < 20; index++) assertTrue(draft.redo());
        assertFalse(draft.redo());
        assertEquals(2024, draft.validate().mass());
        for (int index = 0; index < 20; index++) assertTrue(draft.undo());
        assertFalse(draft.undo());
    }

    @Test void savingProvidesANewCheckpointWithoutLosingHistoryOrTheDraftIdentity() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var token = draft.token();
        draft.set("mass", "2000");
        draft.changePart(0, "option", "opacity 100");
        var saved = draft.validate();
        long revision = draft.revision();
        draft.saved(saved);
        assertSame(saved, draft.base());
        assertEquals(token, draft.token());
        assertEquals(revision + 1, draft.revision());
        assertFalse(draft.dirty());
        assertTrue(draft.undo());
        assertTrue(draft.undo());
        assertTrue(draft.dirty());
        assertTrue(draft.redo());
        assertTrue(draft.redo());
        assertEquals(saved, draft.validate());
        assertFalse(draft.dirty());
        assertThrows(IllegalArgumentException.class, () -> draft.saved(VehicleModelDraft.copy(saved, "other_id").validate()));
        assertFalse(draft.dirty());
    }

    @Test void partSnapshotsAreCachedUntilTheDraftRevisionChanges() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var original = draft.parts();
        assertSame(original, draft.parts());
        draft.position("parts", 0, false, new VehicleVector(1, 2, 3));
        var edited = draft.parts();
        assertNotSame(original, edited);
        assertSame(edited, draft.parts());
        assertTrue(draft.undo());
        assertEquals(original, draft.parts());
        assertTrue(draft.redo());
        assertEquals(edited, draft.parts());
        assertThrows(UnsupportedOperationException.class, () -> draft.parts().clear());
    }

    @Test void positionalEditsPreserveDisplayTransformsColliderSizeAndSeatExits() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.changePart(0, "rotate", "0 37 0");
        draft.changePart(0, "scale", "2 0.5 1.5");
        var oldPart = draft.parts().getFirst();
        var oldCollider = draft.collider(0);
        var oldExit = draft.position("seats", 0, true);
        VehicleVector destination = new VehicleVector(1.25, 2, -0.5);
        draft.position("parts", 0, false, destination);
        assertTrue(new Matrix4f(oldPart.transform()).setTranslation(1.25f, 2, -0.5f).equals(draft.parts().getFirst().transform(), 1.0E-6f));
        draft.position("seats", 0, false, destination);
        assertEquals(oldExit, draft.position("seats", 0, true));
        draft.position("seats", 0, true, destination);
        draft.position("colliders", 0, false, destination);
        assertEquals(destination, draft.collider(0).center());
        assertEquals(oldCollider.halfSize(), draft.collider(0).halfSize());
        assertTrue(draft.undo());
        assertEquals(oldCollider, draft.collider(0));
        draft.position("supports", 0, false, destination);
        assertEquals(destination, draft.position("supports", 0, false));
        assertDoesNotThrow(draft::validate);
    }

    @Test void invalidPositionEditsAreAtomicAndCanBeUndoneAfterValidEdits() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var original = draft.validate();
        for (String section : List.of("parts", "seats", "colliders", "supports")) {
            assertThrows(IllegalArgumentException.class, () -> draft.position(section, 0, false, new VehicleVector(40, 0, 0)));
            assertThrows(IllegalArgumentException.class, () -> draft.position(section, 100, false, VehicleVector.ZERO));
        }
        assertFalse(draft.undo());
        assertEquals(original, draft.validate());
        draft.position("supports", 0, false, new VehicleVector(0.25, 0, 0.25));
        assertTrue(draft.undo());
        assertEquals(original, draft.validate());
    }

    @Test void newModelRequiresPartsAndRetainsPresetPhysics() {
        var draft = VehicleModelDraft.create("sedan", VehicleDefinition.Kind.CAR);
        assertNull(draft.base());
        assertTrue(draft.dirty());
        assertEquals(0, draft.count("parts"));
        assertThrows(IllegalArgumentException.class, draft::validate);
        draft.addParts(List.of(VehicleFixtures.part(new Matrix4f())));
        assertEquals("sedan", draft.validate().id());
        assertEquals(VehicleDefinition.Kind.CAR, draft.validate().kind());
        assertEquals(1, draft.validate().seats().stream().filter(VehicleDefinition.Seat::driver).count());
    }

    @Test void cloneHasIndependentIdentityAndTransforms() {
        var original = VehicleFixtures.definition(VehicleDefinition.Kind.PLANE);
        var clone = VehicleModelDraft.copy(original, "new_plane");
        assertNull(clone.base());
        assertEquals("new_plane", clone.validate().id());
        clone.changePart(0, "position", "1 2 3");
        assertNotEquals(original.parts().getFirst().transform(), clone.validate().parts().getFirst().transform());
        assertThrows(IllegalArgumentException.class, () -> VehicleModelDraft.copy(original, "../bad"));
    }

    @Test void scalarVectorAndGearFieldsCanBeEditedAndUndone() {
        var original = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        var draft = new VehicleModelDraft(original);
        assertFalse(draft.dirty());
        draft.set("mass", "1500");
        draft.set("center-of-mass", "0, 0.4, -0.2");
        draft.set("engine.ratios", "4, 2, 1");
        assertEquals(1500, draft.validate().mass());
        assertEquals(new VehicleVector(0, 0.4, -0.2), draft.validate().centerOfMass());
        assertEquals(List.of(4.0, 2.0, 1.0), draft.validate().engine().ratios());
        assertTrue(draft.undo());
        assertTrue(draft.undo());
        assertTrue(draft.undo());
        assertEquals(original, draft.validate());
        assertFalse(draft.dirty());
        assertFalse(draft.undo());
        assertEquals(6, draft.revision());
    }

    @Test void invalidMutationsDoNotChangeStateOrUndoHistory() {
        var original = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        var draft = new VehicleModelDraft(original);
        for (String value : List.of("NaN", "Infinity", "0", "-1")) assertThrows(IllegalArgumentException.class, () -> draft.set("mass", value));
        assertThrows(IllegalArgumentException.class, () -> draft.set("id", "changed"));
        assertThrows(IllegalArgumentException.class, () -> draft.set("engine.ratios", "2 4"));
        assertThrows(IllegalArgumentException.class, () -> draft.changePart(0, "scale", "0 1 1"));
        assertThrows(IllegalArgumentException.class, () -> draft.changePart(0, "position", "100 0 0"));
        assertThrows(IllegalArgumentException.class, () -> draft.changePart(0, "option", "billboard NOT_REAL"));
        assertThrows(IllegalArgumentException.class, () -> draft.changePart(0, "option", "sky-light 16"));
        assertEquals(original, draft.validate());
        assertFalse(draft.undo());
        assertEquals(0, draft.revision());
    }

    @Test void draftCanBeIncompleteUntilSaveValidation() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.remove("seats", 0);
        assertThrows(IllegalArgumentException.class, draft::validate);
        draft.seat("add", -1, new VehicleVector(0, 1, 0), true);
        assertTrue(draft.validate().seats().getFirst().driver());
        draft.set("engine.redline-rpm", "500");
        assertThrows(IllegalArgumentException.class, draft::validate);
        draft.undo();
        assertDoesNotThrow(draft::validate);
    }

    @Test void settingDriverClearsOtherDriverFlagsAndExitCanBeEdited() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.BOAT));
        draft.seat("add", -1, new VehicleVector(1, 1, 1), true);
        var seats = draft.validate().seats();
        assertEquals(1, seats.stream().filter(VehicleDefinition.Seat::driver).count());
        assertTrue(seats.getLast().driver());
        draft.seat("driver", 0, null, true);
        draft.seat("exit", 0, new VehicleVector(2, 1, 0), false);
        assertTrue(draft.validate().seats().getFirst().driver());
        assertFalse(draft.validate().seats().getLast().driver());
        assertEquals(new VehicleVector(2, 1, 0), draft.validate().seats().getFirst().exit());
    }

    @Test void componentRemovalReindexesNestedFieldsWithoutChangingSurvivors() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.addParts(List.of(VehicleFixtures.part(new Matrix4f().translation(2, 3, 4))));
        draft.remove("parts", 0);
        assertEquals(1, draft.count("parts"));
        assertEquals(2, draft.parts().getFirst().transform().m30());
        var before = draft.validate().supports();
        draft.remove("supports", 1);
        assertEquals(before.get(2), draft.validate().supports().get(1));
        assertThrows(IllegalArgumentException.class, () -> draft.remove("supports", -1));
    }

    @Test void rotationsAndScaleAreRelativeButPositionIsAbsolute() {
        var draft = VehicleModelDraft.create("car", VehicleDefinition.Kind.CAR);
        draft.addParts(List.of(VehicleFixtures.part(new Matrix4f().translation(1, 2, 3).scale(2, 1, 3))));
        draft.changePart(0, "rotate", "0 90 0");
        draft.changePart(0, "scale", "1 2 1");
        draft.changePart(0, "position", "4 5 6");
        var expected = new Matrix4f().translation(1, 2, 3).scale(2, 1, 3).rotateXYZ(0, (float) -Math.PI / 2, 0).scale(1, 2, 1).setTranslation(4, 5, 6);
        assertTrue(expected.equals(draft.parts().getFirst().transform(), 1.0E-5f));
    }

    @Test void displayOptionsAndAnimationArePreserved() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.changePart(0, "animation", "front_wheel");
        draft.changePart(0, "option", "opacity 255");
        draft.changePart(0, "option", "background -16777216");
        draft.changePart(0, "option", "shadow true");
        assertEquals(VehicleDefinition.Animation.FRONT_WHEEL, draft.parts().getFirst().animation());
        assertEquals((byte) -1, draft.parts().getFirst().textOpacity());
        assertEquals(0xFF000000, draft.parts().getFirst().background());
        assertTrue(draft.parts().getFirst().shadow());
        assertThrows(IllegalArgumentException.class, () -> draft.changePart(0, "option", "opacity 256"));
    }

    @Test void collisionBoundsCanBeRecomputedFromEditedGeometry() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.PLANE));
        draft.changePart(0, "position", "2 0 0");
        draft.collider("auto", -1, null);
        assertEquals(List.of(VehicleModel.bounds(draft.parts())), draft.validate().colliders());
        var collider = new VehicleDefinition.Collider(VehicleVector.ZERO, new VehicleVector(0.5, 0.5, 0.5));
        draft.collider("add", -1, collider);
        draft.remove("colliders", 0);
        assertEquals(List.of(collider), draft.validate().colliders());
    }

    @Test void componentAndHistoryLimitsAreEnforced() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.addParts(java.util.Collections.nCopies(127, VehicleFixtures.part(new Matrix4f())));
        assertThrows(IllegalArgumentException.class, () -> draft.addParts(List.of(VehicleFixtures.part(new Matrix4f()))));
        assertEquals(128, draft.count("parts"));
        var fresh = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        for (int index = 0; index < 25; index++) fresh.set("mass", String.valueOf(2000 + index));
        int undone = 0;
        while (fresh.undo()) undone++;
        assertEquals(20, undone);
        assertEquals(2004, fresh.validate().mass());
    }

    @Test void oversizedModelEditIsRejectedAtomically() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var large = VehicleModelDraft.part(VehicleDefinition.PartKind.TEXT, "a".repeat(131072));
        assertThrows(IllegalArgumentException.class, () -> draft.addParts(java.util.Collections.nCopies(64, large)));
        assertEquals(1, draft.count("parts"));
        assertFalse(draft.undo());
    }
}
