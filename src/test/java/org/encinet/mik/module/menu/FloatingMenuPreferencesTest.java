package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuPreferencesTest {
    @Test
    void textChoicesAreOrderedAndPersistedIndependently() {
        assertEquals(9, FloatingMenuTextScale.values().length);
        assertEquals(70, FloatingMenuTextScale.MINIMUM.percent());
        assertEquals(180, FloatingMenuTextScale.MAXIMUM.percent());
        for (FloatingMenuTextScale option : FloatingMenuTextScale.values()) {
            assertEquals(option, FloatingMenuTextScale.fromId(option.id()));
            assertEquals(option.percent(), Math.round(option.factor() * 100));
            if (option != FloatingMenuTextScale.MAXIMUM) assertTrue(option.factor() < option.step(1).factor());
        }
        assertEquals(FloatingMenuTextScale.NORMAL, FloatingMenuTextScale.fromId(null));
        assertEquals(FloatingMenuTextScale.NORMAL, FloatingMenuTextScale.fromId("invalid"));
        assertEquals(FloatingMenuTextScale.MINIMUM, FloatingMenuTextScale.MINIMUM.step(-1));
        assertEquals(FloatingMenuTextScale.MAXIMUM, FloatingMenuTextScale.MAXIMUM.step(1));
        assertEquals(FloatingMenuTextScale.NORMAL, FloatingMenuTextScale.NORMAL.step(0));
    }

    @Test
    void changingEitherDimensionPreservesTheOtherChoice() {
        var original = new FloatingMenuPreferences(FloatingMenuScale.MINIMUM, FloatingMenuTextScale.MAXIMUM);
        var largerMenu = original.withLayout(FloatingMenuScale.MAXIMUM);
        assertEquals(original.text(), largerMenu.text());
        assertEquals(FloatingMenuScale.MAXIMUM, largerMenu.layout());
        var smallerText = original.withText(FloatingMenuTextScale.MINIMUM);
        assertEquals(original.layout(), smallerText.layout());
        assertEquals(FloatingMenuTextScale.MINIMUM, smallerText.text());
        assertEquals(FloatingMenuScale.NORMAL, FloatingMenuPreferences.DEFAULT.layout());
        assertEquals(FloatingMenuTextScale.NORMAL, FloatingMenuPreferences.DEFAULT.text());
    }

    @Test
    void invalidPreferencesCannotEnterRendering() {
        assertThrows(NullPointerException.class, () -> new FloatingMenuPreferences(null, FloatingMenuTextScale.NORMAL));
        assertThrows(NullPointerException.class, () -> new FloatingMenuPreferences(FloatingMenuScale.NORMAL, null));
    }
}
