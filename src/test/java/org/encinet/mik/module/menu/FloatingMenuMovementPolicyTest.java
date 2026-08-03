package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuMovementPolicyTest {

    @Test
    void ordinaryMenusKeepTheShortSafetyLeash() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder(
                Component.text("Menu")).build();

        assertEquals(FloatingMenuMovementPolicy.STANDARD,
                definition.movementPolicy());
        assertFalse(definition.movementPolicy().exceeded(9.0));
        assertTrue(definition.movementPolicy().exceeded(9.01));
    }

    @Test
    void capturedInputScenesCanDeclareASeparateBoundary() {
        FloatingMenuDefinition definition = FloatingMenuDefinition.builder()
                .movementPolicy(FloatingMenuMovementPolicy.CAPTURED_INPUT)
                .build();

        assertEquals(12.0, definition.movementPolicy().maximumDrift());
        assertFalse(definition.movementPolicy().exceeded(144.0));
        assertTrue(definition.movementPolicy().exceeded(144.01));
    }

    @Test
    void invalidBoundariesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new FloatingMenuMovementPolicy(0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new FloatingMenuMovementPolicy(Double.NaN));
    }
}
