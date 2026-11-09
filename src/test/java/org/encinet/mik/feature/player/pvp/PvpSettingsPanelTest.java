package org.encinet.mik.feature.player.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry;
import static org.junit.jupiter.api.Assertions.*;

class PvpSettingsPanelTest {
    @Test void embeddedChoicesPersistAndKeepTheCombatLockWithoutLeavingTheHost(@TempDir Path temporary) throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var viewer = fixture.player(1); UUID id = viewer.id;
            var redraws = new AtomicInteger();
            try (var panel = fixture.controller.settingsPanel(viewer.player, redraws::incrementAndGet)) {
                click(panel, viewer.player, "setting:enabled");
                assertTrue(fixture.store.get(id).enabled());
                click(panel, viewer.player, "setting:protect_mobs");
                assertFalse(fixture.store.get(id).protectMobs());
                fixture.tag(id, System.currentTimeMillis() + 60_000);
                click(panel, viewer.player, "setting:enabled");
                assertTrue(fixture.store.get(id).enabled(), "Combat must still prevent disabling PVP in the embedded pane");
                assertEquals(1, viewer.messages.size());
                fixture.tag(id, 0L);
                click(panel, viewer.player, "setting:enabled");
                assertFalse(fixture.store.get(id).enabled());
                assertFalse(YamlConfiguration.loadConfiguration(temporary.resolve("pvp.yml").toFile()).getBoolean(id + ".enabled"));
                assertEquals(4, redraws.get());
            }
        }
    }

    @Test void retiredRenderClosedPanelAndStoppedControllerCannotChangePreferences(@TempDir Path temporary) throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var viewer = fixture.player(1); var redraws = new AtomicInteger();
            var panel = fixture.controller.settingsPanel(viewer.player, redraws::incrementAndGet);
            var retired = entry(panel.render(), "setting:enabled");
            var current = entry(panel.render(), "setting:enabled");
            trigger(retired, viewer.player);
            assertFalse(fixture.store.get(viewer.id).enabled()); assertEquals(0, redraws.get());
            trigger(current, viewer.player);
            assertTrue(fixture.store.get(viewer.id).enabled()); assertEquals(1, redraws.get());
            var closed = entry(panel.render(), "setting:enabled");
            panel.close(); trigger(closed, viewer.player);
            assertTrue(fixture.store.get(viewer.id).enabled()); assertEquals(1, redraws.get());
            var other = fixture.controller.settingsPanel(viewer.player, redraws::incrementAndGet);
            var action = entry(other.render(), "setting:enabled");
            fixture.controller.close(); trigger(action, viewer.player);
            assertTrue(fixture.store.get(viewer.id).enabled()); assertEquals(1, redraws.get());
            other.close();
        }
    }

    @Test void adminActionsRecheckTheRoleAndCurrentTargetBeforeReadingOrWritingPreferences(@TempDir Path temporary) throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var viewer = fixture.player(1); var target = fixture.player(2);
            assertFalse(fixture.controller.toggleAdmin(viewer.player, target.id));
            assertFalse(fixture.store.get(target.id).enabled()); assertTrue(target.actionBars.isEmpty());
            viewer.moderator = true;
            assertTrue(fixture.controller.toggleAdmin(viewer.player, target.id));
            assertTrue(fixture.store.get(target.id).enabled()); assertEquals(1, target.actionBars.size());
            viewer.moderator = false;
            assertFalse(fixture.controller.toggleAdmin(viewer.player, target.id));
            assertTrue(fixture.store.get(target.id).enabled()); assertEquals(1, target.actionBars.size());
            viewer.moderator = true; target.online = false;
            assertFalse(fixture.controller.toggleAdmin(viewer.player, target.id));
            assertTrue(fixture.store.get(target.id).enabled());
            fixture.players.remove(target.id);
            assertFalse(fixture.controller.toggleAdmin(viewer.player, target.id));
            assertTrue(fixture.store.get(target.id).enabled());
            assertEquals(List.of(Component.text(Message.PVP_NO_PERMISSION_MM.key()), Component.text(Message.PVP_SET_OTHER_RICH.key()),
                    Component.text(Message.PVP_NO_PERMISSION_MM.key()), Component.text(Message.PVP_PLAYER_NOT_FOUND_MM.key()),
                    Component.text(Message.PVP_PLAYER_NOT_FOUND_MM.key())), viewer.messages);
        }
    }

    @Test void anAdminToggleUsesTheLatestPreferenceAndTheNewOnlineObjectForTheSameUuid(@TempDir Path temporary) throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var viewer = fixture.player(1); viewer.moderator = true;
            var oldTarget = fixture.player(2);
            assertFalse(fixture.store.get(oldTarget.id).enabled());
            fixture.store.save(oldTarget.id, fixture.store.get(oldTarget.id).withEnabled(true));
            oldTarget.online = false;
            var currentTarget = fixture.player(2);
            assertTrue(fixture.controller.toggleAdmin(viewer.player, oldTarget.id));
            assertFalse(fixture.store.get(oldTarget.id).enabled(), "Toggle the current preference rather than the rendered value");
            assertTrue(oldTarget.actionBars.isEmpty()); assertEquals(1, currentTarget.actionBars.size());
            fixture.controller.close();
            assertFalse(fixture.controller.toggleAdmin(viewer.player, currentTarget.id));
            assertFalse(fixture.store.get(oldTarget.id).enabled()); assertEquals(1, currentTarget.actionBars.size());
        }
    }

    @Test void aPanelIsBoundToItsViewerAndResolvesTheCurrentOnlinePlayer(@TempDir Path temporary) throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var viewer = fixture.player(1); var other = fixture.player(2); other.moderator = true;
            var redraws = new AtomicInteger();
            try (var panel = fixture.controller.settingsPanel(viewer.player, redraws::incrementAndGet)) {
                var action = entry(panel.render(), "setting:enabled");
                trigger(action, other.player);
                assertFalse(fixture.store.get(viewer.id).enabled()); assertEquals(0, redraws.get());
                viewer.online = false; trigger(action, viewer.player);
                assertFalse(fixture.store.get(viewer.id).enabled());
                viewer.online = true; fixture.players.remove(viewer.id); trigger(action, viewer.player);
                assertFalse(fixture.store.get(viewer.id).enabled());
            }
        }
    }

    @Test void changingTargetsKeepsTheScreenOwnerButReplacesEverySettingInputIdentity(@TempDir Path temporary) throws Exception {
        try (var fixture = new Fixture(temporary)) {
            var first = fixture.player(1); var second = fixture.player(2);
            try (var firstPanel = fixture.controller.settingsPanel(first.player, () -> { });
                 var secondPanel = fixture.controller.settingsPanel(second.player, () -> { })) {
                var firstScene = firstPanel.render(); var secondScene = secondPanel.render();
                assertEquals("pvp-settings", firstScene.screenId()); assertEquals(firstScene.screenId(), secondScene.screenId());
                for (var key : PvpSettingKey.values()) {
                    String name = "setting:" + key.name().toLowerCase(Locale.ROOT);
                    assertNotEquals(entry(firstScene, name).id(), entry(secondScene, name).id());
                }
                trigger(entry(firstScene, "setting:enabled"), first.player);
                assertTrue(fixture.store.get(first.id).enabled()); assertFalse(fixture.store.get(second.id).enabled());
                trigger(entry(secondScene, "setting:enabled"), second.player);
                assertTrue(fixture.store.get(first.id).enabled()); assertTrue(fixture.store.get(second.id).enabled());
            }
        }
    }

    private static void click(MenuPanel panel, Player player, String key) { trigger(entry(panel.render(), key), player); }
    private static void trigger(MenuScene.Entry entry, Player player) {
        assertNotNull(entry); entry.trigger(FloatingMenuInteraction.PRIMARY).execute(player, null, FloatingMenuInteraction.PRIMARY);
    }

    private static final class Fixture implements AutoCloseable {
        final Map<UUID, Player> players = new HashMap<>();
        final PvpSettingsStore store = new PvpSettingsStore(null);
        final TipTestScope tips = new TipTestScope();
        final PvpCombatController combat;
        final PvpMenuController controller;
        Fixture(Path temporary) throws Exception {
            var language = new LanguageService(null) {
                @Override public String t(Player player, Message message, Object... args) { return message.key(); }
                @Override public Language language(Player player) { return Language.EN_US; }
                @Override public Component rich(Player player, Message message, NamedTextColor color, RichArg... args) { return Component.text(message.key()); }
            };
            var data = PvpSettingsStore.class.getDeclaredField("settingsData"); data.setAccessible(true); data.set(store, new YamlConfiguration());
            var destination = PvpSettingsStore.class.getDeclaredField("settingsFile"); destination.setAccessible(true);
            destination.set(store, temporary.resolve("pvp.yml").toFile());
            var resolver = new PvpStateResolver() {
                @Override public boolean effectiveEnabled(UUID id) { return store.get(id).enabled(); }
                @Override public boolean effectiveEnabled(UUID id, boolean preference) { return preference; }
                @Override public boolean hasOverride(UUID id) { return false; }
            };
            combat = new PvpCombatController(null, language, store, resolver, tips, players::get);
            controller = new PvpMenuController(language, store, combat, resolver, () -> List.copyOf(players.values()), players::get);
        }
        TestPlayer player(int value) {
            var player = new TestPlayer(new UUID(0, value)); players.put(player.id, player.player); return player;
        }
        @SuppressWarnings("unchecked") // The reflected field is declared Map<UUID, Long>; vary combat state without a server clock.
        void tag(UUID player, long deadline) throws Exception {
            var field = PvpCombatController.class.getDeclaredField("combatTaggedUntil"); field.setAccessible(true);
            ((Map<UUID, Long>) field.get(combat)).put(player, deadline);
        }
        @Override public void close() { try { controller.close(); } finally { tips.close(); } }
    }

    private static final class TestPlayer {
        final UUID id;
        boolean online = true, moderator;
        final List<Component> messages = new ArrayList<>(), actionBars = new ArrayList<>();
        final Player player;
        TestPlayer(UUID id) {
            this.id = id;
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "Player_" + id.getLeastSignificantBits();
                case "displayName" -> Component.text("Player_" + id.getLeastSignificantBits());
                case "isOnline" -> online;
                case "hasPermission" -> moderator;
                case "sendMessage" -> { messages.add((Component) args[0]); yield null; }
                case "sendActionBar" -> { actionBars.add((Component) args[0]); yield null; }
                case "toString" -> id.toString();
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }
}
