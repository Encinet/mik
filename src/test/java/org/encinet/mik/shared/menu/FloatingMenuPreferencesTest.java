package org.encinet.mik.shared.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FloatingMenuPreferencesTest {
    @Test
    void customTextSizeAcceptsWholePercentagesAndRejectsValuesOutsideTheBounds() {
        assertEquals(1.13, new FloatingMenuTextScale(113).factor(), 0.0001);
        assertThrows(IllegalArgumentException.class, () -> new FloatingMenuTextScale(FloatingMenuTextScale.MIN_PERCENT - 1));
        assertThrows(IllegalArgumentException.class, () -> new FloatingMenuTextScale(FloatingMenuTextScale.MAX_PERCENT + 1));
        assertEquals(new FloatingMenuTextScale(118), new FloatingMenuTextScale(113).step(1));
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
        assertEquals(FloatingMenuScale.DEFAULT, FloatingMenuPreferences.DEFAULT.layout());
        assertEquals(FloatingMenuTextScale.NORMAL, FloatingMenuPreferences.DEFAULT.text());
    }

    @Test
    void invalidPreferencesCannotEnterRendering() {
        assertThrows(NullPointerException.class, () -> new FloatingMenuPreferences(null, FloatingMenuTextScale.NORMAL));
        assertThrows(NullPointerException.class, () -> new FloatingMenuPreferences(FloatingMenuScale.DEFAULT, null));
    }
}
