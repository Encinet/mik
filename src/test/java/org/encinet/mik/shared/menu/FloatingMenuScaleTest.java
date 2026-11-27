package org.encinet.mik.shared.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuScaleTest {

    @Test
    void customSizesUseTheDefaultScaleAndRejectValuesOutsideTheBounds() {
        assertEquals(1.456, new FloatingMenuScale(112).factor(), 0.0001);
        assertThrows(IllegalArgumentException.class, () -> new FloatingMenuScale(FloatingMenuScale.MIN_PERCENT - 1));
        assertThrows(IllegalArgumentException.class, () -> new FloatingMenuScale(FloatingMenuScale.MAX_PERCENT + 1));
    }

    @Test
    void displayedSizesUseTheFormerLargestSizeAsTheNewDefault() {
        assertEquals(100, FloatingMenuScale.DEFAULT.percent());
        assertEquals(1.30, FloatingMenuScale.DEFAULT.factor(), 0.0001);
        assertEquals(7, FloatingMenuScale.PRESETS.size());
        assertEquals(0.70, FloatingMenuScale.COMPACT.factor());
        assertEquals(77, FloatingMenuScale.MEDIUM.percent());
        assertEquals(1.00, FloatingMenuScale.MEDIUM.factor());
        for (int percent = FloatingMenuScale.MIN_PERCENT; percent < FloatingMenuScale.MAX_PERCENT; percent++) {
            assertTrue(new FloatingMenuScale(percent).factor() < new FloatingMenuScale(percent + 1).factor());
        }
    }

    @Test
    void typographyIsAnIndependentMultiplierInsteadOfTheInverseMenuSize() {
        for (FloatingMenuScale layout : FloatingMenuScale.PRESETS) {
            for (FloatingMenuTextScale text : FloatingMenuTextScale.PRESETS) {
                var preferences = new FloatingMenuPreferences(layout, text);
                assertEquals(text.factor(), preferences.typographyFactor(), 0.0001);
            }
        }
    }

    @Test
    void wheelStepsAreClampedInsteadOfWrapping() {
        assertEquals(new FloatingMenuScale(82), FloatingMenuScale.MEDIUM.step(1));
        assertEquals(new FloatingMenuScale(72), FloatingMenuScale.MEDIUM.step(-1));
        assertEquals(FloatingMenuScale.MAXIMUM, FloatingMenuScale.MAXIMUM.step(1));
        assertEquals(FloatingMenuScale.MINIMUM, FloatingMenuScale.MINIMUM.step(-1));
        assertEquals(FloatingMenuScale.MAXIMUM, new FloatingMenuScale(FloatingMenuScale.MAX_PERCENT - 1).step(1));
        assertEquals(FloatingMenuScale.MINIMUM, new FloatingMenuScale(FloatingMenuScale.MIN_PERCENT + 1).step(-1));
        assertEquals(FloatingMenuScale.MEDIUM, FloatingMenuScale.MEDIUM.step(0));
    }
}
