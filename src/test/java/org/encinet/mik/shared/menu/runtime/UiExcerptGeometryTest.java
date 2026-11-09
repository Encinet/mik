package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.encinet.mik.shared.menu.FloatingMenuFieldOfView;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UiExcerptGeometryTest {
    @Test void expansionTracksActualWrappingAndIsAbsentFromThePublishedActionsWhenEverythingFits() {
        Component body = Component.text("园".repeat(80));
        var scene = scene(body, new UiSize(200, 400));
        var normal = SceneGeometry.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        var large = SceneGeometry.measure(scene, 1.8, FloatingMenuFieldOfView.DEFAULT, 1);
        var restored = SceneGeometry.measure(scene, 1, FloatingMenuFieldOfView.DEFAULT, 1);
        assertEquals(body, element(normal.frame(), "body").node().content().label());
        assertTrue(normal.definition().entries().values().stream().noneMatch(MenuScene.Entry::interactive));
        assertTrue(restored.definition().entries().values().stream().noneMatch(MenuScene.Entry::interactive));
        assertNotEquals(body, element(large.frame(), "body").node().content().label());
        assertEquals(1, large.definition().entries().values().stream().filter(MenuScene.Entry::interactive).count());
        assertTrue(large.measurements().get(element(large.frame(), "body").path()).text().lines() <= 8,
                "the reading budget may reserve fewer than the requested maximum lines");
        assertTrue(element(large.frame(), "read").y() >= element(large.frame(), "body").y()
                + element(large.frame(), "body").size().height() + 6 - 1e-6);
        assertEquals(element(large.frame(), "body").node().content().label(),
                large.labels().get(element(large.frame(), "body").path()));
        assertEquals(body,entry(large.definition(), "body").tooltip().text());
        assertDisjoint(large.frame());
    }

    @Test void aShorterHostReservesRoomForTheExpansionActionAndKeepsTheSavedTextIntact() {
        Component body = Component.text("第一段\n第二段\n第三段\n".repeat(100));
        for (double typography : new double[] {.7, 1, 1.8}) {
            var scene = scene(body, new UiSize(200, 140));
            var geometry = SceneGeometry.measure(scene, typography, FloatingMenuFieldOfView.DEFAULT, 1);
            assertNotNull(element(geometry.frame(), "read"));
            assertTrue(geometry.frame().size().height() <= 140 + 1e-6);
            assertEquals(body, entry(scene, "body").label());
            assertDisjoint(geometry.frame());
        }
        // Default keys are safe: the reading and expansion slots have their own stable scopes.
        assertDoesNotThrow(() -> SceneGeometry.measure(MenuScene.screen("anonymous").content(Ui.excerpt(
                Ui.text(body).maxLines(8).build(), Ui.button(Component.text("Read all")).primary((_,_) -> {}).build())).build(),
                1, FloatingMenuFieldOfView.DEFAULT, 1));
    }

    private static MenuScene scene(Component body, UiSize viewport) {
        return MenuScene.screen("preview").viewport(viewport).content(Ui.excerpt(
                Ui.text(body).key("body").textWidth(112).maxLines(8).build(),
                Ui.button(Component.text("查看全文")).key("read").primary((_,_) -> {}).build())).build();
    }
}
