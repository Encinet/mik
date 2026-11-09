package org.encinet.mik.shared.menu.runtime;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MenuMeasurementReuseTest {
    @Test void parentAndCompanionShareTheBoundedCacheAndARefreshedDocumentKeepsItsNewOwnersNavigation() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0,64,0);
        var view = new MenuPresentation();
        var oldPosition = new java.util.concurrent.atomic.AtomicReference<>(UiReadingPosition.START);
        var newPosition = new java.util.concurrent.atomic.AtomicReference<>(UiReadingPosition.START);
        java.util.function.Function<java.util.concurrent.atomic.AtomicReference<UiReadingPosition>,MenuScene> scene = owner -> {
            var document = Ui.document().blocks(java.util.List.of(Ui.text(Component.text("Aé界👩‍🔬".repeat(400)))
                            .key("body").textWidth(320).build())).position(owner.get(), owner::set).build();
            return MenuScene.screen("cached-reader").content(Ui.button(Component.text("Read")).key("source").build())
                    .popover(MenuPopover.attached(UiKey.named("source"), Component.text("Document"), document, Component.text("Close"))).build();
        };
        present(view, eye, scene.apply(oldPosition), 1);
        long hits = view.measurementStats().hits();
        var refreshed = present(view, eye, scene.apply(newPosition), 1);
        assertTrue(view.measurementStats().hits() > hits);
        var page = refreshed.geometry().popover().content().frame().pagination().getFirst();
        assertTrue(page.page().hasNext());
        page.selectPage().accept(1);
        assertEquals(UiReadingPosition.START, oldPosition.get());
        assertTrue(newPosition.get().offset() > 0);
        assertTrue(view.measurementStats().weight() <= org.encinet.mik.shared.menu.runtime.layout.MenuTextMeasurementCache.WEIGHT_BUDGET);
        view.clearMeasurements();
        assertEquals(0, view.measurementStats().entries());
    }

    @Test void repeatedGeometryUsesFreshActionsAndDisabledReasonsInsteadOfRetainingTheOldNode() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 64, 0);
        var view = new MenuPresentation();
        var oldAction = new AtomicInteger();
        var newAction = new AtomicInteger();
        var first = present(view, eye, scene(oldAction, null), 1);
        long hits = view.measurementStats().hits();
        var disabled = present(view, eye, scene(newAction, Component.text("Permission changed")), 1);
        assertTrue(view.measurementStats().hits() > hits);
        var entry = disabled.geometry().definition().entries().values().iterator().next();
        assertFalse(entry.enabled());
        assertEquals(Component.text("Permission changed"), entry.disabledReason());
        var enabled = present(view, eye, scene(newAction, null), 1);
        var action = enabled.geometry().definition().entries().values().iterator().next();
        action.trigger(FloatingMenuInteraction.PRIMARY).execute(null, null, FloatingMenuInteraction.PRIMARY);
        assertEquals(0, oldAction.get());
        assertEquals(1, newAction.get());
        assertEquals(first.geometry().frame().size(), enabled.geometry().frame().size());
    }

    @Test void changedTypographyAndContentCannotReuseAnOldTextSize() {
        var world = new FloatingMenuTestWorld();
        var eye = new Location(world.world(), 0, 64, 0);
        var view = new MenuPresentation();
        var text = MenuScene.screen("metrics").content(Ui.text(Component.text("Short")).key("value").build()).build();
        var first = present(view, eye, text, 1);
        long misses = view.measurementStats().misses();
        var larger = present(view, eye, text, 1.8);
        assertTrue(view.measurementStats().misses() > misses);
        assertTrue(larger.geometry().frame().size().height() > first.geometry().frame().size().height());
        var translated = text.toBuilder().content(Ui.text(Component.text("A much longer translated value")).key("value").build()).build();
        var changed = present(view, eye, translated, 1);
        assertTrue(changed.geometry().frame().size().width() > first.geometry().frame().size().width());
        view.clearMeasurements();
        assertEquals(0, view.measurementStats().entries());
    }

    private static MenuPresentation.Frame present(MenuPresentation view, Location eye, MenuScene scene, double text) {
        return view.prepare(null, eye, new Vector(0, 0, 1), scene, text, 1, FloatingMenuFieldOfView.DEFAULT, UiPalette.DEFAULT);
    }

    private static MenuScene scene(AtomicInteger counter, Component disabled) {
        var button = Ui.button(Component.text("Save")).key("save").primary((_, _) -> counter.incrementAndGet());
        if (disabled != null) button.disabled(disabled);
        return MenuScene.screen("reuse").content(button.build()).build();
    }
}
