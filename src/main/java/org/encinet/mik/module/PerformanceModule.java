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
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceModule implements Listener {

    private static final String MANAGER_PERMISSION = "group.manager";
    private static final long CHECK_INTERVAL_TICKS = 40L;
    private static final long INITIAL_DELAY_TICKS = 1200L;

    private static final double THRESHOLD_SHUTDOWN = 1000.0;
    private static final double THRESHOLD_KICK = 150.0;
    private static final double THRESHOLD_CHUNK_GUARD = 40.0;

    private final JavaPlugin plugin;
    private final ServerTickManager tickManager;
    private final Component kickMessage;

    private final MsptSampler sampler;
    private final FreezeController freezeController;
    private final EmergencyController emergencyController;
    private final RandomTickAdjuster tickAdjuster;
    private final PlayerDistanceController distanceController;
    private final ChunkPressureController pressureController;

    private BukkitTask guardTask;
    private volatile double lastEffectiveMspt = 20.0;
    private volatile boolean liveFrozen = false;

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
        this.emergencyController = new EmergencyController();
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
        liveFrozen = false;
        freezeController.reset();
        emergencyController.reset();
        tickAdjuster.reset();
        distanceController.resetAll();
        pressureController.reset();
    }

    private void tick() {
        double mspt = sampler.update(Bukkit.getAverageTickTime());
        double trend = sampler.trend();

        FreezeController.Decision decision = freezeController.evaluate(mspt, trend, liveFrozen);
        boolean freezeArmed = liveFrozen || decision == FreezeController.Decision.FREEZE;

        EmergencyController.Decision emergencyDecision =
                emergencyController.evaluate(mspt, freezeArmed);

        double effectiveMspt = Math.max(mspt, mspt + trend);
        boolean chunkGuardArmed = freezeArmed || effectiveMspt >= THRESHOLD_CHUNK_GUARD;
        lastEffectiveMspt = effectiveMspt;
        SchedulerUtil.runSync(plugin, () -> {
            distanceController.flushPendingActivity(effectiveMspt);

            switch (decision) {
                case FREEZE -> {
                    if (!tickManager.isFrozen()) tickManager.setFrozen(true);
                    liveFrozen = true;
                    EntityCleaner.run(Bukkit.getWorlds());
                    plugin.getLogger().warning(String.format(Locale.ROOT, "Server frozen (MSPT=%.1f)", mspt));
                }
                case UNFREEZE -> {
                    if (tickManager.isFrozen()) tickManager.setFrozen(false);
                    liveFrozen = false;
                    plugin.getLogger().info("Server recovered, unfreezing.");
                }
                case HOLD -> {
                }
            }

            switch (emergencyDecision) {
                case KICK -> {
                    List<Player> playersToKick = new ArrayList<>();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        if (!player.hasPermission(MANAGER_PERMISSION)) {
                            playersToKick.add(player);
                        }
                    }
                    if (!playersToKick.isEmpty()) {
                        plugin.getLogger().warning(String.format(
                                Locale.ROOT,
                                "MSPT stayed above %.0f ms while frozen (current=%.1f), kicking %d players.",
                                THRESHOLD_KICK,
                                mspt,
                                playersToKick.size()
                        ));
                        for (Player player : playersToKick) {
                            player.kick(kickMessage);
                        }
                    }
                }
                case SHUTDOWN -> {
                    plugin.getLogger().severe(String.format(
                            Locale.ROOT,
                            "MSPT stayed above %.0f ms while frozen (current=%.1f), shutting down!",
                            THRESHOLD_SHUTDOWN,
                            mspt
                    ));
                    Bukkit.getServer().shutdown();
                    return;
                }
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
        distanceController.queueAsyncActivity(event.getPlayer().getUniqueId());
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
        if (isServerFrozen()
                || pressureController.onRedstone(event.getBlock(), shouldApplyChunkGuard())) {
            event.setNewCurrent(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (isServerFrozen()
                || pressureController.onPiston(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (isServerFrozen()
                || pressureController.onPiston(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (!(event.getInitiator().getHolder() instanceof Hopper hopper)) {
            return;
        }
        if (isServerFrozen()
                || pressureController.onHopperMove(hopper.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (isServerFrozen()
                || pressureController.onPhysics(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFallingBlockChange(EntityChangeBlockEvent event) {
        if (event.getEntityType() != EntityType.FALLING_BLOCK) {
            return;
        }
        if (isServerFrozen()
                || pressureController.onFallingBlock(event.getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (isServerFrozen()
                || pressureController.onItemSpawn(event.getLocation().getBlock(), shouldApplyChunkGuard())) {
            event.setCancelled(true);
        }
    }

    private boolean isMeaningfulActivity(Location from, Location to) {
        if (to == null) return false;
        if (!Objects.equals(from.getWorld(), to.getWorld())) return true;
        double dx = from.getX() - to.getX();
        double dy = from.getY() - to.getY();
        double dz = from.getZ() - to.getZ();
        if (dx * dx + dy * dy + dz * dz > 0.01D) return true;
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

    private boolean isServerFrozen() {
        return liveFrozen;
    }

    private boolean shouldApplyChunkGuard() {
        return liveFrozen || lastEffectiveMspt >= THRESHOLD_CHUNK_GUARD;
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

        private int highCount = 0;
        private int normalCount = 0;

        Decision evaluate(double mspt, double trend, boolean currentlyFrozen) {
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

            if (!currentlyFrozen && highCount >= FREEZE_CONFIRM) {
                normalCount = 0;
                return Decision.FREEZE;
            }
            if (currentlyFrozen && normalCount >= UNFREEZE_CONFIRM) {
                highCount = 0;
                normalCount = 0;
                return Decision.UNFREEZE;
            }
            return Decision.HOLD;
        }

        void reset() {
            highCount = 0;
            normalCount = 0;
        }
    }

    /**
     * Requires sustained severe lag before escalating to player kicks or shutdown.
     * Short chunk-generation spikes should be handled by the softer protections first.
     */
    private static class EmergencyController {

        enum Decision {KICK, SHUTDOWN, HOLD}

        private static final int KICK_CONFIRM = 4;  // ~8 s while frozen
        private static final int SHUTDOWN_CONFIRM = 8;  // ~16 s while frozen
        private static final int KICK_COOLDOWN_WINDOWS = 6;  // ~12 s

        private int severeCount = 0;
        private int criticalCount = 0;
        private int kickCooldown = 0;

        Decision evaluate(double mspt, boolean frozen) {
            if (kickCooldown > 0) {
                kickCooldown--;
            }

            if (!frozen) {
                severeCount = Math.max(severeCount - 1, 0);
                criticalCount = Math.max(criticalCount - 1, 0);
                return Decision.HOLD;
            }

            if (mspt >= THRESHOLD_KICK) {
                severeCount = Math.min(severeCount + 1, KICK_CONFIRM);
            } else {
                severeCount = Math.max(severeCount - 1, 0);
            }

            if (mspt >= THRESHOLD_SHUTDOWN) {
                criticalCount = Math.min(criticalCount + 1, SHUTDOWN_CONFIRM);
            } else {
                criticalCount = Math.max(criticalCount - 1, 0);
            }

            if (criticalCount >= SHUTDOWN_CONFIRM) {
                severeCount = 0;
                criticalCount = 0;
                kickCooldown = 0;
                return Decision.SHUTDOWN;
            }

            if (severeCount >= KICK_CONFIRM && kickCooldown == 0) {
                severeCount = 0;
                kickCooldown = KICK_COOLDOWN_WINDOWS;
                return Decision.KICK;
            }

            return Decision.HOLD;
        }

        void reset() {
            severeCount = 0;
            criticalCount = 0;
            kickCooldown = 0;
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
                if (v != null) {
                    originals.put(w, v);
                    applied.put(w, v);
                }
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
        private final Set<UUID> pendingAsyncActivity = ConcurrentHashMap.newKeySet();

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
            long now = System.currentTimeMillis();
            lastActiveAt.put(player.getUniqueId(), now);
            apply(player, now,
                    interpolateMspt(effectiveMspt, baseRenderDistance, PERFORMANCE_RENDER_MIN),
                    interpolateMspt(effectiveMspt, baseSimulationDistance, PERFORMANCE_SIMULATION_MIN));
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
                apply(player, now,
                        interpolateMspt(effectiveMspt, baseRenderDistance, PERFORMANCE_RENDER_MIN),
                        interpolateMspt(effectiveMspt, baseSimulationDistance, PERFORMANCE_SIMULATION_MIN));
            }
        }

        void queueAsyncActivity(UUID playerId) {
            pendingAsyncActivity.add(playerId);
        }

        void flushPendingActivity(double effectiveMspt) {
            if (pendingAsyncActivity.isEmpty()) {
                return;
            }
            long now = System.currentTimeMillis();
            int performanceRender = interpolateMspt(effectiveMspt, baseRenderDistance, PERFORMANCE_RENDER_MIN);
            int performanceSimulation = interpolateMspt(effectiveMspt, baseSimulationDistance, PERFORMANCE_SIMULATION_MIN);
            Iterator<UUID> iterator = pendingAsyncActivity.iterator();
            while (iterator.hasNext()) {
                UUID playerId = iterator.next();
                iterator.remove();
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    continue;
                }
                long previousActiveAt = lastActiveAt.getOrDefault(playerId, 0L);
                boolean restoreImmediately = previousActiveAt == 0L || now - previousActiveAt >= AFK_TIMEOUT_MILLIS;
                lastActiveAt.put(playerId, now);
                if (restoreImmediately) {
                    apply(player, now, performanceRender, performanceSimulation);
                }
            }
        }

        void adjust(double effectiveMspt) {
            long now = System.currentTimeMillis();
            int performanceRender = interpolateMspt(effectiveMspt, baseRenderDistance, PERFORMANCE_RENDER_MIN);
            int performanceSimulation = interpolateMspt(effectiveMspt, baseSimulationDistance, PERFORMANCE_SIMULATION_MIN);
            for (Player player : Bukkit.getOnlinePlayers()) {
                lastActiveAt.putIfAbsent(player.getUniqueId(), now);
                apply(player, now, performanceRender, performanceSimulation);
            }
        }

        void resetAll() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyIfChanged(player, baseRenderDistance, baseSimulationDistance);
            }
            appliedDistances.clear();
            lastActiveAt.clear();
            pendingAsyncActivity.clear();
        }

        private void apply(Player player, long now, int performanceRender, int performanceSimulation) {
            long idleMillis = Math.max(0L, now - lastActiveAt.getOrDefault(player.getUniqueId(), now));

            if (idleMillis >= AFK_TIMEOUT_MILLIS) {
                applyIfChanged(player, afkRenderDistance, afkSimulationDistance);
                return;
            }

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
}
