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

    @Test
    void samplingIsPeriodicAndDoesNotRefreshInputOrActivityClocks() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/afk/AfkModule.java"));
        String sampler = methodSection(source, "private void sampleMovement", "private void flushPendingActivity");
        String tick = methodSection(source, "private void tick()", "private void sampleMovement");

        assertTrue(tick.contains("sampleMovement();"));
        assertTrue(tick.indexOf("sampleMovement();") < tick.indexOf("if (tickCounter"));
        assertTrue(sampler.contains("recordObservation("));
        assertTrue(sampler.contains("player.getVehicle() == null"));
        assertFalse(sampler.contains("getCurrentInput()"));
        assertFalse(sampler.contains("recordMovementInput("));
        assertFalse(sampler.contains("recordLightActivity("));
    }

    @Test
    void combatEvidenceIsSeparatedFromProtectionAndObservedAfterCancellation() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/afk/AfkModule.java"));
        String protection = methodSection(source, "public void onEntityDamageByEntity", "public void onAcceptedEntityDamageByEntity");
        String evidence = methodSection(source, "public void onAcceptedEntityDamageByEntity", "public void onEntityTargetAfkPlayer");

        assertFalse(protection.contains("recordAction("));
        assertTrue(protection.contains("EventPriority.MONITOR, ignoreCancelled = true"));
        assertTrue(evidence.contains("event.getFinalDamage() > 0.0D"));
        assertTrue(evidence.contains("recordAction("));
    }

    private static String methodSection(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, () -> "Missing method marker: " + startMarker);
        assertTrue(end > start, () -> "Missing method marker after " + startMarker + ": " + endMarker);
        return source.substring(start, end);
    }
}
