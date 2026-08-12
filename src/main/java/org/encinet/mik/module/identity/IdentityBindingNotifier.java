package org.encinet.mik.module.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.Objects;
import java.util.function.Function;

/** Delivers asynchronous binding lifecycle notifications to online Minecraft players. */
final class IdentityBindingNotifier {

    private final JavaPlugin plugin;
    private final LanguageService languages;
    private Function<String, String> platformNames = Function.identity();

    IdentityBindingNotifier(JavaPlugin plugin, LanguageService languages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    void platformNames(Function<String, String> platformNames) {
        this.platformNames = Objects.requireNonNull(platformNames, "platformNames");
    }

    void linked(IdentityBinding binding) {
        notify(binding, Message.IDENTITY_NOTIFY_LINKED);
    }

    void unlinked(IdentityBinding binding) {
        notify(binding, Message.IDENTITY_NOTIFY_UNLINKED);
    }

    private void notify(IdentityBinding binding, Message message) {
        Runnable notification = () -> {
            Player player = Bukkit.getPlayer(binding.playerId());
            if (player != null && player.isOnline()) {
                player.sendMessage(Component.text(languages.t(player, message,
                        platformNames.apply(binding.externalKey().platform())),
                        NamedTextColor.YELLOW));
            }
        };
        if (Bukkit.isPrimaryThread()) {
            notification.run();
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, notification);
        } catch (RuntimeException ignored) {
            // The plugin may have stopped between the operation and notification.
        }
    }
}
