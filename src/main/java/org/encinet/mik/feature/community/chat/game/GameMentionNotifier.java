package org.encinet.mik.feature.community.chat.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.model.ChatEffect;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;

import java.util.Set;

/** Applies accepted mention effects to game recipients on Bukkit's primary thread. */
public final class GameMentionNotifier {

    private final Plugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final ChatSettingsStore settingsStore;

    public GameMentionNotifier(Plugin plugin, AfkService afkService, LanguageService languageService,
                          ChatSettingsStore settingsStore) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
        this.settingsStore = settingsStore;
    }

    public void notifyEffects(ChatSender sender, Set<ChatEffect> effects, Set<Player> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        ChatEffect.Mentions mentions = effects.stream()
                .filter(ChatEffect.Mentions.class::isInstance)
                .map(ChatEffect.Mentions.class::cast)
                .findFirst().orElse(null);
        if (mentions == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Component display = Component.text(sender.displayName(), TextColor.color(sender.usernameColor()));
            for (Player player : recipients) {
                if (sender.minecraftId().filter(player.getUniqueId()::equals).isPresent()) {
                    continue;
                }
                ChatSettingsStore.ChatSettings settings = settingsStore.get(player.getUniqueId());
                if (!settings.mentionAlerts()) {
                    continue;
                }
                if (settings.mentionMuteWhileAfk() && afkService.isAfk(player.getUniqueId())) {
                    continue;
                }
                if (!mentions.broadcast()
                        && !mentions.playerIds().contains(player.getUniqueId())) {
                    continue;
                }
                if (settings.mentionSound()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.35F);
                }
                if (settings.mentionActionBar()) {
                    player.sendActionBar(languageService.rich(player, Message.MENTION_ACTION_BAR_TEXT, NamedTextColor.AQUA,
                            RichArg.component("sender", display)));
                }
            }
        });
    }
}
