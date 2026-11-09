package org.encinet.mik.feature.community.chat;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.encinet.mik.feature.community.chat.game.GameChatController;
import org.encinet.mik.feature.community.chat.game.GameChatIdentityResolver;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.community.chat.bridge.ChatMessagePublisher;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.bridge.SocialChatPublisher;
import org.encinet.mik.feature.community.chat.game.GameMentionNotifier;
import org.encinet.mik.feature.community.chat.runtime.ChatSendLedger;
import org.encinet.mik.feature.community.chat.runtime.ChatService;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.menu.MenuPanel;
import java.util.function.BiPredicate;

/** Owns chat lifecycle and exposes its application and game adapters to bootstrap. */
public final class ChatModule {
    private final GameChatController game;
    private final ChatService messages;
    private final ChatSendLedger ledger = new ChatSendLedger();

    public ChatModule(Plugin plugin, GameMentionNotifier mentionNotifier, LanguageService language,
            ChatSettingsStore settings, PlayerNameTagRenderer names, PlayerSourceRenderer sources,
            SocialChatPublisher social, IdentityBindingManager identities, TipScope tips,
            BiPredicate<java.util.UUID, String> menuChatInput) {
        GameChatIdentityResolver playerNames = new GameChatIdentityResolver(names);
        messages = new ChatService(playerNames, action -> {
            if (Bukkit.isPrimaryThread()) action.run();
            else plugin.getServer().getScheduler().runTask(plugin, action);
        }, social, (output, error) -> plugin.getLogger().warning(
                "Could not deliver accepted chat to " + output + ": " + error.getMessage()));
        game = new GameChatController(plugin, mentionNotifier, language, settings, names, sources,
                playerNames, messages, identities, tips, menuChatInput);
    }
    public ChatService messages() { return messages; }
    public GameChatController game() { return game; }
    public ChatSendLedger ledger() { return ledger; }
    public void enable(ChatParticipants participants, ChatMessagePublisher web) {
        messages.connect(participants, game::deliver, web);
        game.enable(participants, ledger);
    }
    public void disable() {
        messages.disconnect();
        game.disable();
        ledger.clear();
    }
    public void registerCommands(LifecycleEventManager<Plugin> manager) { game.registerCommands(manager); }
    public void openSettingsMenu(Player player) { game.openSettingsMenu(player); }
    public MenuPanel settingsPanel(Player player, Runnable redraw) { return game.settingsPanel(player, redraw); }
}
