package org.encinet.mik.module.vehicle;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorTransformTest {
    @Test void relativeRotationUsesTheExistingConventionAndPreservesPositionAndPayload() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.changePart(0, "position", "1 2 3");
        draft.changePart(0, "scale", "2 0.5 1.5");
        var original = draft.parts().getFirst();
        VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.ROTATE, 0, 1, 1, 15);
        var expected = new Matrix4f(original.transform()).rotateXYZ(0, (float) -Math.toRadians(15), 0);
        var adjusted = draft.parts().getFirst();
        assertTrue(expected.equals(adjusted.transform(), 1.0E-6f));
        assertEquals(new VehicleVector(1, 2, 3), draft.position("parts", 0, false));
        assertEquals(original.payload(), adjusted.payload());
        assertEquals(original.animation(), adjusted.animation());
        assertTrue(draft.undo());
        assertEquals(original, draft.parts().getFirst());
        assertTrue(draft.redo());
        assertEquals(adjusted, draft.parts().getFirst());
    }

    @Test void scalingSupportsIndividualAxesAndUniformGrowShrinkWithoutMovingThePart() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.changePart(0, "position", "1 2 3");
        var original = draft.parts().getFirst();
        VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.SCALE, 0, 0, 1, 1.25);
        assertTrue(new Matrix4f(original.transform()).scale(1.25f, 1, 1).equals(draft.parts().getFirst().transform(), 1.0E-6f));
        VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.SCALE, 0, 0, -1, 1.25);
        VehicleEditorTransform.scaleAll(draft, 0, 1, 1.05);
        VehicleEditorTransform.scaleAll(draft, 0, -1, 1.05);
        assertTrue(original.transform().equals(draft.parts().getFirst().transform(), 1.0E-5f));
        assertEquals(new VehicleVector(1, 2, 3), draft.position("parts", 0, false));
    }

    @Test void colliderSizeButtonsAdjustFullDimensionsWhileKeepingTheCenterFixed() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        draft.size(0, new VehicleVector(2, 2, 4));
        var original = draft.collider(0);
        VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.SIZE, 0, 0, 1, 0.25);
        assertEquals(new VehicleVector(2.25, 2, 4), VehicleEditorTransform.value(draft, VehicleEditorTransform.Mode.SIZE, 0));
        assertEquals(original.center(), draft.collider(0).center());
        assertEquals(1.125, draft.collider(0).halfSize().coordinateX());
        assertTrue(draft.undo());
        assertEquals(original, draft.collider(0));
    }

    @Test void invalidAndOutOfBoundsAdjustmentsDoNotChangeTheDraftOrItsHistory() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var original = draft.validate();
        for (double step : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.ROTATE, 0, 0, 1, step));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.SCALE, 0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.SCALE, 0, 0, 1, 1000));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.SIZE, 0, 0, -1, 100));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.SIZE, 0, 0, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.ROTATE, 0, 3, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorTransform.adjust(draft, VehicleEditorTransform.Mode.ROTATE, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> VehicleEditorTransform.scaleAll(draft, 0, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> draft.size(0, new VehicleVector(0, 1, 1)));
        assertEquals(original, draft.validate());
        assertEquals(0, draft.revision());
        assertFalse(draft.canUndo());
    }

    @Test void eachTransformModeHasItsOwnStableStepCycle() {
        for (var mode : VehicleEditorTransform.Mode.values()) {
            double initial = VehicleEditorTransform.defaultStep(mode);
            double second = VehicleEditorTransform.nextStep(mode, initial);
            double third = VehicleEditorTransform.nextStep(mode, second);
            assertNotEquals(initial, second);
            assertNotEquals(second, third);
            assertEquals(initial, VehicleEditorTransform.nextStep(mode, third));
        }
    }
}
