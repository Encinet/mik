package org.encinet.mik.feature.world.vehicle;

import java.util.List;
import org.encinet.mik.feature.world.vehicle.VehicleEditorRoute.*;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorSceneTest {
    @Test void markersSelectSeatsAndTheirOwnExitsSeparatelyAndDrawThePhysicalSupportLength() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.seat("add", -1, new VehicleVector(1, 1, 1), false);
        var scene = VehicleEditorScene.create(draft, new Components(Section.SEATS, 0), true, .1);
        for (int index = 0; index < 2; index++) {
            assertEquals(new VehicleEditorScene.Select(new Position(new ComponentRef(Section.SEATS, index), false)), handle(scene, "SEATS:" + index).action());
            assertEquals(new VehicleEditorScene.Select(new Position(new ComponentRef(Section.SEATS, index), true)), handle(scene, "exit:" + index).action());
            assertEquals(draft.position("seats", index, true), handle(scene, "exit:" + index).point());
        }
        var supports = VehicleEditorScene.create(draft, new Components(Section.SUPPORTS, 0), false, .1);
        assertTrue(supports.handles().stream().noneMatch(handle -> handle.key().startsWith("SEATS:")));
        var support = draft.position("supports", 0, false);
        assertTrue(supports.lines().stream().anyMatch(line -> line.from().equals(support)
                && Math.abs(line.from().coordinateY() - line.to().coordinateY() - Double.parseDouble(draft.field("support-length"))) < 1.0E-9));
        assertFalse(supports.handles().stream().anyMatch(handle -> handle.action() instanceof VehicleEditorScene.Adjust));
    }

    @Test void rotationAndScaleHandlesFollowThePartsOwnAxesWhilePositionUsesTheModelAxes() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.changePart(0, "rotate", "10 90 20");
        var target = new ComponentRef(Section.PARTS, 0);
        for (var mode : List.of(VehicleEditorTransform.Mode.ROTATE, VehicleEditorTransform.Mode.SCALE)) {
            var scene = VehicleEditorScene.create(draft, new Transform(target, mode), true, 5);
            for (int axis = 0; axis < 3; axis++) {
                var basis = new Vector3f(axis == 0 ? 1 : 0, axis == 1 ? 1 : 0, axis == 2 ? 1 : 0);
                draft.parts().getFirst().transform().transformDirection(basis).normalize();
                var direction = handle(scene, "adjust:" + axis + ":1").point().subtract(draft.position("parts", 0, false)).normalized();
                assertEquals(basis.x, direction.coordinateX(), 1.0E-6);
                assertEquals(basis.y, direction.coordinateY(), 1.0E-6);
                assertEquals(basis.z, direction.coordinateZ(), 1.0E-6);
            }
        }
        var position = VehicleEditorScene.create(draft, new Position(target, false), true, .25);
        assertEquals(new VehicleVector(.9, 0, 0), handle(position, "adjust:0:1").point().subtract(draft.position("parts", 0, false)));
    }

    @Test void colliderDimensionHandlesAreOutsideTheBoxAndFrontChoicesAreOutsideTheBody() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var box = draft.collider(0);
        var size = VehicleEditorScene.create(draft, new Transform(new ComponentRef(Section.COLLIDERS, 0), VehicleEditorTransform.Mode.SIZE), true, .1);
        assertTrue(handle(size, "adjust:0:1").point().coordinateX() > box.center().coordinateX() + box.halfSize().coordinateX());
        assertTrue(handle(size, "adjust:2:-1").point().coordinateZ() < box.center().coordinateZ() - box.halfSize().coordinateZ());
        var facing = VehicleEditorScene.create(draft, Facing.INSTANCE, true, .1);
        assertTrue(handle(facing, "front").point().coordinateZ() > 2);
        assertTrue(handle(facing, "front-choice:1").point().coordinateX() < -1);
        assertEquals(new VehicleEditorScene.Face(3), handle(facing, "front-choice:3").action());
        assertFalse(facing.handles().stream().anyMatch(handle -> handle.action() instanceof VehicleEditorScene.Adjust));
    }

    @Test void pickingUsesTheNearestPublishedMarkerObeysOcclusionAndDoesNotSelectBehindTheEye() {
        var near = new VehicleEditorScene.Handle("near", new VehicleVector(0, 0, 2), org.encinet.mik.shared.i18n.Message.VEHICLE_MODEL_FRONT,
                List.of(), VehicleEditorScene.Ink.BLUE, new VehicleEditorScene.Face(1));
        var far = new VehicleEditorScene.Handle("far", new VehicleVector(0, 0, 4), near.label(), List.of(), near.ink(), new VehicleEditorScene.Face(2));
        assertSame(near, VehicleEditorScene.pick(List.of(far, near), VehicleVector.ZERO, new VehicleVector(0, 0, 1), 32));
        assertNull(VehicleEditorScene.pick(List.of(far, near), VehicleVector.ZERO, new VehicleVector(0, 0, 1), 1.5));
        assertNull(VehicleEditorScene.pick(List.of(near), VehicleVector.ZERO, new VehicleVector(0, 0, -1), 32));
        assertNull(VehicleEditorScene.pick(List.of(), VehicleVector.ZERO, new VehicleVector(0, 0, 1), 32));
        assertNull(VehicleEditorScene.pick(List.of(near), VehicleVector.ZERO, VehicleVector.ZERO, 32));
        assertSame(near, VehicleEditorScene.pick(List.of(near), new VehicleVector(0, .25, 0), new VehicleVector(0, 0, 1), 32));
        var adjustment = new VehicleEditorScene.Handle("adjust", near.point(), near.label(), List.of(), near.ink(), new VehicleEditorScene.Adjust(1, -1));
        assertSame(adjustment, VehicleEditorScene.pick(List.of(adjustment), VehicleVector.ZERO, new VehicleVector(0, 0, 1), 1.5));
    }

    private static VehicleEditorScene.Handle handle(VehicleEditorScene scene, String key) {
        return scene.handles().stream().filter(handle -> handle.key().equals(key)).findFirst().orElseThrow();
    }
}
