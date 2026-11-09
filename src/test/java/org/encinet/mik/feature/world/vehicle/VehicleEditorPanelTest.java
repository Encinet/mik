package org.encinet.mik.feature.world.vehicle;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.shared.menu.FloatingMenuInteraction;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.UiSceneTestSupport;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleEditorPanelTest {
    @Test void overviewLeadsToSeatValuesAndSaveHistoryStayOnTheSelectedTool() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.edit();
            assertTrue(FloatingMenus.current(h.player).isPresent());
            h.click("edit:SEATS"); h.click("component:0"); h.click("value:position");
            assertFalse(UiSceneTestSupport.entry(h.scene, "undo").enabled());
            var staleSave = UiSceneTestSupport.entry(h.scene, "save").trigger(FloatingMenuInteraction.PRIMARY);
            h.click("nudge:0:1");
            var frame = SceneGeometryTestSupport.measure(h.scene, 1);
            var minus = UiSceneTestSupport.element(frame, "nudge:0:-1");
            var value = UiSceneTestSupport.element(frame, "value:0");
            var plus = UiSceneTestSupport.element(frame, "nudge:0:1");
            assertTrue(minus.x() < value.x() && value.x() < plus.x());
            assertTrue(PlainTextComponentSerializer.plainText().serialize(value.node().content().label()).contains("0.1"));
            assertNotNull(UiSceneTestSupport.element(frame, "manual"));
            assertNotNull(UiSceneTestSupport.element(frame, "step"));
            staleSave.execute(h.player, null, FloatingMenuInteraction.PRIMARY);
            assertNull(h.saved.get());
            assertNotNull(h.editor.preview(h.player));

            h.click("apply");
            assertEquals(.1, h.saved.get().seats().getFirst().position().coordinateX(), 1.0E-8);
            assertNotNull(UiSceneTestSupport.entry(h.scene, "manual"));
            h.click("undo"); h.click("apply");
            assertEquals(h.definition.seats(), h.saved.get().seats());
            h.click("redo"); h.click("save");
            assertEquals(.1, h.saved.get().seats().getFirst().position().coordinateX(), 1.0E-8);
            assertNull(h.editor.preview(h.player));
            assertNotNull(UiSceneTestSupport.entry(h.scene, "create"));
        }
    }

    @Test void returningFromWorldEditingAndCancellingPlacementRestoreTheSameExitPanel() throws Exception {
        try (var h = new VehicleEditorHarness()) {
            h.edit(); h.click("edit:SEATS"); h.click("component:0"); h.click("value:exit"); h.click("spatial");
            h.advance(7); when(h.player.isSneaking()).thenReturn(true);
            assertTrue(h.editor.place(h.player));
            assertTrue(FloatingMenus.current(h.player).isPresent());
            assertTrue(UiSceneTestSupport.entry(h.scene, "object-tool:exit").selected());
            h.advance(2); assertFalse(h.editor.place(h.player));
            assertFalse(h.editor.scroll(h.player, 1));
            assertTrue(h.editor.preview(h.player).handles().stream().noneMatch(handle -> handle.action() instanceof VehicleEditorScene.Tool));

            when(h.player.isSneaking()).thenReturn(false);
            h.click("look"); h.advance(7); when(h.player.isSneaking()).thenReturn(true);
            assertTrue(h.editor.place(h.player));
            assertTrue(FloatingMenus.current(h.player).isPresent());
            assertTrue(UiSceneTestSupport.entry(h.scene, "object-tool:exit").selected());
            h.click("apply"); assertEquals(h.definition, h.saved.get());
        }
    }
}
