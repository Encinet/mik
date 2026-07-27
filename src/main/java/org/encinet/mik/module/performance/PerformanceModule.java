package org.encinet.mik.module.performance;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.ServerTickManager;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.util.SchedulerUtil;

import java.util.*;

public class PerformanceModule implements Listener {

    private static final String MANAGER_PERMISSION = "group." + Mik.GROUP_MANAGER;
    private static final long CHECK_INTERVAL_TICKS = 40L;
    private static final long INITIAL_DELAY_TICKS = 1200L;
    private static final long WIND_CHARGE_CLEANUP_INTERVAL_TICKS = 100L;
    private static final int WIND_CHARGE_MAX_TICKS = 200;

    private static final double THRESHOLD_CHUNK_GUARD = 40.0;

    private final JavaPlugin plugin;
    private final ServerTickManager tickManager;
    private final Component kickMessage;

    private final MsptSampler sampler;
    private final EmergencyController emergencyController;
    private final RandomTickAdjuster tickAdjuster;
    private final PlayerDistanceController distanceController;
    private final ChunkPressureController pressureController;

    private BukkitTask guardTask;
    private BukkitTask windChargeCleanupTask;
    private volatile double lastEffectiveMspt = 20.0;
    private volatile boolean liveFrozen;

    public PerformanceModule(JavaPlugin plugin, AfkService afkService) {
        this.plugin = plugin;
        this.tickManager = plugin.getServer().getServerTickManager();
        this.kickMessage = Component.text("═══════════════════════════════")
                .append(Component.newline())
                .append(Component.text("服务器出现严重卡顿").color(NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("为帮助服务器恢复，暂时断开连接").color(NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text("═══════════════════════════════"));

        this.sampler = new MsptSampler();
        this.emergencyController = new EmergencyController();
        this.tickAdjuster = new RandomTickAdjuster(Bukkit.getWorlds());
        this.distanceController = new PlayerDistanceController(
                afkService,
                plugin.getServer().getViewDistance(),
                plugin.getServer().getSimulationDistance());
        this.pressureController = new ChunkPressureController();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        distanceController.primeOnlinePlayers();
        guardTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::tick, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
        windChargeCleanupTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> WindChargeCleaner.run(Bukkit.getWorlds()),
                WIND_CHARGE_CLEANUP_INTERVAL_TICKS,
                WIND_CHARGE_CLEANUP_INTERVAL_TICKS);
    }

    public void stop() {
        if (guardTask != null) guardTask.cancel();
        guardTask = null;
        if (windChargeCleanupTask != null) windChargeCleanupTask.cancel();
        windChargeCleanupTask = null;
        if (liveFrozen && tickManager.isFrozen()) tickManager.setFrozen(false);
        liveFrozen = false;
        emergencyController.reset();
        tickAdjuster.reset();
        distanceController.resetAll();
        pressureController.reset();
    }

    private void tick() {
        double rawMspt = Bukkit.getAverageTickTime();
        double mspt = sampler.update(rawMspt);
        double trend = sampler.trend();
        EmergencyController.Decision emergencyDecision = emergencyController.evaluate(rawMspt, liveFrozen);

        double effectiveMspt = Math.max(mspt, mspt + trend);
        boolean emergencyArmed = liveFrozen
                || emergencyDecision == EmergencyController.Decision.FREEZE
                || emergencyDecision == EmergencyController.Decision.FREEZE_AND_KICK;
        boolean chunkGuardArmed = emergencyArmed || effectiveMspt >= THRESHOLD_CHUNK_GUARD;
        lastEffectiveMspt = effectiveMspt;
        SchedulerUtil.runSync(plugin, () -> {
            switch (emergencyDecision) {
                case FREEZE -> freeze(rawMspt, false);
                case FREEZE_AND_KICK -> freeze(rawMspt, true);
                case KICK -> {
                    int kicked = kickNonManagers();
                    plugin.getLogger().warning(String.format(
                            Locale.ROOT,
                            "Extreme lag persisted while frozen (MSPT=%.1f); kicked %d players.",
                            rawMspt,
                            kicked));
                }
                case UNFREEZE -> unfreeze(rawMspt);
                case HOLD -> {
                }
            }

            if (chunkGuardArmed) {
                pressureController.rollWindow();
            } else {
                pressureController.disarm();
            }
            tickAdjuster.adjust(effectiveMspt);
            distanceController.adjust(effectiveMspt);
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        distanceController.track(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        distanceController.untrack(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (liveFrozen || pressureController.onRedstone(event.getBlock(), shouldApplyChunkGuard())) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (liveFrozen || pressureController.onPiston(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (liveFrozen || pressureController.onPiston(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!(event.getInitiator().getHolder() instanceof Hopper hopper)) {
            return;
        }
        if (liveFrozen || pressureController.onHopperMove(hopper.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (liveFrozen || pressureController.onPhysics(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFallingBlockChange(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.FALLING_BLOCK) {
            return;
        }
        if (liveFrozen || pressureController.onFallingBlock(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (liveFrozen || pressureController.onItemSpawn(event.getLocation().getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    private boolean shouldApplyChunkGuard() {
        return liveFrozen || lastEffectiveMspt >= THRESHOLD_CHUNK_GUARD;
    }

    private void freeze(double mspt, boolean kickPlayers) {
        if (!tickManager.isFrozen()) {
            tickManager.setFrozen(true);
        }
        liveFrozen = true;
        EntityCleaner.run(Bukkit.getWorlds());

        int kicked = kickPlayers ? kickNonManagers() : 0;
        if (kickPlayers) {
            plugin.getLogger().warning(String.format(
                    Locale.ROOT,
                    "Server emergency-frozen after sustained extreme lag (MSPT=%.1f); kicked %d players.",
                    mspt,
                    kicked));
        } else {
            plugin.getLogger().warning(String.format(
                    Locale.ROOT,
                    "Server emergency-frozen after sustained extreme lag (MSPT=%.1f).",
                    mspt));
        }
    }

    private int kickNonManagers() {
        int kicked = 0;
        for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (player.hasPermission(MANAGER_PERMISSION)) {
                continue;
            }
            player.kick(kickMessage);
            kicked++;
        }
        return kicked;
    }

    private void unfreeze(double mspt) {
        if (tickManager.isFrozen()) {
            tickManager.setFrozen(false);
        }
        liveFrozen = false;
        plugin.getLogger().info(String.format(
                Locale.ROOT,
                "Server recovered from emergency freeze (MSPT=%.1f).",
                mspt));
    }

    public double effectiveMspt() {
        return lastEffectiveMspt;
    }

    private static boolean shouldRemoveWindCharge(int ticksLived) {
        return ticksLived >= WIND_CHARGE_MAX_TICKS;
    }

    /**
     * EMA smoothing + linear-regression trend over a sliding window.
     * Only accessed from the single async guardian task.
     */
    private static class MsptSampler {
        private static final double EMA_ALPHA = 0.25;
        private static final int WINDOW_SIZE = 10;

        private double smoothed = 0.0;
        private boolean initialized = false;
        private final double[] history = new double[WINDOW_SIZE];
        private int size = 0;
        private int start = 0;

        /**
         * Feed a raw MSPT reading; returns the smoothed value.
         */
        double update(double raw) {
            smoothed = initialized ? EMA_ALPHA * raw + (1 - EMA_ALPHA) * smoothed : raw;
            initialized = true;

            if (size < WINDOW_SIZE) {
                history[(start + size) % WINDOW_SIZE] = smoothed;
                size++;
            } else {
                history[start] = smoothed;
                start = (start + 1) % WINDOW_SIZE;
            }

            return smoothed;
        }

        /**
         * Linear regression slope over the window (ms/reading). Positive = worsening.
         */
        double trend() {
            if (size < 3) return 0.0;

            int n = size;
            double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
            for (int i = 0; i < n; i++) {
                double value = history[(start + i) % WINDOW_SIZE];
                sumX += i;
                sumY += value;
                sumXY += (double) i * value;
                sumX2 += (double) i * i;
            }
            double denom = n * sumX2 - sumX * sumX;
            return denom == 0.0 ? 0.0 : (n * sumXY - sumX * sumY) / denom;
        }
    }

    /**
     * Escalates only sustained near-stall conditions. Lower MSPT remains a
     * supported degraded mode and never accumulates toward freeze or kick.
     */
    private static final class EmergencyController {

        enum Decision {FREEZE, FREEZE_AND_KICK, KICK, UNFREEZE, HOLD}

        private static final double FREEZE_MSPT = 1000.0; // ~1 TPS
        private static final double KICK_MSPT = 2000.0; // ~0.5 TPS
        private static final double RECOVERY_MSPT = 200.0; // ~5 TPS
        private static final int FREEZE_CONFIRM_WINDOWS = 2;
        private static final int KICK_CONFIRM_WINDOWS = 2;
        private static final int RECOVERY_CONFIRM_WINDOWS = 5;

        private int freezeCount;
        private int kickCount;
        private int recoveryCount;
        private boolean kickedDuringFreeze;

        Decision evaluate(double mspt, boolean frozen) {
            if (frozen) {
                freezeCount = 0;
                if (!kickedDuringFreeze && mspt >= KICK_MSPT) {
                    recoveryCount = 0;
                    kickCount = Math.min(kickCount + 1, KICK_CONFIRM_WINDOWS);
                    if (kickCount >= KICK_CONFIRM_WINDOWS) {
                        kickCount = 0;
                        kickedDuringFreeze = true;
                        return Decision.KICK;
                    }
                    return Decision.HOLD;
                }
                kickCount = 0;
                if (mspt > RECOVERY_MSPT) {
                    recoveryCount = 0;
                    return Decision.HOLD;
                }
                recoveryCount = Math.min(recoveryCount + 1, RECOVERY_CONFIRM_WINDOWS);
                if (recoveryCount < RECOVERY_CONFIRM_WINDOWS) {
                    return Decision.HOLD;
                }
                kickedDuringFreeze = false;
                return Decision.UNFREEZE;
            }

            recoveryCount = 0;
            kickedDuringFreeze = false;
            freezeCount = mspt >= FREEZE_MSPT
                    ? Math.min(freezeCount + 1, FREEZE_CONFIRM_WINDOWS)
                    : 0;
            kickCount = mspt >= KICK_MSPT
                    ? Math.min(kickCount + 1, KICK_CONFIRM_WINDOWS)
                    : 0;

            if (kickCount >= KICK_CONFIRM_WINDOWS) {
                freezeCount = 0;
                kickCount = 0;
                kickedDuringFreeze = true;
                return Decision.FREEZE_AND_KICK;
            }
            if (freezeCount >= FREEZE_CONFIRM_WINDOWS) {
                freezeCount = 0;
                kickCount = 0;
                return Decision.FREEZE;
            }
            return Decision.HOLD;
        }

        void reset() {
            freezeCount = 0;
            kickCount = 0;
            recoveryCount = 0;
            kickedDuringFreeze = false;
        }
    }

    /**
     * Manages per-world randomTickSpeed.
     * Speed interpolates linearly from maxSpeed at MSPT_FULL down to 0 at MSPT_ZERO.
     */
    private static class RandomTickAdjuster {

        private static final double MSPT_FULL = 20.0;  // full speed below this
        private static final double MSPT_ZERO = 40.0;  // disabled at or above this
        private final Map<World, Integer> originals = new HashMap<>();
        private final Map<World, Integer> applied = new HashMap<>();

        RandomTickAdjuster(List<World> worlds) {
            for (World w : worlds) {
                Integer v = w.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
                originals.put(w, v);
                applied.put(w, v);
            }
        }

        void adjust(double mspt) {
            originals.forEach((world, original) -> {
                if (original == 0) return;
                int target = interpolate(mspt, original);
                Integer current = applied.get(world);
                if (current == null || current != target) {
                    world.setGameRule(GameRules.RANDOM_TICK_SPEED, target);
                    applied.put(world, target);
                }
            });
        }

        void reset() {
            originals.forEach((world, original) -> {
                Integer current = applied.get(world);
                if (!Objects.equals(current, original)) {
                    world.setGameRule(GameRules.RANDOM_TICK_SPEED, original);
                    applied.put(world, original);
                }
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
     * Dynamically limits per-player render/simulation distance from current MSPT.
     * Players that stay AFK long enough are clamped to fixed distances until they become active again.
     */
    private static class PlayerDistanceController {

        private static final double MSPT_FULL_DISTANCE = 20.0;
        private static final double MSPT_MIN_DISTANCE = 50.0;

        private static final int PERFORMANCE_RENDER_MIN = 5;
        private static final int PERFORMANCE_SIMULATION_MIN = 4;
        private static final int AFK_RENDER_DISTANCE = 2;
        private static final int AFK_SIMULATION_DISTANCE = 2;
        private static final int DISTANCE_ADJUST_COOLDOWN_WINDOWS = 15;  // ~30 s
        private static final int DISTANCE_RECOVERY_CONFIRM_WINDOWS = 5;  // ~10 s

        private final AfkService afkService;
        private final int baseRenderDistance;
        private final int baseSimulationDistance;
        private final int afkRenderDistance;
        private final int afkSimulationDistance;
        private final Map<UUID, AppliedDistances> appliedDistances = new HashMap<>();
        private int currentPerformanceRenderDistance;
        private int currentPerformanceSimulationDistance;
        private int distanceAdjustCooldown = 0;
        private int recoveryConfirmCount = 0;

        PlayerDistanceController(AfkService afkService, int baseRenderDistance, int baseSimulationDistance) {
            this.afkService = afkService;
            this.baseRenderDistance = Math.max(2, baseRenderDistance);
            this.baseSimulationDistance = Math.max(2, baseSimulationDistance);
            this.afkRenderDistance = Math.clamp(AFK_RENDER_DISTANCE, 2, this.baseRenderDistance);
            this.afkSimulationDistance = Math.clamp(AFK_SIMULATION_DISTANCE, 2, this.baseSimulationDistance);
            this.currentPerformanceRenderDistance = this.baseRenderDistance;
            this.currentPerformanceSimulationDistance = this.baseSimulationDistance;
        }

        void primeOnlinePlayers() {
            Bukkit.getOnlinePlayers().forEach(player -> apply(player,
                    baseRenderDistance,
                    baseSimulationDistance));
        }

        void track(Player player) {
            apply(player,
                    currentPerformanceRenderDistance,
                    currentPerformanceSimulationDistance);
        }

        void untrack(Player player) {
            appliedDistances.remove(player.getUniqueId());
        }

        void adjust(double effectiveMspt) {
            int performanceRender = interpolateMspt(effectiveMspt, baseRenderDistance, PERFORMANCE_RENDER_MIN);
            int performanceSimulation = interpolateMspt(effectiveMspt, baseSimulationDistance, PERFORMANCE_SIMULATION_MIN);
            updatePerformanceDistances(performanceRender, performanceSimulation);
            for (Player player : Bukkit.getOnlinePlayers()) {
                apply(player, currentPerformanceRenderDistance, currentPerformanceSimulationDistance);
            }
        }

        void resetAll() {
            currentPerformanceRenderDistance = baseRenderDistance;
            currentPerformanceSimulationDistance = baseSimulationDistance;
            distanceAdjustCooldown = 0;
            recoveryConfirmCount = 0;
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyIfChanged(player, baseRenderDistance, baseSimulationDistance);
            }
            appliedDistances.clear();
        }

        private void apply(Player player, int performanceRender, int performanceSimulation) {
            if (afkService.isAfk(player.getUniqueId())) {
                applyIfChanged(player, afkRenderDistance, afkSimulationDistance);
                return;
            }

            applyIfChanged(player, performanceRender, performanceSimulation);
        }

        private void updatePerformanceDistances(int targetRender, int targetSimulation) {
            if (distanceAdjustCooldown > 0) {
                distanceAdjustCooldown--;
            }

            boolean lowering = targetRender < currentPerformanceRenderDistance
                    || targetSimulation < currentPerformanceSimulationDistance;
            boolean raising = targetRender > currentPerformanceRenderDistance
                    || targetSimulation > currentPerformanceSimulationDistance;
            if (!lowering && !raising) {
                recoveryConfirmCount = 0;
                return;
            }

            if (lowering) {
                recoveryConfirmCount = 0;
                if (distanceAdjustCooldown == 0 || isMinimumPerformanceDistance(targetRender, targetSimulation)) {
                    applyPerformanceDistances(targetRender, targetSimulation);
                }
                return;
            }

            recoveryConfirmCount = Math.min(recoveryConfirmCount + 1, DISTANCE_RECOVERY_CONFIRM_WINDOWS);
            if (distanceAdjustCooldown == 0 && recoveryConfirmCount >= DISTANCE_RECOVERY_CONFIRM_WINDOWS) {
                applyPerformanceDistances(targetRender, targetSimulation);
                recoveryConfirmCount = 0;
            }
        }

        private void applyPerformanceDistances(int renderDistance, int simulationDistance) {
            currentPerformanceRenderDistance = renderDistance;
            currentPerformanceSimulationDistance = simulationDistance;
            distanceAdjustCooldown = DISTANCE_ADJUST_COOLDOWN_WINDOWS;
        }

        private boolean isMinimumPerformanceDistance(int renderDistance, int simulationDistance) {
            return renderDistance <= Math.clamp(PERFORMANCE_RENDER_MIN, 2, baseRenderDistance)
                    && simulationDistance <= Math.clamp(PERFORMANCE_SIMULATION_MIN, 2, baseSimulationDistance);
        }

        private void applyIfChanged(Player player, int renderDistance, int simulationDistance) {
            UUID playerId = player.getUniqueId();
            AppliedDistances current = appliedDistances.get(playerId);
            if (current != null
                    && current.renderDistance == renderDistance
                    && current.simulationDistance == simulationDistance) {
                return;
            }

            player.setViewDistance(renderDistance);
            player.setSimulationDistance(simulationDistance);
            appliedDistances.put(playerId, new AppliedDistances(renderDistance, simulationDistance));
        }

        private int interpolateMspt(double mspt, int baseDistance, int minDistance) {
            int floor = Math.clamp(minDistance, 2, baseDistance);
            if (baseDistance <= floor) return baseDistance;
            if (mspt <= MSPT_FULL_DISTANCE) return baseDistance;
            if (mspt >= MSPT_MIN_DISTANCE) return floor;

            double ratio = (MSPT_MIN_DISTANCE - mspt) / (MSPT_MIN_DISTANCE - MSPT_FULL_DISTANCE);
            return floor + (int) Math.round((baseDistance - floor) * ratio);
        }

        private record AppliedDistances(int renderDistance, int simulationDistance) {
        }
    }

    /**
     * Chunk-local throttling inspired by FAWE's old tick limiter, but without
     * stack-trace probing or global side effects. The goal is to locally melt
     * down abusive chunks before the whole server needs freezing.
     */
    private final class ChunkPressureController {

        private static final int THROTTLE_DURATION_WINDOWS = 1;
        private static final int SOFT_BREACH_MEMORY_WINDOWS = 2;
        private static final long LOG_COOLDOWN_MILLIS = 10_000L;

        private static final int COUNTER_COUNT = 6;
        private static final PressureRule PHYSICS = new PressureRule(0, 768, 2048, "physics");
        private static final PressureRule REDSTONE = new PressureRule(1, 640, 2048, "redstone");
        private static final PressureRule HOPPER = new PressureRule(2, 384, 1024, "hopper");
        private static final PressureRule PISTON = new PressureRule(3, 192, 512, "piston");
        private static final PressureRule ITEM_SPAWN = new PressureRule(4, 96, 256, "item-spawn");
        private static final PressureRule FALLING_BLOCK = new PressureRule(5, 96, 256, "falling-block");

        private final Map<Long, int[]> counters = new HashMap<>();
        private final Map<Long, int[]> softBreaches = new HashMap<>();
        private final Map<Long, Integer> throttledChunks = new HashMap<>();
        private long lastLogAtMillis;

        boolean onPhysics(Block block, boolean underPressure) {
            return shouldThrottle(block, PHYSICS, underPressure);
        }

        boolean onRedstone(Block block, boolean underPressure) {
            return shouldThrottle(block, REDSTONE, underPressure);
        }

        boolean onHopperMove(Block block, boolean underPressure) {
            return shouldThrottle(block, HOPPER, underPressure);
        }

        boolean onPiston(Block block, boolean underPressure) {
            return shouldThrottle(block, PISTON, underPressure);
        }

        boolean onItemSpawn(Block block, boolean underPressure) {
            return shouldThrottle(block, ITEM_SPAWN, underPressure);
        }

        boolean onFallingBlock(Block block, boolean underPressure) {
            return shouldThrottle(block, FALLING_BLOCK, underPressure);
        }

        void rollWindow() {
            counters.clear();
            decaySoftBreaches();
            if (throttledChunks.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<Long, Integer>> iterator = throttledChunks.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Long, Integer> entry = iterator.next();
                int next = entry.getValue() - 1;
                if (next <= 0) {
                    iterator.remove();
                } else {
                    entry.setValue(next);
                }
            }
        }

        void reset() {
            counters.clear();
            softBreaches.clear();
            throttledChunks.clear();
            lastLogAtMillis = 0L;
        }

        void disarm() {
            counters.clear();
            softBreaches.clear();
            throttledChunks.clear();
        }

        private boolean shouldThrottle(Block block, PressureRule rule, boolean underPressure) {
            if (!underPressure) {
                return false;
            }

            long key = chunkKey(block);
            if (throttledChunks.containsKey(key)) {
                return true;
            }

            int[] counts = counters.computeIfAbsent(key, ignored -> new int[COUNTER_COUNT]);
            int current = ++counts[rule.index()];
            if (current >= rule.hardLimit()) {
                throttleChunk(block, rule.category(), current, true);
                return true;
            }

            if (current < rule.softLimit()) {
                return false;
            }

            int[] breaches = softBreaches.computeIfAbsent(key, ignored -> new int[COUNTER_COUNT]);
            if (breaches[rule.index()] > 0) {
                breaches[rule.index()] = 0;
                throttleChunk(block, rule.category(), current, false);
                if (isAllZero(breaches)) {
                    softBreaches.remove(key);
                }
                return true;
            }

            breaches[rule.index()] = SOFT_BREACH_MEMORY_WINDOWS;
            return false;
        }

        private void throttleChunk(Block source, String category, int count, boolean hardLimit) {
            throttledChunks.put(chunkKey(source), THROTTLE_DURATION_WINDOWS);
            maybeLog(source, category, count, hardLimit);
        }

        private void decaySoftBreaches() {
            if (softBreaches.isEmpty()) {
                return;
            }
            Iterator<Map.Entry<Long, int[]>> iterator = softBreaches.entrySet().iterator();
            while (iterator.hasNext()) {
                int[] breaches = iterator.next().getValue();
                for (int i = 0; i < breaches.length; i++) {
                    if (breaches[i] > 0) {
                        breaches[i]--;
                    }
                }
                if (isAllZero(breaches)) {
                    iterator.remove();
                }
            }
        }

        private boolean isAllZero(int[] values) {
            for (int value : values) {
                if (value != 0) {
                    return false;
                }
            }
            return true;
        }

        private void maybeLog(Block source, String category, int count, boolean hardLimit) {
            long now = System.currentTimeMillis();
            if (now - lastLogAtMillis < LOG_COOLDOWN_MILLIS) {
                return;
            }
            lastLogAtMillis = now;
            plugin.getLogger().warning(String.format(
                    Locale.ROOT,
                    "Chunk guard throttled %s near %s,%s,%s in %s (count=%d, mspt=%.1f, mode=%s)",
                    category,
                    source.getX(),
                    source.getY(),
                    source.getZ(),
                    source.getWorld().getName(),
                    count,
                    lastEffectiveMspt,
                    hardLimit ? "hard" : "soft-confirmed"
            ));
        }

        private long chunkKey(Block block) {
            return chunkKey(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
        }

        private long chunkKey(World world, int chunkX, int chunkZ) {
            long worldBits = world.getUID().getMostSignificantBits() ^ world.getUID().getLeastSignificantBits();
            return Long.rotateLeft(worldBits, 21) ^ ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        }

        private record PressureRule(int index, int softLimit, int hardLimit, String category) {
        }
    }

    /**
     * Stateless emergency entity cleanup.
     */
    private static class EntityCleaner {

        private static final int ITEM_MAX_TICKS = 3000;

        private static final Set<EntityType> REMOVABLE = EnumSet.of(
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER, EntityType.SPIDER,
                EntityType.CAVE_SPIDER, EntityType.ENDERMAN, EntityType.WITCH, EntityType.SLIME,
                EntityType.PHANTOM, EntityType.DROWNED, EntityType.HUSK, EntityType.STRAY,
                EntityType.SILVERFISH, EntityType.ENDERMITE, EntityType.BLAZE, EntityType.GHAST,
                EntityType.MAGMA_CUBE, EntityType.ZOMBIFIED_PIGLIN, EntityType.VINDICATOR, EntityType.EVOKER,
                EntityType.VEX, EntityType.PILLAGER, EntityType.RAVAGER, EntityType.WITHER_SKELETON,
                EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN,
                EntityType.HORSE, EntityType.WOLF, EntityType.CAT
        );

        static void run(List<World> worlds) {
            for (World world : worlds) {
                for (Entity entity : world.getEntities()) {
                    if (entity instanceof Vehicle vehicle && !vehicle.getPassengers().isEmpty()) {
                        continue;
                    }
                    if (entity instanceof Item item) {
                        if (item.getTicksLived() > ITEM_MAX_TICKS) {
                            item.remove();
                        }
                        continue;
                    }
                    if (entity instanceof Projectile || entity instanceof ExperienceOrb) {
                        entity.remove();
                        continue;
                    }
                    if (entity instanceof LivingEntity livingEntity && isRemovable(livingEntity)) {
                        entity.remove();
                    }
                }
            }
        }

        private static boolean isRemovable(LivingEntity e) {
            if (!REMOVABLE.contains(e.getType())) return false;
            if (e.customName() != null) return false;
            return !(e instanceof Tameable t) || t.getOwner() == null;
        }
    }

    private static class WindChargeCleaner {

        static void run(List<World> worlds) {
            for (World world : worlds) {
                for (AbstractWindCharge windCharge : world.getEntitiesByClass(AbstractWindCharge.class)) {
                    if (shouldRemoveWindCharge(windCharge.getTicksLived())) {
                        windCharge.remove();
                    }
                }
            }
        }
    }
}
