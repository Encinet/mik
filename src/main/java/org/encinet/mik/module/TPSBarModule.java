package org.encinet.mik.module;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module for TPS bar display
 */
public class TPSBarModule {

    // Configuration
    public static String commandTPSBarTitle = "<gray>TPS<yellow>:</yellow> <tps> MSPT<yellow>:</yellow> <mspt> Ping<yellow>:</yellow> <ping>ms";
    public static BossBar.Overlay commandTPSBarProgressOverlay = BossBar.Overlay.NOTCHED_20;
    public static FillMode commandTPSBarProgressFillMode = FillMode.MSPT;
    public static BossBar.Color commandTPSBarProgressColorGood = BossBar.Color.GREEN;
    public static BossBar.Color commandTPSBarProgressColorMedium = BossBar.Color.YELLOW;
    public static BossBar.Color commandTPSBarProgressColorLow = BossBar.Color.RED;
    public static String commandTPSBarTextColorGood = "<gradient:#55ff55:#00aa00><text></gradient>";
    public static String commandTPSBarTextColorMedium = "<gradient:#ffff55:#ffaa00><text></gradient>";
    public static String commandTPSBarTextColorLow = "<gradient:#ff5555:#aa0000><text></gradient>";
    public static int commandTPSBarTickInterval = 20;

    private static final double TPS_GOOD = 19.0;
    private static final double TPS_MEDIUM = 15.0;
    private static final double MSPT_GOOD = 40.0;
    private static final double MSPT_MEDIUM = 50.0;
    private static final int PING_GOOD = 100;
    private static final int PING_MEDIUM = 200;

    private final JavaPlugin plugin;
    private final Map<UUID, BossBar> playerBossBars;
    private BukkitTask updateTask;

    private double tps = 20.0;
    private double mspt = 0.0;
    private int tick = 0;

