package org.encinet.mik.feature.community.chat.game;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.model.ChatMentions;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;

import java.util.Collection;

/** Applies accepted mention effects to game recipients on Bukkit's primary thread. */
public final class GameMentionNotifier {

    private final AfkService afkService;
    private final LanguageService languageService;
    private final ChatSettingsStore settingsStore;

    public GameMentionNotifier(AfkService afkService, LanguageService languageService,
                          ChatSettingsStore settingsStore) {
        this.afkService = afkService;
        this.languageService = languageService;
        this.settingsStore = settingsStore;
    }

    public void notifyRecipients(ChatSender sender, TextColor color, ChatMentions mentions, Collection<Player> recipients) {
        if (recipients.isEmpty() || (!mentions.broadcast() && mentions.players().isEmpty())) return;
        Component display = Component.text(sender.displayName(), color);
        for (Player player : recipients) {
            var id = player.getUniqueId();
            if (!mentions.includes(id) || sender.minecraftId().filter(id::equals).isPresent()) continue;
            ChatSettingsStore.ChatSettings settings = settingsStore.get(id);
            if (!settings.mentionAlerts() || (settings.mentionMuteWhileAfk() && afkService.isAfk(id))) continue;
            if (settings.mentionSound()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.35F);
            }
            if (settings.mentionActionBar()) {
                player.sendActionBar(languageService.rich(player, Message.MENTION_ACTION_BAR_TEXT, NamedTextColor.AQUA,
                        RichArg.component("sender", display)));
            }
        }
    }
}
