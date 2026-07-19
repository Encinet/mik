package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.util.PlayerDisplay;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerPresenceModule implements Listener {

    private static final int EASTER_EGG_ONE_IN = 8;
    private static final String JOIN_EGGS = "presence-join-eggs";
    private static final String QUIT_EGGS = "presence-quit-eggs";

    private final JavaPlugin plugin;
    private final LanguageService languageService;

    public PlayerPresenceModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("PlayerPresenceModule enabled");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Component original = clickablePresenceMessage(event.joinMessage(), event.getPlayer());
        if (shouldUseEasterEgg()) {
            event.joinMessage(null);
            broadcast(event.getPlayer(), JOIN_EGGS, original, true);
        } else {
            event.joinMessage(original);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.quitMessage() == null) {
            return;
        }
        Component original = clickablePresenceMessage(event.quitMessage(), event.getPlayer());
        if (shouldUseEasterEgg()) {
            event.quitMessage(null);
            broadcast(event.getPlayer(), QUIT_EGGS, original, false);
        } else {
            event.quitMessage(original);
        }
    }

    private boolean shouldUseEasterEgg() {
        return ThreadLocalRandom.current().nextInt(EASTER_EGG_ONE_IN) == 0;
    }

    private void broadcast(Player subject, String messageId, Component original, boolean joining) {
        Map<Language, Optional<Component>> localizedMessages = new EnumMap<>(Language.class);

        for (Player recipient : Bukkit.getOnlinePlayers()) {
            if (!joining && recipient.getUniqueId().equals(subject.getUniqueId())) {
                continue;
            }

            Language language = languageService.language(recipient);
            Optional<Component> message = localizedMessages.computeIfAbsent(language,
                    ignored -> randomEasterEgg(subject, language, messageId));
            message.ifPresentOrElse(recipient::sendMessage, () -> {
                if (original != null) {
                    recipient.sendMessage(original);
                }
            });
        }
    }

    private Optional<Component> randomEasterEgg(Player subject, Language language, String messageId) {
        List<String> entries = languageService.attributeNames(language, messageId);
        if (entries.isEmpty()) {
            return Optional.empty();
        }

        String entry = entries.get(ThreadLocalRandom.current().nextInt(entries.size()));
        return languageService.richAttribute(language, messageId, entry, NamedTextColor.YELLOW,
                RichArg.component("player", clickablePlayerName(subject), subject.getName()));
    }

    private Component clickablePresenceMessage(Component message, Player subject) {
        if (!(message instanceof TranslatableComponent translatable)
                || !(translatable.key().equals("multiplayer.player.joined")
                || translatable.key().equals("multiplayer.player.left"))) {
            return message;
        }
        return translatable.arguments(clickablePlayerName(subject));
    }

    private Component clickablePlayerName(Player player) {
        return PlayerDisplay.name(player, NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.suggestCommand("/tell " + player.getName() + " "));
    }
}
