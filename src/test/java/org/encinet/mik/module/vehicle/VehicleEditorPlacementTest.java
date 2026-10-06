package org.encinet.mik.module.vehicle;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleEditorPlacementTest {
    @Test void armingClickCannotImmediatelyPlaceAndTheToolExpires() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var placement = new VehicleEditorPlacement(draft, draft.revision(), List.of("seat", "position", "0", "look"), 6, 100);
        assertFalse(placement.ready(0));
        assertFalse(placement.ready(5));
        assertTrue(placement.ready(6));
        assertTrue(placement.current(draft, 99));
        assertFalse(placement.current(draft, 100));
    }

    @Test void switchingEditingUndoingAndSavingInvalidateTheArmedTarget() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var placement = new VehicleEditorPlacement(draft, draft.revision(), List.of("part", "position", "0", "look"), 6, 100);
        assertFalse(placement.current(new VehicleModelDraft(draft.validate()), 10));
        assertFalse(placement.current(null, 10));
        draft.set("mass", "2200");
        assertFalse(placement.current(draft, 10));
        assertTrue(draft.undo());
        assertFalse(placement.current(draft, 10));
        var fresh = new VehicleEditorPlacement(draft, draft.revision(), List.of("part", "position", "0", "look"), 6, 100);
        draft.saved(draft.validate());
        assertFalse(fresh.current(draft, 10));
    }

    @Test void armedCommandIsAnImmutableSnapshot() {
        var draft = new VehicleModelDraft(VehicleFixtures.definition(VehicleDefinition.Kind.CAR));
        var command = new ArrayList<>(List.of("seat", "exit", "0", "look"));
        var placement = new VehicleEditorPlacement(draft, draft.revision(), command, 6, 100);
        command.set(2, "1");
        assertEquals("0", placement.command().get(2));
        assertThrows(UnsupportedOperationException.class, () -> placement.command().clear());
    }
}
