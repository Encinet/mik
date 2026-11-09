package org.encinet.mik.feature.world.vehicle;

import java.util.List;
import org.bukkit.Location;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorFacingTest {
    @Test void choosingAnotherFrontKeepsEveryMeshSeatExitAndPhysicsPointInPlaceAndUndoesAsOneEdit() {
        var source = VehicleFixtures.definition(VehicleDefinition.Kind.PLANE);
        var world = VehicleGroupFixtures.world();
        for (float yaw : new float[]{-170, 0, 37, 170}) for (int turns : new int[]{1, 2, 3}) {
            var draft = new VehicleModelDraft(source);
            draft.changePart(0, "rotate", "12 37 19");
            draft.changePart(0, "scale", "0.7 1.3 0.9");
            draft.seat("add", -1, new VehicleVector(0.5, 1, -1), false);
            var before = draft.validate();
            long revision = draft.revision();
            var originalOrigin = new Location(world, 12, 64, -8, yaw, 0);
            draft.front(turns);
            assertEquals(revision + 1, draft.revision());
            assertEquals(turns, draft.frameTurns());
            var after = draft.validate();
            var origin = VehicleEditorGeometry.oriented(originalOrigin, draft.frameTurns());
            Matrix4f oldMesh = new Matrix4f().rotate(new Quaternionf().set(VehicleModel.orientation(yaw))).mul(before.parts().getFirst().transform());
            Matrix4f newMesh = new Matrix4f().rotate(new Quaternionf().set(VehicleModel.orientation(origin.getYaw()))).mul(after.parts().getFirst().transform());
            assertTrue(oldMesh.equals(newMesh, 1.0E-5f), yaw + " / " + turns);
            for (String section : List.of("seats", "colliders", "supports")) {
                var original = new VehicleModelDraft(before);
                for (int index = 0; index < draft.count(section); index++) for (boolean exit : section.equals("seats") ? List.of(false, true) : List.of(false))
                    assertPoint(VehicleEditorGeometry.world(originalOrigin, original.position(section, index, exit)),
                            VehicleEditorGeometry.world(origin, draft.position(section, index, exit)));
            }
            assertPoint(VehicleEditorGeometry.world(originalOrigin, before.centerOfMass()), VehicleEditorGeometry.world(origin, after.centerOfMass()));
            for (int corner = 0; corner < 8; corner++) {
                var box = before.colliders().getFirst();
                var half = box.halfSize();
                var point = box.center().add(new VehicleVector((corner & 1) == 0 ? -half.coordinateX() : half.coordinateX(),
                        (corner & 2) == 0 ? -half.coordinateY() : half.coordinateY(), (corner & 4) == 0 ? -half.coordinateZ() : half.coordinateZ()));
                assertPoint(VehicleEditorGeometry.world(originalOrigin, point), VehicleEditorGeometry.world(origin, VehicleEditorFacing.rebase(point, turns)));
            }
            assertEquals(VehicleEditorFacing.extents(before.colliders().getFirst().halfSize(), turns), after.colliders().getFirst().halfSize());
            assertEquals(VehicleEditorFacing.extents(before.inertia(), turns), after.inertia());
            assertEquals(before.parts().getFirst().payload(), after.parts().getFirst().payload());
            assertTrue(draft.undo()); assertEquals(before, draft.validate()); assertEquals(0, draft.frameTurns());
            assertTrue(draft.redo()); assertEquals(after, draft.validate()); assertEquals(turns, draft.frameTurns());
        }
        assertEquals(VehicleFixtures.definition(VehicleDefinition.Kind.PLANE), source);
    }

    @Test void completeTurnsDoNotAccumulateRoundingAndAnIncompleteDraftCanStillChooseItsFront() {
        var source = VehicleFixtures.definition(VehicleDefinition.Kind.CAR);
        var draft = new VehicleModelDraft(source);
        for (int index = 0; index < 4; index++) draft.front(1);
        assertEquals(source, draft.validate());
        assertEquals(0, draft.frameTurns());
        long revision = draft.revision();
        draft.front(4); assertEquals(revision, draft.revision());
        draft.remove("seats", 0);
        assertDoesNotThrow(() -> draft.front(1));
        assertThrows(IllegalArgumentException.class, draft::validate);
    }

    @Test void savingKeepsThePreviewBasisAndLaterUndoRestoresBothDataAndBasis() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.front(3);
        var saved = draft.validate();
        draft.saved(saved);
        assertFalse(draft.dirty()); assertEquals(3, draft.frameTurns());
        draft.set("mass", "1600");
        assertTrue(draft.undo()); assertEquals(saved, draft.validate()); assertEquals(3, draft.frameTurns());
        assertTrue(draft.undo()); assertEquals(0, draft.frameTurns()); assertTrue(draft.dirty());
    }

    @Test void reanchoringAfterChoosingAFrontUsesTheRequestedHeadingAndDoesNotChangeTheRequestedLocation() {
        var requested = new Location(VehicleGroupFixtures.world(), 10, 72, -8, -37, 64);
        for (int turns = 0; turns < 4; turns++) {
            var anchored = VehicleEditorGeometry.anchored(requested, turns);
            var shown = VehicleEditorGeometry.oriented(anchored, turns);
            assertPoint(requested, shown);
            assertEquals(requested.getYaw(), shown.getYaw()); assertEquals(0, shown.getPitch());
        }
        assertEquals(64, requested.getPitch());
        assertEquals(0, VehicleEditorFacing.nearest(360));
        assertEquals(3, VehicleEditorFacing.nearest(-90));
        assertEquals(1, VehicleEditorFacing.nearest(89));
    }

    private static void assertPoint(Location expected, Location actual) {
        assertEquals(expected.getX(), actual.getX(), 1.0E-8);
        assertEquals(expected.getY(), actual.getY(), 1.0E-8);
        assertEquals(expected.getZ(), actual.getZ(), 1.0E-8);
    }
}
