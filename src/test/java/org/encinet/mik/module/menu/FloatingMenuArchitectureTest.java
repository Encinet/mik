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
    private static final Pattern AMBIGUOUS_TEXT_NODE =
            Pattern.compile("\\b(?:menu|builder)\\.text\\(");

    @Test
    void noInventoryBackedOrNumericSlotMenuImplementationRemains() throws IOException {
        assertFalse(Files.exists(MAIN.resolve("module/menu/"
                + "Menu" + "Builder.java")));
        assertFalse(Files.exists(MAIN.resolve("module/menu/"
                + "Menu" + "Navigation.java")));
        String legacyItemHelpers = "Menu" + "Items";
        String legacyDisplayMetadata = "Tooltip" + "Display";
        String legacyElementCall = ".ele" + "ment(";
        assertFalse(Files.exists(MAIN.resolve("module/menu/" + legacyItemHelpers + ".java")));
        for (Path source : javaSources()) {
            String value = Files.readString(source);
            assertFalse(value.contains("createInventory("), source.toString());
            assertFalse(value.contains("openInventory("), source.toString());
            if (value.contains("FloatingMenuDefinition")) {
                assertFalse(NUMERIC_ELEMENT.matcher(value).find(), source.toString());
                assertFalse(AMBIGUOUS_TEXT_NODE.matcher(value).find(), source.toString());
                assertFalse(value.contains("Map<Integer, ItemStack>"), source.toString());
                assertFalse(value.contains(legacyElementCall), source.toString());
                assertFalse(value.contains(legacyItemHelpers), source.toString());
                assertFalse(value.contains(legacyDisplayMetadata), source.toString());
                assertFalse(value.contains("." + "lo" + "re("), source.toString());
            }
        }
    }

    @Test
    void sceneNodesUseExplicitPresentationAndSemanticRoles() throws IOException {
        String definition = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuDefinition.java"));
        String roles = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuNodeRole.java"));
        String service = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuService.java"));

        assertTrue(definition.contains("Component label, FloatingMenuNodeRole role"));
        assertTrue(definition.contains("NodeBuilder information(String id, Component text)"));
        assertTrue(definition.contains("NodeBuilder control(String id, Component label)"));
        assertTrue(definition.contains("NodeBuilder navigation(String id, Component label)"));
        assertTrue(definition.contains("NodeBuilder item(String id, ItemStack item, Component label)"));
        assertTrue(definition.contains("NodeBuilder block(String id, Material material, Component label)"));
        assertTrue(roles.contains("INFORMATION(FloatingMenuElementStyle.TEXT, false)"));
        assertTrue(roles.contains("NAVIGATION(FloatingMenuElementStyle.TEXT, true)"));

        for (String forbidden : List.of(
                "ItemMeta", "getItemMeta(", "hasDisplayName(",
                "Tooltip" + "Display", "Element" + "Builder",
                "FloatingMenuElementStyle." + "AUTO",
                "public NodeBuilder text(")) {
            assertFalse(definition.contains(forbidden), forbidden);
        }
        assertFalse(definition.contains("." + "lo" + "re("));
        assertFalse(service.toLowerCase(java.util.Locale.ROOT)
                .contains("tool" + "tip"));
        for (Path source : menuSources()) {
            String value = Files.readString(source);
            assertFalse(value.contains("ItemMeta"), source.toString());
            assertFalse(value.contains("org.bukkit.event." + "inventory"), source.toString());
            assertFalse(value.contains("Inventory" + "View"), source.toString());
            assertFalse(value.contains("." + "lo" + "re("), source.toString());
            assertFalse(value.toLowerCase(java.util.Locale.ROOT)
                    .contains("tool" + "tip"), source.toString());
        }
    }

    @Test
    void paperUnknownEntityAndHeldItemEventsOwnMenuInput() throws IOException {
        String service = Files.readString(MAIN.resolve("module/menu/FloatingMenuService.java"));
        assertTrue(service.contains("PlayerUseUnknownEntityEvent"));
        assertTrue(service.contains("event.isAttack()"));
        assertTrue(service.contains("String picked = session == null ? null : pickElement(session, player)"));
        assertTrue(service.contains("FloatingMenuSurfaceGeometry.intersect("));
        assertTrue(service.contains("PlayerItemHeldEvent"));
        assertFalse(service.contains("PacketEvents"));
        assertFalse(service.contains("WrapperPlayClient"));
        assertFalse(service.contains("PlayerToggleSprintEvent"));
        assertFalse(service.contains("isSneaking().orElse"));
    }

    @Test
    void visibleMenuConeProtectsBlocksBehindSpatialControls() throws IOException {
        String service = Files.readString(MAIN.resolve("module/menu/FloatingMenuService.java"));
        String guard = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuWorldInteractionGuard.java"));
        assertTrue(service.contains("onBlockDamage(BlockDamageEvent event)"));
        assertTrue(service.contains("onBlockBreak(BlockBreakEvent event)"));
        assertTrue(service.contains("onBlockPlace(BlockPlaceEvent event)"));
        assertTrue(service.contains("WORLD_INTERACTION_GUARD.contains("));
        assertTrue(guard.contains("UNIFIED_HALF_ANGLE_DEGREES = 45.0"));
        assertTrue(guard.contains("cosine >= requiredCosine"));
        assertTrue(service.contains("session.definition.framing()"));
    }

    @Test
    void virtualEntitiesUseTheServerAllocatorAndCompleteVisibilityMetadata() throws IOException {
        String service = Files.readString(MAIN.resolve("module/menu/FloatingMenuService.java"));
        String renderer = Files.readString(MAIN.resolve("module/menu/VirtualMenuEntityRenderer.java"));
        String anchors = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuAnchorResolver.java"));
        assertTrue(service.contains("VirtualMenuEntityRenderer.allocateId(player)"));
        assertTrue(renderer.contains("getNextEntityId()"));
        assertFalse(service.contains("NEXT_ENTITY_ID"));
        assertFalse(service.contains("getWorld().spawn("));
        assertFalse(renderer.contains("addFreshEntity("));
        assertFalse(renderer.contains("addEntity("));
        assertTrue(renderer.contains("ClientboundAddEntityPacket"));
        assertTrue(renderer.contains("getEntityData().packAll()"));
        assertTrue(renderer.contains("setBrightness(FULL_BRIGHT)"));
        assertTrue(renderer.contains("setSeeThrough(true)"));
        assertTrue(renderer.contains("Display.Billboard.FIXED"));
        assertFalse(renderer.contains("Display.Billboard.CENTER"));
        assertTrue(service.contains("button.renderedYaw"));
        assertTrue(service.contains("button.renderedPitch"));
        assertTrue(service.contains("Vector panelNormal = FloatingMenuSurfaceGeometry.facing(yaw, pitch)"));
        assertTrue(renderer.contains("display.setTeleportDuration(0)"));
        assertTrue(renderer.contains("presentationYaw, float presentationPitch"));
        assertFalse(service.contains("WrapperPlayServerSpawnEntity"));
        assertFalse(service.contains("EntityDataTypes"));
        assertTrue(anchors.contains("rayTraceBlocks("));
    }

    @Test
    void menuAnchorsProbeTheSceneVolumeAndRemainErgonomic() throws IOException {
        String service = Files.readString(MAIN.resolve("module/menu/FloatingMenuService.java"));
        String anchors = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuAnchorResolver.java"));
        assertTrue(service.contains("FloatingMenuAnchorResolver.measure(definition"));
        assertTrue(service.contains("FloatingMenuAnchorResolver.resolve("));
        assertTrue(anchors.contains("PREFERRED_DISTANCE = 2.80"));
        assertTrue(anchors.contains("HIGH_FOV_BASE_SCALE = 1.28"));
        assertTrue(anchors.contains("PREFERRED_DISTANCE / HIGH_FOV_BASE_SCALE"));
        assertTrue(anchors.contains("MIN_COMFORTABLE_DISTANCE = 1.25"));
        assertTrue(anchors.contains("new Candidate(-28.0, 0.0)"));
        assertTrue(anchors.contains("SceneBounds.around(points, definition.framing())"));
        assertTrue(anchors.contains("minRight, maxRight"));
        assertTrue(anchors.contains("comfortScale(distance)"));
        assertTrue(service.contains("session.reanchor(definition, nextLayout)"));
    }

    @Test
    void productionMenusUseMeasuredLayoutsInsteadOfFixedCenterSpacing() throws IOException {
        for (String source : List.of(
                "module/chat/menu/ChatSettingsMenu.java",
                "module/commands/SimpleFeaturesModule.java",
                "module/communication/AnnouncementModule.java",
                "module/event/FifthAnniversaryEventModule.java",
                "module/i18n/LanguageService.java",
                "module/music/ui/JukeboxControlGui.java",
                "module/music/ui/MusicBrowserGui.java",
                "module/player/HomeModule.java",
                "module/player/MainMenuModule.java",
                "module/player/TeleportPreferenceModule.java",
                "module/pvp/PvpMenuController.java")) {
            String value = Files.readString(MAIN.resolve(source));
            assertTrue(value.contains("FloatingMenuLayouts.menu")
                    || value.contains("FloatingMenuLayouts.actions")
                    || value.contains("FloatingMenuLayouts.adaptive")
                    || value.contains("FloatingMenuLayouts.verticalRegions")
                    || value.contains("FloatingMenuLayouts.horizontalPanels"), source);
            for (String centerOnly : List.of(
                    "FloatingMenuLayouts.curvedList(",
                    "FloatingMenuLayouts.cylindricalGrid(",
                    "FloatingMenuLayouts.sphericalGrid(",
                    "FloatingMenuLayouts.ring(")) {
                assertFalse(value.contains(centerOnly), source + " uses " + centerOnly);
            }
        }
    }

    @Test
    void axiomGizmosAreSuppressedBeforeVirtualEntitiesSpawn() throws IOException {
        String service = Files.readString(MAIN.resolve("module/menu/FloatingMenuService.java"));
        String bridge = Files.readString(MAIN.resolve("module/presentation/AxiomGizmoService.java"));
        assertTrue(bridge.contains("axiom:ignore_display_entities"));
        assertTrue(bridge.contains("registerOutgoingPluginChannel"));
        assertTrue(service.indexOf("axiomGizmos.synchronize(player, session.id, session.virtualEntityUuids())")
                < service.indexOf("virtualEntities.spawn(player, session.titleId"));
        assertTrue(service.contains("ids.add(button.hitboxUuid)"));
        assertTrue(service.contains("virtualEntities.destroy(player, ids.stream()"));
        assertTrue(service.contains("axiomGizmos.remove(player, session.id)"));
    }

    @Test
    void spatialSceneSeparatesAmbientContentFromControls() throws IOException {
        String definition = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuDefinition.java"));
        String service = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuService.java"));
        String mainMenu = Files.readString(MAIN.resolve(
                "module/player/MainMenuModule.java"));
        assertTrue(definition.contains("Map<String, FloatingMenuDecoration> decorations"));
        assertTrue(service.contains("updateButtonText("));
        assertTrue(service.contains("button.textOnly"));
        assertFalse(mainMenu.contains("element(\"profile\""));
        assertTrue(mainMenu.contains("information(\"player-info\""));
        assertTrue(mainMenu.contains("FloatingMenuLayouts.sidecar("));
        assertTrue(mainMenu.contains("FloatingMenuDecoration.Alignment.RIGHT"));
        assertFalse(mainMenu.contains("textDecoration(\"player-info\""));
        assertFalse(mainMenu.contains("itemDecoration(\"player-head\""));
        assertTrue(mainMenu.contains(".framing(FloatingMenuFraming.PANORAMIC)"));
        assertTrue(mainMenu.contains("FloatingMenuLayouts.actions(\"actions\", 5)"));
    }

    @Test
    void applicationMenusUseTheSharedScreenAndSemanticControlDsl() throws IOException {
        String definition = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuDefinition.java"));
        assertTrue(definition.contains("public static Builder screen(String screenId)"));
        assertTrue(definition.contains("public NodeBuilder back(Component label)"));
        assertTrue(definition.contains("public NodeBuilder dismiss(Component label)"));
        assertTrue(definition.contains("public Builder pagination("));

        for (String source : List.of(
                "module/chat/menu/ChatSettingsMenu.java",
                "module/commands/SimpleFeaturesModule.java",
                "module/communication/AnnouncementModule.java",
                "module/event/FifthAnniversaryEventModule.java",
                "module/i18n/LanguageService.java",
                "module/music/rhythm/RhythmGameService.java",
                "module/music/ui/JukeboxControlGui.java",
                "module/music/ui/MusicBrowserGui.java",
                "module/player/HomeModule.java",
                "module/player/MainMenuModule.java",
                "module/player/TeleportPreferenceModule.java",
                "module/pvp/PvpMenuController.java")) {
            String value = Files.readString(MAIN.resolve(source));
            assertTrue(value.contains("FloatingMenuDefinition.screen("), source);
            assertFalse(value.contains(".appearance(FloatingMenuAppearance.SPATIAL)"), source);
        }
    }

    @Test
    void languageMenuUsesAnInteractiveMeasuredDomeInsteadOfAFlatGenericGrid()
            throws IOException {
        String language = Files.readString(MAIN.resolve(
                "module/i18n/LanguageService.java"));
        String layouts = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuLayouts.java"));
        String service = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuService.java"));

        assertTrue(language.contains("FloatingMenuLayouts.adaptiveDomeGrid("));
        assertTrue(language.contains(".framing(FloatingMenuFraming.PANORAMIC)"));
        assertTrue(language.contains(".region(REGION_AUTOMATIC)"));
        assertTrue(language.contains(".region(REGION_LANGUAGES)"));
        assertTrue(language.contains(".region(REGION_NAVIGATION)"));
        assertTrue(language.contains(".primary((p, handle) -> selectLanguage(p, AUTO))"));
        assertTrue(language.contains(".primary((p, handle) -> selectLanguage(p, value))"));
        assertTrue(language.contains("builder.back("));
        assertFalse(language.contains(".layout(FloatingMenuLayouts.actions(4))"));
        assertTrue(layouts.contains("base.yawDegrees() + Math.toDegrees(Math.atan(yawSlope))"));
        assertTrue(layouts.contains("base.pitchDegrees() + Math.toDegrees(Math.atan(pitchSlope))"));
        assertTrue(service.contains("FloatingMenuSurfaceGeometry.intersect("));
        assertTrue(service.contains("directAction.execute(player, new Handle(session.id"));
    }

    @Test
    void developmentRuntimeMatchesTheDeclaredMinecraftApi() throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(build.contains("paperDevBundle(\"26.2.build.84-stable\")"));
        assertTrue(build.contains("minecraftVersion(\"26.2\")"));
        assertFalse(build.contains("paper-api:26.1.2"));
        assertFalse(build.contains("minecraftVersion(\"26.1.2\")"));
    }

    @Test
    void complexMenusUseTypedScreenStateAndSemanticJukeboxActions() throws IOException {
        for (String source : List.of(
                "module/communication/AnnouncementModule.java",
                "module/music/ui/JukeboxControlGui.java",
                "module/music/ui/MusicBrowserGui.java",
                "module/player/HomeModule.java",
                "module/commands/SimpleFeaturesModule.java")) {
            assertTrue(Files.readString(MAIN.resolve(source)).contains("FloatingMenuScreen<"), source);
        }
        String actions = Files.readString(MAIN.resolve(
                "module/music/ui/JukeboxControlActionHandler.java"));
        String listener = Files.readString(MAIN.resolve(
                "module/music/listener/JukeboxControlListener.java"));
        assertFalse(actions.contains("int slot"));
        assertFalse(listener.contains("switch (slot)"));
        assertTrue(actions.contains("queueTrack("));
        assertTrue(actions.contains("adjustVolume("));
    }

    @Test
    void liveMenusUseDeclarativeInvalidationInsteadOfModuleOwnedTimers() throws IOException {
        String definition = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuDefinition.java"));
        String service = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuService.java"));
        String mainMenu = Files.readString(MAIN.resolve(
                "module/player/MainMenuModule.java"));

        assertTrue(definition.contains("refreshWhenChanged("));
        assertTrue(service.contains("refreshLiveDefinitions(session, player)"));
        assertTrue(service.contains("refreshFrameDefinition(session, parent, player)"));
        assertTrue(mainMenu.contains(".refreshWhenChanged(LIVE_STATE_REFRESH_TICKS"));
        assertTrue(mainMenu.contains("MainMenuRevision"));
        assertFalse(mainMenu.contains("runTaskTimer("));
    }

    @Test
    void interfaceScaleIsPersistentAndMusicFocusKeepsAStableSpatialFrame()
            throws IOException {
        String service = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuService.java"));
        String store = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuSettingsStore.java"));
        String mainMenu = Files.readString(MAIN.resolve(
                "module/player/MainMenuModule.java"));
        String music = Files.readString(MAIN.resolve(
                "module/music/ui/MusicBrowserGui.java"));

        assertTrue(store.contains("menu-settings.yml"));
        assertTrue(service.contains("session.interfaceScale = selected.factor()"));
        assertTrue(service.contains("session.reanchor(session.definition, session.layoutSnapshot)"));
        assertTrue(service.contains("HOVER_SURFACE_MARGIN * session.spatialScale"));
        assertTrue(service.contains("menu.on(FloatingMenuInteraction.SCROLL_UP"));
        assertTrue(service.contains("menu.on(FloatingMenuInteraction.SCROLL_DOWN"));
        assertTrue(mainMenu.contains("FloatingMenus.openSettings(p)"));
        assertTrue(mainMenu.contains("interfaceScaleLabel(player)"));
        assertTrue(music.contains("FloatingMenuDefinition.screen(\"music-browser\")\n"
                + "                .framing(FloatingMenuFraming.PANORAMIC)\n"
                + "                .stableAnchor()"));
    }

    @Test
    void contextualHotkeysUseTheSwapHandsEventWithoutReplacingNormalSwaps()
            throws IOException {
        String service = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuService.java"));
        String features = Files.readString(MAIN.resolve(
                "module/commands/SimpleFeaturesModule.java"));

        assertTrue(service.contains("onSwapHandItems(PlayerSwapHandItemsEvent event)"));
        assertTrue(service.contains("actionFor(session, picked, FloatingMenuInteraction.HOTKEY)"));
        assertTrue(service.contains("if (!player.isSneaking() || mainMenuOpener == null) return;"));
        assertTrue(features.contains(".hotkey((p, handle) -> deleteCarriedItem("));
    }

    @Test
    void repeatedPhysicalJukeboxInteractionReanchorsTheExistingControlFlow()
            throws IOException {
        String handle = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuHandle.java"));
        String flow = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuFlow.java"));
        String service = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuService.java"));
        String gui = Files.readString(MAIN.resolve(
                "module/music/ui/JukeboxControlGui.java"));
        String listener = Files.readString(MAIN.resolve(
                "module/music/listener/JukeboxControlListener.java"));

        assertTrue(handle.contains("void reanchor()"));
        assertTrue(flow.contains("public void reanchor()"));
        assertTrue(service.contains("session.captureAnchorView(player)"));
        assertTrue(service.contains("session.reanchor(session.definition, session.layoutSnapshot)"));
        assertTrue(gui.contains("openOrRepositionJukeboxControl("));
        assertTrue(gui.contains("boolean sameTarget = flow.state().target().equals(target)"));
        assertTrue(gui.contains("if (!sameTarget) flow.setState(new ViewState(target, 0))"));
        assertTrue(gui.contains("flow.reanchor()"));
        assertTrue(listener.contains(
                "controlGui.openOrRepositionJukeboxControl(event.getPlayer(), jukebox)"));
    }

    @Test
    void bedrockUsesAnAutomaticDefinitionTranslatorInsteadOfPerMenuBranches()
            throws IOException {
        String service = Files.readString(MAIN.resolve(
                "module/menu/FloatingMenuService.java"));
        String translator = Files.readString(MAIN.resolve(
                "module/menu/BedrockMenuTranslator.java"));
        String presenter = Files.readString(MAIN.resolve(
                "module/menu/GeyserFloatingMenuPresenter.java"));

        assertTrue(service.contains("nativeForms.supports(player)"));
        assertTrue(service.contains("showNativeMenu(session, player)"));
        assertTrue(translator.contains("FloatingMenuDefinition definition"));
        assertTrue(translator.contains("definition.entries().values()"));
        assertTrue(presenter.contains("new BedrockSimpleForm("));
        for (String source : List.of(
                "module/chat/menu/ChatSettingsMenu.java",
                "module/communication/AnnouncementModule.java",
                "module/i18n/LanguageService.java",
                "module/music/ui/JukeboxControlGui.java",
                "module/music/ui/MusicBrowserGui.java",
                "module/player/HomeModule.java",
                "module/player/MainMenuModule.java",
                "module/player/TeleportPreferenceModule.java",
                "module/pvp/PvpMenuController.java")) {
            assertFalse(Files.readString(MAIN.resolve(source)).contains("GeyserApi"), source);
        }
    }

    @Test
    void homeSceneKeepsInspectorInformationAndEveryLegacyActionVisible() throws IOException {
        String home = Files.readString(MAIN.resolve("module/player/HomeModule.java"));
        assertTrue(home.contains("FloatingMenuLayouts.horizontalPanels("));
        assertTrue(home.contains("new HomeMenuState("));
        assertTrue(home.contains(".focus((p, handle, focused)"));
        assertTrue(home.contains("menu.item(\"active-home\""));
        for (String information : List.of(
                "Message.HOME_WORLD", "Message.HOME_LOCATION",
                "Message.HOME_DISTANCE", "Message.HOME_ICON")) {
            assertTrue(home.contains(information), information);
        }
        for (String action : List.of(
                "Message.HOME_ACTION_TELEPORT", "Message.HOME_ACTION_UPDATE",
                "Message.HOME_ACTION_ICON", "Message.HOME_ACTION_DELETE")) {
            assertTrue(home.contains(action), action);
        }
        assertTrue(home.contains("menu.item(\"action:update\""));
        assertTrue(home.contains(".hotkey((p, handle) -> openUpdateConfirmMenu("));
        assertTrue(home.contains(".screen(\"home-update\")"));
        assertTrue(home.contains("Location target = proposedLocation.clone()"));
        assertTrue(home.contains("Message.HOME_CONFIRM_UPDATE_DETAIL_RICH"));
        assertTrue(home.contains("buildHomeHelpMenu("));
        assertTrue(home.contains("Message.HOME_USAGE_NAME_RULE"));
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static List<Path> menuSources() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN.resolve("module/menu"))) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
