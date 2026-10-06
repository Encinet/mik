package org.encinet.mik.module.chat.mention;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.chat.ChatSettingsStore;
import org.encinet.mik.module.chat.model.ChatEffect;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

public final class MentionService {

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final ChatSettingsStore settingsStore;
    private final Function<Player, Component> senderDisplayRenderer;

    public MentionService(JavaPlugin plugin, AfkService afkService, LanguageService languageService,
                          ChatSettingsStore settingsStore, Function<Player, Component> senderDisplayRenderer) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
        this.settingsStore = settingsStore;
        this.senderDisplayRenderer = senderDisplayRenderer;
    }

    public String summary(Player player) {
        ChatSettingsStore.ChatSettings settings = settingsStore.get(player.getUniqueId());
        if (!settings.mentionAlerts()) {
            return languageService.t(player, Message.MENTION_SUMMARY_DISABLED);
        }
        List<String> enabled = new ArrayList<>();
        if (settings.mentionSound()) enabled.add(languageService.t(player, Message.MENTION_SUMMARY_SOUND));
        if (settings.mentionActionBar()) enabled.add(languageService.t(player, Message.MENTION_SUMMARY_ACTION_BAR));
        if (enabled.isEmpty()) {
            return languageService.t(player, settings.mentionMuteWhileAfk()
                    ? Message.MENTION_SUMMARY_ENABLED_AFK
                    : Message.MENTION_SUMMARY_ENABLED);
        }
        String modes = String.join(" + ", enabled);
        return settings.mentionMuteWhileAfk()
                ? languageService.t(player, Message.MENTION_SUMMARY_MODES_AFK, modes)
                : modes;
    }

    public void notifyEffects(
            Player sender,
            Set<ChatEffect> effects,
            Set<Player> recipients
    ) {
        if (recipients.isEmpty()) {
            return;
        }
        UUID senderId = sender.getUniqueId();
        notifyEffects(Optional.of(senderId),
                () -> senderDisplayRenderer.apply(sender), sender.getName(),
                effects, recipients);
    }

    public void notifyEffects(
            Optional<UUID> senderId,
            Component senderDisplay,
            String senderName,
            Set<ChatEffect> effects,
            Set<Player> recipients
    ) {
        notifyEffects(senderId, () -> senderDisplay, senderName,
                effects, recipients);
    }

    private void notifyEffects(
            Optional<UUID> senderId,
            Supplier<Component> senderDisplay,
            String senderName,
            Set<ChatEffect> effects,
            Set<Player> recipients
    ) {
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
            Component display = senderDisplay.get();
            for (Player player : recipients) {
                if (senderId.filter(player.getUniqueId()::equals).isPresent()) {
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
                            RichArg.component("sender", display, senderName)));
                }
            }
        });
    }
}
