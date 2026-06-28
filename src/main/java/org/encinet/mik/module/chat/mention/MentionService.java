package org.encinet.mik.module.chat.mention;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.chat.ChatSettingsStore;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class MentionService implements Listener {

    private static final Pattern AT_ALL_PATTERN = Pattern.compile("(?iu)(?<![\\p{Alnum}_])@all(?![\\p{Alnum}_])");

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

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public String summary(Player player) {
        ChatSettingsStore.ChatSettings settings = settingsStore.get(player.getUniqueId());
        if (!settings.mentionAlerts()) {
            return languageService.t(player, Message.MENTION_SUMMARY_DISABLED);
        }
        List<String> enabled = new ArrayList<>();
        if (settings.mentionSound()) enabled.add(languageService.t(player, Message.MENTION_SUMMARY_SOUND));
        if (settings.mentionActionBar()) enabled.add(languageService.t(player, Message.MENTION_SUMMARY_ACTION_BAR));
        String suffix = settings.mentionMuteWhileAfk() ? languageService.t(player, Message.MENTION_SUMMARY_AFK_SUFFIX) : "";
        return enabled.isEmpty()
                ? languageService.t(player, Message.MENTION_SUMMARY_ENABLED) + suffix
                : String.join(" + ", enabled) + suffix;
    }

    public void notifyPrivateMessage(Player sender, String message, Player target) {
        notifyMentions(sender, message, Set.of(target));
    }

    public void notifyMessage(Player sender, String message, Set<Player> recipients) {
        notifyMentions(sender, message, recipients);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatMention(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        notifyMentions(event.getPlayer(), message, playersIn(event.viewers()));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        settingsStore.forget(event.getPlayer().getUniqueId());
    }

    private void notifyMentions(Player sender, String message, Set<Player> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        UUID senderId = sender.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean allMention = sender.hasPermission("group." + Mik.GROUP_MANAGER)
                    && AT_ALL_PATTERN.matcher(message).find();
            Component senderDisplay = senderDisplayRenderer.apply(sender);
            for (Player player : recipients) {
                if (player.getUniqueId().equals(senderId)) {
                    continue;
                }
                ChatSettingsStore.ChatSettings settings = settingsStore.get(player.getUniqueId());
                if (!settings.mentionAlerts()) {
                    continue;
                }
                if (settings.mentionMuteWhileAfk() && afkService.isAfk(player.getUniqueId())) {
                    continue;
                }
                if (!allMention && !mentionsPlayer(player, message)) {
                    continue;
                }
                if (settings.mentionSound()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.35F);
                }
                if (settings.mentionActionBar()) {
                    player.sendActionBar(Component.text()
                            .append(senderDisplay)
                            .append(Component.text(languageService.t(player, Message.MENTION_ACTION_BAR_TEXT), NamedTextColor.AQUA))
                            .build());
                }
            }
        });
    }

    private boolean mentionsPlayer(Player target, String message) {
        return message.toLowerCase(Locale.ROOT).contains(target.getName().toLowerCase(Locale.ROOT));
    }

    private Set<Player> playersIn(Set<Audience> viewers) {
        Set<Player> players = new HashSet<>();
        for (Audience viewer : viewers) {
            if (viewer instanceof Player player) {
                players.add(player);
            }
        }
        return players;
    }
}
