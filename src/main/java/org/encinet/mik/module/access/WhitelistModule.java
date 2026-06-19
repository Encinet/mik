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
    private final Map<String, Long> temporaryWhitelistExpiresAt = new ConcurrentHashMap<>();

    // Bilingual since IP is not available at this stage
    private static final Component KICK_MSG = Component.text()
            .append(Component.text("你不在白名单中", NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("前往 ", NamedTextColor.GRAY))
            .append(Component.text("mikapply.noctiro.moe", NamedTextColor.AQUA))
            .append(Component.text(" 申请白名单", NamedTextColor.GRAY))
            .appendNewline()
            .append(Component.text("官网 ", NamedTextColor.GRAY))
            .append(Component.text("mik.noctiro.moe", NamedTextColor.AQUA))
            .appendNewline()
            .appendNewline()
            .append(Component.text("You are not whitelisted", NamedTextColor.WHITE))
            .appendNewline()
            .append(Component.text("Apply at ", NamedTextColor.GRAY))
            .append(Component.text("mikapply.noctiro.moe", NamedTextColor.AQUA))
            .appendNewline()
            .append(Component.text("Website: ", NamedTextColor.GRAY))
            .append(Component.text("mik.noctiro.moe", NamedTextColor.AQUA))
            .build();

    private final JavaPlugin plugin;

    public WhitelistModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWhitelistVerify(ProfileWhitelistVerifyEvent event) {
        if (event.isWhitelisted()) return;

        String playerName = event.getPlayerProfile().getName();
        if (playerName != null && consumeTemporaryWhitelist(playerName)) {
            UUID playerUUID = event.getPlayerProfile().getId();
            event.setWhitelisted(true);
            if (playerUUID != null) {
                Bukkit.getScheduler().runTask(plugin, () -> addWhitelistEntry(playerUUID, playerName));
            }
            plugin.getLogger().info("Accepted temporary whitelist entry for " + playerName);
            return;
        }

        UUID playerUUID = event.getPlayerProfile().getId();
        if (playerUUID != null) {
            unwhitelistedPlayerUUIDs.add(playerUUID);
        }
        event.kickMessage(KICK_MSG);
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
                    .build(), "添加 1 小时临时白名单", List.of("twl", "tempwl"));
        });
    }

    private void sendTemporaryWhitelistUsage(CommandSender sender) {
        sender.sendMessage(Component.text()
                .append(Component.text("用法 ", NamedTextColor.YELLOW))
                .append(Component.text("/tempwhitelist <玩家名>", NamedTextColor.AQUA))
                .append(Component.text("  添加 1 小时临时白名单", NamedTextColor.GRAY))
                .build());
    }

    private int addTemporaryWhitelist(CommandSender sender, String playerName) {
        if (!PLAYER_NAME_PATTERN.matcher(playerName).matches()) {
            sender.sendMessage(Component.text("玩家名格式不正确", NamedTextColor.RED));
            sendTemporaryWhitelistUsage(sender);
            return 0;
        }

        OfflinePlayer knownPlayer = Bukkit.getOfflinePlayerIfCached(playerName);
        if (knownPlayer != null && knownPlayer.isWhitelisted()) {
            sender.sendMessage(Component.text(playerName + " 已经在白名单中", NamedTextColor.YELLOW));
            return Command.SINGLE_SUCCESS;
        }

        long expiresAt = System.currentTimeMillis() + TEMP_WHITELIST_MILLIS;
        temporaryWhitelistExpiresAt.put(normalizeName(playerName), expiresAt);

        sender.sendMessage(Component.text()
                .append(Component.text("已添加 ", NamedTextColor.GREEN))
                .append(Component.text(playerName, NamedTextColor.AQUA))
                .append(Component.text(" 的临时白名单 ", NamedTextColor.GREEN))
                .append(Component.text("1 小时", NamedTextColor.GRAY))
                .build());
        return Command.SINGLE_SUCCESS;
    }

    private boolean consumeTemporaryWhitelist(String playerName) {
        String normalizedName = normalizeName(playerName);
        Long expiresAt = temporaryWhitelistExpiresAt.get(normalizedName);
        if (expiresAt == null) {
            return false;
        }

        if (System.currentTimeMillis() > expiresAt) {
            temporaryWhitelistExpiresAt.remove(normalizedName);
            return false;
        }

        temporaryWhitelistExpiresAt.remove(normalizedName);
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
}
