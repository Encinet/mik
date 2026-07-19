package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkActivityDetectionTest {

    @Test
    void yawOnlyRotationDoesNotCountAsActivity() {
        assertFalse(AfkModule.isMeaningfulRotation(0.0F, 20.0F, 90.0F, 20.0F));
    }

    @Test
    void pitchOnlyRotationDoesNotCountAsActivity() {
        assertFalse(AfkModule.isMeaningfulRotation(45.0F, 0.0F, 45.0F, 45.0F));
    }

    @Test
    void bothAxesMustExceedTheActivityThreshold() {
        assertTrue(AfkModule.isMeaningfulRotation(0.0F, 0.0F, 8.0F, 8.0F));
        assertFalse(AfkModule.isMeaningfulRotation(0.0F, 0.0F, 7.99F, 30.0F));
        assertFalse(AfkModule.isMeaningfulRotation(0.0F, 0.0F, 30.0F, 7.99F));
    }

    @Test
    void yawWrapAroundUsesTheShortestAngle() {
        assertTrue(AfkModule.isMeaningfulRotation(356.0F, 0.0F, 4.0F, 8.0F));
        assertFalse(AfkModule.isMeaningfulRotation(358.0F, 0.0F, 2.0F, 30.0F));
    }
}
