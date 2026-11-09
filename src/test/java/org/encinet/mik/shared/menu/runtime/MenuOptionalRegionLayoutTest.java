package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuOptionalRegionLayoutTest {
    @Test void anEmptyGridDoesNotCreateAToolbarFromOnlyItsConfiguredGaps() {
        var grid = Ui.grid(3).gap(UiLength.ui(8)).build();
        var frame = measure(UiTemplates.toolbar("tools", grid), 240, 1);
        assertEquals(UiSize.ZERO, frame.size());
        assertTrue(frame.panels().isEmpty());
    }

    @Test void emptyTitleToolsHaveTheSameLayoutAsAbsentToolsAcrossNarrowHostsAndTypography() {
        for (double scale : new double[]{.7, 1, 1.8}) for (int width : new int[]{160, 240, 480}) {
            var title = Ui.heading(Component.text("Community identity and membership")).key("title").build();
            var reference = measure(UiTemplates.titleBar(title, null), width, scale);
            for (UiNode empty : List.of(Ui.row().build(), UiTemplates.window("empty", Ui.column().build()))) {
                var frame = measure(UiTemplates.titleBar(title, empty), width, scale);
                assertSamePlacement(reference, frame, "title");
            }
        }
    }

    @Test void actionRowsTreatEmptyLeadingOrTrailingRegionsAsAbsentWithoutMovingTheVisibleControl() {
        for (double scale : new double[]{.7, 1, 1.8}) for (int width : new int[]{160, 240, 480}) {
            var content = Ui.text(Component.text("Changes are ready to save")).key("content").build();
            var action = Ui.button(Component.text("Save")).key("save").build();
            var empty = Ui.column().build();
            var leading = measure(UiTemplates.actionRow(content, null), width, scale);
            var trailing = measure(UiTemplates.actionRow(null, action), width, scale);
            assertSamePlacement(leading, measure(UiTemplates.actionRow(content, empty), width, scale), "content");
            assertSamePlacement(trailing, measure(UiTemplates.actionRow(empty, action), width, scale), "save");
        }
    }

    private static UiFrame measure(UiNode content, int width, double scale) {
        return SceneGeometryTestSupport.measure(MenuScene.screen("optional").viewport(new UiSize(width, 600)).content(content).build(), scale);
    }

    private static void assertSamePlacement(UiFrame reference, UiFrame current, String key) {
        assertEquals(reference.size(), current.size());
        assertEquals(element(reference, key).x(), element(current, key).x());
        assertEquals(element(reference, key).y(), element(current, key).y());
        assertDisjoint(current);
    }
}
