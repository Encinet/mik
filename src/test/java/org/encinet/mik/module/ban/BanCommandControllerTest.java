package org.encinet.mik.module.ban;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanCommandControllerTest {

    @Test
    void neverJoinedPlayerRequiresExplicitCommandConfirmation() {
        assertTrue(BanCommandController.needsNeverJoinedConfirmation(false, false));
        assertFalse(BanCommandController.needsNeverJoinedConfirmation(false, true));
        assertFalse(BanCommandController.needsNeverJoinedConfirmation(true, false));
    }
}
