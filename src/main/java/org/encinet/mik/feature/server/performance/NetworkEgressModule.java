package org.encinet.mik.feature.server.performance;

import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.player.afk.AfkState;
import org.encinet.mik.feature.player.afk.AfkStateListener;
import org.encinet.mik.feature.player.movement.FlightSpeedControl;
import org.encinet.mik.feature.server.performance.NetworkEgressThrottlePolicy.Limit;

import com.destroystokyo.paper.event.player.PlayerClientOptionsChangeEvent;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.function.DoubleSupplier;

/**
 * Coordinates egress capacity and high-demand exploration using Paper distance and flight controls.
 *
 * <p>The controller deliberately does not cancel protocol packets. Paper remains responsible for
 * chunk and entity state consistency while this module limits demand at its source.</p>
 */
public final class NetworkEgressModule implements Listener, AfkStateListener {

    private static final long SAMPLE_PERIOD_TICKS = 20L;
    private static final long PROBE_REFRESH_NANOS = 60_000_000_000L;
    private static final long UNAVAILABLE_RELEASE_NANOS = 60_000_000_000L;
    private static final long RECONCILE_NANOS = 5_000_000_000L;
    private static final String NETWORK_INTERFACE = "auto";
    private static final double OUTBOUND_BUDGET_MEGABITS = 20.0D;
    private static final double OUTBOUND_BUDGET_BYTES_PER_SECOND =
            OUTBOUND_BUDGET_MEGABITS * 1_000_000.0D / 8.0D;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final DoubleSupplier serverMspt;
    private final Map<UUID, SendDistanceOverride> sendDistanceOverrides = new HashMap<>();
    private final Map<UUID, SendDistanceOverride> loadDistanceOverrides = new HashMap<>();
    private final Map<UUID, BukkitTask> clientOptionTasks = new HashMap<>();
    private final ChunkExplorationController exploration;

    private BukkitTask sampleTask;
    private BukkitTask controlTask;
    private volatile SamplingState samplingState;
    private Limit throttle = NetworkEgressThrottlePolicy.OPEN;

