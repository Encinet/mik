package org.encinet.mik.shared.menu.runtime.motion;

import org.encinet.mik.shared.menu.FloatingMenuEasing;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuTransitionTest {
    @Test void anOvershootingCurveReversesFromTheActualClampedValue() {
        var opacity = new MenuTransition(0);
        opacity.to(1, 0, 10, FloatingMenuEasing.BACK_OUT);
        assertEquals(1, opacity.value(5));
        opacity.to(0, 5, 4, FloatingMenuEasing.CUBIC_OUT);
        assertEquals(1, opacity.value(5));
        assertTrue(opacity.value(6) < 1);
        assertThrows(IllegalArgumentException.class, () -> opacity.to(2, 6, 4, FloatingMenuEasing.CUBIC_OUT));
    }

    @Test void closingDuringOpeningStartsFromTheCurrentlyVisibleValue() {
        var reveal = new MenuTransition(0);
        reveal.to(1, 0, 6, FloatingMenuEasing.CUBIC_OUT);
        double opening = reveal.value(2);
        assertTrue(opening > 0 && opening < 1);
        reveal.to(0, 2, 4, FloatingMenuEasing.CUBIC_OUT);
        assertEquals(opening, reveal.value(2));
        assertTrue(reveal.value(3) < opening);
        assertEquals(0, reveal.value(6));
    }

    @Test void rapidReversalsKeepOnlyTheNewestTargetWithoutJumpingOrQueuing() {
        var expansion = new MenuTransition(0);
        expansion.to(1, 0, 8, FloatingMenuEasing.CUBIC_OUT);
        for (int tick = 1; tick < 20; tick++) {
            double previous = expansion.value(tick);
            expansion.to(tick % 2, tick, 8, FloatingMenuEasing.CUBIC_OUT);
            assertEquals(previous, expansion.value(tick), 1e-12);
        }
        expansion.to(1, 20, 8, FloatingMenuEasing.CUBIC_OUT);
        assertEquals(1, expansion.value(28));
        assertEquals(1, expansion.value(1000));
    }

    @Test void repeatedRefreshesDoNotRestartAnUnchangedTarget() {
        var progress = new MenuTransition(0);
        progress.to(1, 0, 6, FloatingMenuEasing.CUBIC_OUT);
        for (int tick = 1; tick <= 6; tick++) progress.to(1, tick, 6, FloatingMenuEasing.CUBIC_OUT);
        assertEquals(1, progress.value(6));
    }

    @Test void reducedMotionImmediatelyFinishesAnAlreadyRunningTransition() {
        var hover = new MenuTransition(0);
        hover.to(1, 0, 6, FloatingMenuEasing.CUBIC_OUT);
        assertTrue(hover.value(2) < 1);
        assertEquals(1, hover.step(1, 2, 0, FloatingMenuEasing.CUBIC_OUT));
        assertEquals(0, hover.step(0, 3, 0, FloatingMenuEasing.CUBIC_OUT));
    }

    @Test void invalidTargetsAndClockReversalCannotPoisonTheCurrentAnimation() {
        var value = new MenuTransition(0);
        value.to(1, 0, 6, FloatingMenuEasing.CUBIC_OUT);
        double before = value.value(2);
        assertThrows(IllegalArgumentException.class, () -> value.to(Double.NaN, 2, 4, FloatingMenuEasing.CUBIC_OUT));
        assertThrows(IllegalArgumentException.class, () -> value.value(1));
        assertEquals(before, value.value(2));
        assertEquals(1, value.value(6));
    }
}
