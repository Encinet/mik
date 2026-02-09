package org.encinet.mik.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.ServerTickManager;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.util.SchedulerUtil;

import java.util.*;

/**
 * Module for performance monitoring and protection
 */
public class PerformanceModule implements Listener {

    private static final String MANAGER_PERMISSION = "group.manager";
    private static final long CHECK_INTERVAL_TICKS = 40L;
    private static final long INITIAL_DELAY_TICKS = 1200L;
    private static final int ITEM_MAX_TICKS = 3000;

    // MSPT thresholds
    private static final double SHUTDOWN_THRESHOLD = 1000.0;
    private static final double KICK_THRESHOLD = 150.0;
    private static final double FREEZE_SERVER_THRESHOLD = 50.0;
    private static final double FREEZE_REDSTONE_THRESHOLD = 45.0;
    private static final double UNFREEZE_THRESHOLD = 40.0;

    // RandomTickSpeed adjustment thresholds
    private static final double RANDOM_TICK_START_REDUCE = 40.0;  // Start reducing at this MSPT
    private static final double RANDOM_TICK_MIN_MSPT = 20.0;      // Optimal MSPT for full speed
    private static final int RANDOM_TICK_MAX_SPEED = 4;           // Maximum allowed randomTickSpeed

    // Whitelist of mob types to remove during cleanup
    private static final Set<Class<? extends Entity>> REMOVABLE_MOB_TYPES = Set.of(
            // Monster
            Zombie.class,
            Skeleton.class,
            Creeper.class,
            Spider.class,
            CaveSpider.class,
            Enderman.class,
            Witch.class,
            Slime.class,
            Phantom.class,
            Drowned.class,
            Husk.class,
            Stray.class,
            Silverfish.class,
            Endermite.class,
            Blaze.class,
            Ghast.class,
            MagmaCube.class,
            PigZombie.class,
            Vindicator.class,
            Evoker.class,
            Vex.class,
            Pillager.class,
            Ravager.class,
            // Normal
            Pig.class,
            Cow.class,
            Sheep.class,
            Chicken.class,
            Horse.class,
            Wolf.class,
            Cat.class
    );

    private final JavaPlugin plugin;
    private final Component kickMessage;
    private final ServerTickManager serverTickManager;

    private BukkitTask guardTask;
    private volatile boolean isRedstoneFrozen = false;
    private final Map<World, Integer> originalRandomTickSpeed = new HashMap<>();

    // Trend prediction
    private final Queue<Double> msptHistory = new LinkedList<>();
    private static final int HISTORY_SIZE = 10;  // Track last 10 measurements

    public PerformanceModule(JavaPlugin plugin) {
        this.plugin = plugin;

        // Prepare kick message
        this.kickMessage = Component.text("═══════════════════════════════")
                .append(Component.newline())
                .append(Component.text("服务器出现异常卡顿").color(NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("为防止崩服，暂时断开连接").color(NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("═══════════════════════════════"));

        this.serverTickManager = plugin.getServer().getServerTickManager();

        // Store original randomTickSpeed for all worlds
        for (World world : Bukkit.getWorlds()) {
            Integer tickSpeed = world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
            if (tickSpeed != null) {
                originalRandomTickSpeed.put(world, tickSpeed);
            }
        }

        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Start performance monitoring
     */
    public void start() {
        this.guardTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            double mspt = Bukkit.getAverageTickTime();

            // Update history for trend prediction
            updateMsptHistory(mspt);

            // Calculate trend
            double trend = calculateTrend();
            double predictedMspt = mspt + trend;

            if (mspt > SHUTDOWN_THRESHOLD) {
                SchedulerUtil.runSync(plugin, () -> {
                    plugin.getLogger().severe("MSPT critically high (>1000ms), shutting down server!");
                    Bukkit.getServer().shutdown();
                });
                return;
            }

            if (mspt > KICK_THRESHOLD) {
                SchedulerUtil.runSync(plugin, () -> {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!player.hasPermission(MANAGER_PERMISSION)) {
                            player.kick(kickMessage);
                        }
                    }
                });
            }

            // Use predicted MSPT for proactive measures
            if (mspt > FREEZE_REDSTONE_THRESHOLD || (trend > 2.0 && predictedMspt > FREEZE_REDSTONE_THRESHOLD)) {
                handleHighLoad(mspt);
            } else if (mspt < UNFREEZE_THRESHOLD && trend <= 0) {
                handleNormalLoad();
            }

            // Always adjust randomTickSpeed dynamically (use predicted value for smoother adjustment)
            SchedulerUtil.runSync(plugin, () -> adjustRandomTickSpeed(Math.max(mspt, predictedMspt)));
        }, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
    }