    public NetworkEgressModule(JavaPlugin plugin, AfkService afkService,
                               DoubleSupplier serverMspt, FlightSpeedControl flights) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.serverMspt = serverMspt;
        this.exploration = new ChunkExplorationController(plugin, flights, () -> {
            SamplingState state = samplingState;
            return state == null ? -1.0D : state.policy.explorationPressure();
        }, serverMspt, () -> applyThrottle(throttle), System::nanoTime);
    }

    public synchronized void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        afkService.addListener(this);
        SamplingState state = new SamplingState(NETWORK_INTERFACE, OUTBOUND_BUDGET_BYTES_PER_SECOND);
        samplingState = state;
        exploration.enable();
        scheduleNextSample(state, 1L);
        plugin.getLogger().info("NetworkEgressModule enabled (interface=" + NETWORK_INTERFACE
                + ", protection=enabled, budget=" + OUTBOUND_BUDGET_MEGABITS + " Mbps)");
    }

    public synchronized void disable() {
        samplingState = null;
        if (sampleTask != null) {
            sampleTask.cancel();
            sampleTask = null;
        }
        if (controlTask != null) {
            controlTask.cancel();
            controlTask = null;
        }
        clientOptionTasks.values().forEach(BukkitTask::cancel);
        clientOptionTasks.clear();
        HandlerList.unregisterAll(this);
        afkService.removeListener(this);
        exploration.disable();
        throttle = NetworkEgressThrottlePolicy.OPEN;
        restoreDistances(loadDistanceOverrides, true);
        restoreDistances(sendDistanceOverrides, false);
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("network")
                        .requires(source -> RolePermissions.canModerate(source.getSender()))
                        .then(Commands.literal("status")
                                .executes(context -> sendStatus(context.getSource().getSender())))
                        .executes(context -> sendStatus(context.getSource().getSender()))
                        .build(),
                "Network egress and chunk send-distance status"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        applyCurrentMode(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        applyCurrentMode(event.getPlayer());
    }

    @EventHandler
    public void onPlayerClientOptionsChange(PlayerClientOptionsChangeEvent event) {
        if (!event.hasViewDistanceChanged()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        BukkitTask previousTask = clientOptionTasks.remove(playerId);
        if (previousTask != null) {
            previousTask.cancel();
        }
        SamplingState state = samplingState;
        clientOptionTasks.put(playerId, Bukkit.getScheduler().runTask(plugin, () -> {
            clientOptionTasks.remove(playerId);
            if (isActive(state) && player.isOnline()) {
                applyCurrentMode(player);
            }
        }));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        sendDistanceOverrides.remove(playerId);
        loadDistanceOverrides.remove(playerId);
        BukkitTask task = clientOptionTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public void onAfkStateChanged(Player player, AfkState state) {
        applyCurrentMode(player);
    }

    private synchronized void scheduleNextSample(SamplingState state, long delayTicks) {
        if (!isActive(state) || !plugin.isEnabled()) {
            return;
        }
        sampleTask = Bukkit.getScheduler().runTaskLaterAsynchronously(
                plugin, () -> runSample(state), delayTicks);
    }

    private void runSample(SamplingState state) {
        if (!isActive(state)) {
            return;
        }
        NetworkEgressRateMeter.Reading reading = readNetwork(state);
        long sampledAt = System.nanoTime();
        synchronized (this) {
            if (!isActive(state)) {
                return;
            }
            // One outstanding read/control cycle prevents stale samples building up during server lag.
            controlTask = Bukkit.getScheduler().runTask(plugin, () -> {
                if (!isActive(state)) {
                    return;
                }
                try {
                    controlNetwork(state, reading, sampledAt);
                } finally {
                    scheduleNextSample(state, SAMPLE_PERIOD_TICKS);
                }
            });
        }
    }

    private NetworkEgressRateMeter.Reading readNetwork(SamplingState state) {
        try {
            long now = System.nanoTime();
            if (state.probe == null || now - state.lastProbeRefreshNanos >= PROBE_REFRESH_NANOS) {
                refreshProbe(state);
                state.lastProbeRefreshNanos = now;
            }
            long bytes = state.probe == null ? -1L : state.probe.readTransmitBytes();
            if (bytes < 0L) {
                state.lastProbeRefreshNanos = now - PROBE_REFRESH_NANOS;
            }
            return state.meter.sample(bytes, System.nanoTime());
        } catch (RuntimeException failure) {
            // Treat provider failures like unavailable counters, without ending the sampling loop.
            state.meter.reset();
            plugin.getLogger().log(Level.FINE, "Could not sample network egress", failure);
            return new NetworkEgressRateMeter.Reading(-1.0D, 0.0D);
        }
    }

    private void controlNetwork(SamplingState state, NetworkEgressRateMeter.Reading reading, long sampledAt) {
        state.policy.observeExplorers(exploration.hasHeavyDemand());
        Limit next = state.updateThrottle(reading, sampledAt, System.nanoTime(), afkReliefAvailable());
        if (!next.equals(throttle)) {
            transitionTo(next);
            state.lastReconcileNanos = sampledAt;
        } else if (next.throttled() && sampledAt - state.lastReconcileNanos >= RECONCILE_NANOS) {
            applyThrottle(next);
            state.lastReconcileNanos = sampledAt;
        }
    }

    private boolean afkReliefAvailable() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (afkService.isAfk(player.getUniqueId()) && player.getSendViewDistance() > 2) {
                return true;
            }
        }
        return false;
    }

    private boolean isActive(SamplingState state) {
        return state != null && samplingState == state;
    }

    private void transitionTo(Limit nextThrottle) {
        if (nextThrottle.equals(throttle)) {
            return;
        }
        Limit previousThrottle = throttle;
        applyThrottle(nextThrottle);
        String message = String.format(Locale.ROOT,
                "Network egress %s -> %s, tx=%s, budget=%s, send-distance=%s",
                previousThrottle,
                nextThrottle,
                formatThroughput(smoothedTransmitBytesPerSecond()),
                formatThroughput(OUTBOUND_BUDGET_BYTES_PER_SECOND),
                sendDistanceSummary());
        if (!previousThrottle.stage().equals(nextThrottle.stage())) {
            plugin.getLogger().info(message);
        } else {
            plugin.getLogger().fine(message);
        }
    }

    void applyThrottle(Limit nextThrottle) {
        throttle = nextThrottle;
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyCurrentMode(player);
        }
    }

    private void applyCurrentMode(Player player) {
        applyPlayerLimits(player, exploration.limit(player.getUniqueId()));
    }

    void applyPlayerLimits(Player player, ChunkExplorationPolicy.Limit personal) {
        UUID playerId = player.getUniqueId();
        SendDistanceOverride loadOverride = loadDistanceOverrides.get(playerId);
        int originalView = loadOverride == null ? player.getViewDistance()
                : effectiveSendDistance(loadOverride.originalDistance(), player.getWorld().getViewDistance());
        if (personal.loadRatio() < 1.0D || loadOverride != null) {
            loadDistanceOverrides.compute(playerId, (_, current) -> {
                int distance = player.getViewDistance();
                int worldDistance = player.getWorld().getViewDistance();
                SendDistanceUpdate update = sendDistanceUpdate(distance, worldDistance,
                        personal.loadRatio() == 1.0D ? 32 : personal.loadDistance(originalView), current,
                        current == null && distance == worldDistance);
                if (update.apply()) player.setViewDistance(update.distance());
                return update.override();
            });
        }
        if (!throttle.throttled() && personal.sendRatio() == 1.0D && !sendDistanceOverrides.containsKey(playerId)) {
            return;
        }
        boolean afk = afkService.isAfk(playerId);
        sendDistanceOverrides.compute(playerId, (_, currentOverride) -> {
            int currentDistance = player.getSendViewDistance();
            int unthrottledDistance = unthrottledSendDistance(player);
            int originalUnthrottled = Math.min(originalView, PlayerViewDistancePolicy.sendDistance(
                    baseSendDistance(player.getWorld()), player.getClientViewDistance()));
            boolean inheritsDefault = currentOverride == null
                    && currentDistance == unthrottledDistance;
            int originalDistance = currentOverride == null ? (inheritsDefault ? -1 : currentDistance)
                    : currentOverride.originalDistance();
            int baseline = Math.min(originalUnthrottled,
                    effectiveSendDistance(originalDistance, originalUnthrottled));
            int targetDistance = (afk ? throttle.afkRatio() : throttle.activeRatio()) == 1.0D
                    && personal.sendRatio() == 1.0D ? 32
                    : Math.min(throttle.sendDistance(baseline, afk), personal.sendDistance(baseline));
            SendDistanceUpdate update = sendDistanceUpdate(
                    currentDistance,
                    unthrottledDistance,
                    targetDistance,
                    currentOverride,
                    inheritsDefault);
            if (update.apply()) {
                player.setSendViewDistance(update.distance());
            }
            return update.override();
        });
    }

    static SendDistanceUpdate sendDistanceUpdate(
            int currentDistance,
            int unthrottledDistance,
            int targetDistance,
            SendDistanceOverride currentOverride
    ) {
        return sendDistanceUpdate(
                currentDistance,
                unthrottledDistance,
                targetDistance,
                currentOverride,
                false);
    }

    static SendDistanceUpdate sendDistanceUpdate(
            int currentDistance,
            int unthrottledDistance,
            int targetDistance,
            SendDistanceOverride currentOverride,
            boolean inheritsDefault
    ) {
        if (currentOverride != null && currentDistance != currentOverride.appliedDistance()) {
            int effectiveCurrent = effectiveSendDistance(currentDistance, unthrottledDistance);
            boolean hiddenByCap = effectiveCurrent == Math.min(
                    currentOverride.appliedDistance(), unthrottledDistance);
            if (!hiddenByCap) {
                // A visible external change becomes the new baseline before any recovery can raise it.
                currentOverride = null;
                inheritsDefault = currentDistance == unthrottledDistance;
            }
        }
        int originalDistance = currentOverride == null
                ? (inheritsDefault ? -1 : currentDistance)
                : currentOverride.originalDistance();
        int originalEffectiveDistance = effectiveSendDistance(originalDistance, unthrottledDistance);
        int limitedDistance = Math.min(originalEffectiveDistance, Math.clamp(targetDistance, 2, 32));
        int nextDistance = limitedDistance < originalEffectiveDistance ? limitedDistance : originalDistance;
        SendDistanceOverride nextOverride = nextDistance != originalDistance
                ? new SendDistanceOverride(originalDistance, nextDistance)
                : null;

        if (currentOverride != null && nextDistance != currentOverride.appliedDistance()) {
            // The policy changed. Update the explicit override even when another distance cap already
            // makes the getter report the same effective value, so a later cap increase stays correct.
            return new SendDistanceUpdate(true, nextDistance, nextOverride);
        }

        if (currentOverride != null && currentDistance < currentOverride.appliedDistance()) {
            // The getter can hide an owned override behind a lower view/client cap.
            return new SendDistanceUpdate(false, currentDistance, currentOverride);
        }

        if (currentOverride == null && inheritsDefault && nextOverride == null) {
            return new SendDistanceUpdate(false, currentDistance, null);
        }
        return new SendDistanceUpdate(currentDistance != nextDistance, nextDistance, nextOverride);
    }

    private static int effectiveSendDistance(int distance, int unthrottledDistance) {
        return distance == -1
                ? Math.clamp(unthrottledDistance, 2, 32)
                : Math.clamp(distance, 2, 32);
    }

    private int unthrottledSendDistance(Player player) {
        return Math.min(player.getViewDistance(), PlayerViewDistancePolicy.sendDistance(
                baseSendDistance(player.getWorld()), player.getClientViewDistance()));
    }

    private void restoreDistances(Map<UUID, SendDistanceOverride> overrides, boolean loading) {
        overrides.forEach((playerId, distanceOverride) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                int current = loading ? player.getViewDistance() : player.getSendViewDistance();
                int unthrottled = loading ? player.getWorld().getViewDistance() : unthrottledSendDistance(player);
                SendDistanceUpdate update = sendDistanceUpdate(current, unthrottled, 32, distanceOverride);
                if (update.apply()) {
                    if (loading) player.setViewDistance(update.distance());
                    else player.setSendViewDistance(update.distance());
                }
            }
        });
        overrides.clear();
    }

    private int baseSendDistance(World world) {
        int baseDistance = world.getSendViewDistance();
        if (baseDistance < 2) {
            baseDistance = world.getViewDistance();
        }
        return Math.clamp(baseDistance, 2, 32);
    }

    private void refreshProbe(SamplingState state) {
        NetworkInterfaceProbe discovered = NetworkInterfaceProbe.discover(state.configuredInterface);
        if (sameInterface(state.probe, discovered)) {
            return;
        }
        state.probe = discovered;
        state.meter.reset();
    }

    private boolean sameInterface(NetworkInterfaceProbe first, NetworkInterfaceProbe second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.interfaceName().equals(second.interfaceName());
    }

    private int sendStatus(CommandSender sender) {
        sender.sendMessage(statusReport());
        return Command.SINGLE_SUCCESS;
    }

    private Component statusReport() {
        SamplingState state = samplingState;
        double fastPressure = state == null ? -1.0D : state.policy.fastPressure();
        double slowPressure = state == null ? -1.0D : state.policy.slowPressure();
        double rawThroughput = state == null ? -1.0D : state.transmitBytesPerSecond;
        return Component.text("Network egress", NamedTextColor.AQUA)
                .append(metricLine("stage", Component.text(throttle.stage(),
                                throttle.activeRatio() < 1.0D ? NamedTextColor.GOLD
                                        : throttle.afkThrottled() ? NamedTextColor.YELLOW : NamedTextColor.GREEN),
                        "AFK limits are applied and observed before reducing active players' send distances."))
                .append(metricLine("interface", Component.text(interfaceName(), NamedTextColor.WHITE),
                        "Linux interface selected from the active default route."))
                .append(metricLine("outbound", Component.text(formatThroughput(rawThroughput), NamedTextColor.AQUA),
                        "Most recent measured interface throughput, including traffic from other processes."))
                .append(metricLine("budget", Component.text(formatThroughput(OUTBOUND_BUDGET_BYTES_PER_SECOND),
                                NamedTextColor.WHITE),
                        "Host egress budget used by the feedback controller."))
                .append(metricLine("fast pressure", Component.text(formatPressure(fastPressure), pressureColor(fastPressure)),
                        "Time-weighted pressure with a 2-second time constant. Sustained 85% triggers active limits."))
                .append(metricLine("slow pressure", Component.text(formatPressure(slowPressure), pressureColor(slowPressure)),
                        "10-second trend. Recovery needs raw, fast and slow pressure below 65%."))
                .append(metricLine("radius retained", Component.text(String.format(Locale.ROOT, "active: %.0f%%, AFK: %.0f%%",
                                throttle.activeRatio() * 100.0D, throttle.afkRatio() * 100.0D), NamedTextColor.WHITE),
                        "Applied relative to each player's original effective distance; minimums are 3 and 2 chunks."))
                .append(metricLine("recovery wait", Component.text(String.format(Locale.ROOT, "%.0f s",
                                state == null ? 15.0D : state.policy.recoveryDelay()), NamedTextColor.WHITE),
                        "Continuous headroom required for a small recovery probe. Failed probes increase this wait."))
                .append(metricLine("world send limits", Component.text(sendDistanceSummary(), NamedTextColor.WHITE),
                        "World-based upper bounds; individual original and client limits may be lower."))
                .append(metricLine("explorers limited", Component.text(exploration.limitedPlayers(), NamedTextColor.GOLD),
                        "High-demand explorers with temporary send, load or flight speed ceilings."))
                .append(metricLine("server tick", Component.text(String.format(Locale.ROOT, "%.1f ms", serverMspt.getAsDouble()),
                                NamedTextColor.WHITE),
                        "Tick pressure is used by per-explorer load and speed budgets, independently of network pressure."))
                .append(metricLine("exploration limits", Component.text(exploration.limitedSummary(), NamedTextColor.WHITE),
                        "Up to five limited players, ordered by recent chunk demand. Flight ceilings do not disable flight."));
    }

    private Component metricLine(String label, Component value, String hover) {
        Component hoverText = Component.text(hover, NamedTextColor.WHITE);
        return Component.newline()
                .append(Component.text("  " + label + ": ", NamedTextColor.YELLOW)
                        .hoverEvent(HoverEvent.showText(hoverText)))
                .append(value.hoverEvent(HoverEvent.showText(hoverText)));
    }

    private String interfaceName() {
        SamplingState state = samplingState;
        return state == null || state.probe == null ? "unavailable" : state.probe.interfaceName();
    }

    private double smoothedTransmitBytesPerSecond() {
        SamplingState state = samplingState;
        return state == null || state.policy.fastPressure() < 0.0D
                ? -1.0D : state.policy.fastPressure() * state.budgetBytesPerSecond;
    }

    private String sendDistanceSummary() {
        if (!throttle.throttled()) {
            return "world default";
        }
        return "active: " + sendDistanceRange(false) + ", AFK: " + sendDistanceRange(true);
    }

    private String sendDistanceRange(boolean afk) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (World world : Bukkit.getWorlds()) {
            int distance = throttle.sendDistance(baseSendDistance(world), afk);
            minimum = Math.min(minimum, distance);
            maximum = Math.max(maximum, distance);
        }
        if (minimum == Integer.MAX_VALUE) {
            return "unavailable";
        }
        return minimum == maximum ? minimum + " chunks" : minimum + "-" + maximum + " chunks";
    }

    private String formatPressure(double ratio) {
        return ratio < 0.0D ? "unavailable" : String.format(Locale.ROOT, "%.0f%%", ratio * 100.0D);
    }

    private String formatThroughput(double bytesPerSecond) {
        if (bytesPerSecond < 0.0D) {
            return "unavailable";
        }
        double megabitsPerSecond = bytesPerSecond * 8.0D / 1_000_000.0D;
        return String.format(Locale.ROOT, "%.2f Mbps", megabitsPerSecond);
    }

    private NamedTextColor pressureColor(double ratio) {
        if (ratio < 0.0D) return NamedTextColor.WHITE;
        if (ratio >= 0.98D) return NamedTextColor.RED;
        if (ratio >= 0.85D) return NamedTextColor.GOLD;
        return ratio >= 0.70D ? NamedTextColor.YELLOW : NamedTextColor.GREEN;
    }

    static final class SamplingState {
        private final String configuredInterface;
        private final double budgetBytesPerSecond;
        private final NetworkEgressRateMeter meter = new NetworkEgressRateMeter();
        private final NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        private volatile NetworkInterfaceProbe probe;
        private long lastProbeRefreshNanos;
        private long unavailableSinceNanos;
        private long lastReconcileNanos;
        private double transmitBytesPerSecond = -1.0D;

        SamplingState(String configuredInterface, double budgetBytesPerSecond) {
            this.configuredInterface = configuredInterface;
            this.budgetBytesPerSecond = budgetBytesPerSecond;
        }

        Limit updateThrottle(NetworkEgressRateMeter.Reading reading, long sampledAt, long now, boolean afkReliefAvailable) {
            if (!reading.measured() || reading.seconds() > NetworkEgressThrottlePolicy.MAX_SAMPLE_SECONDS
                    || (now - sampledAt) / 1_000_000_000.0D > NetworkEgressThrottlePolicy.MAX_SAMPLE_SECONDS) {
                policy.suspend();
                transmitBytesPerSecond = -1.0D;
                // A readable counter without comparable recent samples is also unavailable for control.
                if (unavailableSinceNanos == 0L) {
                    unavailableSinceNanos = now;
                } else if (now - unavailableSinceNanos >= UNAVAILABLE_RELEASE_NANOS) {
                    return policy.reset();
                }
                return policy.limit();
            }
            unavailableSinceNanos = 0L;
            transmitBytesPerSecond = reading.bytesPerSecond();
            return policy.update(reading.bytesPerSecond() / budgetBytesPerSecond,
                    reading.seconds(), afkReliefAvailable);
        }
    }

    record SendDistanceOverride(int originalDistance, int appliedDistance) {
    }

    record SendDistanceUpdate(boolean apply, int distance, SendDistanceOverride override) {
    }
}
