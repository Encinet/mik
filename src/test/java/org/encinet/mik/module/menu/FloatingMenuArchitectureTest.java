package org.encinet.mik.module.menu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FloatingMenuArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/org/encinet/mik");
    private static final Pattern NUMERIC_ELEMENT = Pattern.compile("\\.element\\(\\s*\\d");

    @Test
    void noInventoryBackedOrNumericSlotMenuImplementationRemains() throws IOException {
        assertFalse(Files.exists(MAIN.resolve("module/menu/MenuBuilder.java")));
        assertFalse(Files.exists(MAIN.resolve("module/menu/MenuNavigation.java")));
        assertFalse(Files.exists(MAIN.resolve("module/menu/MenuItems.java")));

        for (Path source : javaSources()) {
            String value = Files.readString(source);
            assertFalse(value.contains("createInventory("), source.toString());
            assertFalse(value.contains("openInventory("), source.toString());
            if (value.contains("FloatingMenuDefinition")) {
                assertFalse(NUMERIC_ELEMENT.matcher(value).find(), source.toString());
                assertFalse(value.contains("Map<Integer, ItemStack>"), source.toString());
            }
        }
    }

    @Test
    void serviceOwnsPaperInputAndSharedSpatialInfrastructure() throws IOException {
        String service = Files.readString(MAIN.resolve("module/menu/runtime/FloatingMenuService.java"));

        assertTrue(service.contains("PlayerUseUnknownEntityEvent"));
        assertTrue(service.contains("PlayerItemHeldEvent"));
        assertTrue(service.contains("FloatingMenuSurfaceGeometry.intersect("));
        assertTrue(service.contains("FloatingMenuAnchorResolver.resolve("));
        assertTrue(service.contains("WORLD_INTERACTION_GUARD.contains("));
        assertTrue(service.contains("refreshLiveDefinitions(session, player)"));
        assertTrue(service.contains("VirtualMenuEntityRenderer.allocateId(player)"));
        assertFalse(service.contains("PacketEvents"));
        assertFalse(service.contains("WrapperPlayClient"));
    }

    @Test
    void applicationMenusShareOneDefinitionWithoutPlatformBranches() throws IOException {
        for (String source : List.of(
                "module/chat/menu/ChatSettingsMenu.java",
                "module/communication/AnnouncementModule.java",
                "shell/LanguageMenu.java",
                "module/music/ui/JukeboxControlGui.java",
                "module/music/ui/MusicBrowserGui.java",
                "module/player/HomeModule.java",
                "shell/MainMenuModule.java",
                "module/player/TeleportPreferenceModule.java",
                "module/pvp/PvpMenuController.java")) {
            String value = Files.readString(MAIN.resolve(source));
            assertTrue(value.contains("FloatingMenuDefinition.screen("), source);
            assertFalse(value.contains("GeyserApi"), source);
            assertFalse(value.contains("BedrockSimpleForm"), source);
            assertFalse(value.contains("createInventory("), source);
            assertFalse(value.contains("openInventory("), source);
        }

        String translator = Files.readString(MAIN.resolve(
                "module/menu/runtime/BedrockMenuTranslator.java"));
        String presenter = Files.readString(MAIN.resolve(
                "module/menu/runtime/GeyserFloatingMenuPresenter.java"));
        assertTrue(translator.contains(
                "Menu translate(FloatingMenuDefinition definition"));
        assertTrue(presenter.contains("translator.translate(definition"));
        assertTrue(presenter.contains("new BedrockSimpleForm("));
    }

    @Test
    void axiomGizmosAreSuppressedBeforeVirtualEntitiesSpawn() throws IOException {
        String service = Files.readString(MAIN.resolve("module/menu/runtime/FloatingMenuService.java"));
        String bridge = Files.readString(MAIN.resolve(
                "integration/axiom/AxiomGizmoService.java"));
        String synchronize =
                "axiomGizmos.synchronize(player, session.id, session.virtualEntityUuids())";
        String spawn = "virtualEntities.spawn(player, session.titleId";
        int synchronizeAt = service.indexOf(synchronize);
        int spawnAt = service.indexOf(spawn);

        assertTrue(bridge.contains("axiom:ignore_display_entities"));
        assertTrue(bridge.contains("registerOutgoingPluginChannel"));
        assertTrue(synchronizeAt >= 0, "missing Axiom synchronization");
        assertTrue(spawnAt > synchronizeAt,
                "Axiom synchronization must precede virtual entity spawning");
        assertTrue(service.contains("ids.add(button.hitboxUuid)"));
        assertTrue(service.contains("virtualEntities.destroy(player, ids.stream()"));
        assertTrue(service.contains("axiomGizmos.remove(player, session.id)"));
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
