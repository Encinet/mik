package org.encinet.mik.module.performance;

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
import org.encinet.mik.Mik;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
public final class NetworkEgressModule implements Listener {

    private static final Path LINUX_ROUTE_TABLE = Path.of("/proc/net/route");
    private static final Path LINUX_IPV6_ROUTE_TABLE = Path.of("/proc/net/ipv6_route");
    private static final Path LINUX_NETWORK_DEVICES = Path.of("/sys/class/net");
    private static final long SAMPLE_PERIOD_TICKS = 20L;
    private static final int PROBE_REFRESH_SAMPLES = 60;
    private static final int UNAVAILABLE_RELEASE_SAMPLES = 60;
    private static final int SEND_DISTANCE_RECONCILE_SAMPLES = 5;
    private static final double EWMA_WEIGHT = 0.35D;
    private static final double SOFT_PRESSURE_RATIO = 0.70D;
    private static final double HARD_PRESSURE_RATIO = 0.85D;
    private static final double CRITICAL_PRESSURE_RATIO = 0.95D;
    private static final double SOFT_RECOVERY_RATIO = 0.60D;
    private static final double HARD_RECOVERY_RATIO = 0.75D;
    private static final double CRITICAL_RECOVERY_RATIO = 0.85D;
    private static final double SOFT_DISTANCE_FACTOR = 0.75D;
    private static final double HARD_DISTANCE_FACTOR = 0.50D;
    private static final String NETWORK_INTERFACE = "auto";
    private static final double OUTBOUND_BUDGET_MEGABITS = 100.0D;
    private static final double OUTBOUND_BUDGET_BYTES_PER_SECOND =
            OUTBOUND_BUDGET_MEGABITS * 1_000_000.0D / 8.0D;
    private static final int MINIMUM_SEND_DISTANCE = 3;

    private final JavaPlugin plugin;
    private final Map<UUID, SendDistanceOverride> sendDistanceOverrides = new HashMap<>();

    private BukkitTask sampleTask;
    private volatile SamplingState samplingState;
    private volatile ThrottleMode mode = ThrottleMode.OFF;

