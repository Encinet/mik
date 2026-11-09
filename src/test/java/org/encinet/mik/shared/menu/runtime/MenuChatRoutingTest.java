package org.encinet.mik.shared.menu.runtime;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.community.chat.ChatModule;
import org.encinet.mik.feature.player.guidance.TipTestScope;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.shared.i18n.LanguageService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MenuChatRoutingTest {
    @Test void editorMessagesUseOriginalTextAndNeverReachDelayChannelRenderingOrTheSocialBridge() throws Exception {
        UUID id = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (_, method, _) -> {
                    if (method.getName().equals("getUniqueId")) return id;
                    throw new AssertionError("Editor chat must not read Bukkit player state: " + method.getName());
                });
        Plugin plugin = (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[]{Plugin.class},
                (_, method, _) -> {
                    if (method.getName().equals("namespace")) return "mik";
                    throw new AssertionError("Editor chat must not use the server: " + method.getName());
                });
        var inputs = new MenuInputSessions((_, _) -> { });
        var received = new ArrayList<String>();
        var lease = inputs.beginChat(id, () -> true, received::add, () -> { });
        inputs.tick();
        try (var tips = new TipTestScope()) {
            // The null channel dependencies deliberately fail if editing text escapes the ingress branch.
            var chat = new ChatModule(plugin, null, new LanguageService(null), null, new PlayerNameTagRenderer(plugin), null,
                    _ -> { throw new AssertionError("Editor text reached the social bridge"); }, null, tips, inputs::offerText);
            String raw = "<gradient:red:blue>%player_name% </gradient>";
            var event = new AsyncChatEvent(true, player, new HashSet<>(Set.of(Audience.empty())), ChatRenderer.defaultRenderer(),
                    Component.text("already processed elsewhere"), Component.text(raw), SignedMessage.system(raw, Component.text(raw)));
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                executor.submit(() -> { chat.game().onChatRoute(event); chat.game().onChatCommit(event); }).get();
            }
            assertTrue(event.isCancelled());
            assertTrue(received.isEmpty(), "async ingress cannot update a Bukkit menu directly");
            inputs.tick();
            assertEquals(java.util.List.of(raw), received);
            lease.close();
            assertFalse(inputs.offerText(id, "ordinary chat resumes"));
        }
    }
}
