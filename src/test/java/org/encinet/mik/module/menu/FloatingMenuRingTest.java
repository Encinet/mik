package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuRingTest {
    @Test
    void smallCatalogsFillTheWholeRingWithoutDuplicateCards() {
        assertEquals(List.of(), FloatingMenuRing.around(0, 0, 7));
        assertEquals(List.of(new FloatingMenuRing.Slot(0, 0)),
                FloatingMenuRing.around(1, 0, 7));
        assertEquals(List.of(new FloatingMenuRing.Slot(1, 0),
                        new FloatingMenuRing.Slot(0, 1)),
                FloatingMenuRing.around(2, 1, 7));
        assertEquals(List.of(new FloatingMenuRing.Slot(0, 0),
                        new FloatingMenuRing.Slot(1, 1),
                        new FloatingMenuRing.Slot(4, -1),
                        new FloatingMenuRing.Slot(2, 2),
                        new FloatingMenuRing.Slot(3, -2)),
                FloatingMenuRing.around(5, 0, 7));
    }

    @Test
    void longCatalogKeepsTheSelectedCardAndThreeOnEachSide() {
        List<FloatingMenuRing.Slot> slots = FloatingMenuRing.around(20, 0, 7);
        assertEquals(7, slots.size());
        assertEquals(List.of(0, 1, 19, 2, 18, 3, 17),
                slots.stream().map(FloatingMenuRing.Slot::index).toList());
        assertTrue(FloatingMenuRing.pose(3, slots.size(), 3.0, 0.0).forward() > 0.0);
        assertTrue(FloatingMenuRing.pose(-3, slots.size(), 3.0, 0.0).forward() > 0.0);
        assertTrue(FloatingMenuRing.frontArc(0, 3, 45.0, 2.0, -0.5).right() < 0.0);
        assertEquals(0.0, FloatingMenuRing.frontArc(1, 3, 45.0, 2.0, -0.5).right(),
                0.0001);
    }

    @Test
    void movingAcrossTheRearOfTheRingNeverCutsThroughItsCenter() {
        FloatingMenuPoint rightRear = FloatingMenuRing.pose(2, 5, 2.0, 0.8).point();
        FloatingMenuPoint leftRear = FloatingMenuRing.pose(-2, 5, 2.0, 0.8).point();

        FloatingMenuPoint halfway = FloatingMenuRing.approach(rightRear, leftRear, 0.5);
        assertEquals(2.0, Math.hypot(halfway.right(), halfway.forward()), 0.0001);
        assertTrue(halfway.forward() > 0.0);
        assertEquals(0.8, halfway.up(), 0.0001);
    }
}
