package org.encinet.mik.module.access;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.regex.Pattern;

public class WhitelistModule implements Listener {
    private static final String TEMP_WHITELIST_PERMISSION = "group.member";
    private static final long TEMP_WHITELIST_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private final List<UUID> unwhitelistedPlayerUUIDs = new ArrayList<>();
    private final Map<UUID, Long> temporaryWhitelistByUuidExpiresAt = new ConcurrentHashMap<>();
    private final Map<String, Long> temporaryWhitelistByNameExpiresAt = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final LanguageService languageService;

    public WhitelistModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWhitelistVerify(ProfileWhitelistVerifyEvent event) {
        if (event.isWhitelisted()) return;

        UUID playerUUID = event.getPlayerProfile().getId();
        String playerName = event.getPlayerProfile().getName();
        if (consumeTemporaryWhitelist(playerUUID, playerName)) {
            event.setWhitelisted(true);
            if (playerUUID != null) {
                String displayName = playerName != null ? playerName : playerUUID.toString();
                Bukkit.getScheduler().runTask(plugin, () -> addWhitelistEntry(playerUUID, displayName));
            }
            plugin.getLogger().info("Accepted temporary whitelist entry for "
                    + (playerName != null ? playerName : playerUUID));
            return;
        }

        if (playerUUID != null) {
            unwhitelistedPlayerUUIDs.add(playerUUID);
        }
        event.kickMessage(kickMessage());
    }

    /**
     * Register commands
     */
    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(Commands.literal("tempwhitelist")
                    .requires(source -> source.getSender().hasPermission(TEMP_WHITELIST_PERMISSION))
                    .executes(ctx -> {
                        sendTemporaryWhitelistUsage(ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                String playerName = StringArgumentType.getString(ctx, "player");
                                return addTemporaryWhitelist(sender, playerName);
                            }))
                    .build(), languageService.t(Language.DEFAULT, Message.WHITELIST_TEMP_COMMAND_DESCRIPTION),
                    List.of("twl", "tempwl"));
        });
    }

    private void sendTemporaryWhitelistUsage(CommandSender sender) {
        sender.sendMessage(Component.text()
                .append(Component.text(t(sender, Message.USAGE), NamedTextColor.YELLOW))
                .append(Component.text("/tempwhitelist <player>", NamedTextColor.AQUA))
                .append(Component.text("  " + t(sender, Message.WHITELIST_TEMP_USAGE_DESC), NamedTextColor.GRAY))
                .build());
    }

    private int addTemporaryWhitelist(CommandSender sender, String playerName) {
        if (!PLAYER_NAME_PATTERN.matcher(playerName).matches()) {
            sender.sendMessage(Component.text(t(sender, Message.WHITELIST_INVALID_NAME), NamedTextColor.RED));
            sendTemporaryWhitelistUsage(sender);
            return 0;
        }

        OfflinePlayer knownPlayer = Bukkit.getOfflinePlayerIfCached(playerName);
        if (knownPlayer != null && knownPlayer.isWhitelisted()) {
            sender.sendMessage(Component.text(t(sender, Message.WHITELIST_ALREADY_WHITELISTED, playerName),
                    NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        rememberTemporaryWhitelist(playerName, System.currentTimeMillis() + TEMP_WHITELIST_MILLIS);

        Component playerComponent = Component.text(playerName, NamedTextColor.AQUA);
        Component durationComponent = Component.text(t(sender, Message.WHITELIST_TEMP_DURATION), NamedTextColor.GRAY);
        if (sender instanceof org.bukkit.entity.Player player) {
            sender.sendMessage(languageService.rich(player, Message.WHITELIST_TEMP_ADDED_RICH,
                    NamedTextColor.GREEN,
                    RichArg.component("player", playerComponent, playerName),
                    RichArg.component("duration", durationComponent, t(sender, Message.WHITELIST_TEMP_DURATION))));
        } else {
            sender.sendMessage(Component.text(t(Language.DEFAULT, Message.WHITELIST_TEMP_ADDED_PLAIN,
                    playerName, t(Language.DEFAULT, Message.WHITELIST_TEMP_DURATION)), NamedTextColor.GREEN));
        }
        return Command.SINGLE_SUCCESS;
    }

    private String t(CommandSender sender, Message message, Object... args) {
        if (sender instanceof org.bukkit.entity.Player player) {
            return languageService.t(player, message, args);
        }
        return languageService.t(Language.DEFAULT, message, args);
    }

    private String t(Language language, Message message, Object... args) {
        return languageService.t(language, message, args);
    }

    private void rememberTemporaryWhitelist(String playerName, long expiresAt) {
        OfflinePlayer knownPlayer = Bukkit.getOfflinePlayerIfCached(playerName);
        if (knownPlayer != null) {
            temporaryWhitelistByUuidExpiresAt.put(knownPlayer.getUniqueId(), expiresAt);
            return;
        }
        temporaryWhitelistByNameExpiresAt.put(normalizeName(playerName), expiresAt);
    }

    private boolean consumeTemporaryWhitelist(UUID playerUUID, String playerName) {
        if (playerUUID != null && consumeTemporaryWhitelistByUuid(playerUUID)) {
            return true;
        }
        return playerName != null && consumeTemporaryWhitelistByName(playerName);
    }

    private boolean consumeTemporaryWhitelistByUuid(UUID playerUUID) {
        Long expiresAt = temporaryWhitelistByUuidExpiresAt.get(playerUUID);
        if (expiresAt == null) {
            return false;
        }

        if (System.currentTimeMillis() > expiresAt) {
            temporaryWhitelistByUuidExpiresAt.remove(playerUUID);
            return false;
        }

        temporaryWhitelistByUuidExpiresAt.remove(playerUUID);
        return true;
    }

    private boolean consumeTemporaryWhitelistByName(String playerName) {
        String normalizedName = normalizeName(playerName);
        Long expiresAt = temporaryWhitelistByNameExpiresAt.get(normalizedName);
        if (expiresAt == null) {
            return false;
        }

        if (System.currentTimeMillis() > expiresAt) {
            temporaryWhitelistByNameExpiresAt.remove(normalizedName);
            return false;
        }

        temporaryWhitelistByNameExpiresAt.remove(normalizedName);
        return true;
    }

    private void addWhitelistEntry(UUID playerUUID, String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
        player.setWhitelisted(true);
        plugin.getLogger().info("Added " + playerName + " to whitelist from temporary whitelist entry");
    }

    private String normalizeName(String playerName) {
        return playerName.toLowerCase(Locale.ROOT);
    }

    private Component kickMessage() {
        return Component.text()
                .append(kickMessage(Language.ZH_CN))
                .appendNewline()
                .appendNewline()
                .append(kickMessage(Language.EN_US))
                .build();
    }

    private Component kickMessage(Language language) {
        return Component.text()
                .append(Component.text(languageService.t(language, Message.WHITELIST_KICK_TITLE), NamedTextColor.WHITE))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.WHITELIST_KICK_APPLY_PREFIX), NamedTextColor.GRAY))
                .append(Component.text("mikapply.noctiro.moe", NamedTextColor.AQUA))
                .append(Component.text(languageService.t(language, Message.WHITELIST_KICK_APPLY_SUFFIX), NamedTextColor.GRAY))
                .appendNewline()
                .append(Component.text(languageService.t(language, Message.WHITELIST_KICK_WEBSITE_PREFIX), NamedTextColor.GRAY))
                .append(Component.text("mcmik.top", NamedTextColor.AQUA))
                .build();
    }
}
