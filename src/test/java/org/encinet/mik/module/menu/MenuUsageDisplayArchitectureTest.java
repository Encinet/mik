package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuUsageDisplayArchitectureTest {

    private static final Path MENU = Path.of("src/main/java/org/encinet/mik/module/menu");

    @Test
    void menuSessionOwnsTheOverheadStatusLifetime() throws IOException {
        String service = Files.readString(MENU.resolve("runtime/FloatingMenuService.java"));

        assertTrue(service.contains(
                "menuStatusDisplays.update(player, session.definition.screenId())"));
        assertTrue(service.contains(
                "menuStatusDisplays.updateTrackedPlayers(activeScreenIds())"));
        assertTrue(service.contains("if (!sessions.containsKey(playerId))"));
        assertTrue(service.contains("menuStatusDisplays.remove(playerId)"));
        assertTrue(service.contains("menuStatusDisplays.forgetViewer(playerId)"));
        assertTrue(service.contains("menuStatusDisplays.refreshViewerLanguage(player)"));
    }

    @Test
    void axiomExclusionWrapsTheMenuStatusDisplayLifetime() throws IOException {
        String source = Files.readString(MENU.resolve("runtime/MenuUsageDisplayController.java"));

        int synchronize = source.indexOf(
                "axiomGizmos.synchronize(viewer, display.entityUuid, Set.of(display.entityUuid))");
        int spawn = source.indexOf("new WrapperPlayServerSpawnEntity(");
        int destroy = source.indexOf("new WrapperPlayServerDestroyEntities(display.entityId)");
        int remove = source.indexOf("axiomGizmos.remove(viewer, display.entityUuid)");

        assertTrue(synchronize >= 0 && synchronize < spawn);
        assertTrue(destroy >= 0 && destroy < remove);
        assertTrue(source.contains("axiomGizmos.forgetViewer(viewerId)"));
    }

    @Test
    void activeRhythmGameplayOwnsADistinctLocalizedOverheadStatus()
            throws IOException {
        String source = Files.readString(MENU.resolve("runtime/MenuUsageDisplayController.java"));

        assertTrue(source.contains("RHYTHM_GAME_SCREEN_ID = \"jukebox-rhythm\""));
        assertTrue(source.contains("RHYTHM_CALIBRATION_SCREEN_ID"));
        assertTrue(source.contains("Message.MENU_RHYTHM_GAME_DISPLAY"));
        assertTrue(source.contains("boolean contentChanged = display.updateUsage(usage)"));
        assertTrue(source.contains("sendMetadata(viewer.player, display)"));
    }
}
