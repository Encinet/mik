package org.encinet.mik.feature.world.vehicle;

import java.util.Map;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.MenuInputForm;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleSpatialEditorTest {
    @Test void choosingTheFrontKeepsTheActualPreviewMeshInPlaceAndUndoRestoresIt() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.edit(); var original = h.mesh().matrix.get();
            h.click("front"); h.click("front:positive-x");
            assertTrue(original.equals(h.mesh().matrix.get(), 1.0E-5f));
            h.click("undo"); assertTrue(original.equals(h.mesh().matrix.get(), 1.0E-5f));
            h.click("redo"); h.editor.save(h.player, true);
            assertEquals(new VehicleVector(2, 0, -1), new VehicleModelDraft(h.saved.get()).position("parts", 0, false));
            assertTrue(original.equals(h.mesh().matrix.get(), 1.0E-5f));
        }
    }

    @Test void scrollChangesTheHandleStepWithoutEditingTheModelAndForegroundMenusKeepTheirOwnInput() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.edit();
            assertFalse(h.editor.scroll(h.player, 1));
            h.click("section:SEATS"); h.click("component:0"); h.click("object-tool:position"); h.click("spatial");
            h.advance(7);
            assertTrue(h.editor.scroll(h.player, 1));
            h.editor.save(h.player, true); assertEquals(h.definition, h.saved.get());
            h.aimHandle("adjust:0:1");
            h.editor.place(h.player); h.editor.save(h.player, true);
            assertEquals(.25, h.saved.get().seats().getFirst().position().coordinateX(), 1.0E-8);
            h.editor.open(h.player); assertFalse(h.editor.scroll(h.player, 1));
        }
    }

    @Test void aWorldHandleMovesTheSelectedSeatAndDeletingItDisarmsItsOldHandle() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.edit(); h.click("section:SEATS"); h.click("component:0"); h.click("object-tool:position"); h.click("spatial");
            h.advance(7);
            h.aimHandle("adjust:0:1");
            var endpoint = h.feet.get().clone().add(0, 1.62, 3);
            assertTrue(h.editor.place(h.player));
            h.editor.save(h.player, true);
            assertEquals(.1, h.saved.get().seats().getFirst().position().coordinateX(), 1.0E-8);
            assertEquals(h.definition.seats().getFirst().exit(), h.saved.get().seats().getFirst().exit());
            h.editor.mutate(h.player, draft -> draft.seat("add", -1, new VehicleVector(1, 1, -1), true));
            h.editor.mutate(h.player, draft -> draft.remove("seats", 0));
            h.advance(2); h.lookAt(endpoint);
            h.editor.place(h.player);
            h.editor.save(h.player, true);
            assertEquals(new VehicleVector(1, 1, -1), h.saved.get().seats().getFirst().position());
        }
    }

    @Test void modelEntryStartsAnInteractiveFloatingMiniatureAndReleasesAllDisplays() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.editor.open(h.player); h.click("model:" + h.definition.id()); h.click("edit");
            var preview = h.editor.preview(h.player);
            assertTrue(preview.scale() < 1);
            assertTrue(preview.origin().getY() > h.feet.get().getY());
            assertTrue(preview.handles().stream().anyMatch(handle -> handle.key().equals("stage:SAVE")));
            for (var mesh : h.active()) {
                verify(mesh.display).setVisibleByDefault(false);
                verify(mesh.display, atLeastOnce()).setPersistent(false);
                verify(h.player).showEntity(h.plugin, mesh.display);
            }
            h.advance(7); h.aimHandle("section:SEATS"); assertTrue(h.editor.place(h.player));
            h.advance(2); h.aimHandle("SEATS:0"); assertTrue(h.editor.place(h.player));
            h.advance(2); h.aimHandle("adjust:0:1"); assertTrue(h.editor.place(h.player));
            h.editor.save(h.player, true);
            assertEquals(.1, h.saved.get().seats().getFirst().position().coordinateX(), 1.0E-8);
            h.editor.close(); assertTrue(h.active().isEmpty());
        }
    }

    @Test void createWizardClosesItsOwnForegroundAndEntersTheOperableMiniature() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.editor.newModel(h.player, VehicleModelEditor.Source.EMPTY); h.click("CAR");
            var values = new MenuInputForm.Values(Map.of("value", "new_car"));
            assertNull(h.form.get().validation().apply(h.player, values));
            h.form.get().onSubmit().accept(h.player, values);
            assertTrue(org.encinet.mik.shared.menu.FloatingMenus.current(h.player).isEmpty());
            double originalScale = h.editor.preview(h.player).scale();
            h.advance(7); h.aimHandle("stage:ZOOM_IN"); h.editor.place(h.player);
            assertTrue(h.editor.preview(h.player).scale() > originalScale);
            h.editor.mutate(h.player, draft -> draft.addParts(h.definition.parts()));
            h.editor.save(h.player, true);
            assertEquals("new_car", h.saved.get().id());
        }
    }

    @Test void vectorFormsKeepSeparateDraftFieldsValidateAtomicallyAndRejectAStaleSubmission() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.edit(); h.click("section:SEATS"); h.click("component:0"); h.click("object-tool:position"); h.click("manual");
            MenuInputForm form = h.form.get();
            assertEquals(java.util.List.of("x", "y", "z"), form.fields().stream().map(MenuInputForm.Field::key).toList());
            var bad = new MenuInputForm.Values(Map.of("x", "40", "y", "1", "z", "0"));
            assertNotNull(form.validation().apply(h.player, bad));
            var good = new MenuInputForm.Values(Map.of("x", "0.25", "y", "1.2", "z", "-0.5"));
            assertNull(form.validation().apply(h.player, good));
            form.onSubmit().accept(h.player, good);
            h.editor.save(h.player, true);
            assertEquals(new VehicleVector(.25, 1.2, -.5), h.saved.get().seats().getFirst().position());
            assertNotNull(form.validation().apply(h.player, good));
            h.editor.history(h.player, false);
            h.editor.save(h.player, true);
            assertEquals(h.definition.seats(), h.saved.get().seats());
        }
    }

    @Test void textOptionsOfferActualChoicesAndNeverAskForACommandFragment() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.edit(); h.click("section:PARTS"); h.click("component:0"); h.click("text");
            h.form.get().onSubmit().accept(h.player, new MenuInputForm.Values(Map.of("value", "Test label")));
            h.click("section:PARTS"); h.click("component:0"); h.click("option");
            h.click("property:ALIGNMENT"); h.click("choice:LEFT");
            h.editor.save(h.player, true); assertEquals("LEFT", h.saved.get().parts().getFirst().alignment());
            h.click("back"); h.click("property:OPACITY");
            assertEquals("255", h.form.get().fields().getFirst().initial());
            var opacity = new MenuInputForm.Values(Map.of("value", "128"));
            assertNull(h.form.get().validation().apply(h.player, opacity));
            h.form.get().onSubmit().accept(h.player, opacity);
            h.editor.save(h.player, true); assertEquals(128, Byte.toUnsignedInt(h.saved.get().parts().getFirst().textOpacity()));
            assertTrue(h.active().stream().filter(mesh -> mesh.display instanceof org.bukkit.entity.TextDisplay)
                    .anyMatch(mesh -> PlainTextComponentSerializer.plainText().serialize(mesh.text.get()).contains("Test label")));
        }
    }

    @Test void selectingTheCenterOfMassReplacesThePreviousSeatAsTheQuickPlacementTarget() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.edit(); h.click("section:SEATS"); h.click("component:0");
            h.click("section:parameters"); h.click("body"); h.click("center-of-mass");
            h.feet.set(new org.bukkit.Location(h.world, .25, 65, -.5));
            h.click("here"); h.editor.save(h.player, true);
            assertEquals(new VehicleVector(.25, 1, -.5), h.saved.get().centerOfMass());
            assertEquals(h.definition.seats(), h.saved.get().seats());
        }
    }
}
