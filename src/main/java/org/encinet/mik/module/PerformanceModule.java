package org.encinet.mik.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.ServerTickManager;
import org.bukkit.World;
import org.bukkit.block.Hopper;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.util.SchedulerUtil;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PerformanceModule implements Listener {

    private static final String MANAGER_PERMISSION = "group.manager";
    private static final long CHECK_INTERVAL_TICKS = 40L;
    private static final long INITIAL_DELAY_TICKS = 1200L;

    private static final double THRESHOLD_SHUTDOWN = 1000.0;
    private static final double THRESHOLD_KICK = 150.0;

    private final JavaPlugin plugin;
    private final ServerTickManager tickManager;
    private final Component kickMessage;

    private final MsptSampler sampler;
    private final FreezeController freezeController;
    private final RandomTickAdjuster tickAdjuster;

    private BukkitTask guardTask;

    public PerformanceModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tickManager = plugin.getServer().getServerTickManager();

        this.kickMessage = Component.text("═══════════════════════════════")
                .append(Component.newline())
                .append(Component.text("服务器出现异常卡顿").color(NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("为防止崩服，暂时断开连接").color(NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("═══════════════════════════════"));

        this.sampler = new MsptSampler();
        this.freezeController = new FreezeController();
        this.tickAdjuster = new RandomTickAdjuster(Bukkit.getWorlds());

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        guardTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::tick, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void stop() {
        if (guardTask != null) guardTask.cancel();
    }

    private void tick() {
        double mspt = sampler.update(Bukkit.getAverageTickTime());
        double trend = sampler.trend();

        if (mspt > THRESHOLD_SHUTDOWN) {
            SchedulerUtil.runSync(plugin, () -> {
                plugin.getLogger().severe(String.format("MSPT critically high (%.0f ms), shutting down!", mspt));
                Bukkit.getServer().shutdown();
            });
            return;
        }

        if (mspt > THRESHOLD_KICK) {
            SchedulerUtil.runSync(plugin, () ->
                    Bukkit.getOnlinePlayers().stream()
                            .filter(p -> !p.hasPermission(MANAGER_PERMISSION))
                            .forEach(p -> p.kick(kickMessage))
            );
        }

        FreezeController.Decision decision = freezeController.evaluate(mspt, trend);
        switch (decision) {
            case FREEZE -> SchedulerUtil.runSync(plugin, () -> {
                if (!tickManager.isFrozen()) tickManager.setFrozen(true);
                EntityCleaner.run(Bukkit.getWorlds());
                plugin.getLogger().warning(String.format("Server frozen (MSPT=%.1f)", mspt));
            });
            case UNFREEZE -> SchedulerUtil.runSync(plugin, () -> {
                if (tickManager.isFrozen()) tickManager.setFrozen(false);
                plugin.getLogger().info("Server recovered, unfreezing.");
            });
            case HOLD -> {
            }
        }

        double effectiveMspt = Math.max(mspt, mspt + trend);
        SchedulerUtil.runSync(plugin, () -> tickAdjuster.adjust(effectiveMspt));
    }

    // ── Event handlers ──────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (freezeController.isFrozen()) event.setNewCurrent(0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (freezeController.isFrozen()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (freezeController.isFrozen()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (freezeController.isFrozen() && event.getSource().getHolder() instanceof Hopper)
            event.setCancelled(true);
    }

    // ── Inner classes ───────────────────────────────────────────────────────

    /**
     * EMA smoothing + linear-regression trend over a sliding window.
     * Only accessed from the single async guardian task.
     */
    private static class MsptSampler {
        private static final double EMA_ALPHA = 0.25;
        private static final int WINDOW_SIZE = 10;

        private double smoothed = 0.0;
        private boolean initialized = false;
        private final Deque<Double> history = new ArrayDeque<>(WINDOW_SIZE + 1);

        /**
         * Feed a raw MSPT reading; returns the smoothed value.
         */
        double update(double raw) {
            smoothed = initialized ? EMA_ALPHA * raw + (1 - EMA_ALPHA) * smoothed : raw;
            initialized = true;

            history.addLast(smoothed);
            if (history.size() > WINDOW_SIZE) history.removeFirst();

            return smoothed;
        }

        /**
         * Linear regression slope over the window (ms/reading). Positive = worsening.
         */
        double trend() {
            if (history.size() < 3) return 0.0;

            List<Double> h = new ArrayList<>(history);
            int n = h.size();
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                sumX += i;
                sumY += h.get(i);
                sumXY += (double) i * h.get(i);
                sumX2 += (double) i * i;
            }
            double denom = n * sumX2 - sumX * sumX;
            return denom == 0.0 ? 0.0 : (n * sumXY - sumX * sumY) / denom;
        }
    }

    /**
     * Debounced freeze state machine.
     * <p>
     * Requires FREEZE_CONFIRM consecutive high readings to freeze,
     * and UNFREEZE_CONFIRM consecutive recovery readings to unfreeze.
     * A rising trend accelerates the freeze counter but cannot skip debounce.
     */
    private static class FreezeController {

        enum Decision {FREEZE, UNFREEZE, HOLD}

        // Thresholds
        private static final double MSPT_FREEZE_REDSTONE = 48.0;
        private static final double MSPT_FREEZE_SERVER = 50.0;
        private static final double MSPT_UNFREEZE = 38.0;  // hysteresis gap = 10 ms

        // Debounce
        private static final int FREEZE_CONFIRM = 3;  // ~6 s
        private static final int UNFREEZE_CONFIRM = 5;  // ~10 s

        // Trend boost: each SLOPE ms/reading of rise = +1 extra high count, capped at MAX
        private static final double TREND_BOOST_SLOPE = 3.0;
        private static final int TREND_BOOST_MAX = 1;

        private final AtomicBoolean frozen = new AtomicBoolean(false);
        private int highCount = 0;
        private int normalCount = 0;

        Decision evaluate(double mspt, double trend) {
            if (mspt >= MSPT_FREEZE_REDSTONE) {
                int boost = trend > TREND_BOOST_SLOPE
                        ? Math.min(TREND_BOOST_MAX, (int) (trend / TREND_BOOST_SLOPE)) : 0;
                highCount = Math.min(highCount + 1 + boost, FREEZE_CONFIRM);
                normalCount = 0;
            } else if (mspt < MSPT_UNFREEZE) {
                normalCount = Math.min(normalCount + 1, UNFREEZE_CONFIRM);
                highCount = Math.max(highCount - 1, 0);
            }
            // mspt in hysteresis band → no counter change

            if (!frozen.get() && highCount >= FREEZE_CONFIRM) {
                frozen.set(true);
                normalCount = 0;
                return Decision.FREEZE;
            }
            if (frozen.get() && normalCount >= UNFREEZE_CONFIRM) {
                frozen.set(false);
                highCount = 0;
                normalCount = 0;
                return Decision.UNFREEZE;
            }
            return Decision.HOLD;
        }

        boolean isFrozen() {
            return frozen.get();
        }
    }

    /**
     * Manages per-world randomTickSpeed.
     * Speed interpolates linearly from maxSpeed at MSPT_FULL down to 0 at MSPT_ZERO.
     */
    private static class RandomTickAdjuster {

        private static final double MSPT_FULL = 20.0;  // full speed below this
        private static final double MSPT_ZERO = 40.0;  // disabled at or above this
        private static final int MAX_SPEED = 3;

        private final Map<World, Integer> originals = new HashMap<>();

        RandomTickAdjuster(List<World> worlds) {
            for (World w : worlds) {
                Integer v = w.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
                if (v != null) originals.put(w, v);
            }
        }

        void adjust(double mspt) {
            originals.forEach((world, original) -> {
                if (original == 0) return;
                int target = interpolate(mspt, Math.min(original, MAX_SPEED));
                Integer current = world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
                if (current == null || current != target)
                    world.setGameRule(GameRules.RANDOM_TICK_SPEED, target);
            });
        }

        private int interpolate(double mspt, int max) {
            if (mspt <= MSPT_FULL) return max;
            if (mspt >= MSPT_ZERO) return 0;
            double ratio = (MSPT_ZERO - mspt) / (MSPT_ZERO - MSPT_FULL);
            return (int) Math.round(max * ratio);
        }
    }

    /**
     * Stateless emergency entity cleanup.
     */
    private static class EntityCleaner {

        private static final int ITEM_MAX_TICKS = 3000;

        private static final Set<Class<? extends Entity>> REMOVABLE = Set.of(
                Zombie.class, Skeleton.class, Creeper.class, Spider.class,
                CaveSpider.class, Enderman.class, Witch.class, Slime.class,
                Phantom.class, Drowned.class, Husk.class, Stray.class,
                Silverfish.class, Endermite.class, Blaze.class, Ghast.class,
                MagmaCube.class, PigZombie.class, Vindicator.class, Evoker.class,
                Vex.class, Pillager.class, Ravager.class,
                Pig.class, Cow.class, Sheep.class, Chicken.class,
                Horse.class, Wolf.class, Cat.class
        );

        static void run(List<World> worlds) {
            worlds.forEach(world -> {
                world.getEntitiesByClass(Item.class).stream()
                        .filter(i -> i.getTicksLived() > ITEM_MAX_TICKS)
                        .forEach(Entity::remove);

                world.getEntitiesByClass(Projectile.class).forEach(Entity::remove);
                world.getEntitiesByClass(ExperienceOrb.class).forEach(Entity::remove);

                world.getEntitiesByClass(LivingEntity.class).stream()
                        .filter(EntityCleaner::isRemovable)
                        .forEach(Entity::remove);
            });
        }

        private static boolean isRemovable(LivingEntity e) {
            if (REMOVABLE.stream().noneMatch(t -> t.isInstance(e))) return false;
            if (e.customName() != null) return false;
            if (e instanceof Tameable t && t.getOwner() != null) return false;
            return true;
        }
    }
}
