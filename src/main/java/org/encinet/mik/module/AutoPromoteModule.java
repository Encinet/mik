package org.encinet.mik.module;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/**
 * Module for automatic player promotion based on activity stats.
 * <p>
 * Algorithm:
 * Hard requirements (rejected immediately if not met):
 * - Account age >= 2 days
 * - Playtime   >= 8 hours
 * <p>
 * Score-based criteria — each Criterion defines:
 * full  : value at which full points are awarded (linear from 0 to full)
 * max   : point cap for this criterion (prevents single-stat abuse)
 * <p>
 * Points are awarded proportionally: score = clamp(value * max / full, 0, max)
 * No single criterion can hard-fail the check — players earn points across all stats.
 * <p>
 * Promotion requires total score >= SCORE_THRESHOLD (100).
 */
public class AutoPromoteModule implements Listener {

    /**
     * Encapsulates per-stat scoring parameters.
     */
    private record Criterion(Statistic stat, int min, int full, int max) {
        int score(OfflinePlayer op) {
            int value = op.getStatistic(stat);
            if (value < min) return -1;
            return (int) ((long) (value - min) * max / (full - min));
        }
    }

    private record ScoreBreakdown(int flyRaw, int sneakRaw, int leaveRaw, int jumpRaw, int total, boolean qualified) {
    }

    // Hard minimums (not stat-based)
    private static final long JOIN_DAYS_MILLIS = TimeUnit.DAYS.toMillis(2);
    private static final int PLAYED_HOURS_TICKS = 20 * 60 * 60 * 8; // 8 h

    // Stat criteria: (stat, min, full, maxPoints)
    private static final Criterion FLY = new Criterion(Statistic.FLY_ONE_CM, 2_000_000, 6_000_000, 25);
    private static final Criterion SNEAK = new Criterion(Statistic.SNEAK_TIME, 6_000, 20_000, 25);
    private static final Criterion LEAVE = new Criterion(Statistic.LEAVE_GAME, 3, 15, 25);
    private static final Criterion JUMP = new Criterion(Statistic.JUMP, 500, 1_000, 25);

    private static final int SCORE_THRESHOLD = 100;
    private static final long CHECK_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private static final String TAINT_PERMISSION = "mik.autopromote.taint";

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastCheckAt = new HashMap<>();
    private LuckPerms luckPerms;

    public AutoPromoteModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enable auto-promote module (called in onEnable)
     */
    public void enable() {
        // Get LuckPerms API
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found! AutoPromoteModule disabled.");
            return;
        }
        luckPerms = provider.getProvider();

        // Register event listener
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
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> sendScoreReport(sender, name));
                            return Command.SINGLE_SUCCESS;
                        })).build(), "View promotion score"));
    }

    private void sendScoreReport(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        Component report = !target.hasPlayedBefore()
                ? Component.text("Player '" + name + "' has never played on this server.", NamedTextColor.RED)
                : buildScoreReport(target);
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(report));
    }

    private Component buildScoreReport(OfflinePlayer op) {
        boolean ageOk = System.currentTimeMillis() - op.getFirstPlayed() >= JOIN_DAYS_MILLIS;
        boolean playtimeOk = op.getStatistic(Statistic.PLAY_ONE_MINUTE) >= PLAYED_HOURS_TICKS;
        ScoreBreakdown scores = calculateScores(op);

        String displayName = op.getName() != null ? op.getName() : op.getUniqueId().toString();
        Component header = Component.text("─── " + displayName + "'s Promotion Score ───", NamedTextColor.GOLD);
        if (op.getPlayer() == null) {
            header = header.append(Component.text(" [offline]", NamedTextColor.GRAY));
        }

        return header
                .append(Component.newline())
                .append(status(ageOk)).append(Component.text(" Account age >= 2 days", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(status(playtimeOk)).append(Component.text(" Playtime >= 8 hours", NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("Fly distance : ", NamedTextColor.GRAY))
                .append(pts(scores.flyRaw(), FLY.max(), scores.flyRaw() >= 0))
                .append(Component.newline())
                .append(Component.text("Sneak time   : ", NamedTextColor.GRAY))
                .append(pts(scores.sneakRaw(), SNEAK.max(), scores.sneakRaw() >= 0))
                .append(Component.newline())
                .append(Component.text("Leave count  : ", NamedTextColor.GRAY))
                .append(pts(scores.leaveRaw(), LEAVE.max(), scores.leaveRaw() >= 0))
                .append(Component.newline())
                .append(Component.text("Jump count   : ", NamedTextColor.GRAY))
                .append(pts(scores.jumpRaw(), JUMP.max(), scores.jumpRaw() >= 0))
                .append(Component.newline())
                .append(Component.text("Total: ", NamedTextColor.GOLD))
                .append(Component.text(scores.total() + " / " + SCORE_THRESHOLD,
                        scores.qualified() ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private Component status(boolean ok) {
        return ok ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✘", NamedTextColor.RED);
    }

    private Component pts(int pts, int max, boolean minMet) {
        NamedTextColor color = !minMet ? NamedTextColor.RED : pts >= max ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        String suffix = !minMet ? " (below minimum)" : "";
        return Component.text(pts + " / " + max + suffix, color);
    }

    private boolean shouldPromotePlayer(OfflinePlayer player) {
        // Hard requirement: account age
        if (System.currentTimeMillis() - player.getFirstPlayed() < JOIN_DAYS_MILLIS) return false;

        // Hard requirement: playtime
        if (player.getStatistic(Statistic.PLAY_ONE_MINUTE) < PLAYED_HOURS_TICKS) return false;

        return calculateScores(player).qualified();
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
            Component message = Component.text("恭喜！", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text(" 你已成为 ", NamedTextColor.GREEN))
                    .append(Component.text("正式成员", NamedTextColor.GOLD))
                    .append(Component.text(" , 现在你拥有更多的命令和功能访问权限，比如可以进行投影一键打印！", NamedTextColor.GREEN));
            player.sendMessage(message);
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
        });

        plugin.getLogger().info("Promoted player " + playerName + " to member group");
    }

    private ScoreBreakdown calculateScores(OfflinePlayer player) {
        int flyRaw = FLY.score(player);
        int sneakRaw = SNEAK.score(player);
        int leaveRaw = LEAVE.score(player);
        int jumpRaw = JUMP.score(player);

        boolean qualified = flyRaw >= 0
                && sneakRaw >= 0
                && leaveRaw >= 0
                && jumpRaw >= 0;

        int total = qualified
                ? flyRaw + sneakRaw + leaveRaw + jumpRaw
                : 0;

        return new ScoreBreakdown(flyRaw, sneakRaw, leaveRaw, jumpRaw, total, qualified);
    }
}
