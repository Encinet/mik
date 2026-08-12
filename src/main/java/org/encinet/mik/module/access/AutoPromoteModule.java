package org.encinet.mik.module.access;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Automatically promotes players who first joined at least three days ago and
 * have played for at least eight hours.
 */
public class AutoPromoteModule implements Listener {

    private static final long REQUIRED_JOIN_AGE_MILLIS = TimeUnit.DAYS.toMillis(3);
    private static final int REQUIRED_PLAYTIME_TICKS = 20 * 60 * 60 * 8;
    private static final long CHECK_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private static final String TAINT_PERMISSION = "mik.autopromote.taint";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final Map<UUID, Long> lastCheckAt = new HashMap<>();
    private LuckPerms luckPerms;

    public AutoPromoteModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found! AutoPromoteModule disabled.");
            return;
        }
        luckPerms = provider.getProvider();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("AutoPromoteModule enabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        schedulePromotionCheck(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> schedulePromotionCheck(player));
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("promotecheck")
                .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_HELPER))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Bukkit.getOnlinePlayers().stream()
                                    .map(Player::getName)
                                    .filter(n -> n.toLowerCase().startsWith(builder.getRemaining().toLowerCase()))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            String name = StringArgumentType.getString(ctx, "player");
                            Language language = senderLanguage(sender);
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendPromotionReport(sender, name, language));
                            return Command.SINGLE_SUCCESS;
                        })).build(), languageService.t(Language.DEFAULT, Message.PROMOTECHECK_COMMAND_DESCRIPTION)));
    }

    private void sendPromotionReport(CommandSender sender, String name, Language language) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        Component report = !target.hasPlayedBefore()
                ? Component.text(languageService.t(language, Message.PROMOTECHECK_NEVER_PLAYED, name), NamedTextColor.RED)
                : buildPromotionReport(target, language);
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(report));
    }

    private Component buildPromotionReport(OfflinePlayer op, Language language) {
        boolean ageOk = System.currentTimeMillis() - op.getFirstPlayed() >= REQUIRED_JOIN_AGE_MILLIS;
        boolean playtimeOk = op.getStatistic(Statistic.PLAY_ONE_MINUTE) >= REQUIRED_PLAYTIME_TICKS;

        String displayName = op.getName() != null ? op.getName() : op.getUniqueId().toString();
        Component header = Component.text(languageService.t(language, Message.PROMOTECHECK_HEADER, displayName), NamedTextColor.GOLD);
        if (op.getPlayer() == null) {
            header = header.append(Component.text("  " + languageService.t(language, Message.PROMOTECHECK_OFFLINE), NamedTextColor.GRAY));
        }

        return header
                .append(Component.newline())
                .append(status(ageOk)).append(Component.text("  " + languageService.t(language, Message.PROMOTECHECK_ACCOUNT_AGE), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(status(playtimeOk)).append(Component.text("  " + languageService.t(language, Message.PROMOTECHECK_PLAYTIME), NamedTextColor.WHITE));
    }

    private Component status(boolean ok) {
        return ok ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✘", NamedTextColor.RED);
    }

    private boolean shouldPromotePlayer(OfflinePlayer player) {
        return System.currentTimeMillis() - player.getFirstPlayed() >= REQUIRED_JOIN_AGE_MILLIS
                && player.getStatistic(Statistic.PLAY_ONE_MINUTE) >= REQUIRED_PLAYTIME_TICKS;
    }

    private void schedulePromotionCheck(Player player) {
        if (player.hasPermission(TAINT_PERMISSION) || player.hasPermission("group." + Mik.GROUP_MEMBER)) {
            lastCheckAt.remove(player.getUniqueId());
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastChecked = lastCheckAt.getOrDefault(playerId, 0L);
        if (now - lastChecked < CHECK_INTERVAL_MILLIS) {
            return;
        }

        lastCheckAt.put(playerId, now);
        String playerName = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            if (shouldPromotePlayer(offlinePlayer)) {
                promotePlayer(playerId, playerName);
            }
        });
    }

    /**
     * Promote player to member group
     */
    private void promotePlayer(UUID playerId, String playerName) {
        User user = luckPerms.getUserManager().getUser(playerId);
        if (user == null) {
            return;
        }

        // Add member group
        Node memberNode = Node.builder("group." + Mik.GROUP_MEMBER).build();
        user.data().add(memberNode);

        // Set member as primary group
        user.setPrimaryGroup(Mik.GROUP_MEMBER);

        // Save changes
        luckPerms.getUserManager().saveUser(user);

        // Notify player on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                return;
            }
            lastCheckAt.remove(playerId);
            player.sendMessage(MINI_MESSAGE.deserialize(languageService.t(player, Message.AUTOPROMOTE_SUCCESS_MM)));
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
        });

        plugin.getLogger().info("Promoted player " + playerName + " to member group");
    }

    private Language senderLanguage(CommandSender sender) {
        if (sender instanceof Player player) {
            return languageService.language(player);
        }
        return Language.DEFAULT;
    }
}
