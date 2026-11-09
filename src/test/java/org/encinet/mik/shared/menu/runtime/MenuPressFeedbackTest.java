package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.FloatingMenuAnimation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuPressFeedbackTest {
    @Test void repeatedPresentationsAtTheSameTickDoNotConsumeTheHold() {
        var feedback = new MenuPressFeedback();
        feedback.select("save", 10, 3);
        for (int frame = 0; frame < 100; frame++) {
            assertFalse(feedback.advance(10));
            assertTrue(feedback.pressed("save"));
        }
        assertFalse(feedback.advance(11));
        assertFalse(feedback.advance(12));
        assertTrue(feedback.advance(13));
        assertFalse(feedback.pressed("save"));
        assertNull(feedback.elementId());
        assertFalse(feedback.advance(13), "An expired press must not dirty repeated frames");
    }

    @Test void interruptionAndRepeatedClicksUseTheLatestActionTime() {
        var feedback = new MenuPressFeedback();
        feedback.select("save", 10, 3);
        feedback.select("cancel", 11, 3);
        assertFalse(feedback.pressed("save"));
        assertTrue(feedback.pressed("cancel"));
        assertFalse(feedback.advance(13), "The replaced deadline must not clear the latest press");
        feedback.select("cancel", 13, 3);
        assertFalse(feedback.advance(15));
        assertTrue(feedback.advance(16));
    }

    @Test void hiddenTimeExpiresTheSemanticPressWithoutAnyPresentation() {
        var feedback = new MenuPressFeedback();
        feedback.select("toggle", 5, 3);
        assertTrue(feedback.advance(500));
        for (int frame = 0; frame < 10; frame++) {
            assertFalse(feedback.advance(500));
            assertFalse(feedback.pressed("toggle"));
        }
        feedback.select("toggle", 501, 3);
        assertFalse(feedback.advance(503));
        assertTrue(feedback.advance(504));
    }

    @Test void clearingASessionDropsTheSourceBeforeItsOldDeadline() {
        var feedback = new MenuPressFeedback();
        feedback.select("source", 7, 3);
        feedback.clear();
        feedback.clear();
        assertFalse(feedback.pressed("source"));
        assertNull(feedback.elementId());
        assertFalse(feedback.advance(100));
        feedback.select("replacement", 100, 3);
        assertFalse(feedback.pressed("source"));
        assertTrue(feedback.pressed("replacement"));
        assertTrue(feedback.advance(103));
    }

    @Test void reducedMotionHoldsTheThemeStateForOneTickRegardlessOfPublicationCount() {
        var feedback = new MenuPressFeedback();
        feedback.select("save", 20, FloatingMenuAnimation.DEFAULT.reduced().pressTicks());
        for (int frame = 0; frame < 10; frame++) {
            assertFalse(feedback.advance(20));
            assertTrue(feedback.pressed("save"));
        }
        assertTrue(feedback.advance(21));
        assertFalse(feedback.pressed("save"));
    }

    @Test void elapsedTimeDoesNotOverflowNearTheClockLimit() {
        var feedback = new MenuPressFeedback();
        feedback.select("save", Long.MAX_VALUE - 3, 3);
        assertFalse(feedback.advance(Long.MAX_VALUE - 1));
        assertTrue(feedback.advance(Long.MAX_VALUE));
    }

    @Test void invalidInputCannotReplaceTheCurrentPress() {
        var feedback = new MenuPressFeedback();
        feedback.select("save", 10, 3);
        assertThrows(NullPointerException.class, () -> feedback.select(null, 11, 3));
        assertThrows(IllegalArgumentException.class, () -> feedback.select("cancel", 11, 0));
        assertThrows(IllegalArgumentException.class, () -> feedback.select("cancel", 9, 3));
        assertThrows(IllegalArgumentException.class, () -> feedback.advance(9));
        assertTrue(feedback.pressed("save"));
        assertFalse(feedback.advance(12));
        assertTrue(feedback.advance(13));
    }
}
