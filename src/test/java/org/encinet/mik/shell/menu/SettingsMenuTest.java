package org.encinet.mik.shell.menu;

import org.encinet.mik.shared.menu.MenuPanel;
import org.encinet.mik.shared.menu.MenuScene;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SettingsMenuTest {
    @Test void changingCategoryClosesItsDraftBeforeMountingTheNextPanelAndRejectsStaleRedraws() {
        var selection = new SettingsMenu.Selection();
        var changes = new AtomicInteger();
        var closed = new AtomicInteger();
        var oldRedraw = new AtomicReference<Runnable>();
        assertTrue(selection.select(SettingsMenu.Category.NAME_TAG, redraw -> {
            oldRedraw.set(redraw);
            return new MenuPanel() {
                @Override public MenuScene render() { return MenuScene.screen("nametag").build(); }
                @Override public void close() { closed.incrementAndGet(); redraw.run(); }
            };
        }, changes::incrementAndGet));
        oldRedraw.get().run();
        assertEquals(1, changes.get());

        assertTrue(selection.select(SettingsMenu.Category.PVP, _ -> {
            assertEquals(1, closed.get(), "the old chat receiver must be released before the new pane is created");
            return () -> MenuScene.screen("pvp").build();
        }, changes::incrementAndGet));
        oldRedraw.get().run();
        assertEquals(1, changes.get(), "saving or loading the previous pane must not redraw its replacement");
        assertEquals(SettingsMenu.Category.PVP, selection.category);
        assertFalse(selection.select(SettingsMenu.Category.PVP, _ -> { fail("reselecting must retain the current pane"); return null; },
                changes::incrementAndGet));
        selection.close();
        selection.close();
        oldRedraw.get().run();
        assertNull(selection.panel);
        assertEquals(1, closed.get());
        assertEquals(1, changes.get());
    }
}
