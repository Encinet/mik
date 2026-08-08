package org.encinet.mik.module.music.rhythm.mode.falling;

import org.encinet.mik.module.menu.FloatingMenuPoint;
import org.encinet.mik.module.music.rhythm.RhythmInput;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RhythmFallingLayoutTest {

    @Test
    void fourInputsOwnFourOrderedFixedLanes() {
        FloatingMenuPoint one = RhythmFallingLayout.target(RhythmInput.ONE);
        FloatingMenuPoint two = RhythmFallingLayout.target(RhythmInput.TWO);
        FloatingMenuPoint three = RhythmFallingLayout.target(RhythmInput.THREE);
        FloatingMenuPoint four = RhythmFallingLayout.target(RhythmInput.FOUR);

        assertTrue(one.right() < two.right());
        assertTrue(two.right() < three.right());
        assertTrue(three.right() < four.right());
        for (FloatingMenuPoint target : new FloatingMenuPoint[]{
                one, two, three, four}) {
            assertEquals(RhythmFallingLayout.HIT_LINE_UP, target.up(), 0.0);
            assertEquals(RhythmFallingLayout.LANE_FORWARD,
                    target.forward(), 0.0);
        }
    }

    @Test
    void notesFallVerticallyAndCrossTheHitLine() {
        FloatingMenuPoint target = RhythmFallingLayout.target(RhythmInput.THREE);

        FloatingMenuPoint spawn = RhythmFallingLayout.point(target, 0.0);
        FloatingMenuPoint hit = RhythmFallingLayout.point(target, 1.0);
        FloatingMenuPoint late = RhythmFallingLayout.point(target, 1.1);

        assertEquals(target.right(), spawn.right(), 1.0E-9);
        assertEquals(target.forward(), spawn.forward(), 1.0E-9);
        assertTrue(spawn.up() > hit.up());
        assertEquals(target.up(), hit.up(), 1.0E-9);
        assertTrue(late.up() < hit.up());
        assertThrows(IllegalArgumentException.class,
                () -> RhythmFallingLayout.point(target, Double.NaN));
    }
}
