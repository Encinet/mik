package org.encinet.mik.module.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpaceFrameTest {

    @Test
    void localPointAndVectorTransformsRoundTrip() {
        SpaceFrame frame = SpaceFrame.oriented(
                new SpaceVector(12.5, 64.0, -8.0), 37.0, -21.0, 18.0);
        SpaceVector point = new SpaceVector(14.0, 66.0, -3.5);
        SpaceVector vector = new SpaceVector(-0.4, 1.2, 0.75);

        assertVector(point, frame.fromLocalPoint(frame.toLocalPoint(point)));
        assertVector(vector, frame.fromLocalVector(frame.toLocalVector(vector)));
    }

    @Test
    void minecraftYawConventionIsUsed() {
        assertVector(new SpaceVector(0.0, 0.0, 1.0),
                SpaceFrame.oriented(SpaceVector.ZERO, 0.0, 0.0, 0.0).forward());
        assertVector(new SpaceVector(-1.0, 0.0, 0.0),
                SpaceFrame.oriented(SpaceVector.ZERO, 90.0, 0.0, 0.0).forward());
        assertVector(new SpaceVector(0.0, 1.0, 0.0),
                SpaceFrame.fromAxes(
                        SpaceVector.ZERO,
                        new SpaceVector(0.0, 1.0, 0.0),
                        new SpaceVector(0.0, 0.0, -1.0)).forward());
    }

    private void assertVector(SpaceVector expected, SpaceVector actual) {
        assertEquals(expected.x(), actual.x(), 1.0E-9);
        assertEquals(expected.y(), actual.y(), 1.0E-9);
        assertEquals(expected.z(), actual.z(), 1.0E-9);
    }
}
