package org.encinet.mik.feature.server.ban;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanDialogControllerTest {

    @Test
    void acceptsPaperDialogInputNames() {
        assertDoesNotThrow(() -> DialogInputKeys.requireValid("manual_player"));
        assertDoesNotThrow(() -> DialogInputKeys.requireValid("online.player"));
    }

    @Test
    void rejectsHyphenatedInputNames() {
        assertThrows(IllegalArgumentException.class,
                () -> DialogInputKeys.requireValid("manual-player"));
    }

    @Test
    void onlyPlayersWhoNeverJoinedNeedAnotherConfirmation() {
        assertTrue(BanDialogController.needsNeverJoinedConfirmation(false));
        assertFalse(BanDialogController.needsNeverJoinedConfirmation(true));
    }
}
