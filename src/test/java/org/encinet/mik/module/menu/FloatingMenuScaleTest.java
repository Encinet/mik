package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuScaleTest {

    @Test
    void persistedIdsRoundTripAndUnknownValuesStaySafe() {
        for (FloatingMenuScale option : FloatingMenuScale.values()) {
            assertEquals(option, FloatingMenuScale.fromId(option.id()));
        }
        assertEquals(FloatingMenuScale.NORMAL, FloatingMenuScale.fromId(null));
        assertEquals(FloatingMenuScale.NORMAL, FloatingMenuScale.fromId("future-value"));
    }

    @Test
    void optionsAreStrictlyOrderedAroundTheDefault() {
        assertTrue(FloatingMenuScale.SMALL.factor() < FloatingMenuScale.NORMAL.factor());
        assertEquals(1.0, FloatingMenuScale.NORMAL.factor(), 0.0001);
        assertTrue(FloatingMenuScale.LARGE.factor() > FloatingMenuScale.NORMAL.factor());
        assertEquals(7, FloatingMenuScale.values().length);
        assertEquals(70, FloatingMenuScale.MINIMUM.percent());
        assertEquals(130, FloatingMenuScale.MAXIMUM.percent());
        for (FloatingMenuScale option : FloatingMenuScale.values()) {
            assertEquals(option.percent(), Math.round(option.factor() * 100));
            if (option != FloatingMenuScale.MAXIMUM) assertTrue(option.factor() < option.step(1).factor());
        }
    }

    @Test
    void typographyIsAnIndependentMultiplierInsteadOfTheInverseMenuSize() {
        for (FloatingMenuScale layout : FloatingMenuScale.values()) {
            for (FloatingMenuTextScale text : FloatingMenuTextScale.values()) {
                var preferences = new FloatingMenuPreferences(layout, text);
                assertEquals(text.factor(), preferences.typographyFactor(), 0.0001);
            }
        }
    }

    @Test
    void wheelStepsAreClampedInsteadOfWrapping() {
        assertEquals(FloatingMenuScale.LARGE, FloatingMenuScale.NORMAL.step(1));
        assertEquals(FloatingMenuScale.SMALL, FloatingMenuScale.NORMAL.step(-1));
        assertEquals(FloatingMenuScale.MAXIMUM, FloatingMenuScale.MAXIMUM.step(1));
        assertEquals(FloatingMenuScale.MINIMUM, FloatingMenuScale.MINIMUM.step(-1));
        assertEquals(FloatingMenuScale.NORMAL, FloatingMenuScale.NORMAL.step(0));
    }
}
