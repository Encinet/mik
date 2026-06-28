package org.encinet.mik.module.performance;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMovement;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityStatus;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHurtAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.pvp.PvpModule;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class NetworkThrottleModule implements Listener {

    private static final long DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND = 12L * 1024L * 1024L;
    private static final long DEFAULT_PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND = 400L * 1024L;
    private static final long DEFAULT_PLAYER_OUTBOUND_HARD_BYTES_PER_SECOND = 800L * 1024L;
    private static final long DEFAULT_PLAYER_OUTBOUND_CRITICAL_BYTES_PER_SECOND = 1_200L * 1024L;
    private static final long MIN_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND = 4L * 1024L * 1024L;
    private static final long MAX_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND = 24L * 1024L * 1024L;
    private static final long MIN_PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND = 160L * 1024L;
    private static final long MAX_PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND = 1_024L * 1024L;
    private static final int MAX_CORRECTIONS_PER_TICK = 96;
    private static final int MIN_CORRECTIONS_PER_TICK = 24;
    private static final int MAX_CORRECTION_INSPECTIONS_PER_TICK = MAX_CORRECTIONS_PER_TICK * 4;
    private static final long RATE_LIMIT_EXPIRY_NANOS = 60_000_000_000L;
    private static final double MSPT_SOFT_PRESSURE = 45.0D;
    private static final double MSPT_HARD_PRESSURE = 65.0D;
    private static final double MSPT_CRITICAL_PRESSURE = 90.0D;
    private static final double SOFT_PRESSURE_RATIO = 0.70D;
    private static final double HARD_PRESSURE_RATIO = 0.85D;
    private static final double CRITICAL_PRESSURE_RATIO = 0.95D;
    private static final double EWMA_WEIGHT = 0.35D;
    private static final double ADAPTIVE_INCREASE_FACTOR = 1.10D;
    private static final double ADAPTIVE_DECREASE_FACTOR = 0.86D;
    private static final double ADAPTIVE_IDLE_DECAY_FACTOR = 0.995D;
    private static final double ADAPTIVE_PRESSURE_TRIGGER = 0.78D;
    private static final double ADAPTIVE_CRITICAL_TRIGGER = 0.92D;
    private static final int ADAPTIVE_RECOVERY_SAMPLES = 3;
    private static final int MODE_UP_SAMPLES = 2;
    private static final int MODE_DOWN_SAMPLES = 8;
    private static final int PING_SOFT_MILLIS = 180;
    private static final int PING_HARD_MILLIS = 300;
    private static final int PING_CRITICAL_MILLIS = 500;
    private static final double PING_SOFT_INTERVAL_MULTIPLIER = 1.20D;
    private static final double PING_HARD_INTERVAL_MULTIPLIER = 1.50D;
    private static final double PING_CRITICAL_INTERVAL_MULTIPLIER = 2.00D;
    private static final ThrottleMode AFK_VIEWER_MODE = ThrottleMode.SOFT;
    private static final ThrottleMode AFK_SUBJECT_MODE = ThrottleMode.SOFT;
    private static final double AFK_VIEWER_INTERVAL_MULTIPLIER = 1.50D;
    private static final double AFK_SUBJECT_INTERVAL_MULTIPLIER = 1.25D;
    private static final double MAX_INTERVAL_MULTIPLIER = 4.00D;

    private static final long NEAR_DISTANCE_SQUARED = 48L * 48L;
    private static final long MID_DISTANCE_SQUARED = 96L * 96L;
    private static final long FAR_DISTANCE_SQUARED = 160L * 160L;
    private static final long CLOSE_PVP_DISTANCE_SQUARED = 96L * 96L;
    private static final long FOCUSED_SUBJECT_DISTANCE_SQUARED = 256L * 256L;
    private static final double FOCUSED_SUBJECT_DOT = 0.72D;
    private static final double FOCUSED_SUBJECT_DOT_SQUARED = FOCUSED_SUBJECT_DOT * FOCUSED_SUBJECT_DOT;
    private static final double VIEWER_EYE_HEIGHT = 1.62D;
    private static final double SUBJECT_FOCUS_HEIGHT = 1.00D;
    private static final double FAST_MOVEMENT_DELTA_SQUARED = 1.0D;
    private static final double FAST_VERTICAL_MOVEMENT_DELTA = 0.70D;
    private static final int FAST_MOVEMENT_HOLD_SNAPSHOTS = 4;
    private static final float FAST_LOOK_DELTA_DEGREES = 35.0F;
    private static final int FAST_LOOK_HOLD_SNAPSHOTS = 3;

    private static final long SOFT_MID_INTERVAL_NANOS = 100_000_000L;
    private static final long SOFT_FAR_INTERVAL_NANOS = 200_000_000L;
    private static final long SOFT_EXTREME_INTERVAL_NANOS = 500_000_000L;
    private static final long HARD_MID_INTERVAL_NANOS = 200_000_000L;
    private static final long HARD_FAR_INTERVAL_NANOS = 500_000_000L;
    private static final long HARD_EXTREME_INTERVAL_NANOS = 1_000_000_000L;
    private static final long CRITICAL_MID_INTERVAL_NANOS = 500_000_000L;
    private static final long CRITICAL_FAR_INTERVAL_NANOS = 1_000_000_000L;
    private static final long CRITICAL_EXTREME_INTERVAL_NANOS = 2_000_000_000L;
    private static final long MID_MOVEMENT_INTERVAL_CAP_NANOS = 250_000_000L;
    private static final long FAR_MOVEMENT_INTERVAL_CAP_NANOS = 1_000_000_000L;
    private static final long FAR_LIMITED_PACKET_INTERVAL_CAP_NANOS = 500_000_000L;
    private static final long LOOK_PACKET_INTERVAL_NANOS = 100_000_000L;
    private static final long VELOCITY_PACKET_INTERVAL_NANOS = 200_000_000L;
    private static final long ANIMATION_PACKET_INTERVAL_NANOS = 250_000_000L;
    private static final long STATUS_PACKET_INTERVAL_NANOS = 500_000_000L;
    private static final long STATE_PACKET_INTERVAL_NANOS = 1_000_000_000L;

    private static final long BANDWIDTH_SAMPLE_PERIOD_TICKS = 20L;
    private static final long SNAPSHOT_PERIOD_TICKS = 2L;
    private static final long LOG_PERIOD_NANOS = 30_000_000_000L;
    private static final long RECENT_TELEPORT_SUPPRESS_MILLIS = 5_000L;
    private static final long RECENT_TELEPORT_SUPPRESS_NANOS =
            TimeUnit.MILLISECONDS.toNanos(RECENT_TELEPORT_SUPPRESS_MILLIS);
    private static final Path LINUX_PROC_NET_DEV = Path.of("/proc/net/dev");
    private static final Set<String> CONSERVATIVE_WORLD_NAMES = Set.of("spawn", "lobby", "hub");
    private static final Set<String> AGGRESSIVE_WORLD_NAMES = Set.of("resource", "resources", "world_nether", "world_the_end");

    private final JavaPlugin plugin;
    private final PvpModule pvpModule;
    private final PerformanceModule performanceModule;
    private final AfkService afkService;
    private final AtomicLong outboundBytes = new AtomicLong();
    private final AtomicLong controlledOutboundBytes = new AtomicLong();
    private final AtomicLong inboundBytes = new AtomicLong();
    private final AtomicLong throttledPackets = new AtomicLong();
    private final AtomicLong throttledBytes = new AtomicLong();
    private final LongAdder throttledPacketsTotal = new LongAdder();
    private final LongAdder throttledBytesTotal = new LongAdder();
    private final Map<PacketTypeCommon, LongAdder> packetBytes = new ConcurrentHashMap<>();
    private final EnumMap<PacketGroup, LongAdder> throttledByGroup = new EnumMap<>(PacketGroup.class);
    private final EnumMap<DistanceBand, LongAdder> throttledByDistance = new EnumMap<>(DistanceBand.class);
    private final Map<UUID, AtomicLong> playerOutboundBytes = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerSmoothedOutboundBytes = new ConcurrentHashMap<>();
    private final Map<UUID, ThrottleMode> playerPressureModes = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> playerEntityIds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> playerCurrentEntityIds = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerSnapshot> playerSnapshots = new ConcurrentHashMap<>();
    private final Map<ThrottleKey, CorrectionState> pendingCorrections = new ConcurrentHashMap<>();
    private final Set<ThrottleKey> queuedCorrectionKeys = ConcurrentHashMap.newKeySet();
    private final DelayQueue<DelayedCorrection> correctionQueue = new DelayQueue<>();
    private final Map<PacketLimitKey, RateState> packetRateLimits = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentTeleportsUntil = new ConcurrentHashMap<>();

    private PacketListenerCommon packetListener;
    private BukkitTask bandwidthTask;
    private BukkitTask snapshotTask;
    private BukkitTask correctionTask;
    private volatile ThrottleMode mode = ThrottleMode.OFF;
    private volatile boolean throttleArmed = false;
    private volatile boolean afkThrottleArmed = false;
    private volatile double smoothedBytesPerSecond = 0.0D;
    private volatile double smoothedDeliveredBytesPerSecond = 0.0D;
    private volatile double smoothedInboundBytesPerSecond = 0.0D;
    private volatile double smoothedSystemTransmitBytesPerSecond = -1.0D;
    private volatile AdaptiveLimits adaptiveLimits = AdaptiveLimits.defaults();
    private long lastSystemTransmitBytes = -1L;
    private long lastBandwidthSampleNanos = 0L;
    private long lastLogNanos = 0L;
    private double adaptiveServerLimitBytesPerSecond = DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND;
    private int adaptiveRecoverySamples = 0;
    private ThrottleMode pendingMode = ThrottleMode.OFF;
    private int pendingModeSamples = 0;
    private volatile String lastTopPacketSummary = "none";
    private volatile String lastTopPlayerSummary = "none";
    private volatile String lastHighPingSummary = "none";
    private volatile String lastAfkSummary = "none";

    public NetworkThrottleModule(
            JavaPlugin plugin,
            PvpModule pvpModule,
            PerformanceModule performanceModule,
            AfkService afkService
    ) {
        this.plugin = plugin;
        this.pvpModule = pvpModule;
        this.performanceModule = performanceModule;
        this.afkService = afkService;
        for (PacketGroup group : PacketGroup.values()) {
            throttledByGroup.put(group, new LongAdder());
        }
        for (DistanceBand band : DistanceBand.values()) {
            throttledByDistance.put(band, new LongAdder());
        }
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        primeOnlinePlayers();

        packetListener = PacketEvents.getAPI().getEventManager().registerListener(new NetworkPacketListener());
        snapshotTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSnapshots, 1L, SNAPSHOT_PERIOD_TICKS);
        correctionTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushCorrections, 1L, 1L);
        bandwidthTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::sampleBandwidth, BANDWIDTH_SAMPLE_PERIOD_TICKS, BANDWIDTH_SAMPLE_PERIOD_TICKS);

        plugin.getLogger().info("NetworkThrottleModule enabled");
    }

    public void disable() {
        if (bandwidthTask != null) {
            bandwidthTask.cancel();
        }
        if (snapshotTask != null) {
            snapshotTask.cancel();
        }
        if (correctionTask != null) {
            correctionTask.cancel();
        }
        bandwidthTask = null;
        snapshotTask = null;
        correctionTask = null;

        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
            packetListener = null;
        }
        HandlerList.unregisterAll(this);

        resetRuntimeState();
    }

    private void resetRuntimeState() {
        outboundBytes.set(0L);
        controlledOutboundBytes.set(0L);
        inboundBytes.set(0L);
        throttledPackets.set(0L);
        throttledBytes.set(0L);
        throttledPacketsTotal.reset();
        throttledBytesTotal.reset();
        for (LongAdder counter : throttledByGroup.values()) {
            counter.reset();
        }
        for (LongAdder counter : throttledByDistance.values()) {
            counter.reset();
        }
        playerEntityIds.clear();
        playerCurrentEntityIds.clear();
        playerSnapshots.clear();
        playerNames.clear();
        playerOutboundBytes.clear();
        playerSmoothedOutboundBytes.clear();
        playerPressureModes.clear();
        pendingCorrections.clear();
        queuedCorrectionKeys.clear();
        correctionQueue.clear();
        packetRateLimits.clear();
        recentTeleportsUntil.clear();
        packetBytes.clear();
        mode = ThrottleMode.OFF;
        throttleArmed = false;
        afkThrottleArmed = false;
        smoothedBytesPerSecond = 0.0D;
        smoothedDeliveredBytesPerSecond = 0.0D;
        smoothedInboundBytesPerSecond = 0.0D;
        smoothedSystemTransmitBytesPerSecond = -1.0D;
        adaptiveLimits = AdaptiveLimits.defaults();
        lastSystemTransmitBytes = -1L;
        lastBandwidthSampleNanos = 0L;
        lastLogNanos = 0L;
        adaptiveServerLimitBytesPerSecond = DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND;
        adaptiveRecoverySamples = 0;
        pendingMode = ThrottleMode.OFF;
        pendingModeSamples = 0;
        lastTopPacketSummary = "none";
        lastTopPlayerSummary = "none";
        lastHighPingSummary = "none";
        lastAfkSummary = "none";
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("network")
                .requires(source -> source.getSender().hasPermission("group." + Mik.GROUP_HELPER))
                .then(Commands.literal("status")
                        .executes(ctx -> sendNetwork(ctx.getSource().getSender(), NetworkView.STATUS)))
                .then(Commands.literal("players")
                        .executes(ctx -> sendNetwork(ctx.getSource().getSender(), NetworkView.PLAYERS)))
                .then(Commands.literal("packets")
                        .executes(ctx -> sendNetwork(ctx.getSource().getSender(), NetworkView.PACKETS)))
                .then(Commands.literal("limits")
                        .executes(ctx -> sendNetwork(ctx.getSource().getSender(), NetworkView.LIMITS)))
                .then(Commands.literal("rules")
                        .executes(ctx -> sendNetwork(ctx.getSource().getSender(), NetworkView.RULES)))
                .then(Commands.literal("help")
                        .executes(ctx -> sendNetwork(ctx.getSource().getSender(), NetworkView.HELP)))
                .then(Commands.literal("all")
                        .executes(ctx -> sendNetwork(ctx.getSource().getSender(), NetworkView.ALL)))
                .executes(ctx -> sendNetwork(ctx.getSource().getSender(), NetworkView.STATUS))
                .build(), "Network bandwidth and packet throttle status"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        trackPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Integer entityId = playerCurrentEntityIds.remove(player.getUniqueId());
        if (entityId != null) {
            playerEntityIds.remove(entityId);
        }
        playerEntityIds.remove(player.getEntityId());
        playerSnapshots.remove(player.getUniqueId());
        playerNames.remove(player.getUniqueId());
        playerOutboundBytes.remove(player.getUniqueId());
        playerSmoothedOutboundBytes.remove(player.getUniqueId());
        playerPressureModes.remove(player.getUniqueId());
        recentTeleportsUntil.remove(player.getUniqueId());
        pendingCorrections.keySet().removeIf(key ->
                key.viewerId().equals(player.getUniqueId()) || key.subjectId().equals(player.getUniqueId()));
        queuedCorrectionKeys.removeIf(key ->
                key.viewerId().equals(player.getUniqueId()) || key.subjectId().equals(player.getUniqueId()));
        packetRateLimits.keySet().removeIf(key ->
                key.viewerId().equals(player.getUniqueId()) || key.subjectId().equals(player.getUniqueId()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        markRecentTeleport(event.getPlayer().getUniqueId());
    }

    public void markRecentTeleport(UUID playerId) {
        recentTeleportsUntil.put(playerId, System.nanoTime() + RECENT_TELEPORT_SUPPRESS_NANOS);
    }

    private void primeOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            trackPlayer(player);
        }
        refreshSnapshots();
    }

    private void trackPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        int entityId = player.getEntityId();
        Integer previousEntityId = playerCurrentEntityIds.put(playerId, entityId);
        if (previousEntityId != null && previousEntityId != entityId) {
            playerEntityIds.remove(previousEntityId, playerId);
        }
        playerEntityIds.put(entityId, playerId);
        playerOutboundBytes.putIfAbsent(playerId, new AtomicLong());
        playerSnapshots.compute(playerId, (_, previousSnapshot) -> PlayerSnapshot.from(player, afkService, adaptiveLimits, previousSnapshot));
        playerNames.put(playerId, player.getName());
    }

    private void refreshSnapshots() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            trackPlayer(player);
        }
    }

    private void sampleBandwidth() {
        long now = System.nanoTime();
        double elapsedSeconds = elapsedSeconds(now);
        long sentBytes = outboundBytes.getAndSet(0L);
        long controlledSentBytes = controlledOutboundBytes.getAndSet(0L);
        long inbound = inboundBytes.getAndSet(0L);
        long cancelledPackets = throttledPackets.getAndSet(0L);
        long cancelledBytes = throttledBytes.getAndSet(0L);
        double deliveredBytesPerSecond = bytesPerSecond(sentBytes, elapsedSeconds);
        double demandedBytesPerSecond = bytesPerSecond(controlledSentBytes + cancelledBytes, elapsedSeconds);

        double previousOutboundBytesPerSecond = smoothedBytesPerSecond;
        double previousDeliveredBytesPerSecond = smoothedDeliveredBytesPerSecond;
        double previousInboundBytesPerSecond = smoothedInboundBytesPerSecond;
        smoothedBytesPerSecond = smooth(previousOutboundBytesPerSecond, demandedBytesPerSecond);
        smoothedDeliveredBytesPerSecond = smooth(previousDeliveredBytesPerSecond, deliveredBytesPerSecond);
        smoothedInboundBytesPerSecond = smooth(previousInboundBytesPerSecond, bytesPerSecond(inbound, elapsedSeconds));
        sampleSystemNetwork(elapsedSeconds);
        samplePlayerBandwidth(elapsedSeconds);
        cleanupRateLimits(now);
        cleanupCorrections(now);
        updateAdaptiveLimits(controlledSentBytes, cancelledBytes);
        refreshPlayerPressureModes();

        ThrottleMode previous = mode;
        boolean playerPressure = hasPlayerPressure();
        SnapshotPressure snapshotPressure = snapshotPressure();
        boolean pingPressure = snapshotPressure.pingPressure();
        boolean afkPressure = snapshotPressure.afkPressure();
        mode = hysteresisMode(modeForPressure(adaptiveLimits.pressureScore()));
        afkThrottleArmed = afkPressure;
        throttleArmed = mode != ThrottleMode.OFF || playerPressure || pingPressure || afkPressure;
        if (!throttleArmed) {
            pendingCorrections.clear();
            queuedCorrectionKeys.clear();
            correctionQueue.clear();
            packetRateLimits.clear();
        }

        lastTopPacketSummary = topPacketTypes(elapsedSeconds);
        lastTopPlayerSummary = topPlayers();
        lastHighPingSummary = highPingPlayers();
        lastAfkSummary = afkPlayers();
        if (mode != previous || (mode != ThrottleMode.OFF && now - lastLogNanos >= LOG_PERIOD_NANOS)) {
            lastLogNanos = now;
            plugin.getLogger().info(String.format(Locale.ROOT,
                    "Network throttle %s -> %s, sent=%.2f MiB/s, demand=%.2f MiB/s, in=%.2f MiB/s, sysTx=%s, throttled=%d packets %.2f MiB/s, topPackets=%s, topViewers=%s, highPing=%s, afk=%s",
                    previous,
                    mode,
                    smoothedDeliveredBytesPerSecond / 1024.0D / 1024.0D,
                    smoothedBytesPerSecond / 1024.0D / 1024.0D,
                    smoothedInboundBytesPerSecond / 1024.0D / 1024.0D,
                    formatSystemTransmit(),
                    cancelledPackets,
                    bytesPerSecond(cancelledBytes, elapsedSeconds) / 1024.0D / 1024.0D,
                    lastTopPacketSummary,
                    lastTopPlayerSummary,
                    lastHighPingSummary,
                    lastAfkSummary));
        }
    }

    private double elapsedSeconds(long now) {
        if (lastBandwidthSampleNanos == 0L) {
            lastBandwidthSampleNanos = now;
            return BANDWIDTH_SAMPLE_PERIOD_TICKS / 20.0D;
        }
        long elapsedNanos = Math.max(1L, now - lastBandwidthSampleNanos);
        lastBandwidthSampleNanos = now;
        return elapsedNanos / 1_000_000_000.0D;
    }

    private double bytesPerSecond(long bytes, double elapsedSeconds) {
        return elapsedSeconds <= 0.0D ? 0.0D : bytes / elapsedSeconds;
    }

    private double smooth(double previous, double current) {
        if (previous <= 0.0D) {
            return current;
        }
        return previous * (1.0D - EWMA_WEIGHT) + current * EWMA_WEIGHT;
    }

    private void samplePlayerBandwidth(double elapsedSeconds) {
        for (Map.Entry<UUID, AtomicLong> entry : playerOutboundBytes.entrySet()) {
            long bytes = entry.getValue().getAndSet(0L);
            UUID playerId = entry.getKey();
            playerSmoothedOutboundBytes.compute(playerId, (ignored, previous) ->
                    playerSnapshots.containsKey(playerId)
                            ? smooth(previous == null ? 0.0D : previous, bytesPerSecond(bytes, elapsedSeconds))
                            : null);
        }
    }

    private void refreshPlayerPressureModes() {
        for (Map.Entry<UUID, Double> entry : playerSmoothedOutboundBytes.entrySet()) {
            if (!playerSnapshots.containsKey(entry.getKey())) {
                playerPressureModes.remove(entry.getKey());
                playerSmoothedOutboundBytes.remove(entry.getKey(), entry.getValue());
                continue;
            }
            playerPressureModes.put(entry.getKey(), playerModeFor(entry.getValue()));
        }
    }

    private void updateAdaptiveLimits(long controlledSentBytes, long cancelledBytes) {
        double demand = smoothedBytesPerSecond;
        double delivered = smoothedDeliveredBytesPerSecond;
        double mspt = performanceModule.effectiveMspt();
        int onlinePlayers = Math.max(1, playerSnapshots.size());
        double cancelRatio = cancelledRatio(controlledSentBytes, cancelledBytes);
        PlayerDemandStats playerDemand = playerDemandStats();
        PingStats pingStats = pingStats();

        double currentServerLimit = Math.max(1.0D, adaptiveServerLimitBytesPerSecond);
        double bandwidthPressure = demand / currentServerLimit;
        double deliveredPressure = delivered / currentServerLimit;
        double systemPressure = smoothedSystemTransmitBytesPerSecond > 0.0D
                ? smoothedSystemTransmitBytesPerSecond
                / Math.max(1.0D, DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND * 1.25D)
                : 0.0D;
        double msptPressure = msptPressureRatio(mspt);
        double queuePressure = Math.min(1.0D, pendingCorrections.size() / Math.max(16.0D, onlinePlayers * 12.0D));
        double cancelSampleConfidence = Math.clamp(
                (controlledSentBytes + cancelledBytes) / Math.max(64.0D * 1024.0D, onlinePlayers * 24.0D * 1024.0D),
                0.0D,
                1.0D);
        double cancelPressure = Math.clamp(cancelRatio * 2.5D * cancelSampleConfidence, 0.0D, 1.0D);
        double pressureScore = Math.clamp(
                Math.max(Math.max(bandwidthPressure, deliveredPressure), Math.max(systemPressure, msptPressure)),
                0.0D,
                1.0D);
        double intervalPressureScore = Math.clamp(
                Math.max(pressureScore, Math.max(queuePressure * 0.85D, cancelPressure * 0.70D)),
                0.0D,
                1.0D);
        double capacityPressure = Math.max(systemPressure, msptPressure);
        boolean capacityHealthy = capacityPressure < SOFT_PRESSURE_RATIO;
        double desiredHealthyLimit = clamp(
                Math.max(DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND,
                        Math.max(demand, delivered) / SOFT_PRESSURE_RATIO),
                MIN_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND,
                MAX_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND);

        if (capacityPressure >= ADAPTIVE_CRITICAL_TRIGGER) {
            adaptiveServerLimitBytesPerSecond *= ADAPTIVE_DECREASE_FACTOR * ADAPTIVE_DECREASE_FACTOR;
            adaptiveRecoverySamples = 0;
        } else if (capacityPressure >= ADAPTIVE_PRESSURE_TRIGGER) {
            adaptiveServerLimitBytesPerSecond *= ADAPTIVE_DECREASE_FACTOR;
            adaptiveRecoverySamples = 0;
        } else if (capacityHealthy) {
            adaptiveRecoverySamples++;
            if (adaptiveRecoverySamples >= ADAPTIVE_RECOVERY_SAMPLES) {
                if (adaptiveServerLimitBytesPerSecond < desiredHealthyLimit) {
                    adaptiveServerLimitBytesPerSecond = Math.min(
                            desiredHealthyLimit,
                            adaptiveServerLimitBytesPerSecond * ADAPTIVE_INCREASE_FACTOR);
                } else if (adaptiveServerLimitBytesPerSecond > DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND
                        && Math.max(demand, delivered) < DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND * 0.40D) {
                    adaptiveServerLimitBytesPerSecond = Math.max(
                            DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND,
                            adaptiveServerLimitBytesPerSecond * ADAPTIVE_IDLE_DECAY_FACTOR);
                }
                adaptiveRecoverySamples = 0;
            }
        } else {
            adaptiveRecoverySamples = 0;
        }

        adaptiveServerLimitBytesPerSecond = clamp(
                adaptiveServerLimitBytesPerSecond,
                MIN_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND,
                MAX_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND);

        double fairShare = adaptiveServerLimitBytesPerSecond / Math.max(onlinePlayers, 4);
        long playerSoft = Math.round(clamp(
                Math.max(Math.max(playerDemand.p75BytesPerSecond() * 1.25D, fairShare * 0.60D),
                        MIN_PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND),
                MIN_PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND,
                MAX_PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND));
        long playerHard = Math.round(clamp(playerSoft * 1.80D, playerSoft + 64.0D * 1024.0D,
                DEFAULT_PLAYER_OUTBOUND_CRITICAL_BYTES_PER_SECOND * 2.0D));
        long playerCritical = Math.round(clamp(playerSoft * 2.80D, playerHard + 64.0D * 1024.0D,
                DEFAULT_PLAYER_OUTBOUND_CRITICAL_BYTES_PER_SECOND * 3.0D));

        int pingSoft = PING_SOFT_MILLIS;
        int pingHard = PING_HARD_MILLIS;
        int pingCritical = PING_CRITICAL_MILLIS;
        if (onlinePlayers >= 4) {
            pingSoft = (int) Math.round(clamp(Math.max(PING_SOFT_MILLIS, pingStats.p75Millis() * 1.20D),
                    PING_SOFT_MILLIS, PING_HARD_MILLIS));
            pingHard = (int) Math.round(clamp(Math.max(PING_HARD_MILLIS, pingStats.p90Millis() * 1.20D),
                    pingSoft + 40.0D, PING_CRITICAL_MILLIS));
            pingCritical = (int) Math.round(clamp(Math.max(PING_CRITICAL_MILLIS, pingStats.p95Millis() * 1.20D),
                    pingHard + 80.0D, 900.0D));
        }

        double intervalScale = clamp(1.0D + intervalPressureScore * 1.25D,
                1.0D, 2.75D);
        int correctionBudget = (int) Math.round(clamp(
                MAX_CORRECTIONS_PER_TICK * (1.05D - Math.min(0.55D, msptPressure * 0.45D))
                        + MAX_CORRECTIONS_PER_TICK * Math.min(0.25D, queuePressure * 0.25D),
                MIN_CORRECTIONS_PER_TICK,
                MAX_CORRECTIONS_PER_TICK));

        adaptiveLimits = new AdaptiveLimits(
                Math.round(adaptiveServerLimitBytesPerSecond),
                playerSoft,
                playerHard,
                playerCritical,
                pingSoft,
                pingHard,
                pingCritical,
                correctionBudget,
                intervalScale,
                pressureScore,
                intervalPressureScore,
                cancelRatio,
                playerDemand.p75BytesPerSecond(),
                playerDemand.p95BytesPerSecond(),
                pingStats.p90Millis());
    }

    private void sampleSystemNetwork(double elapsedSeconds) {
        long transmitBytes = readLinuxTransmitBytes();
        if (transmitBytes < 0L) {
            return;
        }
        if (lastSystemTransmitBytes >= 0L && transmitBytes >= lastSystemTransmitBytes) {
            double previousSystemTransmitBytesPerSecond = smoothedSystemTransmitBytesPerSecond;
            smoothedSystemTransmitBytesPerSecond = smooth(previousSystemTransmitBytesPerSecond,
                    bytesPerSecond(transmitBytes - lastSystemTransmitBytes, elapsedSeconds));
        }
        lastSystemTransmitBytes = transmitBytes;
    }

    private double cancelledRatio(long controlledSentBytes, long cancelledBytes) {
        long demandBytes = controlledSentBytes + cancelledBytes;
        if (demandBytes <= 0L) {
            return 0.0D;
        }
        return cancelledBytes / (double) demandBytes;
    }

    private PlayerDemandStats playerDemandStats() {
        ArrayList<Double> values = new ArrayList<>();
        for (double bytesPerSecond : playerSmoothedOutboundBytes.values()) {
            if (bytesPerSecond > 0.0D) {
                values.add(bytesPerSecond);
            }
        }
        if (values.isEmpty()) {
            return new PlayerDemandStats(0.0D, 0.0D);
        }
        values.sort(Comparator.naturalOrder());
        return new PlayerDemandStats(percentile(values, 0.75D), percentile(values, 0.95D));
    }

    private PingStats pingStats() {
        ArrayList<Double> values = new ArrayList<>();
        for (PlayerSnapshot snapshot : playerSnapshots.values()) {
            values.add((double) snapshot.pingMillis());
        }
        if (values.isEmpty()) {
            return new PingStats(0.0D, 0.0D, 0.0D);
        }
        values.sort(Comparator.naturalOrder());
        return new PingStats(percentile(values, 0.75D), percentile(values, 0.90D), percentile(values, 0.95D));
    }

    private double percentile(ArrayList<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0.0D;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.clamp(index, 0, sortedValues.size() - 1));
    }

    private double msptPressureRatio(double value) {
        if (value <= MSPT_SOFT_PRESSURE) {
            return value / MSPT_SOFT_PRESSURE * SOFT_PRESSURE_RATIO;
        }
        if (value <= MSPT_HARD_PRESSURE) {
            return SOFT_PRESSURE_RATIO
                    + (value - MSPT_SOFT_PRESSURE) / (MSPT_HARD_PRESSURE - MSPT_SOFT_PRESSURE)
                    * (HARD_PRESSURE_RATIO - SOFT_PRESSURE_RATIO);
        }
        if (value <= MSPT_CRITICAL_PRESSURE) {
            return HARD_PRESSURE_RATIO
                    + (value - MSPT_HARD_PRESSURE) / (MSPT_CRITICAL_PRESSURE - MSPT_HARD_PRESSURE)
                    * (CRITICAL_PRESSURE_RATIO - HARD_PRESSURE_RATIO);
        }
        return 1.0D;
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.clamp(value, minimum, maximum);
    }

    private ThrottleMode playerModeFor(double bytesPerSecond) {
        AdaptiveLimits limits = adaptiveLimits;
        if (bytesPerSecond >= limits.playerCriticalBytesPerSecond()) {
            return ThrottleMode.CRITICAL;
        }
        if (bytesPerSecond >= limits.playerHardBytesPerSecond()) {
            return ThrottleMode.HARD;
        }
        if (bytesPerSecond >= limits.playerSoftBytesPerSecond()) {
            return ThrottleMode.SOFT;
        }
        return ThrottleMode.OFF;
    }

    private ThrottleMode playerMode(UUID playerId) {
        return playerPressureModes.getOrDefault(playerId, ThrottleMode.OFF);
    }

    private ThrottleMode modeForPressure(double pressureScore) {
        if (pressureScore >= CRITICAL_PRESSURE_RATIO) {
            return ThrottleMode.CRITICAL;
        }
        if (pressureScore >= HARD_PRESSURE_RATIO) {
            return ThrottleMode.HARD;
        }
        if (pressureScore >= SOFT_PRESSURE_RATIO) {
            return ThrottleMode.SOFT;
        }
        return ThrottleMode.OFF;
    }

    private ThrottleMode hysteresisMode(ThrottleMode desiredMode) {
        if (desiredMode == mode) {
            pendingMode = desiredMode;
            pendingModeSamples = 0;
            return mode;
        }

        if (desiredMode != pendingMode) {
            pendingMode = desiredMode;
            pendingModeSamples = 1;
        } else {
            pendingModeSamples++;
        }

        if (desiredMode.ordinal() > mode.ordinal()) {
            int requiredSamples = desiredMode == ThrottleMode.CRITICAL ? 1 : MODE_UP_SAMPLES;
            if (pendingModeSamples >= requiredSamples) {
                pendingModeSamples = 0;
                return desiredMode;
            }
            return mode;
        }

        if (pendingModeSamples >= MODE_DOWN_SAMPLES) {
            pendingModeSamples = 0;
            return desiredMode;
        }
        return mode;
    }

    private static ThrottleMode pingMode(int pingMillis, AdaptiveLimits limits) {
        if (pingMillis >= limits.pingCriticalMillis()) {
            return ThrottleMode.CRITICAL;
        }
        if (pingMillis >= limits.pingHardMillis()) {
            return ThrottleMode.HARD;
        }
        if (pingMillis >= limits.pingSoftMillis()) {
            return ThrottleMode.SOFT;
        }
        return ThrottleMode.OFF;
    }

    private ThrottleMode maxMode(ThrottleMode first, ThrottleMode second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private boolean hasPlayerPressure() {
        for (ThrottleMode playerMode : playerPressureModes.values()) {
            if (playerMode != ThrottleMode.OFF) {
                return true;
            }
        }
        return false;
    }

    private SnapshotPressure snapshotPressure() {
        boolean pingPressure = false;
        boolean afkPressure = false;
        for (PlayerSnapshot snapshot : playerSnapshots.values()) {
            if (snapshot.pingMode() != ThrottleMode.OFF) {
                pingPressure = true;
            }
            if (snapshot.afk()) {
                afkPressure = true;
            }
            if (pingPressure && afkPressure) {
                break;
            }
        }
        return new SnapshotPressure(pingPressure, afkPressure);
    }

    private void cleanupRateLimits(long now) {
        packetRateLimits.entrySet().removeIf(entry -> now - entry.getValue().lastSeenNanos() > RATE_LIMIT_EXPIRY_NANOS);
    }

    private void cleanupCorrections(long now) {
        pendingCorrections.entrySet().removeIf(entry -> {
            if (now - entry.getValue().lastSeenNanos() <= RATE_LIMIT_EXPIRY_NANOS) {
                return false;
            }
            queuedCorrectionKeys.remove(entry.getKey());
            return true;
        });
    }

    private long readLinuxTransmitBytes() {
        if (!Files.isReadable(LINUX_PROC_NET_DEV)) {
            return -1L;
        }

        long total = 0L;
        try (BufferedReader reader = Files.newBufferedReader(LINUX_PROC_NET_DEV)) {
            String line;
            while ((line = reader.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String interfaceName = line.substring(0, colon).trim();
                if ("lo".equals(interfaceName)) {
                    continue;
                }
                String[] fields = line.substring(colon + 1).trim().split("\\s+");
                if (fields.length <= 8) {
                    continue;
                }
                total += Long.parseLong(fields[8]);
            }
            return total;
        } catch (IOException | NumberFormatException ignored) {
            return -1L;
        }
    }

    private String formatSystemTransmit() {
        if (smoothedSystemTransmitBytesPerSecond < 0.0D) {
            return "unavailable";
        }
        return formatThroughput(smoothedSystemTransmitBytesPerSecond);
    }

    private String topPacketTypes(double elapsedSeconds) {
        ArrayList<PacketVolume> entries = new ArrayList<>();
        for (Map.Entry<PacketTypeCommon, LongAdder> entry : packetBytes.entrySet()) {
            long bytes = entry.getValue().sumThenReset();
            if (bytes > 0L) {
                entries.add(new PacketVolume(entry.getKey(), bytes));
            }
        }
        entries.sort(Comparator.comparingLong(PacketVolume::bytes).reversed());

        StringBuilder builder = new StringBuilder();
        int count = Math.min(3, entries.size());
        for (int i = 0; i < count; i++) {
            PacketVolume entry = entries.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(packetName(entry.type()))
                    .append("=")
                    .append(formatThroughput(bytesPerSecond(entry.bytes(), elapsedSeconds)));
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private String topPlayers() {
        ArrayList<PlayerVolume> entries = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : playerSmoothedOutboundBytes.entrySet()) {
            double bytes = entry.getValue();
            if (bytes > 0.0D) {
                entries.add(new PlayerVolume(entry.getKey(), bytes));
            }
        }
        entries.sort(Comparator.comparingDouble(PlayerVolume::bytes).reversed());

        StringBuilder builder = new StringBuilder();
        int count = Math.min(3, entries.size());
        for (int i = 0; i < count; i++) {
            PlayerVolume entry = entries.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(playerNames.getOrDefault(entry.playerId(), entry.playerId().toString().substring(0, 8)))
                    .append("=")
                    .append(formatThroughput(entry.bytes()));
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private String highPingPlayers() {
        AdaptiveLimits limits = adaptiveLimits;
        ArrayList<PlayerPing> entries = new ArrayList<>();
        for (Map.Entry<UUID, PlayerSnapshot> entry : playerSnapshots.entrySet()) {
            int ping = entry.getValue().pingMillis();
            if (ping >= limits.pingSoftMillis()) {
                entries.add(new PlayerPing(entry.getKey(), ping));
            }
        }
        entries.sort(Comparator.comparingInt(PlayerPing::pingMillis).reversed());

        StringBuilder builder = new StringBuilder();
        int count = Math.min(3, entries.size());
        for (int i = 0; i < count; i++) {
            PlayerPing entry = entries.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(playerNames.getOrDefault(entry.playerId(), entry.playerId().toString().substring(0, 8)))
                    .append("=")
                    .append(entry.pingMillis())
                    .append("ms");
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private String afkPlayers() {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        int shown = 0;
        for (Map.Entry<UUID, PlayerSnapshot> entry : playerSnapshots.entrySet()) {
            if (!entry.getValue().afk()) {
                continue;
            }
            count++;
            if (shown >= 3) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(playerNames.getOrDefault(entry.getKey(), entry.getKey().toString().substring(0, 8)));
            shown++;
        }
        if (count == 0) {
            return "none";
        }
        if (count > shown) {
            builder.append(" +").append(count - shown);
        }
        return builder.toString();
    }

    private void flushCorrections() {
        long now = System.nanoTime();
        int sent = 0;
        int inspected = 0;
        int maxCorrections = adaptiveLimits.maxCorrectionsPerTick();
        int maxInspections = Math.min(MAX_CORRECTION_INSPECTIONS_PER_TICK, maxCorrections * 4);
        while (sent < maxCorrections && inspected < maxInspections) {
            DelayedCorrection correction = correctionQueue.poll();
            if (correction == null) {
                break;
            }
            ThrottleKey key = correction.key();
            queuedCorrectionKeys.remove(key);
            inspected++;

            CorrectionState state = pendingCorrections.get(key);
            if (state == null) {
                continue;
            }
            if (now < state.nextSendNanos()) {
                enqueueCorrection(key, state);
                continue;
            }
            if (!state.consumeDirty()) {
                pendingCorrections.computeIfPresent(key, (ignored, current) ->
                        current == state && current.isClean() ? null : current);
                continue;
            }

            Player viewer = Bukkit.getPlayer(key.viewerId());
            PlayerSnapshot viewerSnapshot = playerSnapshots.get(key.viewerId());
            PlayerSnapshot subjectSnapshot = playerSnapshots.get(key.subjectId());
            if (viewer == null || !viewer.isOnline() || viewerSnapshot == null || subjectSnapshot == null) {
                pendingCorrections.remove(key);
                continue;
            }
            if (recentlyTeleported(key.viewerId()) || recentlyTeleported(key.subjectId())) {
                pendingCorrections.remove(key);
                continue;
            }
            if (!viewerSnapshot.worldId().equals(subjectSnapshot.worldId())) {
                pendingCorrections.remove(key);
                continue;
            }

            ThrottleMode currentMode = effectiveMode(key.viewerId(), viewerSnapshot, subjectSnapshot);
            DistanceBand distanceBand = DistanceBand.from(viewerSnapshot.distanceSquared(subjectSnapshot));
            currentMode = perceptualAdjustedMode(currentMode, distanceBand, viewerSnapshot, subjectSnapshot);
            if (currentMode == ThrottleMode.OFF) {
                sendAbsoluteEntityPosition(viewer, subjectSnapshot);
                sent++;
                pendingCorrections.remove(key);
                continue;
            }

            long interval = correctionInterval(
                    currentMode,
                    distanceBand,
                    intervalMultiplier(viewerSnapshot, subjectSnapshot));
            if (interval <= 0L) {
                sendAbsoluteEntityPosition(viewer, subjectSnapshot);
                sent++;
                pendingCorrections.remove(key);
                continue;
            }

            sendAbsoluteEntityPosition(viewer, subjectSnapshot);
            sent++;
            state.setNextSendNanos(now + interval);
            if (state.isDirty()) {
                enqueueCorrection(key, state);
            }
        }
    }

    private void sendAbsoluteEntityPosition(Player viewer, PlayerSnapshot subject) {
        PacketEvents.getAPI().getPlayerManager().sendPacket(
                viewer,
                new WrapperPlayServerEntityTeleport(
                        subject.entityId(),
                        new Vector3d(subject.x(), subject.y(), subject.z()),
                        subject.yaw(),
                        subject.pitch(),
                        true)
        );
    }

    private boolean shouldThrottleMovement(UUID viewerId, ThrottleContext context) {
        long interval = correctionInterval(context.mode(), context.distanceBand(), context.viewerPingIntervalMultiplier());
        if (interval <= 0L) {
            return false;
        }

        ThrottleKey key = new ThrottleKey(viewerId, context.subjectId());
        CorrectionState state = pendingCorrections.get(key);
        if (state == null) {
            CorrectionState created = new CorrectionState();
            state = pendingCorrections.putIfAbsent(key, created);
            if (state == null) {
                state = created;
            } else {
                state.markDirty();
            }
        } else {
            state.markDirty();
        }
        enqueueCorrection(key, state);
        return true;
    }

    private void enqueueCorrection(ThrottleKey key, CorrectionState state) {
        if (state != null && queuedCorrectionKeys.add(key)) {
            long dueNanos = Math.max(System.nanoTime(), state.nextSendNanos());
            correctionQueue.offer(new DelayedCorrection(key, dueNanos));
        }
    }

    private boolean shouldThrottleLimitedPacket(
            PacketTypeCommon packetType,
            PacketGroup packetGroup,
            UUID viewerId,
            ThrottleContext context
    ) {
        long interval = limitedPacketInterval(
                packetGroup,
                context.mode(),
                context.distanceBand(),
                context.viewerPingIntervalMultiplier(),
                context.focusedSubject());
        if (interval <= 0L) {
            return false;
        }

        PacketLimitKey key = new PacketLimitKey(viewerId, context.subjectId(), packetType);
        RateState state = packetRateLimits.get(key);
        if (state == null) {
            RateState created = new RateState();
            state = packetRateLimits.putIfAbsent(key, created);
            if (state == null) {
                state = created;
            }
        }
        return !state.tryAcquire(System.nanoTime(), interval);
    }

    private ThrottleContext throttleContext(
            UUID viewerId,
            PlayerSnapshot viewerSnapshot,
            ThrottleMode viewerDirectMode,
            boolean afkSubjectOnly,
            int subjectEntityId
    ) {
        UUID subjectId = playerEntityIds.get(subjectEntityId);
        if (subjectId == null || subjectId.equals(viewerId)) {
            return null;
        }

        PlayerSnapshot subjectSnapshot = playerSnapshots.get(subjectId);
        if (subjectSnapshot == null || !viewerSnapshot.worldId().equals(subjectSnapshot.worldId())) {
            return null;
        }
        if (afkSubjectOnly && !subjectSnapshot.afk()) {
            return null;
        }
        if (recentlyTeleported(viewerId) || recentlyTeleported(subjectId)) {
            return null;
        }
        if (sharesVehicleOrRiding(viewerSnapshot, subjectSnapshot)) {
            return null;
        }

        ThrottleMode currentMode = viewerDirectMode;
        if (subjectSnapshot.afk()) {
            currentMode = maxMode(currentMode, AFK_SUBJECT_MODE);
        }
        if (currentMode == ThrottleMode.OFF) {
            return null;
        }

        double distanceSquared = viewerSnapshot.distanceSquared(subjectSnapshot);
        if (isClosePvpPair(viewerId, subjectId, distanceSquared)) {
            return null;
        }
        DistanceBand distanceBand = DistanceBand.from(distanceSquared);
        currentMode = distanceAdjustedMode(currentMode, distanceBand);
        if (currentMode == ThrottleMode.OFF) {
            return null;
        }
        boolean focusedSubject = false;
        if (!viewerSnapshot.afk()) {
            if (shouldRelaxFarSubject(currentMode, viewerSnapshot, subjectSnapshot, distanceBand)) {
                currentMode = previousMode(currentMode);
                if (currentMode == ThrottleMode.OFF) {
                    return null;
                }
            }
            focusedSubject = isFocusedSubject(viewerSnapshot, subjectSnapshot);
            if (focusedSubject) {
                currentMode = focusAdjustedMode(currentMode, distanceBand);
                if (currentMode == ThrottleMode.OFF) {
                    return null;
                }
            }
        }
        return new ThrottleContext(subjectId, distanceBand, intervalMultiplier(viewerSnapshot, subjectSnapshot),
                currentMode, focusedSubject);
    }

    private ThrottleMode effectiveMode(UUID viewerId, PlayerSnapshot viewerSnapshot) {
        return effectiveMode(viewerId, viewerSnapshot, null);
    }

    private ThrottleMode effectiveMode(UUID viewerId, PlayerSnapshot viewerSnapshot, PlayerSnapshot subjectSnapshot) {
        ThrottleMode networkMode = worldAdjustedMode(maxMode(mode, playerMode(viewerId)), viewerSnapshot.worldName());
        ThrottleMode currentMode = maxMode(networkMode, viewerSnapshot.pingMode());
        if (viewerSnapshot.afk()) {
            currentMode = maxMode(currentMode, AFK_VIEWER_MODE);
        }
        if (subjectSnapshot != null && subjectSnapshot.afk()) {
            currentMode = maxMode(currentMode, AFK_SUBJECT_MODE);
        }
        return currentMode;
    }

    private PlayerSnapshot viewerSnapshot(UUID viewerId) {
        if (!throttleArmed || viewerId == null) {
            return null;
        }
        return playerSnapshots.get(viewerId);
    }

    private double intervalMultiplier(PlayerSnapshot viewerSnapshot, PlayerSnapshot subjectSnapshot) {
        double multiplier = viewerSnapshot.pingIntervalMultiplier();
        if (viewerSnapshot.afk()) {
            multiplier *= AFK_VIEWER_INTERVAL_MULTIPLIER;
        }
        if (subjectSnapshot.afk()) {
            multiplier *= AFK_SUBJECT_INTERVAL_MULTIPLIER;
        }
        return Math.min(multiplier, MAX_INTERVAL_MULTIPLIER);
    }

    private ThrottleMode worldAdjustedMode(ThrottleMode currentMode, String worldName) {
        if (currentMode == ThrottleMode.OFF) {
            return ThrottleMode.OFF;
        }
        String normalized = worldName.toLowerCase(Locale.ROOT);
        if (CONSERVATIVE_WORLD_NAMES.contains(normalized)) {
            return previousMode(currentMode);
        }
        if (AGGRESSIVE_WORLD_NAMES.contains(normalized)) {
            return nextMode(currentMode);
        }
        return currentMode;
    }

    private ThrottleMode nextMode(ThrottleMode currentMode) {
        return switch (currentMode) {
            case OFF -> ThrottleMode.OFF;
            case SOFT -> ThrottleMode.HARD;
            case HARD, CRITICAL -> ThrottleMode.CRITICAL;
        };
    }

    private ThrottleMode previousMode(ThrottleMode currentMode) {
        return switch (currentMode) {
            case OFF, SOFT -> ThrottleMode.OFF;
            case HARD -> ThrottleMode.SOFT;
            case CRITICAL -> ThrottleMode.HARD;
        };
    }

    private ThrottleMode distanceAdjustedMode(ThrottleMode currentMode, DistanceBand distanceBand) {
        return switch (distanceBand) {
            case NEAR -> ThrottleMode.OFF;
            case MID -> currentMode == ThrottleMode.CRITICAL ? ThrottleMode.SOFT : previousMode(currentMode);
            case FAR, EXTREME -> currentMode;
        };
    }

    private ThrottleMode perceptualAdjustedMode(
            ThrottleMode currentMode,
            DistanceBand distanceBand,
            PlayerSnapshot viewerSnapshot,
            PlayerSnapshot subjectSnapshot
    ) {
        ThrottleMode adjustedMode = distanceAdjustedMode(currentMode, distanceBand);
        if (adjustedMode == ThrottleMode.OFF) {
            return ThrottleMode.OFF;
        }
        if (viewerSnapshot.afk()) {
            return adjustedMode;
        }
        if (shouldRelaxFarSubject(adjustedMode, viewerSnapshot, subjectSnapshot, distanceBand)) {
            adjustedMode = previousMode(adjustedMode);
            if (adjustedMode == ThrottleMode.OFF) {
                return ThrottleMode.OFF;
            }
        }
        if (!isFocusedSubject(viewerSnapshot, subjectSnapshot)) {
            return adjustedMode;
        }
        return focusAdjustedMode(adjustedMode, distanceBand);
    }

    private ThrottleMode focusAdjustedMode(ThrottleMode currentMode, DistanceBand distanceBand) {
        return switch (distanceBand) {
            case NEAR, MID -> ThrottleMode.OFF;
            case FAR -> currentMode.ordinal() > ThrottleMode.SOFT.ordinal()
                    ? ThrottleMode.SOFT
                    : previousMode(currentMode);
            case EXTREME -> previousMode(currentMode);
        };
    }

    private boolean shouldRelaxFarSubject(
            ThrottleMode currentMode,
            PlayerSnapshot viewerSnapshot,
            PlayerSnapshot subjectSnapshot,
            DistanceBand distanceBand
    ) {
        return distanceBand == DistanceBand.FAR
                && (subjectSnapshot.fastMoving()
                || (currentMode != ThrottleMode.CRITICAL && viewerSnapshot.fastLooking()));
    }

    private boolean isFocusedSubject(PlayerSnapshot viewerSnapshot, PlayerSnapshot subjectSnapshot) {
        double dx = subjectSnapshot.x() - viewerSnapshot.x();
        double dz = subjectSnapshot.z() - viewerSnapshot.z();
        double horizontalDistanceSquared = dx * dx + dz * dz;
        if (horizontalDistanceSquared > FOCUSED_SUBJECT_DISTANCE_SQUARED) {
            return false;
        }

        double dy = subjectSnapshot.y() + SUBJECT_FOCUS_HEIGHT - viewerSnapshot.y() - VIEWER_EYE_HEIGHT;
        double distanceSquared = horizontalDistanceSquared + dy * dy;
        if (distanceSquared <= 0.0001D || distanceSquared > FOCUSED_SUBJECT_DISTANCE_SQUARED) {
            return false;
        }

        double projected = dx * viewerSnapshot.lookX()
                + dy * viewerSnapshot.lookY()
                + dz * viewerSnapshot.lookZ();
        return projected > 0.0D && projected * projected >= FOCUSED_SUBJECT_DOT_SQUARED * distanceSquared;
    }

    private boolean sharesVehicleOrRiding(PlayerSnapshot viewerSnapshot, PlayerSnapshot subjectSnapshot) {
        if (viewerSnapshot.rootVehicleEntityId() > 0
                && viewerSnapshot.rootVehicleEntityId() == subjectSnapshot.rootVehicleEntityId()) {
            return true;
        }
        return viewerSnapshot.vehicleEntityId() == subjectSnapshot.entityId()
                || subjectSnapshot.vehicleEntityId() == viewerSnapshot.entityId();
    }

    private boolean recentlyTeleported(UUID playerId) {
        Long expiresAt = recentTeleportsUntil.get(playerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.nanoTime()) {
            recentTeleportsUntil.remove(playerId, expiresAt);
            return false;
        }
        return true;
    }

    private boolean isClosePvpPair(UUID viewerId, UUID subjectId, double distanceSquared) {
        return distanceSquared < CLOSE_PVP_DISTANCE_SQUARED
                && (pvpModule.isCombatTagged(viewerId) || pvpModule.isCombatTagged(subjectId));
    }

    private long correctionInterval(ThrottleMode currentMode, DistanceBand distanceBand, double pingIntervalMultiplier) {
        AdaptiveLimits limits = adaptiveLimits;
        long baseInterval = switch (currentMode) {
            case OFF -> 0L;
            case SOFT -> switch (distanceBand) {
                case NEAR -> 0L;
                case MID -> SOFT_MID_INTERVAL_NANOS;
                case FAR -> SOFT_FAR_INTERVAL_NANOS;
                case EXTREME -> SOFT_EXTREME_INTERVAL_NANOS;
            };
            case HARD -> switch (distanceBand) {
                case NEAR -> 0L;
                case MID -> HARD_MID_INTERVAL_NANOS;
                case FAR -> HARD_FAR_INTERVAL_NANOS;
                case EXTREME -> HARD_EXTREME_INTERVAL_NANOS;
            };
            case CRITICAL -> switch (distanceBand) {
                case NEAR -> 0L;
                case MID -> CRITICAL_MID_INTERVAL_NANOS;
                case FAR -> CRITICAL_FAR_INTERVAL_NANOS;
                case EXTREME -> CRITICAL_EXTREME_INTERVAL_NANOS;
            };
        };
        return distanceCappedMovementInterval(
                pingAdjustedInterval(scaledInterval(baseInterval, limits.intervalScale()), pingIntervalMultiplier),
                distanceBand);
    }

    private long limitedPacketInterval(
            PacketGroup packetGroup,
            ThrottleMode currentMode,
            DistanceBand distanceBand,
            double pingIntervalMultiplier,
            boolean focusedSubject
    ) {
        if (distanceBand == DistanceBand.NEAR || distanceBand == DistanceBand.MID) {
            return 0L;
        }
        if (focusedSubject
                && distanceBand == DistanceBand.FAR
                && (packetGroup == PacketGroup.LOOK || packetGroup == PacketGroup.ANIMATION)) {
            return 0L;
        }

        AdaptiveLimits limits = adaptiveLimits;
        long baseInterval = switch (packetGroup) {
            case NONE, MOVE -> 0L;
            case LOOK -> switch (currentMode) {
                case OFF -> 0L;
                case SOFT, HARD -> LOOK_PACKET_INTERVAL_NANOS;
                case CRITICAL -> LOOK_PACKET_INTERVAL_NANOS * 2L;
            };
            case VELOCITY -> switch (currentMode) {
                case OFF, SOFT -> 0L;
                case HARD, CRITICAL -> VELOCITY_PACKET_INTERVAL_NANOS;
            };
            case ANIMATION -> switch (currentMode) {
                case OFF -> 0L;
                case SOFT, HARD -> ANIMATION_PACKET_INTERVAL_NANOS;
                case CRITICAL -> ANIMATION_PACKET_INTERVAL_NANOS * 2L;
            };
            case STATUS -> switch (currentMode) {
                case OFF, SOFT -> 0L;
                case HARD, CRITICAL -> STATUS_PACKET_INTERVAL_NANOS;
            };
            case STATE -> currentMode == ThrottleMode.CRITICAL
                    ? STATE_PACKET_INTERVAL_NANOS
                    : 0L;
        };
        return distanceCappedLimitedPacketInterval(
                pingAdjustedInterval(scaledInterval(baseInterval, limits.intervalScale()), pingIntervalMultiplier),
                distanceBand);
    }

    private long distanceCappedMovementInterval(long interval, DistanceBand distanceBand) {
        if (interval <= 0L) {
            return 0L;
        }
        return switch (distanceBand) {
            case NEAR -> 0L;
            case MID -> Math.min(interval, MID_MOVEMENT_INTERVAL_CAP_NANOS);
            case FAR -> Math.min(interval, FAR_MOVEMENT_INTERVAL_CAP_NANOS);
            case EXTREME -> interval;
        };
    }

    private long distanceCappedLimitedPacketInterval(long interval, DistanceBand distanceBand) {
        if (interval <= 0L) {
            return 0L;
        }
        return switch (distanceBand) {
            case NEAR, MID -> 0L;
            case FAR -> Math.min(interval, FAR_LIMITED_PACKET_INTERVAL_CAP_NANOS);
            case EXTREME -> interval;
        };
    }

    private long scaledInterval(long interval, double multiplier) {
        if (interval <= 0L) {
            return 0L;
        }
        return Math.max(interval, Math.round(interval * multiplier));
    }

    private long pingAdjustedInterval(long interval, double multiplier) {
        if (interval <= 0L) {
            return 0L;
        }
        return Math.max(interval, Math.round(interval * multiplier));
    }

    private static double pingIntervalMultiplier(int pingMillis, AdaptiveLimits limits) {
        if (pingMillis >= limits.pingCriticalMillis()) {
            return PING_CRITICAL_INTERVAL_MULTIPLIER;
        }
        if (pingMillis >= limits.pingHardMillis()) {
            return PING_HARD_INTERVAL_MULTIPLIER;
        }
        if (pingMillis >= limits.pingSoftMillis()) {
            return PING_SOFT_INTERVAL_MULTIPLIER;
        }
        return 1.0D;
    }

    private int entityId(PacketSendEvent event) {
        if (!(event.getPacketType() instanceof PacketType.Play.Server type)) {
            return -1;
        }

        return switch (type) {
            case ENTITY_RELATIVE_MOVE -> new WrapperPlayServerEntityRelativeMove(event).getEntityId();
            case ENTITY_RELATIVE_MOVE_AND_ROTATION ->
                    new WrapperPlayServerEntityRelativeMoveAndRotation(event).getEntityId();
            case ENTITY_ROTATION -> new WrapperPlayServerEntityRotation(event).getEntityId();
            case ENTITY_MOVEMENT -> new WrapperPlayServerEntityMovement(event).getEntityId();
            case ENTITY_HEAD_LOOK -> new WrapperPlayServerEntityHeadLook(event).getEntityId();
            case ENTITY_VELOCITY -> new WrapperPlayServerEntityVelocity(event).getEntityId();
            case ENTITY_ANIMATION -> new WrapperPlayServerEntityAnimation(event).getEntityId();
            case HURT_ANIMATION -> new WrapperPlayServerHurtAnimation(event).getEntityId();
            case ENTITY_STATUS -> new WrapperPlayServerEntityStatus(event).getEntityId();
            case ENTITY_EFFECT -> new WrapperPlayServerEntityEffect(event).getEntityId();
            case REMOVE_ENTITY_EFFECT -> new WrapperPlayServerRemoveEntityEffect(event).getEntityId();
            case ENTITY_EQUIPMENT -> new WrapperPlayServerEntityEquipment(event).getEntityId();
            case ENTITY_METADATA -> new WrapperPlayServerEntityMetadata(event).getEntityId();
            case UPDATE_ATTRIBUTES -> new WrapperPlayServerUpdateAttributes(event).getEntityId();
            default -> -1;
        };
    }

    private PacketGroup packetGroup(PacketTypeCommon type) {
        if (!(type instanceof PacketType.Play.Server serverType)) {
            return PacketGroup.NONE;
        }

        return switch (serverType) {
            case ENTITY_RELATIVE_MOVE,
                 ENTITY_RELATIVE_MOVE_AND_ROTATION,
                 ENTITY_MOVEMENT -> PacketGroup.MOVE;
            case ENTITY_ROTATION,
                 ENTITY_HEAD_LOOK -> PacketGroup.LOOK;
            case ENTITY_VELOCITY -> PacketGroup.VELOCITY;
            case ENTITY_ANIMATION,
                 HURT_ANIMATION -> PacketGroup.ANIMATION;
            case ENTITY_STATUS -> PacketGroup.STATUS;
            case ENTITY_EFFECT,
                 REMOVE_ENTITY_EFFECT,
                 ENTITY_EQUIPMENT,
                 ENTITY_METADATA,
                 UPDATE_ATTRIBUTES -> PacketGroup.STATE;
            default -> PacketGroup.NONE;
        };
    }

    private String packetName(PacketTypeCommon type) {
        return type instanceof Enum<?> enumType ? enumType.name() : String.valueOf(type);
    }

    private void recordOutbound(PacketTypeCommon packetType, UUID viewerId, int bytes, boolean controlledPacket) {
        outboundBytes.addAndGet(bytes);
        if (!controlledPacket) {
            return;
        }

        controlledOutboundBytes.addAndGet(bytes);
        recordPacketDemand(packetType, bytes);

        if (viewerId != null) {
            AtomicLong playerBytes = playerOutboundBytes.get(viewerId);
            if (playerBytes == null) {
                playerBytes = playerOutboundBytes.computeIfAbsent(viewerId, ignored -> new AtomicLong());
            }
            playerBytes.addAndGet(bytes);
        }
    }

    private void recordPacketDemand(PacketTypeCommon packetType, int bytes) {
        if (bytes > 0) {
            packetBytes.computeIfAbsent(packetType, ignored -> new LongAdder()).add(bytes);
        }
    }

    private int sendNetwork(CommandSender sender, NetworkView view) {
        sender.sendMessage(networkReport(view));
        return Command.SINGLE_SUCCESS;
    }

    private Component networkReport(NetworkView view) {
        Component report = Component.text("Network", NamedTextColor.AQUA)
                .append(Component.text(" packet throttle", NamedTextColor.DARK_AQUA))
                .append(Component.newline())
                .append(networkNavigation(view));

        if (view == NetworkView.STATUS || view == NetworkView.ALL) {
            report = report.append(Component.newline()).append(statusSection());
        }
        if (view == NetworkView.PLAYERS || view == NetworkView.ALL) {
            report = report.append(Component.newline()).append(playersSection());
        }
        if (view == NetworkView.PACKETS || view == NetworkView.ALL) {
            report = report.append(Component.newline()).append(packetsSection());
        }
        if (view == NetworkView.LIMITS || view == NetworkView.ALL) {
            report = report.append(Component.newline()).append(limitsSection());
        }
        if (view == NetworkView.RULES || view == NetworkView.ALL) {
            report = report.append(Component.newline()).append(rulesSection());
        }
        if (view == NetworkView.HELP) {
            report = report.append(Component.newline()).append(helpSection());
        }
        return report;
    }

    private Component networkNavigation(NetworkView activeView) {
        return Component.text("views: ", NamedTextColor.DARK_GRAY)
                .append(commandButton("status", "/network status", activeView == NetworkView.STATUS,
                        "Live global throttle state."))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(commandButton("players", "/network players", activeView == NetworkView.PLAYERS,
                        "Per-player bandwidth, ping, and AFK pressure."))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(commandButton("packets", "/network packets", activeView == NetworkView.PACKETS,
                        "Packet cancellation counters and hot packet types."))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(commandButton("limits", "/network limits", activeView == NetworkView.LIMITS,
                        "Adaptive bandwidth, ping, and player thresholds."))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(commandButton("rules", "/network rules", activeView == NetworkView.RULES,
                        "Which packets and scenes are throttled."))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(commandButton("all", "/network all", activeView == NetworkView.ALL,
                        "Everything in one report."))
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(commandButton("help", "/network help", activeView == NetworkView.HELP,
                        "Clickable command reference."));
    }

    private Component statusSection() {
        AdaptiveLimits limits = adaptiveLimits;
        double mspt = performanceModule.effectiveMspt();
        double outboundRatio = smoothedBytesPerSecond / limits.serverLimitBytesPerSecond();
        return sectionTitle("Status", "Current global network throttle state.")
                .append(metricLine("mode", modeComponent(mode),
                        "Global mode from server outbound demand, system transmit, and MSPT pressure."))
                .append(metricLine("pressure",
                        Component.text(String.format(Locale.ROOT, "%.0f%%", limits.pressureScore() * 100.0D),
                                pressureColor(modeForPressure(limits.pressureScore()))),
                        "Adaptive pressure score from bandwidth demand, delivered traffic, system transmit, and MSPT. Queue backlog and cancellation ratio are handled separately."))
                .append(metricLine("interval pressure",
                        Component.text(String.format(Locale.ROOT, "%.0f%%", limits.intervalPressureScore() * 100.0D),
                                pressureColor(modeForPressure(limits.intervalPressureScore()))),
                        "Pressure used for packet spacing. It also reacts to correction backlog and cancellation ratio, but does not lower the capacity estimate."))
                .append(metricLine("armed", booleanComponent(throttleArmed),
                        "Armed means packet inspection is active because global, player, ping, or AFK pressure exists."))
                .append(metricLine("mspt", Component.text(String.format(Locale.ROOT, "%.1f", mspt),
                                msptColor(mspt)),
                        "Effective MSPT from PerformanceModule. Network throttling also reacts to server tick pressure."))
                .append(metricLine("plugin sent",
                        Component.text(formatThroughput(smoothedDeliveredBytesPerSecond), NamedTextColor.AQUA),
                        "Smoothed PacketEvents outbound bytes that were actually allowed through by this module. This is still logical packet-buffer size, not Linux eth0 bytes."))
                .append(metricLine("plugin demand",
                        Component.text(formatThroughput(smoothedBytesPerSecond) + "  "
                                + formatPercent(outboundRatio), pressureColor(modeForPressure(outboundRatio))),
                        "Smoothed demand for packets this module can throttle: allowed controlled packets plus packets cancelled by this module. This is the value used for global pressure."))
                .append(metricLine("cancel ratio",
                        Component.text(formatPercent(limits.cancelRatio()), cancelRatioColor(limits.cancelRatio())),
                        "Share of controlled packet demand cancelled in the last adaptive sample."))
                .append(metricLine("server inbound", Component.text(formatThroughput(smoothedInboundBytesPerSecond),
                                NamedTextColor.GRAY),
                        "Smoothed inbound bytes observed by PacketEvents."))
                .append(metricLine("system tx", Component.text(formatSystemTransmit(), NamedTextColor.GRAY),
                        "Linux /proc/net/dev transmit rate. This is the closest value to Spark's system network tx and includes compression/TCP behavior."))
                .append(metricLine("online players", Component.text(Integer.toString(playerSnapshots.size()), NamedTextColor.AQUA),
                        "Players currently tracked in the network snapshot cache."))
                .append(metricLine("afk pressure", booleanComponent(afkThrottleArmed),
                        "AFK pressure arms throttling even when global bandwidth is still healthy."));
    }

    private Component playersSection() {
        return sectionTitle("Players", "Per-viewer pressure, ping, and AFK diagnostics.")
                .append(metricLine("top viewers", Component.text(lastTopPlayerSummary, NamedTextColor.GRAY),
                        "Top receiving viewers by smoothed controlled packet demand. Chunk/data packets are excluded because this module cannot throttle them."))
                .append(metricLine("high ping", Component.text(lastHighPingSummary, NamedTextColor.GRAY),
                        "Players whose ping triggers additional per-viewer throttling."))
                .append(metricLine("afk", Component.text(lastAfkSummary, NamedTextColor.GRAY),
                        "AFK viewers or subjects can be throttled more aggressively at distance."))
                .append(metricLine("player pressure", Component.text(playerPressureSummary(), NamedTextColor.GRAY),
                        "Count of players currently above per-player outbound thresholds."));
    }

    private Component packetsSection() {
        return sectionTitle("Packets", "Packet groups, cancellation counters, and correction queues.")
                .append(metricLine("window throttled",
                        Component.text(throttledPackets.get() + " packets, " + formatBytes(throttledBytes.get()),
                                NamedTextColor.YELLOW),
                        "Packets and bytes cancelled in the current sampling window. These counters reset every bandwidth sample."))
                .append(metricLine("total throttled",
                        Component.text(throttledPacketsTotal.sum() + " packets, " + formatBytes(throttledBytesTotal.sum()),
                                NamedTextColor.YELLOW),
                        "Lifetime packet and byte cancellations since module enable."))
                .append(metricLine("pending corrections", Component.text(Integer.toString(pendingCorrections.size()),
                                pendingCorrections.isEmpty() ? NamedTextColor.GRAY : NamedTextColor.AQUA),
                        "Movement packets cancelled for far subjects are repaired by queued absolute position corrections."))
                .append(metricLine("rate-limit keys", Component.text(Integer.toString(packetRateLimits.size()),
                                packetRateLimits.isEmpty() ? NamedTextColor.GRAY : NamedTextColor.AQUA),
                        "Per viewer-subject-packet limiter state entries. They expire after inactivity."))
                .append(metricLine("top packets", Component.text(lastTopPacketSummary, NamedTextColor.GRAY),
                        "Largest controlled outbound packet types per second in the last adaptive sample."))
                .append(metricLine("groups", Component.text(throttledGroupSummary(), NamedTextColor.GRAY),
                        "Cancellation totals grouped by movement, look, velocity, animation, status, and state packets."))
                .append(metricLine("distance", Component.text(throttledDistanceSummary(), NamedTextColor.GRAY),
                        "Cancellation totals grouped by subject distance band."));
    }

    private Component limitsSection() {
        AdaptiveLimits limits = adaptiveLimits;
        return sectionTitle("Limits", "Adaptive thresholds currently used by this module.")
                .append(metricLine("server demand limit", Component.text(formatThroughput(limits.serverLimitBytesPerSecond()),
                                NamedTextColor.AQUA),
                        "Adaptive global PacketEvents demand target. It grows with healthy observed demand and shrinks only on external capacity pressure."))
                .append(metricLine("server soft", Component.text(formatThroughput(limits.serverLimitBytesPerSecond()
                                * SOFT_PRESSURE_RATIO), NamedTextColor.YELLOW),
                        "Global SOFT threshold."))
                .append(metricLine("server hard", Component.text(formatThroughput(limits.serverLimitBytesPerSecond()
                                * HARD_PRESSURE_RATIO), NamedTextColor.GOLD),
                        "Global HARD threshold."))
                .append(metricLine("server critical", Component.text(formatThroughput(limits.serverLimitBytesPerSecond()
                                * CRITICAL_PRESSURE_RATIO), NamedTextColor.RED),
                        "Global CRITICAL threshold."))
                .append(metricLine("player soft", Component.text(formatThroughput(limits.playerSoftBytesPerSecond()),
                                NamedTextColor.YELLOW),
                        "Adaptive per-viewer receive-side SOFT threshold from fair share and player demand percentiles."))
                .append(metricLine("player hard", Component.text(formatThroughput(limits.playerHardBytesPerSecond()),
                                NamedTextColor.GOLD),
                        "Adaptive per-viewer receive-side HARD threshold."))
                .append(metricLine("player critical", Component.text(formatThroughput(limits.playerCriticalBytesPerSecond()),
                                NamedTextColor.RED),
                        "Adaptive per-viewer receive-side CRITICAL threshold."))
                .append(metricLine("player demand p75/p95",
                        Component.text(formatThroughput(limits.playerP75BytesPerSecond()) + " / "
                                + formatThroughput(limits.playerP95BytesPerSecond()), NamedTextColor.GRAY),
                        "Smoothed controlled packet demand percentiles used by the adaptive controller."))
                .append(metricLine("ping", Component.text(limits.pingSoftMillis() + "/" + limits.pingHardMillis() + "/"
                                + limits.pingCriticalMillis() + " ms", NamedTextColor.GRAY),
                        "Adaptive ping thresholds for SOFT, HARD, and CRITICAL per-viewer throttling."))
                .append(metricLine("ping p90", Component.text(String.format(Locale.ROOT, "%.0f ms", limits.pingP90Millis()),
                                NamedTextColor.GRAY),
                        "Current p90 ping in the snapshot cache."))
                .append(metricLine("interval scale", Component.text("x" + formatMultiplier(limits.intervalScale()),
                                NamedTextColor.GRAY),
                        "Adaptive multiplier applied to movement and low-priority packet intervals from interval pressure."))
                .append(metricLine("recent teleport", Component.text(RECENT_TELEPORT_SUPPRESS_MILLIS + " ms",
                                NamedTextColor.GRAY),
                        "Viewer or subject teleports suppress throttling briefly to avoid bad visual state."))
                .append(metricLine("distance bands", Component.text("48 / 96 / 160 blocks", NamedTextColor.GRAY),
                        "Near, mid, far, and extreme distance boundaries. Closer or focused subjects automatically reduce or skip throttling."))
                .append(metricLine("movement caps",
                        Component.text(MID_MOVEMENT_INTERVAL_CAP_NANOS / 1_000_000L + " / "
                                + FAR_MOVEMENT_INTERVAL_CAP_NANOS / 1_000_000L + " ms", NamedTextColor.GRAY),
                        "Maximum movement correction spacing for mid and far subjects after adaptive and ping scaling. Extreme subjects are uncapped."))
                .append(metricLine("packet cap",
                        Component.text(FAR_LIMITED_PACKET_INTERVAL_CAP_NANOS / 1_000_000L + " ms far",
                                NamedTextColor.GRAY),
                        "Maximum low-priority packet limiter spacing for far subjects after adaptive and ping scaling. Extreme subjects are uncapped."))
                .append(metricLine("perception hold",
                        Component.text(FAST_MOVEMENT_HOLD_SNAPSHOTS + " movement, "
                                + FAST_LOOK_HOLD_SNAPSHOTS + " look snapshots", NamedTextColor.GRAY),
                        "Fast movement and fast camera-turn protection stay active for this many snapshot refreshes before decaying."))
                .append(metricLine("correction budget", Component.text(limits.maxCorrectionsPerTick() + "/tick",
                                NamedTextColor.GRAY),
                        "Adaptive maximum absolute movement corrections flushed back to viewers per tick."))
                .append(metricLine("rate expiry", Component.text(RATE_LIMIT_EXPIRY_NANOS / 1_000_000_000L + " s",
                                NamedTextColor.GRAY),
                        "Inactive per-packet limiter state is removed after this many seconds."));
    }

    private Component rulesSection() {
        return sectionTitle("Rules", "Runtime behavior used when throttling is armed.")
                .append(metricLine("bypass", Component.text("disabled", NamedTextColor.YELLOW),
                        "Helper, admin, and spectator viewers are still subject to network throttling."))
                .append(metricLine("accounting", Component.text("viewer / logical bytes",
                                NamedTextColor.GRAY),
                        "Per-player numbers are attributed to the receiving viewer and only count packets this module can throttle. Global sent still uses PacketEvents logical buffer bytes, not eth0 bytes."))
                .append(metricLine("sample", Component.text(BANDWIDTH_SAMPLE_PERIOD_TICKS + " ticks",
                                NamedTextColor.GRAY),
                        "Bandwidth, player, ping, interval, and correction budgets are recalculated by the sampler; packet send handling only reads the current snapshot."))
                .append(metricLine("movement", Component.text(throttleArmed ? "armed" : "standby",
                                throttleArmed ? NamedTextColor.YELLOW : NamedTextColor.GRAY),
                        "Relative movement is distance-aware: close subjects are protected, far subjects can be cancelled, then repaired with bounded absolute position corrections when throttled or released."))
                .append(metricLine("packet groups",
                        Component.text("6 controlled", NamedTextColor.GRAY),
                        "Low-priority packet groups receive longer per-packet intervals as pressure rises, but near and mid subjects are not rate-limited."))
                .append(metricLine("focus guard", Component.text("256 blocks / 44 deg", NamedTextColor.GREEN),
                        "Active viewer yaw and pitch from cached snapshots reduce throttling for subjects currently in the viewer's focus cone, including far look and animation packets."))
                .append(metricLine("motion guard", Component.text("fast subjects <160 blocks", NamedTextColor.GREEN),
                        "Snapshot-to-snapshot movement reduces throttling for fast-moving subjects in the far distance band for active viewers."))
                .append(metricLine("look guard", Component.text("fast camera turns <160 blocks", NamedTextColor.GREEN),
                        "Fast viewer yaw or pitch changes briefly reduce non-critical far-distance throttling so focus snapshots do not feel stale while scanning."))
                .append(metricLine("ping scaling", Component.text("x" + formatMultiplier(PING_SOFT_INTERVAL_MULTIPLIER)
                                + " / x" + formatMultiplier(PING_HARD_INTERVAL_MULTIPLIER)
                                + " / x" + formatMultiplier(PING_CRITICAL_INTERVAL_MULTIPLIER), NamedTextColor.GRAY),
                        "Viewer ping increases packet spacing at soft, hard, and critical ping thresholds."))
                .append(metricLine("afk scaling", Component.text("viewer x" + formatMultiplier(AFK_VIEWER_INTERVAL_MULTIPLIER)
                                        + ", subject x" + formatMultiplier(AFK_SUBJECT_INTERVAL_MULTIPLIER),
                                NamedTextColor.GRAY),
                        "AFK viewers and AFK subjects receive wider intervals at distance."))
                .append(metricLine("guards", Component.text("PvP / vehicle / teleport",
                                NamedTextColor.GREEN),
                        "Critical interaction scenes avoid the most disruptive distance throttling."))
                .append(metricLine("world bias", Component.text("lobby -1, resource +1",
                                NamedTextColor.GRAY),
                        "World names adjust throttle mode up or down for known scene types."));
    }

    private Component helpSection() {
        return sectionTitle("Commands", "Click a command to run it.")
                .append(commandLine("/network", "status"))
                .append(commandLine("/network players", "players"))
                .append(commandLine("/network packets", "packets"))
                .append(commandLine("/network limits", "limits"))
                .append(commandLine("/network rules", "rules"))
                .append(commandLine("/network all", "all"));
    }

    private Component sectionTitle(String title, String hover) {
        return Component.newline()
                .append(Component.text(title, NamedTextColor.AQUA)
                        .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY))));
    }

    private Component metricLine(String label, Component value, String hover) {
        return Component.newline()
                .append(Component.text("  " + label + ": ", NamedTextColor.DARK_GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY))))
                .append(value.hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY))));
    }

    private Component commandButton(String label, String command, boolean active, String hover) {
        NamedTextColor color = active ? NamedTextColor.GREEN : NamedTextColor.AQUA;
        return Component.text("[" + label + "]", color, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text("Run " + command + "\n" + hover, NamedTextColor.GRAY)));
    }

    private Component commandLine(String command, String description) {
        return Component.newline()
                .append(Component.text("  " + command, NamedTextColor.AQUA, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.runCommand(command))
                        .hoverEvent(HoverEvent.showText(Component.text("Run " + command, NamedTextColor.GRAY))))
                .append(Component.text(" - " + description, NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text(description, NamedTextColor.GRAY))));
    }

    private Component modeComponent(ThrottleMode throttleMode) {
        return Component.text(throttleMode.name(), modeColor(throttleMode))
                .hoverEvent(HoverEvent.showText(Component.text(modeHover(throttleMode), NamedTextColor.GRAY)));
    }

    private Component booleanComponent(boolean value) {
        return Component.text(Boolean.toString(value), value ? NamedTextColor.GREEN : NamedTextColor.GRAY);
    }

    private String playerPressureSummary() {
        int soft = 0;
        int hard = 0;
        int critical = 0;
        for (ThrottleMode playerMode : playerPressureModes.values()) {
            switch (playerMode) {
                case SOFT -> soft++;
                case HARD -> hard++;
                case CRITICAL -> critical++;
                case OFF -> {
                }
            }
        }
        if (soft == 0 && hard == 0 && critical == 0) {
            return "none";
        }
        return "soft=" + soft + ", hard=" + hard + ", critical=" + critical;
    }

    private String formatPercent(double ratio) {
        return String.format(Locale.ROOT, "(%.0f%%)", ratio * 100.0D);
    }

    private String formatThroughput(double bytesPerSecond) {
        return formatBytes(bytesPerSecond) + "/s, " + formatBits(bytesPerSecond);
    }

    private String formatBytes(double bytes) {
        double abs = Math.abs(bytes);
        if (abs >= 1024.0D * 1024.0D) {
            return String.format(Locale.ROOT, "%.2f MiB", bytes / 1024.0D / 1024.0D);
        }
        if (abs >= 1024.0D) {
            return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0D);
        }
        return String.format(Locale.ROOT, "%.0f B", bytes);
    }

    private String formatBits(double bytesPerSecond) {
        double bitsPerSecond = bytesPerSecond * 8.0D;
        if (Math.abs(bitsPerSecond) >= 1_000_000.0D) {
            return String.format(Locale.ROOT, "%.2f Mbps", bitsPerSecond / 1_000_000.0D);
        }
        if (Math.abs(bitsPerSecond) >= 1_000.0D) {
            return String.format(Locale.ROOT, "%.0f Kbps", bitsPerSecond / 1_000.0D);
        }
        return String.format(Locale.ROOT, "%.0f bps", bitsPerSecond);
    }

    private String formatMultiplier(double multiplier) {
        return String.format(Locale.ROOT, "%.2f", multiplier);
    }

    private NamedTextColor modeColor(ThrottleMode throttleMode) {
        return switch (throttleMode) {
            case OFF -> NamedTextColor.GRAY;
            case SOFT -> NamedTextColor.YELLOW;
            case HARD -> NamedTextColor.GOLD;
            case CRITICAL -> NamedTextColor.RED;
        };
    }

    private NamedTextColor pressureColor(ThrottleMode throttleMode) {
        return modeColor(throttleMode);
    }

    private NamedTextColor cancelRatioColor(double ratio) {
        if (ratio >= 0.25D) {
            return NamedTextColor.RED;
        }
        if (ratio >= 0.10D) {
            return NamedTextColor.GOLD;
        }
        if (ratio >= 0.03D) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.GRAY;
    }

    private NamedTextColor msptColor(double mspt) {
        if (mspt >= MSPT_CRITICAL_PRESSURE) {
            return NamedTextColor.RED;
        }
        if (mspt >= MSPT_HARD_PRESSURE) {
            return NamedTextColor.GOLD;
        }
        if (mspt >= MSPT_SOFT_PRESSURE) {
            return NamedTextColor.YELLOW;
        }
        return NamedTextColor.GREEN;
    }

    private String modeHover(ThrottleMode throttleMode) {
        return switch (throttleMode) {
            case OFF -> "No global network throttle pressure.";
            case SOFT -> "Soft mode: far movement and some low-priority packets are reduced.";
            case HARD -> "Hard mode: stronger distance-based packet limiting.";
            case CRITICAL -> "Critical mode: aggressive distance and packet group throttling.";
        };
    }

    private String throttledGroupSummary() {
        StringBuilder builder = new StringBuilder();
        for (PacketGroup group : PacketGroup.values()) {
            if (group == PacketGroup.NONE) {
                continue;
            }
            long value = throttledByGroup.get(group).sum();
            if (value <= 0L) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(group.name().toLowerCase(Locale.ROOT)).append('=').append(value);
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private String throttledDistanceSummary() {
        StringBuilder builder = new StringBuilder();
        for (DistanceBand band : DistanceBand.values()) {
            long value = throttledByDistance.get(band).sum();
            if (value <= 0L) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append(band.name().toLowerCase(Locale.ROOT)).append('=').append(value);
        }
        return builder.isEmpty() ? "none" : builder.toString();
    }

    private enum NetworkView {
        STATUS,
        PLAYERS,
        PACKETS,
        LIMITS,
        RULES,
        HELP,
        ALL
    }

    private enum ThrottleMode {
        OFF,
        SOFT,
        HARD,
        CRITICAL
    }

    private enum PacketGroup {
        NONE,
        MOVE,
        LOOK,
        VELOCITY,
        ANIMATION,
        STATUS,
        STATE
    }

    private enum DistanceBand {
        NEAR,
        MID,
        FAR,
        EXTREME;

        static DistanceBand from(double distanceSquared) {
            if (distanceSquared < NEAR_DISTANCE_SQUARED) {
                return NEAR;
            }
            if (distanceSquared < MID_DISTANCE_SQUARED) {
                return MID;
            }
            if (distanceSquared < FAR_DISTANCE_SQUARED) {
                return FAR;
            }
            return EXTREME;
        }
    }

    private record AdaptiveLimits(long serverLimitBytesPerSecond,
                                  long playerSoftBytesPerSecond,
                                  long playerHardBytesPerSecond,
                                  long playerCriticalBytesPerSecond,
                                  int pingSoftMillis,
                                  int pingHardMillis,
                                  int pingCriticalMillis,
                                  int maxCorrectionsPerTick,
                                  double intervalScale,
                                  double pressureScore,
                                  double intervalPressureScore,
                                  double cancelRatio,
                                  double playerP75BytesPerSecond,
                                  double playerP95BytesPerSecond,
                                  double pingP90Millis) {
        static AdaptiveLimits defaults() {
            return new AdaptiveLimits(
                    DEFAULT_SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND,
                    DEFAULT_PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND,
                    DEFAULT_PLAYER_OUTBOUND_HARD_BYTES_PER_SECOND,
                    DEFAULT_PLAYER_OUTBOUND_CRITICAL_BYTES_PER_SECOND,
                    PING_SOFT_MILLIS,
                    PING_HARD_MILLIS,
                    PING_CRITICAL_MILLIS,
                    MAX_CORRECTIONS_PER_TICK,
                    1.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D);
        }
    }

    private record ThrottleKey(UUID viewerId, UUID subjectId) {
    }

    private record PacketLimitKey(UUID viewerId, UUID subjectId, PacketTypeCommon packetType) {
    }

    private record ThrottleContext(UUID subjectId, DistanceBand distanceBand, double viewerPingIntervalMultiplier,
                                   ThrottleMode mode, boolean focusedSubject) {
    }

    private record PacketVolume(PacketTypeCommon type, long bytes) {
    }

    private record PlayerVolume(UUID playerId, double bytes) {
    }

    private record PlayerPing(UUID playerId, int pingMillis) {
    }

    private record PlayerDemandStats(double p75BytesPerSecond, double p95BytesPerSecond) {
    }

    private record PingStats(double p75Millis, double p90Millis, double p95Millis) {
    }

    private record SnapshotPressure(boolean pingPressure, boolean afkPressure) {
    }

    private record DelayedCorrection(ThrottleKey key, long dueNanos) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(dueNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(@NotNull Delayed other) {
            if (other instanceof DelayedCorrection correction) {
                return Long.compare(dueNanos, correction.dueNanos);
            }
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }

    private record PlayerSnapshot(UUID worldId, String worldName, int entityId, int vehicleEntityId,
                                  int rootVehicleEntityId,
                                  boolean afk,
                                  int pingMillis, ThrottleMode pingMode, double pingIntervalMultiplier,
                                  double x, double y, double z, float yaw, float pitch,
                                  double lookX, double lookY, double lookZ,
                                  int fastMovementHoldSnapshots,
                                  int fastLookHoldSnapshots) {
        static PlayerSnapshot from(
                Player player,
                AfkService afkService,
                AdaptiveLimits limits,
                PlayerSnapshot previousSnapshot
        ) {
            Location location = player.getLocation();
            World world = location.getWorld();
            Entity vehicle = player.getVehicle();
            int vehicleEntityId = vehicle == null ? -1 : vehicle.getEntityId();
            int rootVehicleEntityId = rootVehicleEntityId(player);
            int pingMillis = Math.max(0, player.getPing());
            float yaw = location.getYaw();
            float pitch = location.getPitch();
            double yawRadians = Math.toRadians(yaw);
            double pitchRadians = Math.toRadians(pitch);
            double pitchCos = Math.cos(pitchRadians);
            return new PlayerSnapshot(
                    world.getUID(),
                    world.getName(),
                    player.getEntityId(),
                    vehicleEntityId,
                    rootVehicleEntityId,
                    afkService.isAfk(player.getUniqueId()),
                    pingMillis,
                    NetworkThrottleModule.pingMode(pingMillis, limits),
                    NetworkThrottleModule.pingIntervalMultiplier(pingMillis, limits),
                    location.x(),
                    location.y(),
                    location.z(),
                    yaw,
                    pitch,
                    -pitchCos * Math.sin(yawRadians),
                    -Math.sin(pitchRadians),
                    pitchCos * Math.cos(yawRadians),
                    fastMovementHoldSnapshots(location, world, player.getEntityId(), previousSnapshot),
                    fastLookHoldSnapshots(yaw, pitch, world, player.getEntityId(), previousSnapshot));
        }

        static int rootVehicleEntityId(Player player) {
            Entity current = player.getVehicle();
            if (current == null) {
                return -1;
            }
            Entity next = current.getVehicle();
            while (next != null) {
                current = next;
                next = current.getVehicle();
            }
            return current.getEntityId();
        }

        static int fastMovementHoldSnapshots(
                Location location,
                World world,
                int entityId,
                PlayerSnapshot previousSnapshot
        ) {
            if (previousSnapshot == null
                    || previousSnapshot.entityId() != entityId
                    || !previousSnapshot.worldId().equals(world.getUID())) {
                return 0;
            }

            double dx = location.x() - previousSnapshot.x();
            double dy = location.y() - previousSnapshot.y();
            double dz = location.z() - previousSnapshot.z();
            if (dx * dx + dy * dy + dz * dz >= FAST_MOVEMENT_DELTA_SQUARED
                    || Math.abs(dy) >= FAST_VERTICAL_MOVEMENT_DELTA) {
                return FAST_MOVEMENT_HOLD_SNAPSHOTS;
            }
            return Math.max(0, previousSnapshot.fastMovementHoldSnapshots() - 1);
        }

        boolean fastMoving() {
            return fastMovementHoldSnapshots > 0;
        }

        static int fastLookHoldSnapshots(
                float yaw,
                float pitch,
                World world,
                int entityId,
                PlayerSnapshot previousSnapshot
        ) {
            if (previousSnapshot == null
                    || previousSnapshot.entityId() != entityId
                    || !previousSnapshot.worldId().equals(world.getUID())) {
                return 0;
            }

            float yawDelta = Math.abs(wrapDegrees(yaw - previousSnapshot.yaw()));
            float pitchDelta = Math.abs(pitch - previousSnapshot.pitch());
            if (yawDelta >= FAST_LOOK_DELTA_DEGREES || pitchDelta >= FAST_LOOK_DELTA_DEGREES) {
                return FAST_LOOK_HOLD_SNAPSHOTS;
            }
            return Math.max(0, previousSnapshot.fastLookHoldSnapshots() - 1);
        }

        static float wrapDegrees(float value) {
            float wrapped = value % 360.0F;
            if (wrapped >= 180.0F) {
                wrapped -= 360.0F;
            }
            if (wrapped < -180.0F) {
                wrapped += 360.0F;
            }
            return wrapped;
        }

        boolean fastLooking() {
            return fastLookHoldSnapshots > 0;
        }

        double distanceSquared(PlayerSnapshot other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private static class CorrectionState {
        private final AtomicBoolean dirty = new AtomicBoolean(true);
        private volatile long lastSeenNanos = System.nanoTime();
        private volatile long nextSendNanos;

        CorrectionState() {
        }

        long nextSendNanos() {
            return nextSendNanos;
        }

        void setNextSendNanos(long nextSendNanos) {
            this.nextSendNanos = nextSendNanos;
        }

        void markDirty() {
            lastSeenNanos = System.nanoTime();
            dirty.set(true);
        }

        boolean consumeDirty() {
            return dirty.getAndSet(false);
        }

        boolean isClean() {
            return !dirty.get();
        }

        boolean isDirty() {
            return dirty.get();
        }

        long lastSeenNanos() {
            return lastSeenNanos;
        }
    }

    private static class RateState {
        private final AtomicLong nextAllowedNanos = new AtomicLong();
        private volatile long lastSeenNanos = System.nanoTime();

        long lastSeenNanos() {
            return lastSeenNanos;
        }

        boolean tryAcquire(long now, long interval) {
            lastSeenNanos = now;
            long nextAllowed = nextAllowedNanos.get();
            if (now < nextAllowed) {
                return false;
            }
            return nextAllowedNanos.compareAndSet(nextAllowed, now + interval);
        }
    }

    private class NetworkPacketListener extends PacketListenerAbstract {

        NetworkPacketListener() {
            super(PacketListenerPriority.HIGHEST);
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
            if (event.isCancelled()) {
                return;
            }

            int bytes = ByteBufHelper.readableBytes(event.getByteBuf());
            UUID viewerId = event.getUser().getUUID();
            PacketTypeCommon packetType = event.getPacketType();
            PacketGroup packetGroup = packetGroup(packetType);

            if (event.getConnectionState() == ConnectionState.PLAY && throttleArmed) {
                if (packetGroup != PacketGroup.NONE) {
                    PlayerSnapshot viewerSnapshot = viewerSnapshot(viewerId);
                    if (viewerSnapshot != null) {
                        ThrottleMode viewerDirectMode = effectiveMode(viewerId, viewerSnapshot);
                        boolean afkSubjectOnly = viewerDirectMode == ThrottleMode.OFF;
                        if (viewerDirectMode != ThrottleMode.OFF || afkThrottleArmed) {
                            int subjectEntityId = entityId(event);
                            if (subjectEntityId > 0) {
                                ThrottleContext context = throttleContext(
                                        viewerId, viewerSnapshot, viewerDirectMode, afkSubjectOnly, subjectEntityId);
                                if (context != null) {
                                    if (packetGroup == PacketGroup.MOVE) {
                                        if (shouldThrottleMovement(viewerId, context)) {
                                            cancel(event, viewerId, bytes, packetGroup, context.distanceBand());
                                            return;
                                        }
                                    } else if (shouldThrottleLimitedPacket(packetType, packetGroup, viewerId, context)) {
                                        cancel(event, viewerId, bytes, packetGroup, context.distanceBand());
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            recordOutbound(packetType, viewerId, bytes, packetGroup != PacketGroup.NONE);
        }

        @Override
        public void onPacketReceive(PacketReceiveEvent event) {
            inboundBytes.addAndGet(ByteBufHelper.readableBytes(event.getByteBuf()));
        }

        private void cancel(PacketSendEvent event, UUID viewerId, int bytes, PacketGroup packetGroup, DistanceBand distanceBand) {
            event.setCancelled(true);
            throttledPackets.incrementAndGet();
            throttledBytes.addAndGet(bytes);
            throttledPacketsTotal.increment();
            throttledBytesTotal.add(bytes);
            throttledByGroup.get(packetGroup).increment();
            throttledByDistance.get(distanceBand).increment();
            recordPacketDemand(event.getPacketType(), bytes);
            if (viewerId != null) {
                AtomicLong playerBytes = playerOutboundBytes.get(viewerId);
                if (playerBytes == null) {
                    playerBytes = playerOutboundBytes.computeIfAbsent(viewerId, ignored -> new AtomicLong());
                }
                playerBytes.addAndGet(bytes);
            }
        }
    }
}
