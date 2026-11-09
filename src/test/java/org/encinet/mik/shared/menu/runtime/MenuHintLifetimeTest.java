package org.encinet.mik.shared.menu.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuHintLifetimeTest {
    @Test void crossingTheGapAndReadingDoNotResetTheHintOrReleaseItsOwner() {
        var hint = new MenuHintLifetime(0, 10);
        assertEquals(MenuHintLifetime.Phase.WAITING, hint.update(9, true, false));
        assertEquals(MenuHintLifetime.Phase.VISIBLE, hint.update(10, true, false));
        assertEquals(MenuHintLifetime.Phase.VISIBLE, hint.update(12, false, false));
        assertTrue(hint.holds(12));
        assertEquals(MenuHintLifetime.Phase.VISIBLE, hint.update(13, false, true));
        assertEquals(MenuHintLifetime.Phase.VISIBLE, hint.update(200, false, true));
        assertTrue(hint.holds(200));
        assertEquals(MenuHintLifetime.Phase.VISIBLE, hint.update(204, false, false));
        assertEquals(MenuHintLifetime.Phase.CLOSED, hint.update(205, false, false));
        assertFalse(hint.holds(205));
    }

    @Test void aSweepBeforeTheDelayClosesWithoutShowingAndDismissalRequiresReentry() {
        var sweep = new MenuHintLifetime(0, 10);
        assertEquals(MenuHintLifetime.Phase.CLOSED, sweep.update(3, false, false));
        assertEquals(MenuHintLifetime.Phase.CLOSED, sweep.update(30, true, false));
        var hint = new MenuHintLifetime(0, 0);
        assertEquals(MenuHintLifetime.Phase.VISIBLE, hint.update(0, true, false));
        hint.dismiss();
        assertEquals(MenuHintLifetime.Phase.SUPPRESSED, hint.update(50, true, false));
        assertFalse(hint.holds(50));
        assertEquals(MenuHintLifetime.Phase.CLOSED, hint.update(51, false, false));
        assertEquals(MenuHintLifetime.Phase.VISIBLE, new MenuHintLifetime(52, 0).update(52, true, false));
    }

    @Test void explicitRequestsShowTheSameFloatingHelpImmediately() {
        var hint = new MenuHintLifetime(10, 10);
        hint.reveal();
        assertEquals(MenuHintLifetime.Phase.VISIBLE, hint.update(10, true, false));
        hint.dismiss();
        assertEquals(MenuHintLifetime.Phase.SUPPRESSED, hint.update(11, true, false));
        hint.reveal();
        assertEquals(MenuHintLifetime.Phase.VISIBLE, hint.update(12, true, false));
    }
}