    public TPSBarModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerBossBars = new ConcurrentHashMap<>();
    }

    /**
     * Start the TPS bar update task
     */
    public void start() {
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (++tick < commandTPSBarTickInterval) {
                return;
            }
            tick = 0;

            this.tps = Math.clamp(Bukkit.getTPS()[0], 0.0, 20.0);
            this.mspt = Bukkit.getAverageTickTime();

            updateAllBossBars();
        }, 0L, 1L);
    }

    /**
     * Stop the TPS bar update task
     */
    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        playerBossBars.values().forEach(bossBar -> Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bossBar)));
        playerBossBars.clear();
    }

    /**
     * Register commands
     */
    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(Commands.literal("tpsbar")
                    .requires(source -> source.getSender().hasPermission("mik.tpsbar"))
                    .executes(ctx -> {
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            toggleTPSBar(player);
                            return Command.SINGLE_SUCCESS;
                        } else {
                            ctx.getSource().getSender().sendMessage("你不是玩家");
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), "显示/隐藏TPS信息栏");
        });
    }

    /**
     * Toggle TPS bar for a player
     */
    private void toggleTPSBar(Player player) {
        UUID playerId = player.getUniqueId();

        if (playerBossBars.containsKey(playerId)) {
            BossBar bossBar = playerBossBars.remove(playerId);
            player.hideBossBar(bossBar);
            player.sendMessage(Component.text("已隐藏TPS信息栏"));
        } else {
            BossBar bossBar = createBossBar();
            playerBossBars.put(playerId, bossBar);
            player.showBossBar(bossBar);
            updateBossBar(bossBar, player);
            player.sendMessage(Component.text("已显示TPS信息栏"));
        }
    }

    /**
     * Create a new boss bar
     */
    private BossBar createBossBar() {
        return BossBar.bossBar(
                Component.text(""),
                0.0f,
                getBossBarColor(0),
                commandTPSBarProgressOverlay
        );
    }

    /**
     * Update all boss bars
     */
    private void updateAllBossBars() {
        playerBossBars.forEach((playerId, bossBar) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                updateBossBar(bossBar, player);
            } else {
                playerBossBars.remove(playerId);
            }
        });
    }

    /**
     * Update a single boss bar
     */
    private void updateBossBar(BossBar bossBar, Player player) {
        bossBar.progress(getBossBarProgress(player.getPing()));
        bossBar.color(getBossBarColor(player.getPing()));
        bossBar.name(MiniMessage.miniMessage().deserialize(commandTPSBarTitle,
                Placeholder.component("tps", getTPSColor()),
                Placeholder.component("mspt", getMSPTColor()),
                Placeholder.component("ping", getPingColor(player.getPing()))
        ));
    }

    /**
     * Get boss bar progress based on fill mode
     */
    private float getBossBarProgress(int ping) {
        if (commandTPSBarProgressFillMode == FillMode.MSPT) {
            return Math.clamp((float) mspt / 50.0f, 0.0f, 1.0f);
        } else if (commandTPSBarProgressFillMode == FillMode.TPS) {
            return Math.clamp((float) tps / 20.0f, 0.0f, 1.0f);
        } else if (commandTPSBarProgressFillMode == FillMode.PING) {
            return Math.clamp((float) ping / 300.0f, 0.0f, 1.0f);
        }
        return 0.0f;
    }

    /**
     * Get boss bar color based on fill mode
     */
    private BossBar.Color getBossBarColor(int ping) {
        if (isGood(commandTPSBarProgressFillMode, ping)) {
            return commandTPSBarProgressColorGood;
        } else if (isMedium(commandTPSBarProgressFillMode, ping)) {
            return commandTPSBarProgressColorMedium;
        } else {
            return commandTPSBarProgressColorLow;
        }
    }

    private boolean isGood(FillMode mode, int ping) {
        if (mode == FillMode.MSPT) {
            return mspt < MSPT_GOOD;
        } else if (mode == FillMode.TPS) {
            return tps >= TPS_GOOD;
        } else if (mode == FillMode.PING) {
            return ping < PING_GOOD;
        }
        return false;
    }

    private boolean isMedium(FillMode mode, int ping) {
        if (mode == FillMode.MSPT) {
            return mspt < MSPT_MEDIUM;
        } else if (mode == FillMode.TPS) {
            return tps >= TPS_MEDIUM;
        } else if (mode == FillMode.PING) {
            return ping < PING_MEDIUM;
        }
        return false;
    }

    /**
     * Get TPS component with color
     */
    private Component getTPSColor() {
        String color;
        if (isGood(FillMode.TPS, 0)) {
            color = commandTPSBarTextColorGood;
        } else if (isMedium(FillMode.TPS, 0)) {
            color = commandTPSBarTextColorMedium;
        } else {
            color = commandTPSBarTextColorLow;
        }
        return MiniMessage.miniMessage().deserialize(color,
                Placeholder.parsed("text", String.format("%.2f", tps)));
    }

    /**
     * Get MSPT component with color
     */
    private Component getMSPTColor() {
        String color;
        if (isGood(FillMode.MSPT, 0)) {
            color = commandTPSBarTextColorGood;
        } else if (isMedium(FillMode.MSPT, 0)) {
            color = commandTPSBarTextColorMedium;
        } else {
            color = commandTPSBarTextColorLow;
        }
        return MiniMessage.miniMessage().deserialize(color,
                Placeholder.parsed("text", String.format("%.2f", mspt)));
    }

    /**
     * Get ping component with color
     */
    private Component getPingColor(int ping) {
        String color;
        if (isGood(FillMode.PING, ping)) {
            color = commandTPSBarTextColorGood;
        } else if (isMedium(FillMode.PING, ping)) {
            color = commandTPSBarTextColorMedium;
        } else {
            color = commandTPSBarTextColorLow;
        }
        return MiniMessage.miniMessage().deserialize(color,
                Placeholder.parsed("text", String.valueOf(ping)));
    }

    public enum FillMode {
        TPS, MSPT, PING
    }
}
