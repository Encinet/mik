package org.encinet.mik.shell.menu;

import net.quickwrite.fluent4j.container.FluentBundle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.menu.ChatSettingsMenu;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.feature.player.pvp.PvpModule;
import org.encinet.mik.feature.player.teleport.TeleportPreferenceModule;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

class SettingsMenuCompositionTest {
    private static final Player PLAYER = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (_, method, _) -> switch (method.getName()) {
                case "getUniqueId" -> new UUID(0, 1);
                case "getName" -> "Player";
                default -> throw new UnsupportedOperationException(method.getName());
            });

    @Test void embeddedChatChoicesChangeTheOwnedPreferenceAndRequestAHostRedraw() {
        var language = new LanguageService(null) {
            @Override public String t(Player player, Message message, Object... args) { return message.key(); }
        };
        var store = new ChatSettingsStore(null);
        var redraws = new AtomicInteger();
        var panel = new ChatSettingsMenu(language, store).panel(PLAYER, redraws::incrementAndGet);
        try {
            var hosted = SettingsMenuTestSupport.frame(panel.render(), Message::key, "CHAT");
            entry(hosted, "mention:alerts").trigger(FloatingMenuInteraction.PRIMARY).execute(PLAYER, null, FloatingMenuInteraction.PRIMARY);
            assertFalse(store.get(PLAYER.getUniqueId()).mentionAlerts());
            assertFalse(entry(panel.render(), "mention:alerts").selected());
            var retiredMention = entry(panel.render(), "mention:alerts").trigger(FloatingMenuInteraction.PRIMARY);
            entry(panel.render(), "select-field:delays").trigger(FloatingMenuInteraction.PRIMARY).execute(PLAYER, null, FloatingMenuInteraction.PRIMARY);
            retiredMention.execute(PLAYER, null, FloatingMenuInteraction.PRIMARY);
            assertFalse(store.get(PLAYER.getUniqueId()).mentionAlerts(), "Retired groups cannot mutate preferences");
            entry(panel.render(), "delay:5s").trigger(FloatingMenuInteraction.PRIMARY).execute(PLAYER, null, FloatingMenuInteraction.PRIMARY);
            assertEquals(org.encinet.mik.feature.community.chat.settings.ChatDelayOption.FIVE_SECONDS, store.get(PLAYER.getUniqueId()).delay());
            assertTrue(entry(panel.render(), "delay:5s").selected());
            assertEquals(3, redraws.get());
            var retiredDelay = entry(panel.render(), "delay:3s").trigger(FloatingMenuInteraction.PRIMARY);
            panel.close();
            retiredDelay.execute(PLAYER, null, FloatingMenuInteraction.PRIMARY);
            assertEquals(org.encinet.mik.feature.community.chat.settings.ChatDelayOption.FIVE_SECONDS, store.get(PLAYER.getUniqueId()).delay());
            assertTrue(panel.render().entries().isEmpty());
            assertEquals(3, redraws.get());
        } finally {
            panel.close();
        }
    }

    @Test void realSettingsFitBesideTheSameCategoryRailInEveryLanguageAndTextSize() throws Exception {
        for (Language locale : Language.values()) {
            FluentBundle bundle = FluentTestSupport.bundle(locale);
            LanguageService language = new LanguageService(null) {
                @Override public Language language(Player player) { return locale; }
                @Override public String preference(UUID id) { return locale.id(); }
                @Override public String t(Player player, Message message, Object... args) { return FluentTestSupport.text(bundle, message, args); }
                @Override public String t(Language value, Message message, Object... args) { return FluentTestSupport.text(bundle, message, args); }
            };
            var chat = new ChatSettingsMenu(language, new ChatSettingsStore(null));
            var teleports = new TeleportPreferenceModule(null, null, language);
            field(teleports, "settingsData", new YamlConfiguration());
            try (var tips = new TipTestScope()) {
                var pvp = new PvpModule(null, language, tips);
                field(field(pvp, "settingsStore"), "settingsData", new YamlConfiguration());
                Map<SettingsMenu.Category, MenuPanel> panels = Map.of(
                        SettingsMenu.Category.LANGUAGE, new LanguageMenu(language).panel(PLAYER, () -> { }),
                        SettingsMenu.Category.CHAT, chat.panel(PLAYER, () -> { }),
                        SettingsMenu.Category.TELEPORT, teleports.settingsPanel(PLAYER, () -> { }),
                        SettingsMenu.Category.PVP, pvp.settingsPanel(PLAYER, () -> { }));
                for (FloatingMenuTextScale scale : FloatingMenuTextScale.values()) {
                    UiFrame reference = null;
                    var layout = new SceneGeometryTestSupport.LayoutSession();
                    for (var entry : panels.entrySet()) {
                        var scene = SettingsMenuTestSupport.frame(entry.getValue().render(),
                                message -> FluentTestSupport.text(bundle, message), entry.getKey().name());
                        String detail = locale + " @ " + scale + " " + entry.getKey();
                        UiFrame frame = assertDoesNotThrow(() -> layout.measure(scene, scale.factor()), detail);
                        assertDisjoint(frame);
                        if (entry.getKey() != SettingsMenu.Category.LANGUAGE) assertColumn(frame.elements().stream().filter(element -> {
                            String key = element.node().key().value();
                            return key.startsWith("mention:") || key.startsWith("delay:") || key.startsWith("setting:");
                        }).toList());
                        if (entry.getKey() == SettingsMenu.Category.CHAT && scale == FloatingMenuTextScale.NORMAL) {
                            var current = element(frame,"mention:alerts");
                            var lastGroup = element(frame,"select-field:delays");
                            assertTrue(current.y() >= lastGroup.y() + lastGroup.size().height(),detail);
                        }
                        assertEquals("main-settings", scene.screenId());
                        assertFalse(scene.titleVisible());
                        assertEquals(1, scene.entries().values().stream().filter(node -> node.id().contains("/category:") && node.selected()).count());
                        for (SettingsMenu.Category category : SettingsMenu.Category.values()) {
                            String key = "category:" + category.id();
                            if (frame.elements().stream().noneMatch(node -> node.node().key().value().equals(key))) continue;
                            assertTrue(entry(scene, key).keepAccessible(), detail);
                            assertEquals(UiRole.NAVIGATION, entry(scene, key).role());
                            if (reference != null && reference.elements().stream().anyMatch(node -> node.node().key().value().equals(key))) {
                                assertEquals(element(reference, key).x(), element(frame, key).x(), 1e-6, detail);
                                assertEquals(element(reference, key).y(), element(frame, key).y(), 1e-6, detail);
                            }
                        }
                        var rail = frame.panels().stream().filter(panel -> panel.path().endsWith("/sidebar")).findFirst().orElseThrow();
                        double occupiedRight = frame.panels().stream().mapToDouble(panel -> panel.x() + panel.size().width()).max().orElseThrow();
                        assertEquals(occupiedRight,frame.size().width(),1e-6,
                                "Short settings must not retain an empty slot to their right: " + detail);
                        assertTrue(frame.panels().stream().filter(panel -> !panel.path().equals(rail.path()))
                                .allMatch(panel -> panel.x() >= rail.x() + rail.size().width()), detail);
                        var main = SceneGeometryTestSupport.readingPose(scene, scale.factor());
                        assertEquals(0, main.yawDegrees(), 1e-6, detail);
                        assertEquals(0, main.pitchDegrees(), 1e-6, detail);
                        var pose = SceneGeometryTestSupport.pose(scene, scale.factor(), frame.elements().stream().filter(node -> node.node().key().value().startsWith("category:")).findFirst().orElseThrow().node().key().value());
                        assertTrue(pose.yawDegrees() < 0, detail);
                        assertEquals(0, pose.pitchDegrees(), 1e-6, detail);
                        reference = frame;
                    }
                }
            }
        }
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void field(Object target, String name, Object value) throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
