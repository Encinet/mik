package org.encinet.mik.module;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.ServerTickManager;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.util.SchedulerUtil;

import java.util.*;

public class PerformanceModule implements Listener {

    private static final String MANAGER_PERMISSION = "group.manager";
    private static final long CHECK_INTERVAL_TICKS = 40L;
    private static final long INITIAL_DELAY_TICKS = 1200L;

    private static final double THRESHOLD_SHUTDOWN = 1000.0;
    private static final double THRESHOLD_KICK = 150.0;
    private static final double THRESHOLD_CHUNK_GUARD = 32.0;

    private final JavaPlugin plugin;
    private final ServerTickManager tickManager;
    private final Component kickMessage;

    private final MsptSampler sampler;
    private final FreezeController freezeController;
    private final RandomTickAdjuster tickAdjuster;
    private final PlayerDistanceController distanceController;
    private final ChunkPressureController pressureController;

    private BukkitTask guardTask;
    private volatile double lastEffectiveMspt = 20.0;

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
        this.distanceController = new PlayerDistanceController(
                plugin.getServer().getViewDistance(),
                plugin.getServer().getSimulationDistance());
        this.pressureController = new ChunkPressureController();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        distanceController.primeOnlinePlayers();
        guardTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::tick, INITIAL_DELAY_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void stop() {
        if (guardTask != null) guardTask.cancel();
        guardTask = null;
        if (tickManager.isFrozen()) tickManager.setFrozen(false);
        freezeController.reset();
        tickAdjuster.reset();
        distanceController.resetAll();
        pressureController.reset();
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
            SchedulerUtil.runSync(plugin, () -> {
                List<Player> playersToKick = new ArrayList<>(Bukkit.getOnlinePlayers());
                playersToKick.stream()
                        .filter(player -> !player.hasPermission(MANAGER_PERMISSION))
                        .forEach(player -> player.kick(kickMessage));
            });
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
        lastEffectiveMspt = effectiveMspt;
        SchedulerUtil.runSync(plugin, () -> {
            pressureController.rollWindow();
            tickAdjuster.adjust(effectiveMspt);
            distanceController.adjust(effectiveMspt);
        });
    }

