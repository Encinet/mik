package org.encinet.mik.feature.community.chat;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.community.chat.game.ChatRelay;
import org.encinet.mik.feature.community.chat.game.ChatSendLedger;
import org.encinet.mik.feature.community.chat.game.GameChatController;
import org.encinet.mik.feature.community.chat.game.GameChatDirectory;
import org.encinet.mik.feature.community.chat.game.GameChatService;
import org.encinet.mik.feature.community.chat.game.GameMentionNotifier;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.menu.MenuPanel;

import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Level;

/** Owns the game chat lifecycle; the shared transport is replaceable. */
public final class ChatModule {
    private final Plugin plugin;
    private final GameChatDirectory directory;
    private final GameChatController game;
    private final GameChatService messages;
    private final ChatSendLedger ledger = new ChatSendLedger();

    public ChatModule(Plugin plugin, GameChatDirectory directory, Function<Player, BooleanSupplier> authentication,
            GameMentionNotifier mentionNotifier, LanguageService language, ChatSettingsStore settings,
            PlayerNameTagRenderer names, PlayerSourceRenderer sources, TipScope tips,
            BiPredicate<UUID, String> menuChatInput) {
        this.plugin = plugin;
        this.directory = directory;
        messages = new GameChatService(directory, authentication, action -> {
            if (Bukkit.isPrimaryThread()) action.run();
            else plugin.getServer().getScheduler().runTask(plugin, action);
        }, (stage, error) -> plugin.getLogger().log(Level.WARNING, "Could not " + stage, error));
        game = new GameChatController(plugin, directory, mentionNotifier, language, settings, names, sources,
                messages, tips, menuChatInput, ledger::allowMessage);
    }

    public GameChatController game() { return game; }

    public void enable(ChatRelay relay) {
        directory.enable(plugin);
        messages.connect(relay);
        game.enable();
    }

    public void disable() {
        messages.disconnect();
        game.disable();
        directory.disable();
        ledger.clear();
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) { game.registerCommands(manager); }
    public void openSettingsMenu(Player player) { game.openSettingsMenu(player); }
    public MenuPanel settingsPanel(Player player, Runnable redraw) { return game.settingsPanel(player, redraw); }
}