    public NetworkEgressModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public synchronized void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
        mode = ThrottleMode.OFF;
        restoreSendDistances();
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("network")
                        .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_HELPER))
                        .then(Commands.literal("status")
                                .executes(context -> sendStatus(context.getSource().getSender())))
                        .executes(context -> sendStatus(context.getSource().getSender()))
                        .build(),
                "Network egress and chunk send-distance status"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (mode != ThrottleMode.OFF) {
            applyCurrentMode(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (mode != ThrottleMode.OFF) {
            applyCurrentMode(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        SendDistanceOverride override = sendDistanceOverrides.remove(player.getUniqueId());
        if (override != null && player.getSendViewDistance() == override.appliedDistance()) {
            player.setSendViewDistance(override.originalDistance());
        }
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
            ThrottleMode resetMode = state.policy.reset();
            state.desiredMode = resetMode;
            requestTransition(resetMode, state);
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

        ThrottleMode nextMode = state.policy.update(
                smoothedTransmitBytesPerSecond / state.budgetBytesPerSecond);
        state.desiredMode = nextMode;
        if (nextMode != mode) {
            state.sendDistanceReconcileSamples = 0;
            requestTransition(nextMode, state);
        } else if (nextMode != ThrottleMode.OFF
                && ++state.sendDistanceReconcileSamples >= SEND_DISTANCE_RECONCILE_SAMPLES) {
            state.sendDistanceReconcileSamples = 0;
            requestReconcile(nextMode, state);
        }
    }

    private boolean isActive(SamplingState state) {
        return samplingState == state;
    }

    private void requestTransition(ThrottleMode nextMode, SamplingState state) {
        synchronized (this) {
            if (!isActive(state)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isActive(state) && state.desiredMode == nextMode) {
                    transitionTo(nextMode);
                }
            });
        }
    }

    private void requestReconcile(ThrottleMode expectedMode, SamplingState state) {
        synchronized (this) {
            if (!isActive(state)) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (isActive(state) && state.desiredMode == expectedMode && mode == expectedMode) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        applyCurrentMode(player);
                    }
                }
            });
        }
    }

    private void transitionTo(ThrottleMode nextMode) {
        if (nextMode == mode) {
            return;
        }
        ThrottleMode previousMode = mode;
        mode = nextMode;
        if (nextMode == ThrottleMode.OFF) {
            restoreSendDistances();
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) {
                applyCurrentMode(player);
            }
        }
        plugin.getLogger().info(String.format(Locale.ROOT,
                "Network egress %s -> %s, tx=%s, budget=%s, send-distance=%s",
                previousMode,
                nextMode,
                formatThroughput(smoothedTransmitBytesPerSecond()),
                formatThroughput(OUTBOUND_BUDGET_BYTES_PER_SECOND),
                sendDistanceSummary()));
    }

    private void applyCurrentMode(Player player) {
        if (mode == ThrottleMode.OFF) {
            return;
        }
        UUID playerId = player.getUniqueId();
        int defaultDistance = baseSendDistance(player.getWorld());
        int targetDistance = targetSendDistance(defaultDistance, MINIMUM_SEND_DISTANCE, mode);
        sendDistanceOverrides.compute(playerId, (ignored, currentOverride) -> {
            SendDistanceUpdate update = sendDistanceUpdate(
                    player.getSendViewDistance(), defaultDistance, targetDistance, currentOverride);
            if (update.apply()) {
                player.setSendViewDistance(update.distance());
            }
            return update.override();
        });
    }

    static SendDistanceUpdate sendDistanceUpdate(
            int currentDistance,
            int defaultDistance,
            int targetDistance,
            SendDistanceOverride currentOverride
    ) {
        if (currentOverride != null && currentDistance != currentOverride.appliedDistance()) {
            int currentEffectiveDistance = effectiveSendDistance(currentDistance, defaultDistance);
            if (currentEffectiveDistance <= targetDistance) {
                return new SendDistanceUpdate(false, currentDistance, null);
            }
            return new SendDistanceUpdate(
                    true,
                    targetDistance,
                    new SendDistanceOverride(currentDistance, targetDistance));
        }

        int originalDistance = currentOverride == null
                ? currentDistance
                : currentOverride.originalDistance();
        int originalEffectiveDistance = effectiveSendDistance(originalDistance, defaultDistance);
        int limitedDistance = Math.min(originalEffectiveDistance, Math.clamp(targetDistance, 2, 32));
        int nextDistance = limitedDistance < originalEffectiveDistance ? limitedDistance : originalDistance;
        SendDistanceOverride nextOverride = nextDistance != originalDistance
                ? new SendDistanceOverride(originalDistance, nextDistance)
                : null;
        return new SendDistanceUpdate(currentDistance != nextDistance, nextDistance, nextOverride);
    }

    private static int effectiveSendDistance(int distance, int defaultDistance) {
        return distance == -1
                ? Math.clamp(defaultDistance, 2, 32)
                : Math.clamp(distance, 2, 32);
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

    private int targetSendDistance(World world, ThrottleMode throttleMode) {
        return targetSendDistance(baseSendDistance(world), MINIMUM_SEND_DISTANCE, throttleMode);
    }

    private int baseSendDistance(World world) {
        int baseDistance = world.getSendViewDistance();
        if (baseDistance < 2) {
            baseDistance = world.getViewDistance();
        }
        return Math.clamp(baseDistance, 2, 32);
    }

    static int targetSendDistance(int baseDistance, int minimumDistance, ThrottleMode throttleMode) {
        int normalizedBase = Math.clamp(baseDistance, 2, 32);
        int floor = Math.clamp(minimumDistance, 2, normalizedBase);
        return switch (throttleMode) {
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
            ThrottleMode releasedMode = state.policy.reset();
            state.desiredMode = releasedMode;
            if (mode != ThrottleMode.OFF) {
                requestTransition(releasedMode, state);
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
                .append(metricLine("mode", Component.text(mode.name(), modeColor(mode)),
                        "Chunk send distance is reduced only while host egress stays under pressure."))
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
                        "Paper chunk send distance. View and simulation distance remain owned by PerformanceModule."));
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
        if (mode == ThrottleMode.OFF) {
            return "world default";
        }
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;
        for (World world : Bukkit.getWorlds()) {
            int distance = targetSendDistance(world, mode);
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
        return modeColor(ThrottlePolicy.modeForPressure(ratio));
    }

    enum ThrottleMode {
        OFF,
        SOFT,
        HARD,
        CRITICAL
    }

    static final class ThrottlePolicy {

        private static final int ESCALATION_CONFIRM_SAMPLES = 2;
        private static final int RECOVERY_CONFIRM_SAMPLES = 15;

        private ThrottleMode mode = ThrottleMode.OFF;
        private ThrottleMode pendingEscalation = ThrottleMode.OFF;
        private int escalationSamples;
        private int recoverySamples;

        synchronized ThrottleMode update(double pressureRatio) {
            ThrottleMode desired = modeForPressure(pressureRatio);
            if (desired.ordinal() > mode.ordinal()) {
                if (desired != pendingEscalation) {
                    pendingEscalation = desired;
                    escalationSamples = 1;
                } else {
                    escalationSamples++;
                }
                int requiredSamples = desired == ThrottleMode.CRITICAL ? 1 : ESCALATION_CONFIRM_SAMPLES;
                if (escalationSamples >= requiredSamples) {
                    mode = desired;
                    escalationSamples = 0;
                }
                recoverySamples = 0;
                return mode;
            }

            pendingEscalation = mode;
            escalationSamples = 0;
            if (mode == ThrottleMode.OFF || pressureRatio >= recoveryRatio(mode)) {
                recoverySamples = 0;
                return mode;
            }

            recoverySamples++;
            if (recoverySamples >= RECOVERY_CONFIRM_SAMPLES) {
                mode = previousMode(mode);
                recoverySamples = 0;
            }
            return mode;
        }

        synchronized ThrottleMode reset() {
            mode = ThrottleMode.OFF;
            pendingEscalation = ThrottleMode.OFF;
            escalationSamples = 0;
            recoverySamples = 0;
            return mode;
        }

        static ThrottleMode modeForPressure(double pressureRatio) {
            if (pressureRatio >= CRITICAL_PRESSURE_RATIO) {
                return ThrottleMode.CRITICAL;
            }
            if (pressureRatio >= HARD_PRESSURE_RATIO) {
                return ThrottleMode.HARD;
            }
            if (pressureRatio >= SOFT_PRESSURE_RATIO) {
                return ThrottleMode.SOFT;
            }
            return ThrottleMode.OFF;
        }

        private static double recoveryRatio(ThrottleMode throttleMode) {
            return switch (throttleMode) {
                case OFF -> 0.0D;
                case SOFT -> SOFT_RECOVERY_RATIO;
                case HARD -> HARD_RECOVERY_RATIO;
                case CRITICAL -> CRITICAL_RECOVERY_RATIO;
            };
        }

        private static ThrottleMode previousMode(ThrottleMode throttleMode) {
            return switch (throttleMode) {
                case OFF, SOFT -> ThrottleMode.OFF;
                case HARD -> ThrottleMode.SOFT;
                case CRITICAL -> ThrottleMode.HARD;
            };
        }
    }

    private record NetworkInterfaceProbe(String interfaceName, Path transmitBytesPath) {

        static NetworkInterfaceProbe discover(String configuredInterface) {
            String requestedInterface = configuredInterface == null ? "" : configuredInterface.trim();
            if (!requestedInterface.isEmpty() && !"auto".equalsIgnoreCase(requestedInterface)) {
                return forInterface(requestedInterface);
            }

            RouteCandidate best = null;
            if (Files.isReadable(LINUX_ROUTE_TABLE)) {
                best = discoverIpv4DefaultRoute();
            }
            if (best == null) {
                best = discoverIpv6DefaultRoute();
            }
            return best == null ? null : forInterface(best.interfaceName());
        }

        private static RouteCandidate discoverIpv4DefaultRoute() {
            RouteCandidate best = null;
            try (BufferedReader reader = Files.newBufferedReader(LINUX_ROUTE_TABLE)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.trim().split("\\s+");
                    if (fields.length < 8 || "Iface".equals(fields[0]) || !"00000000".equals(fields[1])) {
                        continue;
                    }
                    try {
                        long flags = Long.parseUnsignedLong(fields[3], 16);
                        long metric = Long.parseLong(fields[6]);
                        if ((flags & 0x1L) == 0L || !validInterfaceName(fields[0])) {
                            continue;
                        }
                        RouteCandidate candidate = new RouteCandidate(fields[0], metric);
                        if (best == null || candidate.metric() < best.metric()) {
                            best = candidate;
                        }
                    } catch (NumberFormatException ignored) {
                        // Ignore only the malformed route row.
                    }
                }
            } catch (IOException ignored) {
                return null;
            }
            return best;
        }

        private static RouteCandidate discoverIpv6DefaultRoute() {
            if (!Files.isReadable(LINUX_IPV6_ROUTE_TABLE)) {
                return null;
            }
            RouteCandidate best = null;
            try (BufferedReader reader = Files.newBufferedReader(LINUX_IPV6_ROUTE_TABLE)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.trim().split("\\s+");
                    if (fields.length < 10 || !isIpv6DefaultRoute(fields)) {
                        continue;
                    }
                    try {
                        long metric = Long.parseUnsignedLong(fields[5], 16);
                        long flags = Long.parseUnsignedLong(fields[8], 16);
                        String interfaceName = fields[9];
                        if ((flags & 0x1L) == 0L || !validInterfaceName(interfaceName)) {
                            continue;
                        }
                        RouteCandidate candidate = new RouteCandidate(interfaceName, metric);
                        if (best == null || candidate.metric() < best.metric()) {
                            best = candidate;
                        }
                    } catch (NumberFormatException ignored) {
                        // Ignore only the malformed route row.
                    }
                }
            } catch (IOException ignored) {
                return null;
            }
            return best;
        }

        private static boolean isIpv6DefaultRoute(String[] fields) {
            return "00000000000000000000000000000000".equals(fields[0]) && "00".equals(fields[1]);
        }

        private static NetworkInterfaceProbe forInterface(String interfaceName) {
            if (!validInterfaceName(interfaceName)) {
                return null;
            }
            Path transmitBytesPath = LINUX_NETWORK_DEVICES.resolve(interfaceName)
                    .resolve("statistics/tx_bytes");
            return Files.isReadable(transmitBytesPath)
                    ? new NetworkInterfaceProbe(interfaceName, transmitBytesPath)
                    : null;
        }

        private static boolean validInterfaceName(String interfaceName) {
            return !interfaceName.isBlank()
                    && !"lo".equals(interfaceName)
                    && !interfaceName.contains("/")
                    && !interfaceName.contains("\\")
                    && !interfaceName.contains("..");
        }

        long readTransmitBytes() {
            try {
                return Long.parseLong(Files.readString(transmitBytesPath).trim());
            } catch (IOException | NumberFormatException ignored) {
                return -1L;
            }
        }
    }

    private record RouteCandidate(String interfaceName, long metric) {
    }

    private static final class SamplingState {

        private final String configuredInterface;
        private final double budgetBytesPerSecond;
        private final ThrottlePolicy policy = new ThrottlePolicy();

        private volatile NetworkInterfaceProbe probe;
        private volatile double smoothedTransmitBytesPerSecond = -1.0D;
        private volatile ThrottleMode desiredMode = ThrottleMode.OFF;
        private long lastTransmitBytes = -1L;
        private long lastSampleNanos;
        private int samplesUntilProbeRefresh;
        private int unavailableSamples;
        private int sendDistanceReconcileSamples;

        private SamplingState(String configuredInterface, double budgetBytesPerSecond) {
            this.configuredInterface = configuredInterface;
            this.budgetBytesPerSecond = budgetBytesPerSecond;
        }

        private void resetSample() {
            lastTransmitBytes = -1L;
            lastSampleNanos = 0L;
            smoothedTransmitBytesPerSecond = -1.0D;
            unavailableSamples = 0;
            sendDistanceReconcileSamples = 0;
        }
    }

    record SendDistanceOverride(int originalDistance, int appliedDistance) {
    }

    record SendDistanceUpdate(boolean apply, int distance, SendDistanceOverride override) {
    }
}
