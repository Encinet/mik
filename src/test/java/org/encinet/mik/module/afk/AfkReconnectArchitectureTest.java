package org.encinet.mik.module.afk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AfkReconnectArchitectureTest {

    @Test
    void shortReconnectCarriesAndSilentlyRestoresAfkState() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/org/encinet/mik/module/afk/AfkModule.java"));

        String joinHandler = methodSection(
                source, "public void onPlayerJoin", "public void onPlayerQuit");
        String quitHandler = methodSection(
                source, "public void onPlayerQuit", "public void onPlayerMove");

        assertTrue(quitHandler.contains("AfkState afkState = states.remove(playerId)"));
        assertTrue(quitHandler.contains("session.isAutomaticAfkCandidate(now)"));
        assertTrue(quitHandler.contains("automaticAfkCandidate"));
        assertTrue(joinHandler.contains("reconnectAfk = suspended.afkState()"));
        assertTrue(joinHandler.contains(
                "setAfk(player, reconnectAfk.message(), reconnectAfk.source(), false)"));
        assertTrue(joinHandler.contains("reconnectAutomaticCandidate"));
        assertTrue(joinHandler.contains("entrySafety(player).permitsAutomaticAfk()"));
        assertTrue(joinHandler.contains("setAfk(player, null, AfkSource.AUTOMATIC, false)"));
    }

    private static String methodSection(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, () -> "Missing method marker: " + startMarker);
        assertTrue(end > start, () -> "Missing method marker after " + startMarker + ": " + endMarker);
        return source.substring(start, end);
    }
}
