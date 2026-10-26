package org.encinet.mik.feature.server.performance;

import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.feature.player.afk.AfkState;
import org.encinet.mik.feature.player.afk.AfkStateListener;
import org.encinet.mik.feature.server.performance.NetworkEgressThrottlePolicy.ThrottleMode;

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

/**
 * Protects host egress capacity by reducing Paper's chunk send distance.
 *
 * <p>The controller deliberately does not cancel protocol packets. Paper remains responsible for
 * chunk and entity state consistency while this module only changes how much world data is sent.</p>
 */
public final class NetworkEgressModule implements Listener, AfkStateListener {

    private static final long SAMPLE_PERIOD_TICKS = 20L;
    private static final int PROBE_REFRESH_SAMPLES = 60;
    private static final int UNAVAILABLE_RELEASE_SAMPLES = 60;
    private static final int SEND_DISTANCE_RECONCILE_SAMPLES = 5;
    private static final double EWMA_WEIGHT = 0.35D;
    private static final double SOFT_DISTANCE_FACTOR = 0.75D;
    private static final double HARD_DISTANCE_FACTOR = 0.50D;
    private static final String NETWORK_INTERFACE = "auto";
    private static final double OUTBOUND_BUDGET_MEGABITS = 20.0D;
    private static final double OUTBOUND_BUDGET_BYTES_PER_SECOND =
            OUTBOUND_BUDGET_MEGABITS * 1_000_000.0D / 8.0D;
    private static final int MINIMUM_SEND_DISTANCE = 3;
    private static final int AFK_SEND_DISTANCE = 2;
    private static final ThrottleState UNTHROTTLED = new ThrottleState(ThrottleMode.OFF, false);

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final Map<UUID, SendDistanceOverride> sendDistanceOverrides = new HashMap<>();

    private BukkitTask sampleTask;
    private volatile SamplingState samplingState;
    private volatile ThrottleState throttle = UNTHROTTLED;

    public NetworkEgressModule(JavaPlugin plugin, AfkService afkService) {
        this.plugin = plugin;
        this.afkService = afkService;
    }

