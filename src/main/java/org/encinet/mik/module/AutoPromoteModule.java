package org.encinet.mik.module;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

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

    /**
     * Criterion for USE_ITEM — sums across all materials. Works for both online and offline.
     */
    private record BlockCriterion(int min, int full, int max) {
        int score(OfflinePlayer op) {
            int total = Arrays.stream(Material.values())
                    .filter(Material::isItem)
                    .mapToInt(m -> op.getStatistic(Statistic.USE_ITEM, m))
                    .sum();
            if (total < min) return -1;
            return (int) ((long) (total - min) * max / (full - min));
        }
    }

    // Hard minimums (not stat-based)
    private static final long JOIN_DAYS_MILLIS = TimeUnit.DAYS.toMillis(2);
    private static final int PLAYED_HOURS_TICKS = 20 * 60 * 60 * 8; // 8 h

    // Stat criteria: (stat, min, full, maxPoints)
    private static final Criterion FLY = new Criterion(Statistic.FLY_ONE_CM, 2_000_000, 6_000_000, 25);
    private static final Criterion SNEAK = new Criterion(Statistic.SNEAK_TIME, 6_000, 20_000, 25);
    private static final Criterion LEAVE = new Criterion(Statistic.LEAVE_GAME, 3, 15, 25);
    // Total items used across all materials
    private static final BlockCriterion USE_ITEMS = new BlockCriterion(5_000, 10_000, 25);

    private static final int SCORE_THRESHOLD = 100;

    private static final String TAINT_PERMISSION = "mik.autopromote.taint";

    private final JavaPlugin plugin;
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
        Player player = event.getPlayer();

        // Check conditions asynchronously to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (shouldPromotePlayer(player)) {
                promotePlayer(player);
            }
        });
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("promotecheck")
                .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_HELPER))
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getExecutor() instanceof Player sender)) {
                                ctx.getSource().getSender().sendMessage("Only players can use this command.");
                                return Command.SINGLE_SUCCESS;
                            }
                            String name = StringArgumentType.getString(ctx, "player");
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                                if (!target.hasPlayedBefore()) {
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                            sender.sendMessage(Component.text("Player '" + name + "' has never played on this server.", NamedTextColor.RED)));
                                    return;
                                }
                                Component report = buildScoreReport(target);
                                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(report));
                            });
                            return Command.SINGLE_SUCCESS;
                        })).build(), "View promotion score"));
    }

    private Component buildScoreReport(OfflinePlayer op) {
        boolean ageOk = System.currentTimeMillis() - op.getFirstPlayed() >= JOIN_DAYS_MILLIS;
        boolean playtimeOk = op.getStatistic(Statistic.PLAY_ONE_MINUTE) >= PLAYED_HOURS_TICKS;

        int flyRaw = FLY.score(op);
        int sneakRaw = SNEAK.score(op);
        int leaveRaw = LEAVE.score(op);
        int mineRaw = USE_ITEMS.score(op);
        int flyPts = Math.max(0, flyRaw);
        int sneakPts = Math.max(0, sneakRaw);
        int leavePts = Math.max(0, leaveRaw);
        int minePts = Math.max(0, mineRaw);
        boolean anyFailed = flyRaw < 0 || sneakRaw < 0 || leaveRaw < 0 || mineRaw < 0;
        int total = anyFailed ? 0 : flyPts + sneakPts + leavePts + minePts;

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
                .append(pts(flyPts, FLY.max(), flyRaw >= 0))
                .append(Component.newline())
                .append(Component.text("Sneak time   : ", NamedTextColor.GRAY))
                .append(pts(sneakPts, SNEAK.max(), sneakRaw >= 0))
                .append(Component.newline())
                .append(Component.text("Leave count  : ", NamedTextColor.GRAY))
                .append(pts(leavePts, LEAVE.max(), leaveRaw >= 0))
                .append(Component.newline())
                .append(Component.text("Items used   : ", NamedTextColor.GRAY))
                .append(pts(minePts, USE_ITEMS.max(), mineRaw >= 0))
                .append(Component.newline())
                .append(Component.text("Total: ", NamedTextColor.GOLD))
                .append(Component.text(total + " / " + SCORE_THRESHOLD,
                        total >= SCORE_THRESHOLD ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private Component status(boolean ok) {
        return ok ? Component.text("✔", NamedTextColor.GREEN) : Component.text("✘", NamedTextColor.RED);
    }

    private Component pts(int pts, int max, boolean minMet) {
        NamedTextColor color = !minMet ? NamedTextColor.RED : pts >= max ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        String suffix = !minMet ? " (below minimum)" : "";
        return Component.text(pts + " / " + max + suffix, color);
    }


    private boolean shouldPromotePlayer(Player player) {
        if (player.hasPermission(TAINT_PERMISSION)) return false;
        if (player.hasPermission("group." + Mik.GROUP_MEMBER)) return false;

        // Hard requirement: account age
        if (System.currentTimeMillis() - player.getFirstPlayed() < JOIN_DAYS_MILLIS) return false;

        // Hard requirement: playtime
        if (player.getStatistic(Statistic.PLAY_ONE_MINUTE) < PLAYED_HOURS_TICKS) return false;

        // Score-based: each criterion must meet its minimum, then scores are summed
        int flyPts = FLY.score(player);
        if (flyPts < 0) return false;
        int sneakPts = SNEAK.score(player);
        if (sneakPts < 0) return false;
        int leavePts = LEAVE.score(player);
        if (leavePts < 0) return false;
        int minePts = USE_ITEMS.score(player);
        if (minePts < 0) return false;
        return flyPts + sneakPts + leavePts + minePts >= SCORE_THRESHOLD;
    }

    /**
     * Promote player to member group
     */
    private void promotePlayer(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
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
            Component message = Component.text("恭喜！", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text(" 你已成为 ", NamedTextColor.GREEN))
                    .append(Component.text("正式成员", NamedTextColor.GOLD))
                    .append(Component.text(" , 现在你拥有更多的命令和功能访问权限，比如可以进行投影一键打印！", NamedTextColor.GREEN));
            player.sendMessage(message);
            player.playSound(player, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
        });

        plugin.getLogger().info("Promoted player " + player.getName() + " to member group");
    }
}
