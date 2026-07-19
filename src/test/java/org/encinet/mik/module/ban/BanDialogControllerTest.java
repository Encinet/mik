package org.encinet.mik.module.ban;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
