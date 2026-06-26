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
import org.encinet.mik.module.player.PvpModule;

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

    private static final long SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND = 12L * 1024L * 1024L;
    private static final long PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND = 400L * 1024L;
    private static final long PLAYER_OUTBOUND_HARD_BYTES_PER_SECOND = 800L * 1024L;
    private static final long PLAYER_OUTBOUND_CRITICAL_BYTES_PER_SECOND = 1_200L * 1024L;
    private static final int MAX_CORRECTIONS_PER_TICK = 96;
    private static final int MAX_CORRECTION_INSPECTIONS_PER_TICK = MAX_CORRECTIONS_PER_TICK * 4;
    private static final long RATE_LIMIT_EXPIRY_NANOS = 60_000_000_000L;
    private static final double MSPT_SOFT_PRESSURE = 45.0D;
    private static final double MSPT_HARD_PRESSURE = 65.0D;
    private static final double MSPT_CRITICAL_PRESSURE = 90.0D;
    private static final double SOFT_PRESSURE_RATIO = 0.70D;
    private static final double HARD_PRESSURE_RATIO = 0.85D;
    private static final double CRITICAL_PRESSURE_RATIO = 0.95D;
    private static final double EWMA_WEIGHT = 0.35D;
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

    private static final long SOFT_MID_INTERVAL_NANOS = 100_000_000L;
    private static final long SOFT_FAR_INTERVAL_NANOS = 200_000_000L;
    private static final long SOFT_EXTREME_INTERVAL_NANOS = 500_000_000L;
    private static final long HARD_MID_INTERVAL_NANOS = 200_000_000L;
    private static final long HARD_FAR_INTERVAL_NANOS = 500_000_000L;
    private static final long HARD_EXTREME_INTERVAL_NANOS = 1_000_000_000L;
    private static final long CRITICAL_MID_INTERVAL_NANOS = 500_000_000L;
    private static final long CRITICAL_FAR_INTERVAL_NANOS = 1_000_000_000L;
    private static final long CRITICAL_EXTREME_INTERVAL_NANOS = 2_000_000_000L;
    private static final long LOOK_PACKET_INTERVAL_NANOS = 100_000_000L;
    private static final long VELOCITY_PACKET_INTERVAL_NANOS = 200_000_000L;
    private static final long ANIMATION_PACKET_INTERVAL_NANOS = 250_000_000L;
    private static final long STATUS_PACKET_INTERVAL_NANOS = 500_000_000L;
    private static final long STATE_PACKET_INTERVAL_NANOS = 1_000_000_000L;

    private static final long BANDWIDTH_SAMPLE_PERIOD_TICKS = 20L;
    private static final long SNAPSHOT_PERIOD_TICKS = 2L;
    private static final long LOG_PERIOD_NANOS = 30_000_000_000L;
    private static final long RECENT_TELEPORT_SUPPRESS_MILLIS = 5_000L;
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
    private long lastSystemTransmitBytes = -1L;
    private long lastBandwidthSampleNanos = 0L;
    private long lastLogNanos = 0L;
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
        correctionQueue.removeIf(correction ->
                correction.key().viewerId().equals(player.getUniqueId())
                        || correction.key().subjectId().equals(player.getUniqueId()));
        packetRateLimits.keySet().removeIf(key ->
                key.viewerId().equals(player.getUniqueId()) || key.subjectId().equals(player.getUniqueId()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        markRecentTeleport(event.getPlayer().getUniqueId());
    }

    public void markRecentTeleport(UUID playerId) {
        recentTeleportsUntil.put(playerId, System.currentTimeMillis() + RECENT_TELEPORT_SUPPRESS_MILLIS);
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
        playerSnapshots.put(playerId, PlayerSnapshot.from(player, afkService));
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

        ThrottleMode previous = mode;
        boolean playerPressure = hasPlayerPressure();
        SnapshotPressure snapshotPressure = snapshotPressure();
        boolean pingPressure = snapshotPressure.pingPressure();
        boolean afkPressure = snapshotPressure.afkPressure();
        mode = maxMode(modeFor(smoothedBytesPerSecond), msptMode());
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
            double smoothedBytes = playerSmoothedOutboundBytes.compute(playerId, (ignored, previous) ->
                    smooth(previous == null ? 0.0D : previous, bytesPerSecond(bytes, elapsedSeconds)));
            playerPressureModes.put(playerId, playerModeFor(smoothedBytes));
        }
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

    private ThrottleMode modeFor(double bytesPerSecond) {
        double ratio = bytesPerSecond / SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND;
        return modeForRatio(ratio);
    }

    private ThrottleMode playerModeFor(double bytesPerSecond) {
        if (bytesPerSecond >= PLAYER_OUTBOUND_CRITICAL_BYTES_PER_SECOND) {
            return ThrottleMode.CRITICAL;
        }
        if (bytesPerSecond >= PLAYER_OUTBOUND_HARD_BYTES_PER_SECOND) {
            return ThrottleMode.HARD;
        }
        if (bytesPerSecond >= PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND) {
            return ThrottleMode.SOFT;
        }
        return ThrottleMode.OFF;
    }

    private ThrottleMode playerMode(UUID playerId) {
        return playerPressureModes.getOrDefault(playerId, ThrottleMode.OFF);
    }

    private ThrottleMode msptMode() {
        double mspt = performanceModule.effectiveMspt();
        if (mspt >= MSPT_CRITICAL_PRESSURE) {
            return ThrottleMode.CRITICAL;
        }
        if (mspt >= MSPT_HARD_PRESSURE) {
            return ThrottleMode.HARD;
        }
        if (mspt >= MSPT_SOFT_PRESSURE) {
            return ThrottleMode.SOFT;
        }
        return ThrottleMode.OFF;
    }

    private static ThrottleMode pingMode(int pingMillis) {
        if (pingMillis >= PING_CRITICAL_MILLIS) {
            return ThrottleMode.CRITICAL;
        }
        if (pingMillis >= PING_HARD_MILLIS) {
            return ThrottleMode.HARD;
        }
        if (pingMillis >= PING_SOFT_MILLIS) {
            return ThrottleMode.SOFT;
        }
        return ThrottleMode.OFF;
    }

    private ThrottleMode maxMode(ThrottleMode first, ThrottleMode second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private ThrottleMode modeForRatio(double ratio) {
        if (ratio >= CRITICAL_PRESSURE_RATIO) {
            return ThrottleMode.CRITICAL;
        }
        if (ratio >= HARD_PRESSURE_RATIO) {
            return ThrottleMode.HARD;
        }
        if (ratio >= SOFT_PRESSURE_RATIO) {
            return ThrottleMode.SOFT;
        }
        return ThrottleMode.OFF;
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
            correctionQueue.removeIf(correction -> correction.key().equals(entry.getKey()));
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
        ArrayList<PlayerPing> entries = new ArrayList<>();
        for (Map.Entry<UUID, PlayerSnapshot> entry : playerSnapshots.entrySet()) {
            int ping = entry.getValue().pingMillis();
            if (ping >= PING_SOFT_MILLIS) {
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
        while (sent < MAX_CORRECTIONS_PER_TICK && inspected < MAX_CORRECTION_INSPECTIONS_PER_TICK) {
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
                enqueueCorrection(key);
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
            if (currentMode == ThrottleMode.OFF) {
                pendingCorrections.remove(key);
                continue;
            }

            double distanceSquared = viewerSnapshot.distanceSquared(subjectSnapshot);
            long interval = correctionInterval(currentMode, distanceSquared,
                    intervalMultiplier(viewerSnapshot, subjectSnapshot));
            if (interval <= 0L) {
                pendingCorrections.remove(key);
                continue;
            }

            sendAbsoluteEntityPosition(viewer, subjectSnapshot);
            sent++;
            state.setNextSendNanos(now + interval);
            if (state.isDirty()) {
                enqueueCorrection(key);
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
        long interval = correctionInterval(context.mode(), context.distanceSquared(), context.viewerPingIntervalMultiplier());
        if (interval <= 0L) {
            return false;
        }

        ThrottleKey key = new ThrottleKey(viewerId, context.subjectId());
        pendingCorrections.compute(key, (ignored, state) -> {
            if (state == null) {
                return new CorrectionState();
            }
            state.markDirty();
            return state;
        });
        enqueueCorrection(key);
        return true;
    }

    private void enqueueCorrection(ThrottleKey key) {
        CorrectionState state = pendingCorrections.get(key);
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
                context.distanceSquared(),
                context.viewerPingIntervalMultiplier());
        if (interval <= 0L) {
            return false;
        }

        PacketLimitKey key = new PacketLimitKey(viewerId, context.subjectId(), packetType);
        RateState state = packetRateLimits.computeIfAbsent(key, ignored -> new RateState());
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
        return new ThrottleContext(subjectId, distanceSquared,
                intervalMultiplier(viewerSnapshot, subjectSnapshot), currentMode);
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
        if (expiresAt <= System.currentTimeMillis()) {
            recentTeleportsUntil.remove(playerId, expiresAt);
            return false;
        }
        return true;
    }

    private boolean isClosePvpPair(UUID viewerId, UUID subjectId, double distanceSquared) {
        return distanceSquared < CLOSE_PVP_DISTANCE_SQUARED
                && (pvpModule.isCombatTagged(viewerId) || pvpModule.isCombatTagged(subjectId));
    }

    private long correctionInterval(ThrottleMode currentMode, double distanceSquared, double pingIntervalMultiplier) {
        if (distanceSquared < NEAR_DISTANCE_SQUARED) {
            return 0L;
        }

        long baseInterval = switch (currentMode) {
            case OFF -> 0L;
            case SOFT -> {
                if (distanceSquared < MID_DISTANCE_SQUARED) yield SOFT_MID_INTERVAL_NANOS;
                if (distanceSquared < FAR_DISTANCE_SQUARED) yield SOFT_FAR_INTERVAL_NANOS;
                yield SOFT_EXTREME_INTERVAL_NANOS;
            }
            case HARD -> {
                if (distanceSquared < MID_DISTANCE_SQUARED) yield HARD_MID_INTERVAL_NANOS;
                if (distanceSquared < FAR_DISTANCE_SQUARED) yield HARD_FAR_INTERVAL_NANOS;
                yield HARD_EXTREME_INTERVAL_NANOS;
            }
            case CRITICAL -> {
                if (distanceSquared < MID_DISTANCE_SQUARED) yield CRITICAL_MID_INTERVAL_NANOS;
                if (distanceSquared < FAR_DISTANCE_SQUARED) yield CRITICAL_FAR_INTERVAL_NANOS;
                yield CRITICAL_EXTREME_INTERVAL_NANOS;
            }
        };
        return pingAdjustedInterval(baseInterval, pingIntervalMultiplier);
    }

    private long limitedPacketInterval(
            PacketGroup packetGroup,
            ThrottleMode currentMode,
            double distanceSquared,
            double pingIntervalMultiplier
    ) {
        if (distanceSquared < MID_DISTANCE_SQUARED) {
            return 0L;
        }

        long baseInterval = switch (packetGroup) {
            case NONE, MOVE -> 0L;
            case LOOK -> switch (currentMode) {
                case OFF -> 0L;
                case SOFT -> distanceSquared >= FAR_DISTANCE_SQUARED ? LOOK_PACKET_INTERVAL_NANOS : 0L;
                case HARD -> LOOK_PACKET_INTERVAL_NANOS;
                case CRITICAL -> LOOK_PACKET_INTERVAL_NANOS * 2L;
            };
            case VELOCITY -> switch (currentMode) {
                case OFF, SOFT -> 0L;
                case HARD -> distanceSquared >= FAR_DISTANCE_SQUARED ? VELOCITY_PACKET_INTERVAL_NANOS : 0L;
                case CRITICAL -> VELOCITY_PACKET_INTERVAL_NANOS;
            };
            case ANIMATION -> switch (currentMode) {
                case OFF -> 0L;
                case SOFT -> distanceSquared >= FAR_DISTANCE_SQUARED ? ANIMATION_PACKET_INTERVAL_NANOS : 0L;
                case HARD -> ANIMATION_PACKET_INTERVAL_NANOS;
                case CRITICAL -> ANIMATION_PACKET_INTERVAL_NANOS * 2L;
            };
            case STATUS -> switch (currentMode) {
                case OFF, SOFT -> 0L;
                case HARD -> distanceSquared >= FAR_DISTANCE_SQUARED ? STATUS_PACKET_INTERVAL_NANOS : 0L;
                case CRITICAL -> STATUS_PACKET_INTERVAL_NANOS;
            };
            case STATE -> currentMode == ThrottleMode.CRITICAL && distanceSquared >= FAR_DISTANCE_SQUARED
                    ? STATE_PACKET_INTERVAL_NANOS
                    : 0L;
        };
        return pingAdjustedInterval(baseInterval, pingIntervalMultiplier);
    }

    private long pingAdjustedInterval(long interval, double multiplier) {
        if (interval <= 0L) {
            return 0L;
        }
        return Math.max(interval, Math.round(interval * multiplier));
    }

    private static double pingIntervalMultiplier(int pingMillis) {
        if (pingMillis >= PING_CRITICAL_MILLIS) {
            return PING_CRITICAL_INTERVAL_MULTIPLIER;
        }
        if (pingMillis >= PING_HARD_MILLIS) {
            return PING_HARD_INTERVAL_MULTIPLIER;
        }
        if (pingMillis >= PING_SOFT_MILLIS) {
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
        if (throttleArmed && bytes > 0) {
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
                        "Bandwidth, ping, MSPT, and player thresholds."))
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
        double outboundRatio = smoothedBytesPerSecond / SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND;
        return sectionTitle("Status", "Current global network throttle state.")
                .append(metricLine("mode", modeComponent(mode),
                        "Global mode from server outbound demand and MSPT pressure."))
                .append(metricLine("armed", booleanComponent(throttleArmed),
                        "Armed means packet inspection is active because global, player, ping, or AFK pressure exists."))
                .append(metricLine("mspt", Component.text(String.format(Locale.ROOT, "%.1f", performanceModule.effectiveMspt()),
                                msptColor(performanceModule.effectiveMspt())),
                        "Effective MSPT from PerformanceModule. Network throttling also reacts to server tick pressure."))
                .append(metricLine("plugin sent",
                        Component.text(formatThroughput(smoothedDeliveredBytesPerSecond), NamedTextColor.AQUA),
                        "Smoothed PacketEvents outbound bytes that were actually allowed through by this module. This is still logical packet-buffer size, not Linux eth0 bytes."))
                .append(metricLine("plugin demand",
                        Component.text(formatThroughput(smoothedBytesPerSecond) + "  "
                                + formatPercent(outboundRatio), pressureColor(modeFor(smoothedBytesPerSecond))),
                        "Smoothed demand for packets this module can throttle: allowed controlled packets plus packets cancelled by this module. This is the value used for global pressure."))
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
                        "Largest outbound packet types per second in the last sample while the throttle was armed."))
                .append(metricLine("groups", Component.text(throttledGroupSummary(), NamedTextColor.GRAY),
                        "Cancellation totals grouped by movement, look, velocity, animation, status, and state packets."))
                .append(metricLine("distance", Component.text(throttledDistanceSummary(), NamedTextColor.GRAY),
                        "Cancellation totals grouped by subject distance band."));
    }

    private Component limitsSection() {
        return sectionTitle("Limits", "Hard-coded thresholds used by this module.")
                .append(metricLine("server demand limit", Component.text(formatThroughput(SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND),
                                NamedTextColor.AQUA),
                        "Global PacketEvents demand target. Modes begin at 70%, 85%, and 95% of this value."))
                .append(metricLine("server soft", Component.text(formatThroughput(SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND
                                * SOFT_PRESSURE_RATIO), NamedTextColor.YELLOW),
                        "Global SOFT threshold."))
                .append(metricLine("server hard", Component.text(formatThroughput(SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND
                                * HARD_PRESSURE_RATIO), NamedTextColor.GOLD),
                        "Global HARD threshold."))
                .append(metricLine("server critical", Component.text(formatThroughput(SERVER_OUTBOUND_LIMIT_BYTES_PER_SECOND
                                * CRITICAL_PRESSURE_RATIO), NamedTextColor.RED),
                        "Global CRITICAL threshold."))
                .append(metricLine("player soft", Component.text(formatThroughput(PLAYER_OUTBOUND_SOFT_BYTES_PER_SECOND),
                                NamedTextColor.YELLOW),
                        "Per-viewer receive-side SOFT threshold, roughly 400 KiB/s."))
                .append(metricLine("player hard", Component.text(formatThroughput(PLAYER_OUTBOUND_HARD_BYTES_PER_SECOND),
                                NamedTextColor.GOLD),
                        "Per-viewer receive-side HARD threshold, roughly 800 KiB/s."))
                .append(metricLine("player critical", Component.text(formatThroughput(PLAYER_OUTBOUND_CRITICAL_BYTES_PER_SECOND),
                                NamedTextColor.RED),
                        "Per-viewer receive-side CRITICAL threshold, roughly 1.2 MiB/s."))
                .append(metricLine("ping", Component.text(PING_SOFT_MILLIS + "/" + PING_HARD_MILLIS + "/"
                                + PING_CRITICAL_MILLIS + " ms", NamedTextColor.GRAY),
                        "Ping thresholds for SOFT, HARD, and CRITICAL per-viewer throttling."))
                .append(metricLine("recent teleport", Component.text(RECENT_TELEPORT_SUPPRESS_MILLIS + " ms",
                                NamedTextColor.GRAY),
                        "Viewer or subject teleports suppress throttling briefly to avoid bad visual state."))
                .append(metricLine("distance bands", Component.text("48 / 96 / 160 blocks", NamedTextColor.GRAY),
                        "Near, mid, far, and extreme distance boundaries for movement throttling."))
                .append(metricLine("correction budget", Component.text(MAX_CORRECTIONS_PER_TICK + "/tick",
                                NamedTextColor.GRAY),
                        "Maximum absolute movement corrections flushed back to viewers per tick."))
                .append(metricLine("rate expiry", Component.text(RATE_LIMIT_EXPIRY_NANOS / 1_000_000_000L + " s",
                                NamedTextColor.GRAY),
                        "Inactive per-packet limiter state is removed after this many seconds."));
    }

    private Component rulesSection() {
        return sectionTitle("Rules", "Runtime behavior used when throttling is armed.")
                .append(metricLine("no bypass", Component.text("roles and spectators are included", NamedTextColor.YELLOW),
                        "Helper, admin, and spectator viewers are still subject to network throttling."))
                .append(metricLine("accounting", Component.text("viewer receive-side, logical packet bytes",
                                NamedTextColor.GRAY),
                        "Per-player numbers are attributed to the receiving viewer and only count packets this module can throttle. Global sent still uses PacketEvents logical buffer bytes, not eth0 bytes."))
                .append(metricLine("movement", Component.text("distance based + correction queue", NamedTextColor.GRAY),
                        "Far relative movement can be cancelled, then repaired with bounded absolute position corrections."))
                .append(metricLine("packet groups",
                        Component.text("move, look, velocity, animation, status, state", NamedTextColor.GRAY),
                        "Low-priority packet groups receive longer per-packet intervals as pressure rises."))
                .append(metricLine("ping scaling", Component.text("x" + formatMultiplier(PING_SOFT_INTERVAL_MULTIPLIER)
                                + " / x" + formatMultiplier(PING_HARD_INTERVAL_MULTIPLIER)
                                + " / x" + formatMultiplier(PING_CRITICAL_INTERVAL_MULTIPLIER), NamedTextColor.GRAY),
                        "Viewer ping increases packet spacing at soft, hard, and critical ping thresholds."))
                .append(metricLine("afk scaling", Component.text("viewer x" + formatMultiplier(AFK_VIEWER_INTERVAL_MULTIPLIER)
                                        + ", subject x" + formatMultiplier(AFK_SUBJECT_INTERVAL_MULTIPLIER),
                                NamedTextColor.GRAY),
                        "AFK viewers and AFK subjects receive wider intervals at distance."))
                .append(metricLine("interaction guard", Component.text("close PvP, vehicle, teleport guarded",
                                NamedTextColor.GREEN),
                        "Critical interaction scenes avoid the most disruptive distance throttling."))
                .append(metricLine("world pressure", Component.text("lobby conservative, resource worlds aggressive",
                                NamedTextColor.GRAY),
                        "World names adjust throttle mode up or down for known scene types."));
    }

    private Component helpSection() {
        return sectionTitle("Commands", "Click a command to run it.")
                .append(commandLine("/network", "current status overview"))
                .append(commandLine("/network players", "per-player bandwidth, ping, and AFK pressure"))
                .append(commandLine("/network packets", "packet throttle counters and hot packet types"))
                .append(commandLine("/network limits", "hard-coded bandwidth and pressure thresholds"))
                .append(commandLine("/network rules", "scene, distance, ping, and AFK throttle rules"))
                .append(commandLine("/network all", "full diagnostic report"));
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

    private record ThrottleKey(UUID viewerId, UUID subjectId) {
    }

    private record PacketLimitKey(UUID viewerId, UUID subjectId, PacketTypeCommon packetType) {
    }

    private record ThrottleContext(UUID subjectId, double distanceSquared, double viewerPingIntervalMultiplier,
                                   ThrottleMode mode) {
        DistanceBand distanceBand() {
            return DistanceBand.from(distanceSquared);
        }
    }

    private record PacketVolume(PacketTypeCommon type, long bytes) {
    }

    private record PlayerVolume(UUID playerId, double bytes) {
    }

    private record PlayerPing(UUID playerId, int pingMillis) {
    }

    private record SnapshotPressure(boolean pingPressure, boolean afkPressure) {
    }

    private record DelayedCorrection(ThrottleKey key, long dueNanos) implements Delayed {
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(dueNanos - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
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
                                  double x, double y, double z, float yaw, float pitch) {
        static PlayerSnapshot from(Player player, AfkService afkService) {
            Location location = player.getLocation();
            World world = location.getWorld();
            Entity vehicle = player.getVehicle();
            int vehicleEntityId = vehicle == null ? -1 : vehicle.getEntityId();
            int rootVehicleEntityId = rootVehicleEntityId(player);
            int pingMillis = Math.max(0, player.getPing());
            return new PlayerSnapshot(
                    world.getUID(),
                    world.getName(),
                    player.getEntityId(),
                    vehicleEntityId,
                    rootVehicleEntityId,
                    afkService.isAfk(player.getUniqueId()),
                    pingMillis,
                    NetworkThrottleModule.pingMode(pingMillis),
                    NetworkThrottleModule.pingIntervalMultiplier(pingMillis),
                    location.x(),
                    location.y(),
                    location.z(),
                    location.getYaw(),
                    location.getPitch());
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