    /**
     * Stop performance monitoring
     */
    public void stop() {
        if (guardTask != null) {
            guardTask.cancel();
        }
    }

    /**
     * Update MSPT history for trend analysis
     */
    private void updateMsptHistory(double mspt) {
        msptHistory.offer(mspt);
        if (msptHistory.size() > HISTORY_SIZE) {
            msptHistory.poll();
        }
    }

    /**
     * Calculate MSPT trend using simple linear regression
     * Returns the slope (change per measurement)
     * Positive = increasing (worsening), Negative = decreasing (improving)
     */
    private double calculateTrend() {
        if (msptHistory.size() < 3) {
            return 0.0;  // Not enough data
        }

        java.util.List<Double> history = new java.util.ArrayList<>(msptHistory);
        int n = history.size();

        // Calculate simple linear regression slope
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = history.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        // Slope formula: (n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)
        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    /**
     * Handle high server load
     */
    private void handleHighLoad(double mspt) {
        if (mspt >= FREEZE_SERVER_THRESHOLD && !serverTickManager.isFrozen()) {
            SchedulerUtil.runSync(plugin, () -> {
                if (!serverTickManager.isFrozen()) {
                    serverTickManager.setFrozen(true);
                }
            });
        }
        isRedstoneFrozen = true;
        SchedulerUtil.runSync(plugin, this::performEmergencyCleanup);
    }

    /**
     * Handle normal server load
     */
    private void handleNormalLoad() {
        if (serverTickManager.isFrozen()) {
            SchedulerUtil.runSync(plugin, () -> {
                if (serverTickManager.isFrozen()) {
                    serverTickManager.setFrozen(false);
                }
            });
        }
        if (isRedstoneFrozen) {
            isRedstoneFrozen = false;
            plugin.getLogger().info("Redstone unfrozen");
        }
    }

    /**
     * Dynamically adjust randomTickSpeed based on current MSPT
     * Formula: speed = min(maxSpeed, originalSpeed * max(0, (threshold - mspt) / (threshold - minMspt)))
     *
     * @param mspt current milliseconds per tick
     */
    private void adjustRandomTickSpeed(double mspt) {
        Bukkit.getWorlds().forEach(world -> {
            Integer originalSpeed = originalRandomTickSpeed.get(world);
            if (originalSpeed == null || originalSpeed == 0) return;

            int cappedSpeed = Math.min(originalSpeed, RANDOM_TICK_MAX_SPEED);
            int newSpeed = calculateAdjustedSpeed(mspt, cappedSpeed);

            Integer currentSpeed = world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
            if (currentSpeed == null || currentSpeed != newSpeed) {
                world.setGameRule(GameRules.RANDOM_TICK_SPEED, newSpeed);
            }
        });
    }

    /**
     * Calculate adjusted speed based on MSPT
     */
    private int calculateAdjustedSpeed(double mspt, int maxSpeed) {
        if (mspt < RANDOM_TICK_START_REDUCE) {
            return maxSpeed;
        }
        double ratio = Math.max(0.0, (RANDOM_TICK_START_REDUCE - mspt) /
                                     (RANDOM_TICK_START_REDUCE - RANDOM_TICK_MIN_MSPT));
        return (int) Math.round(maxSpeed * ratio);
    }

    /**
     * Perform emergency entity cleanup
     */
    private void performEmergencyCleanup() {
        Bukkit.getWorlds().forEach(world -> {
            // Remove old items
            world.getEntitiesByClass(Item.class).stream()
                    .filter(item -> item.getTicksLived() > ITEM_MAX_TICKS)
                    .forEach(Entity::remove);

            // Remove projectiles
            world.getEntitiesByClass(Projectile.class).forEach(Entity::remove);

            // Remove experience orbs
            world.getEntitiesByClass(ExperienceOrb.class).forEach(Entity::remove);

            // Remove specific mobs from whitelist
            world.getEntitiesByClass(LivingEntity.class).stream()
                    .filter(this::shouldRemoveEntity)
                    .forEach(Entity::remove);
        });
    }

    /**
     * Check if entity should be removed during cleanup
     */
    private boolean shouldRemoveEntity(LivingEntity entity) {
        // Check if entity type is in removable whitelist
        boolean isRemovable = REMOVABLE_MOB_TYPES.stream()
                .anyMatch(type -> type.isInstance(entity));

        if (!isRemovable) return false;

        // Skip entities with custom names
        if (entity.customName() != null) return false;

        // Skip tameable entities with owners
        return !(entity instanceof Tameable tameable) || tameable.getOwner() == null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (isRedstoneFrozen) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (isRedstoneFrozen) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (isRedstoneFrozen) {
            event.setCancelled(true);
        }
    }
}
