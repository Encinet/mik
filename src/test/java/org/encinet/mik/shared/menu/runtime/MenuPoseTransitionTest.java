package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MenuPoseTransitionTest {
    @Test void aReversedExpansionContinuesFromItsSampledPoseAndFinishesAtTheLatestLayout() {
        var transition = new MenuPoseTransition();
        var collapsed = FloatingMenuPose.at(new FloatingMenuPoint(-2, 0, 0));
        var expanded = FloatingMenuPose.at(new FloatingMenuPoint(2, 1, 0));
        transition.snap(collapsed, 0);
        transition.advance(expanded, 0, 6, FloatingMenuEasing.CUBIC_OUT, MenuPoseTransition.Path.LINEAR);
        var current = transition.advance(expanded, 2, 6, FloatingMenuEasing.CUBIC_OUT, MenuPoseTransition.Path.LINEAR);
        assertEquals(current, transition.advance(collapsed, 2, 6, FloatingMenuEasing.CUBIC_OUT, MenuPoseTransition.Path.LINEAR));
        assertEquals(collapsed, transition.advance(collapsed, 8, 6, FloatingMenuEasing.CUBIC_OUT, MenuPoseTransition.Path.LINEAR));
    }

    @Test void contentAndBackgroundMaintainTheirRelativePositionsThroughoutTheSameTransition() {
        var content = new MenuPoseTransition();
        var background = new MenuPoseTransition();
        content.snap(FloatingMenuPose.at(new FloatingMenuPoint(-1, .5, 0)), 0);
        background.snap(FloatingMenuPose.ORIGIN, 0);
        for (int tick = 0; tick <= 6; tick++) {
            var a = content.advance(FloatingMenuPose.at(new FloatingMenuPoint(1, 1.5, 0)), tick, 6,
                    FloatingMenuEasing.CUBIC_OUT, MenuPoseTransition.Path.LINEAR);
            var b = background.advance(FloatingMenuPose.at(new FloatingMenuPoint(2, 1, 0)), tick, 6,
                    FloatingMenuEasing.CUBIC_OUT, MenuPoseTransition.Path.LINEAR);
            assertEquals(-1, a.right() - b.right(), 1e-8);
            assertEquals(.5, a.up() - b.up(), 1e-8);
        }
    }

    @Test void orbitingPreservesItsRadiusAndReducedMotionSnapsToTheFinalPose() {
        var transition = new MenuPoseTransition();
        var start = FloatingMenuPose.oriented(new FloatingMenuPoint(0, 0, -2), 0, 0);
        var end = FloatingMenuPose.oriented(new FloatingMenuPoint(2, 0, 0), 90, 0);
        transition.snap(start, 0);
        transition.advance(end, 0, 6, FloatingMenuEasing.LINEAR, MenuPoseTransition.Path.ORBIT);
        var middle = transition.advance(end, 3, 6, FloatingMenuEasing.LINEAR, MenuPoseTransition.Path.ORBIT);
        assertEquals(2, Math.hypot(middle.right(), middle.forward()), 1e-8);
        assertEquals(end, transition.advance(end, 3, 0, FloatingMenuEasing.LINEAR, MenuPoseTransition.Path.ORBIT));
    }
}
