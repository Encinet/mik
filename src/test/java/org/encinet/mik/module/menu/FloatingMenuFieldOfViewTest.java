package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuFieldOfViewTest {
    @Test
    void presetsAndExactValuesCoverTheSupportedRangeWithoutWrapping() {
        assertEquals(9, FloatingMenuFieldOfView.PRESETS.size());
        assertEquals(30, FloatingMenuFieldOfView.PRESETS.getFirst().degrees());
        assertEquals(110, FloatingMenuFieldOfView.PRESETS.getLast().degrees());
        assertEquals(new FloatingMenuFieldOfView(73), FloatingMenuFieldOfView.fromDegrees(73));
        assertEquals(new FloatingMenuFieldOfView(78), new FloatingMenuFieldOfView(73).step(1));
        assertEquals(new FloatingMenuFieldOfView(68), new FloatingMenuFieldOfView(73).step(-1));
        assertEquals(new FloatingMenuFieldOfView(30), new FloatingMenuFieldOfView(30).step(-1));
        assertEquals(new FloatingMenuFieldOfView(110), new FloatingMenuFieldOfView(110).step(1));
        assertEquals(FloatingMenuFieldOfView.DEFAULT, FloatingMenuFieldOfView.DEFAULT.step(0));
    }

    @Test
    void invalidStoredValuesFallBackButInvalidUserValuesAreRejected() {
        for (int degrees : new int[]{Integer.MIN_VALUE, -1, 0, 29, 111, Integer.MAX_VALUE}) {
            assertEquals(FloatingMenuFieldOfView.DEFAULT, FloatingMenuFieldOfView.fromDegrees(degrees));
            assertThrows(IllegalArgumentException.class, () -> new FloatingMenuFieldOfView(degrees));
        }
    }

    @Test
    void everySupportedFovHasItsOwnVerticalEnvelopeIncludingValuesAboveSeventy() {
        double previous = 0;
        for (int degrees = 30; degrees <= 110; degrees++) {
            var reading = new FloatingMenuFieldOfView(degrees).readingFraming();
            assertTrue(reading.verticalHalfAngleDegrees() >= previous);
            assertTrue(reading.verticalHalfAngleDegrees() <= degrees / 2.0);
            assertTrue(reading.verticalHalfAngleDegrees() <= 55);
            previous = reading.verticalHalfAngleDegrees();
        }
        assertEquals(35, FloatingMenuFieldOfView.DEFAULT.readingFraming().verticalHalfAngleDegrees());
        assertEquals(55, new FloatingMenuFieldOfView(110).readingFraming().verticalHalfAngleDegrees());
        assertEquals(1, FloatingMenuFieldOfView.DEFAULT.projectionFactor(), 1.0E-9);
        assertTrue(new FloatingMenuFieldOfView(110).projectionFactor() > 2);
    }

    @Test
    void changingFovDoesNotChangeLayoutOrTextAndSizeResetRetainsIt() {
        var original = new FloatingMenuPreferences(FloatingMenuScale.MINIMUM, FloatingMenuTextScale.MAXIMUM)
                .withFieldOfView(new FloatingMenuFieldOfView(73));
        assertEquals(new FloatingMenuFieldOfView(73), original.withText(FloatingMenuTextScale.NORMAL).fieldOfView());
        assertEquals(new FloatingMenuFieldOfView(73), original.withLayout(FloatingMenuScale.NORMAL).fieldOfView());
        var reset = original.withLayout(FloatingMenuScale.NORMAL).withText(FloatingMenuTextScale.NORMAL);
        assertEquals(new FloatingMenuFieldOfView(73), reset.fieldOfView());
        var changed = original.withFieldOfView(new FloatingMenuFieldOfView(30));
        assertEquals(original.layout(), changed.layout());
        assertEquals(original.text(), changed.text());
        assertEquals(original.typographyFactor(), changed.typographyFactor());
        assertThrows(NullPointerException.class, () -> original.withFieldOfView(null));
    }
}
