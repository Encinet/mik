package org.encinet.mik.shell.menu;

import org.encinet.mik.shared.i18n.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguageMenuNavigationTest {
    @Test
    void wheelStepsRemainContinuousAcrossCatalogBoundaries() {
        int count = Language.values().length;
        var state = new LanguageMenu.MenuState(count - 1);
        state = LanguageMenu.rotate(state, 1);
        assertEquals(count, state.rotation());
        assertEquals(0, Math.floorMod(state.rotation(), count));
        state = LanguageMenu.rotate(new LanguageMenu.MenuState(0), -1);
        assertEquals(-1, state.rotation());
        assertEquals(count - 1, Math.floorMod(state.rotation(), count));
    }

    @Test
    void selectingAChoiceUsesTheNearestTurnWithoutResettingTheOrbit() {
        int count = Language.values().length;
        var state = new LanguageMenu.MenuState(count * 3L + 1);
        assertEquals(count * 3L - 1, LanguageMenu.focus(state, count - 1).rotation());
        assertEquals(count * 3L + 2, LanguageMenu.focus(state, 2).rotation());
        assertEquals(state, LanguageMenu.focus(state, 1));
    }
}