    // ── Event handlers ──────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        distanceController.track(event.getPlayer(), lastEffectiveMspt);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        distanceController.untrack(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isMeaningfulActivity(event.getFrom(), event.getTo())) {
            recordActivity(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        SchedulerUtil.runSync(plugin, () -> recordActivity(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerSprint(PlayerToggleSprintEvent event) {
        recordActivity(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            recordActivity(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRedstoneChange(BlockRedstoneEvent event) {
        if (freezeController.isFrozen()
                || pressureController.onRedstone(event.getBlock(), shouldApplyChunkGuard())) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (freezeController.isFrozen()
                || pressureController.onPiston(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (freezeController.isFrozen()
                || pressureController.onPiston(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!(event.getInitiator().getHolder() instanceof Hopper hopper)) {
            return;
        }
        if (freezeController.isFrozen()
                || pressureController.onHopperMove(hopper.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (freezeController.isFrozen()
                || pressureController.onPhysics(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFallingBlockChange(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.FALLING_BLOCK) {
            return;
        }
        if (freezeController.isFrozen()
                || pressureController.onFallingBlock(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (freezeController.isFrozen()
                || pressureController.onItemSpawn(event.getLocation().getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    private boolean isMeaningfulActivity(Location from, Location to) {
        if (to == null) return false;
        if (!Objects.equals(from.getWorld(), to.getWorld())) return true;
        if (from.distanceSquared(to) > 0.01D) return true;
        return angularDelta(from.getYaw(), to.getYaw()) >= 8.0F
                || Math.abs(from.getPitch() - to.getPitch()) >= 8.0F;
    }

    private float angularDelta(float a, float b) {
        float delta = Math.abs(a - b) % 360.0F;
        return delta > 180.0F ? 360.0F - delta : delta;
    }

    private void recordActivity(Player player) {
        distanceController.markActive(player, lastEffectiveMspt);
    }

    private boolean shouldApplyChunkGuard() {
        return freezeController.isFrozen() || lastEffectiveMspt >= THRESHOLD_CHUNK_GUARD;
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
        private static final double MSPT_UNFREEZE = 38.0;  // hysteresis gap = 10 ms

        // Debounce
        private static final int FREEZE_CONFIRM = 3;  // ~6 s
        private static final int UNFREEZE_CONFIRM = 5;  // ~10 s

        // Trend boost: each SLOPE ms/reading of rise = +1 extra high count, capped at MAX
        private static final double TREND_BOOST_SLOPE = 3.0;
        private static final int TREND_BOOST_MAX = 1;

        private volatile boolean frozen;
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

            if (!frozen && highCount >= FREEZE_CONFIRM) {
                frozen = true;
                normalCount = 0;
                return Decision.FREEZE;
            }
            if (frozen && normalCount >= UNFREEZE_CONFIRM) {
                frozen = false;
                highCount = 0;
                normalCount = 0;
                return Decision.UNFREEZE;
            }
            return Decision.HOLD;
        }

        boolean isFrozen() {
            return frozen;
        }

        void reset() {
            frozen = false;
            highCount = 0;
            normalCount = 0;
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

        void reset() {
            originals.forEach((world, original) -> {
                Integer current = world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED);
                if (current == null || !current.equals(original)) {
                    world.setGameRule(GameRules.RANDOM_TICK_SPEED, original);
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

        private static final long AFK_TIMEOUT_MILLIS = 3L * 60L * 1000L;

        private static final int PERFORMANCE_RENDER_MIN = 5;
        private static final int PERFORMANCE_SIMULATION_MIN = 4;
        private static final int AFK_RENDER_DISTANCE = 2;
        private static final int AFK_SIMULATION_DISTANCE = 2;

        private final int baseRenderDistance;
        private final int baseSimulationDistance;
        private final int afkRenderDistance;
        private final int afkSimulationDistance;
        private final Map<UUID, AppliedDistances> appliedDistances = new HashMap<>();
        private final Map<UUID, Long> lastActiveAt = new HashMap<>();

        PlayerDistanceController(int baseRenderDistance, int baseSimulationDistance) {
            this.baseRenderDistance = Math.max(2, baseRenderDistance);
            this.baseSimulationDistance = Math.max(2, baseSimulationDistance);
            this.afkRenderDistance = Math.clamp(AFK_RENDER_DISTANCE, 2, this.baseRenderDistance);
            this.afkSimulationDistance = Math.clamp(AFK_SIMULATION_DISTANCE, 2, this.baseSimulationDistance);
        }

        void primeOnlinePlayers() {
            long now = System.currentTimeMillis();
            Bukkit.getOnlinePlayers().forEach(player -> lastActiveAt.putIfAbsent(player.getUniqueId(), now));
        }

        void track(Player player, double effectiveMspt) {
            lastActiveAt.put(player.getUniqueId(), System.currentTimeMillis());
            apply(player, effectiveMspt);
        }

        void untrack(Player player) {
            UUID playerId = player.getUniqueId();
            lastActiveAt.remove(playerId);
            appliedDistances.remove(playerId);
        }

        void markActive(Player player, double effectiveMspt) {
            long now = System.currentTimeMillis();
            UUID playerId = player.getUniqueId();
            long previousActiveAt = lastActiveAt.getOrDefault(playerId, 0L);
            boolean restoreImmediately = previousActiveAt == 0L || now - previousActiveAt >= AFK_TIMEOUT_MILLIS;

            lastActiveAt.put(playerId, now);
            if (restoreImmediately) {
                apply(player, effectiveMspt);
            }
        }

        void adjust(double effectiveMspt) {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                lastActiveAt.putIfAbsent(player.getUniqueId(), now);
                apply(player, effectiveMspt);
            }
        }

        void resetAll() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyIfChanged(player, baseRenderDistance, baseSimulationDistance);
            }
            appliedDistances.clear();
            lastActiveAt.clear();
        }

        private void apply(Player player, double effectiveMspt) {
            long idleMillis = Math.max(0L,
                    System.currentTimeMillis() - lastActiveAt.getOrDefault(player.getUniqueId(), System.currentTimeMillis()));

            if (idleMillis >= AFK_TIMEOUT_MILLIS) {
                applyIfChanged(player, afkRenderDistance, afkSimulationDistance);
                return;
            }

            int performanceRender = interpolateMspt(effectiveMspt, baseRenderDistance, PERFORMANCE_RENDER_MIN);
            int performanceSimulation = interpolateMspt(effectiveMspt, baseSimulationDistance, PERFORMANCE_SIMULATION_MIN);
            applyIfChanged(player, performanceRender, performanceSimulation);
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

        private static final int THROTTLE_DURATION_WINDOWS = 2;
        private static final long LOG_COOLDOWN_MILLIS = 10_000L;

        private static final int COUNTER_COUNT = 6;
        private static final PressureRule PHYSICS = new PressureRule(0, 384, 1024, "physics");
        private static final PressureRule REDSTONE = new PressureRule(1, 256, 768, "redstone");
        private static final PressureRule HOPPER = new PressureRule(2, 192, 512, "hopper");
        private static final PressureRule PISTON = new PressureRule(3, 96, 256, "piston");
        private static final PressureRule ITEM_SPAWN = new PressureRule(4, 48, 128, "item-spawn");
        private static final PressureRule FALLING_BLOCK = new PressureRule(5, 48, 128, "falling-block");

        private final Map<Long, int[]> counters = new HashMap<>();
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
            throttledChunks.clear();
            lastLogAtMillis = 0L;
        }

        private boolean shouldThrottle(Block block, PressureRule rule, boolean underPressure) {
            long key = chunkKey(block);
            if (throttledChunks.containsKey(key)) {
                return true;
            }

            int[] counts = counters.computeIfAbsent(key, ignored -> new int[COUNTER_COUNT]);
            int current = ++counts[rule.index()];
            if (current >= rule.hardLimit() || (underPressure && current >= rule.softLimit())) {
                throttleNeighborhood(block, rule.category(), current);
                return true;
            }
            return false;
        }

        private void throttleNeighborhood(Block source, String category, int count) {
            int chunkX = source.getX() >> 4;
            int chunkZ = source.getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    throttledChunks.put(chunkKey(source.getWorld(), chunkX + dx, chunkZ + dz), THROTTLE_DURATION_WINDOWS);
                }
            }
            maybeLog(source, category, count);
        }

        private void maybeLog(Block source, String category, int count) {
            long now = System.currentTimeMillis();
            if (now - lastLogAtMillis < LOG_COOLDOWN_MILLIS) {
                return;
            }
            lastLogAtMillis = now;
            plugin.getLogger().warning(String.format(
                    Locale.ROOT,
                    "Chunk guard throttled %s near %s,%s,%s in %s (count=%d, mspt=%.1f)",
                    category,
                    source.getX(),
                    source.getY(),
                    source.getZ(),
                    source.getWorld().getName(),
                    count,
                    lastEffectiveMspt
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
                EntityType.VEX, EntityType.PILLAGER, EntityType.RAVAGER,
                EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN,
                EntityType.HORSE, EntityType.WOLF, EntityType.CAT
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
            if (!REMOVABLE.contains(e.getType())) return false;
            if (e.customName() != null) return false;
            return !(e instanceof Tameable t) || t.getOwner() == null;
        }
    }
}
