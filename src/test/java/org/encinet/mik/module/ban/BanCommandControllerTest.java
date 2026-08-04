package org.encinet.mik.module.ban;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanCommandControllerTest {

    @Test
    void neverJoinedPlayerRequiresExplicitCommandConfirmation() {
        assertTrue(BanCommandController.needsNeverJoinedConfirmation(false, false));
        assertFalse(BanCommandController.needsNeverJoinedConfirmation(false, true));
        assertFalse(BanCommandController.needsNeverJoinedConfirmation(true, false));
    }

    @Test
    void editCommandExposesAGreedyReasonBranch() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/ban/BanCommandController.java"));

        assertTrue(source.contains("Commands.literal(\"reason\")"));
        assertTrue(source.contains("Commands.argument(\"reason\", StringArgumentType.greedyString())"));
        assertTrue(source.contains(".executes(this::editReason)"));
        assertTrue(source.contains("banService.editReason("));
    }
}
