package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkMovementEventArchitectureTest {

    @Test
    void acceptedDisplacementCannotSynthesizeClientMovementInput() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/afk/AfkModule.java"));

        String moveHandler = methodSection(
                source, "public void onAcceptedPlayerMove", "public void onPlayerInput");
        String inputHandler = methodSection(
                source, "public void onPlayerInput", "public void onPlayerInteract");

        assertFalse(moveHandler.contains("recordMovementInput("));
        assertFalse(moveHandler.contains("getCurrentInput()"));
        assertTrue(inputHandler.contains("recordMovementInput("));
        assertTrue(inputHandler.contains("event.getInput()"));
    }

    private static String methodSection(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, () -> "Missing method marker: " + startMarker);
        assertTrue(end > start, () -> "Missing method marker after " + startMarker + ": " + endMarker);
        return source.substring(start, end);
    }
}
