package org.encinet.mik.shared.menu.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InertialMenuAnchorTest {
    @Test void ordinarySmallTurnsStayInTheDeadZoneAndFollowingIsBounded() {
        var anchor = new InertialMenuAnchor(0);
        assertEquals(50, anchor.step(5, false, false));
        double before = anchor.yaw();
        for (int tick = 1; tick <= 60; tick++) {
            double next = anchor.step(-tick * 0.5, false, false);
            assertTrue(Math.abs(next - before) <= 4.5 + 1e-9);
            before = next;
        }
        assertTrue(anchor.yaw() < 50);
        assertTrue(anchor.yaw() > 20);
    }
    @Test void aQuickGlanceFreezesTheToolUntilTheViewerLeaves() {
        var anchor = new InertialMenuAnchor(0);
        for (int i = 0; i < 100; i++) assertEquals(50, anchor.step(50, false, false));
        for (int i = 0; i < 20; i++) assertEquals(50, anchor.step(-30, true, false));
        for (int i = 0; i < 100; i++) anchor.step(-30, false, false);
        assertTrue(Math.abs(anchor.yaw() - 20) <= 10);
    }
    @Test void angleSeamsAndReducedMotionDoNotCauseAFullTurn() {
        var anchor = new InertialMenuAnchor(179);
        assertEquals(229, anchor.step(-179, false, false));
        var reduced = new InertialMenuAnchor(0);
        for (int i = 0; i < 12; i++) reduced.step(-30, false, true);
        assertEquals(20, reduced.yaw());
    }
}
