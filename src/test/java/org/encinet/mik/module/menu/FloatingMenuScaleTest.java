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
        assertEquals(0.75, FloatingMenuScale.SMALL.textFactor(), 0.0001);
        assertEquals(1.00, FloatingMenuScale.NORMAL.textFactor(), 0.0001);
        assertEquals(1.40, FloatingMenuScale.LARGE.textFactor(), 0.0001);
        assertEquals(75, FloatingMenuScale.SMALL.textPercent());
        assertEquals(100, FloatingMenuScale.NORMAL.textPercent());
        assertEquals(140, FloatingMenuScale.LARGE.textPercent());
    }

    @Test
    void typographyCorrectionProducesTheRequestedEffectiveGlyphSize() {
        for (FloatingMenuScale option : FloatingMenuScale.values()) {
            assertEquals(option.textFactor(),
                    option.factor() * option.typographyFactor(), 0.0001);
        }
        assertTrue(FloatingMenuScale.LARGE.textFactor()
                / FloatingMenuScale.SMALL.textFactor() > 1.8);
    }

    @Test
    void wheelStepsAreClampedInsteadOfWrapping() {
        assertEquals(FloatingMenuScale.LARGE, FloatingMenuScale.NORMAL.step(1));
        assertEquals(FloatingMenuScale.SMALL, FloatingMenuScale.NORMAL.step(-1));
        assertEquals(FloatingMenuScale.LARGE, FloatingMenuScale.LARGE.step(1));
        assertEquals(FloatingMenuScale.SMALL, FloatingMenuScale.SMALL.step(-1));
    }
}