    public synchronized void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        afkService.addListener(this);
        SamplingState state = new SamplingState(NETWORK_INTERFACE, OUTBOUND_BUDGET_BYTES_PER_SECOND);
        samplingState = state;
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
        HandlerList.unregisterAll(this);
        afkService.removeListener(this);
        throttle = UNTHROTTLED;
        restoreSendDistances();
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
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                applyCurrentMode(player);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sendDistanceOverrides.remove(event.getPlayer().getUniqueId());
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
        try {
            sampleNetwork(state);
        } finally {
            scheduleNextSample(state, SAMPLE_PERIOD_TICKS);
        }
    }

    private void sampleNetwork(SamplingState state) {
        if (!isActive(state)) {
            return;
        }
        if (state.probe == null || --state.samplesUntilProbeRefresh <= 0) {
            refreshProbe(state);
        }
        if (!isActive(state)) {
            return;
        }
        if (state.probe == null) {
            markUnavailable(state);
            return;
        }

        long now = System.nanoTime();
        long transmitBytes = state.probe.readTransmitBytes();
        if (!isActive(state)) {
            return;
        }
        if (transmitBytes < 0L) {
            refreshProbe(state);
            markUnavailable(state);
            return;
        }
        if (state.lastTransmitBytes < 0L || state.lastSampleNanos == 0L) {
            state.lastTransmitBytes = transmitBytes;
            state.lastSampleNanos = now;
            return;
        }
        if (transmitBytes < state.lastTransmitBytes) {
            requestTransition(state.resetThrottle(), state);
            state.resetSample();
            state.lastTransmitBytes = transmitBytes;
            state.lastSampleNanos = now;
            return;
        }

        double elapsedSeconds = Math.max(1L, now - state.lastSampleNanos) / 1_000_000_000.0D;
        double transmitBytesPerSecond = (transmitBytes - state.lastTransmitBytes) / elapsedSeconds;
        state.unavailableSamples = 0;
        double smoothedTransmitBytesPerSecond = smooth(
                state.smoothedTransmitBytesPerSecond, transmitBytesPerSecond);
        state.smoothedTransmitBytesPerSecond = smoothedTransmitBytesPerSecond;
        state.lastTransmitBytes = transmitBytes;
        state.lastSampleNanos = now;

        ThrottleState nextThrottle = state.updateThrottle(
                smoothedTransmitBytesPerSecond / state.budgetBytesPerSecond);
        if (!nextThrottle.equals(throttle)) {
            state.sendDistanceReconcileSamples = 0;
            requestTransition(nextThrottle, state);
        } else if (nextThrottle.throttled()
                && ++state.sendDistanceReconcileSamples >= SEND_DISTANCE_RECONCILE_SAMPLES) {
            state.sendDistanceReconcileSamples = 0;
            requestReconcile(nextThrottle, state);
        }
    }

    private boolean isActive(SamplingState state) {
        return samplingState == state;
    }

    private void requestTransition(ThrottleState nextThrottle, SamplingState state) {
        synchronized (this) {
            if (!isActive(state)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isActive(state) && state.desiredThrottle.equals(nextThrottle)) {
                    transitionTo(nextThrottle);
                }
            });
        }
    }

    private void requestReconcile(ThrottleState expectedThrottle, SamplingState state) {
        synchronized (this) {
            if (!isActive(state)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isActive(state) && state.desiredThrottle.equals(expectedThrottle)
                        && throttle.equals(expectedThrottle)) {
                    applyThrottle(expectedThrottle);
                }
            });
        }
    }

    private void transitionTo(ThrottleState nextThrottle) {
        if (nextThrottle.equals(throttle)) {
            return;
        }
        ThrottleState previousThrottle = throttle;
        applyThrottle(nextThrottle);
        plugin.getLogger().info(String.format(Locale.ROOT,
                "Network egress %s -> %s, tx=%s, budget=%s, send-distance=%s",
                previousThrottle,
                nextThrottle,
                formatThroughput(smoothedTransmitBytesPerSecond()),
                formatThroughput(OUTBOUND_BUDGET_BYTES_PER_SECOND),
                sendDistanceSummary()));
    }

    void applyThrottle(ThrottleState nextThrottle) {
        throttle = nextThrottle;
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyCurrentMode(player);
        }
    }

    private void applyCurrentMode(Player player) {
        UUID playerId = player.getUniqueId();
        if (!throttle.throttled() && !sendDistanceOverrides.containsKey(playerId)) {
            return;
        }
        int defaultDistance = baseSendDistance(player.getWorld());
        int targetDistance = PlayerViewDistancePolicy.sendDistance(
                targetSendDistance(defaultDistance, MINIMUM_SEND_DISTANCE, throttle, afkService.isAfk(playerId)),
                player.getClientViewDistance());
        sendDistanceOverrides.compute(playerId, (ignored, currentOverride) -> {
            int currentDistance = player.getSendViewDistance();
            int unthrottledDistance = PlayerViewDistancePolicy.sendDistance(
                    defaultDistance,
                    player.getClientViewDistance());
            int inheritedDistance = inheritedSendDistance(player, unthrottledDistance);
            boolean inheritsDefault = currentOverride == null
                    && currentDistance == inheritedDistance;
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

        if (currentOverride != null && currentDistance != currentOverride.appliedDistance()) {
            int currentEffectiveDistance = effectiveSendDistance(currentDistance, unthrottledDistance);
            if (currentEffectiveDistance < currentOverride.appliedDistance()) {
                // View/client limits can make an explicit send override appear lower. Keep ownership
                // without forcing a redundant update; external lower limits are respected as well.
                return new SendDistanceUpdate(false, currentDistance, currentOverride);
            }
            if (currentEffectiveDistance <= targetDistance) {
                return new SendDistanceUpdate(false, currentDistance, null);
            }
            return new SendDistanceUpdate(
                    true,
                    targetDistance,
                    new SendDistanceOverride(currentDistance, targetDistance));
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

    private static int inheritedSendDistance(Player player, int unthrottledDistance) {
        return Math.min(player.getViewDistance(), unthrottledDistance);
    }

    private void restoreSendDistances() {
        sendDistanceOverrides.forEach((playerId, sendDistanceOverride) -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null
                    && player.isOnline()
                    && player.getSendViewDistance() == sendDistanceOverride.appliedDistance()) {
                player.setSendViewDistance(sendDistanceOverride.originalDistance());
            }
        });
        sendDistanceOverrides.clear();
    }

    private int targetSendDistance(World world, ThrottleState throttleState, boolean afk) {
        return targetSendDistance(baseSendDistance(world), MINIMUM_SEND_DISTANCE, throttleState, afk);
    }

    private int baseSendDistance(World world) {
        int baseDistance = world.getSendViewDistance();
        if (baseDistance < 2) {
            baseDistance = world.getViewDistance();
        }
        return Math.clamp(baseDistance, 2, 32);
    }

    static int targetSendDistance(int baseDistance, int minimumDistance, ThrottleState throttleState, boolean afk) {
        int normalizedBase = Math.clamp(baseDistance, 2, 32);
        if (afk && throttleState.afkThrottled()) {
            return AFK_SEND_DISTANCE;
        }
        int floor = Math.clamp(minimumDistance, 2, normalizedBase);
        return switch (throttleState.mode()) {
            case OFF -> normalizedBase;
            case SOFT -> Math.max(floor, (int) Math.ceil(normalizedBase * SOFT_DISTANCE_FACTOR));
            case HARD -> Math.max(floor, (int) Math.ceil(normalizedBase * HARD_DISTANCE_FACTOR));
            case CRITICAL -> floor;
        };
    }

    private void refreshProbe(SamplingState state) {
        NetworkInterfaceProbe discovered = NetworkInterfaceProbe.discover(state.configuredInterface);
        state.samplesUntilProbeRefresh = PROBE_REFRESH_SAMPLES;
        if (sameInterface(state.probe, discovered)) {
            return;
        }
        state.probe = discovered;
        state.resetSample();
    }

    private void markUnavailable(SamplingState state) {
        state.smoothedTransmitBytesPerSecond = -1.0D;
        state.unavailableSamples++;
        if (state.unavailableSamples == UNAVAILABLE_RELEASE_SAMPLES) {
            ThrottleState releasedThrottle = state.resetThrottle();
            if (throttle.throttled()) {
                requestTransition(releasedThrottle, state);
            }
        }
    }

    private boolean sameInterface(NetworkInterfaceProbe first, NetworkInterfaceProbe second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.interfaceName().equals(second.interfaceName());
    }

    private double smooth(double previous, double current) {
        if (previous < 0.0D) {
            return current;
        }
        return previous * (1.0D - EWMA_WEIGHT) + current * EWMA_WEIGHT;
    }

    private int sendStatus(CommandSender sender) {
        sender.sendMessage(statusReport());
        return Command.SINGLE_SUCCESS;
    }

    private Component statusReport() {
        double transmitBytesPerSecond = smoothedTransmitBytesPerSecond();
        double pressureRatio = transmitBytesPerSecond < 0.0D
                ? -1.0D
                : transmitBytesPerSecond / OUTBOUND_BUDGET_BYTES_PER_SECOND;
        return Component.text("Network egress", NamedTextColor.AQUA)
                .append(metricLine("mode", Component.text(throttle.mode().name(), modeColor(throttle.mode())),
                        "Chunk send distance is reduced only while host egress stays under pressure."))
                .append(metricLine("AFK protection", Component.text(throttle.afkThrottled() ? "ON" : "OFF",
                                throttle.afkThrottled() ? NamedTextColor.YELLOW : NamedTextColor.GREEN),
                        "AFK players drop to 2 chunks as soon as bandwidth risk is detected."))
                .append(metricLine("interface", Component.text(interfaceName(), NamedTextColor.GRAY),
                        "Linux interface selected from the active default route."))
                .append(metricLine("outbound", Component.text(formatThroughput(transmitBytesPerSecond),
                                NamedTextColor.AQUA),
                        "Smoothed bytes transmitted by the default-route interface after Minecraft compression."))
                .append(metricLine("budget", Component.text(formatThroughput(OUTBOUND_BUDGET_BYTES_PER_SECOND),
                                NamedTextColor.GRAY),
                        "Compiled host egress budget. This may include traffic from other processes."))
                .append(metricLine("pressure", Component.text(formatPressure(pressureRatio), pressureColor(pressureRatio)),
                        "Outbound throughput divided by the configured budget."))
                .append(metricLine("send distance", Component.text(sendDistanceSummary(), NamedTextColor.GRAY),
                        "Paper chunk send distance. View and simulation distance keep their configured values."));
    }

    private Component metricLine(String label, Component value, String hover) {
        Component hoverText = Component.text(hover, NamedTextColor.GRAY);
        return Component.newline()
                .append(Component.text("  " + label + ": ", NamedTextColor.DARK_GRAY)
                        .hoverEvent(HoverEvent.showText(hoverText)))
                .append(value.hoverEvent(HoverEvent.showText(hoverText)));
    }

    private String interfaceName() {
        SamplingState state = samplingState;
        return state == null || state.probe == null ? "unavailable" : state.probe.interfaceName();
    }

    private double smoothedTransmitBytesPerSecond() {
        SamplingState state = samplingState;
        return state == null ? -1.0D : state.smoothedTransmitBytesPerSecond;
    }

    private String sendDistanceSummary() {
        if (!throttle.throttled()) {
            return "world default";
        }
        String activeDistance = throttle.mode() == ThrottleMode.OFF
                ? "world default" : sendDistanceRange(false);
        return "active: " + activeDistance + ", AFK: " + sendDistanceRange(true);
    }

    private String sendDistanceRange(boolean afk) {
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (World world : Bukkit.getWorlds()) {
            int distance = targetSendDistance(world, throttle, afk);
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

    private NamedTextColor modeColor(ThrottleMode throttleMode) {
        return switch (throttleMode) {
            case OFF -> NamedTextColor.GREEN;
            case SOFT -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.GOLD;
            case CRITICAL -> NamedTextColor.RED;
        };
    }

    private NamedTextColor pressureColor(double ratio) {
        if (ratio < 0.0D) {
            return NamedTextColor.GRAY;
        }
        return modeColor(NetworkEgressThrottlePolicy.modeForPressure(ratio));
    }

    static final class SamplingState {

        private final String configuredInterface;
        private final double budgetBytesPerSecond;
        private final NetworkEgressThrottlePolicy policy = new NetworkEgressThrottlePolicy();
        private final NetworkEgressThrottlePolicy afkPolicy = new NetworkEgressThrottlePolicy(1);

        private volatile NetworkInterfaceProbe probe;
        private volatile double smoothedTransmitBytesPerSecond = -1.0D;
        private volatile ThrottleState desiredThrottle = UNTHROTTLED;
        private long lastTransmitBytes = -1L;
        private long lastSampleNanos;
        private int samplesUntilProbeRefresh;
        private int unavailableSamples;
        private int sendDistanceReconcileSamples;

        SamplingState(String configuredInterface, double budgetBytesPerSecond) {
            this.configuredInterface = configuredInterface;
            this.budgetBytesPerSecond = budgetBytesPerSecond;
        }

        ThrottleState updateThrottle(double pressureRatio) {
            desiredThrottle = new ThrottleState(policy.update(pressureRatio),
                    afkPolicy.update(pressureRatio) != ThrottleMode.OFF);
            return desiredThrottle;
        }

        ThrottleState resetThrottle() {
            policy.reset();
            afkPolicy.reset();
            desiredThrottle = UNTHROTTLED;
            return desiredThrottle;
        }

        private void resetSample() {
            lastTransmitBytes = -1L;
            lastSampleNanos = 0L;
            smoothedTransmitBytesPerSecond = -1.0D;
            unavailableSamples = 0;
            sendDistanceReconcileSamples = 0;
        }
    }

    record ThrottleState(ThrottleMode mode, boolean afkThrottled) {
        boolean throttled() {
            return mode != ThrottleMode.OFF || afkThrottled;
        }
    }

    record SendDistanceOverride(int originalDistance, int appliedDistance) {
    }

    record SendDistanceUpdate(boolean apply, int distance, SendDistanceOverride override) {
    }
}
