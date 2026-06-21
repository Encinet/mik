package org.encinet.mik.module.safety;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.encinet.mik.util.PlayerDisplay;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Module for detecting and auto-banning griefers on the Mik creative server.
 *
 * <p>Only monitors players who do NOT have the {@code group.member} permission.
 *
 * <p><b>Detection subsystems:</b>
 * <ul>
 *   <li><b>Lava spam</b>: multiple lava-bucket placements in a short window</li>
 *   <li><b>WorldEdit air</b>: ultra-rapid block breaks indicating {@code //set air}</li>
 *   <li><b>Mass break</b>: sustained high-rate block breaking</li>
 *   <li><b>Scatter grief</b>: breaks spread over a large area, indicating a griefing patrol</li>
 *   <li><b>Structure grief</b>: breaking many non-natural (player-placed) blocks</li>
 * </ul>
 *
 * <p><b>Scoring:</b> each detection event adds points; points decay over time.
 * Reaching {@link #SCORE_WARN} notifies staff; reaching {@link #SCORE_BAN} triggers an auto-ban.
 */
public class GrieferModule implements Listener {

    /** Players with this permission are never monitored. */
    private static final String EXEMPT_PERMISSION = "group.member";

    /** Permission node for staff broadcast messages. */
    private static final String STAFF_PERMISSION = "mik.staff";

    /** How many lava-bucket placements in {@link #LAVA_WINDOW_MS} triggers suspicion. */
    private static final int  LAVA_COUNT     = 3;
    private static final long LAVA_WINDOW_MS = 20_000L; // 20 s

    /**
     * Block breaks per {@link #WE_WINDOW_MS} that indicate WorldEdit mass-air.
     * Hand-breaking in creative tops out ~8 bps; WE can do thousands.
     */
    private static final int  WE_COUNT     = 35;
    private static final long WE_WINDOW_MS = 1_000L; // 1 s

    /** Sustained high-rate breaking (includes aggressive hand-griefing). */
    private static final int  MASS_COUNT     = 60;
    private static final long MASS_WINDOW_MS = 8_000L; // 8 s

    /**
     * Breaking {@link #SCATTER_COUNT} blocks across a radius larger than
     * {@link #SCATTER_SPREAD} blocks (bounding-box diagonal) within
     * {@link #SCATTER_WIND_MS} signals a roaming griefer.
     */
    private static final int    SCATTER_COUNT   = 25;
    private static final double SCATTER_SPREAD  = 50.0; // blocks (diagonal)
    private static final long   SCATTER_WIND_MS = 30_000L; // 30 s

    /** Breaking many non-natural (player-placed) blocks in a window. */
    private static final int  STRUCTURE_COUNT     = 20;
    private static final long STRUCTURE_WINDOW_MS = 60_000L; // 60 s

    private static final int SCORE_WARN = 60;
    private static final int SCORE_BAN  = 110;
    private static final int SCORE_CAP  = 200;

    // Points awarded per detection trigger
    private static final int PTS_LAVA      = 35;
    private static final int PTS_WE        = 55;
    private static final int PTS_MASS      = 20;
    private static final int PTS_SCATTER   = 28;
    private static final int PTS_STRUCTURE = 18;

    // Score decays by DECAY_AMOUNT every DECAY_INTERVAL_TICKS ticks (main thread)
    private static final long DECAY_INTERVAL_TICKS = 600L; // 30 s
    private static final int  DECAY_AMOUNT         = 8;

    /** Offline player data older than this is removed by the cleanup task. */
    private static final long DATA_MAX_AGE_MS = 120_000L; // 2 min
    private static final long CLEANUP_INTERVAL_TICKS = 12_000L; // 10 min

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final JavaPlugin plugin;
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();

    public GrieferModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Register this module: attach event listeners and start background tasks.
     * Call this once during plugin enable, after other modules if needed.
     */
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startDecayTask();
        startCleanupTask();
        plugin.getLogger().info("[GrieferModule] 已启动反破坏检测 | Anti-grief detection active.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.LAVA_BUCKET) return;
        Player player = event.getPlayer();
        if (!shouldMonitor(player)) return;

        PlayerData data = getData(player);
        long now = System.currentTimeMillis();
        data.addLava(now);

        if (data.lavaRateTriggered(now) && !data.onCooldown(CooldownKey.LAVA)) {
            data.setCooldown(CooldownKey.LAVA, 15_000L);
            flag(player, data, PTS_LAVA,
                    "岩浆桶放置过频繁 | Lava spam: " + LAVA_COUNT + " 次 / " + LAVA_WINDOW_MS / 1000 + "s");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!shouldMonitor(player)) return;
        Block b = event.getBlockPlaced();
        long now = System.currentTimeMillis();
        getData(player).addPlace(now, packXYZ(b.getX(), b.getY(), b.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!shouldMonitor(player)) return;

        Block block = event.getBlock();
        long now = System.currentTimeMillis();
        PlayerData data = getData(player);
        data.addBreak(now, block.getX(), block.getZ());

        if (data.breakRateTriggered(WE_COUNT, WE_WINDOW_MS, now)
                && !data.onCooldown(CooldownKey.WE)) {
            data.setCooldown(CooldownKey.WE, 8_000L);
            flag(player, data, PTS_WE, "疑似 //set air | Possible WorldEdit mass-air: " + WE_COUNT + "+ 块/s");
            return;
        }

        if (data.breakRateTriggered(MASS_COUNT, MASS_WINDOW_MS, now)
                && !data.onCooldown(CooldownKey.MASS)
                && !data.isLikelyBuilding(now)) {
            data.setCooldown(CooldownKey.MASS, 12_000L);
            flag(player, data, PTS_MASS,
                    "大规模连续破坏 | Mass break: " + MASS_COUNT + " 块 / " + MASS_WINDOW_MS / 1000 + "s");
        }

        if (!data.onCooldown(CooldownKey.SCATTER) && !data.isLikelyBuilding(now)) {
            double spread = data.recentScatterDiagonal(now);
            if (spread >= SCATTER_SPREAD) {
                data.setCooldown(CooldownKey.SCATTER, 20_000L);
                flag(player, data, PTS_SCATTER,
                        "大范围分散破坏 | Scatter grief: " + SCATTER_COUNT
                                + " 块, 扩散=" + String.format("%.0f", spread) + " 格");
            }
        }

        if (isLikelyPlayerPlaced(block.getType())
                && !data.wasOwnBlock(packXYZ(block.getX(), block.getY(), block.getZ()), now)) {
            data.addStructBreak(now);
            if (data.structRateTriggered(now) && !data.onCooldown(CooldownKey.STRUCTURE)) {
                data.setCooldown(CooldownKey.STRUCTURE, 30_000L);
                flag(player, data, PTS_STRUCTURE,
                        "破坏玩家建筑 | Structure grief: " + STRUCTURE_COUNT + " 个建筑方块 / 60s");
            }
        }
    }

    /**
     * Remove data when a player leaves (will be recreated if they return quickly).
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = players.get(uuid);
        if (data != null) data.lastSeen = System.currentTimeMillis();
    }

    /**
     * Add suspicion points to a player and enforce bans/warnings as needed.
     *
     * @param player the suspect
     * @param data   their tracking data
     * @param points points to add
     * @param reason human-readable reason (logged and broadcast)
     */
    private void flag(Player player, PlayerData data, int points, String reason) {
        if (data.banned) return;  // 提前退出
        int score = data.addScore(points);
        String timestamp = TIME_FMT.format(Instant.now());

        plugin.getLogger().warning(String.format(
                "[GrieferModule] [%s] %s | score=%d (+%d) | %s",
                timestamp, player.getName(), score, points, reason));

        if (data.banned) return;

        if (score >= SCORE_BAN) {
            data.banned = true;
            executeBan(player, reason);
        } else if (score >= SCORE_WARN && !data.warnSent) {
            data.warnSent = true;
            notifyStaff(player, score, reason);
        }
    }

    private void executeBan(Player player, String triggerReason) {
        String banReason = "[Mik GrieferModule] 检测到破坏行为 | Griefing detected: " + triggerReason;

        // Must run on main thread; flag() is called from event handler (already main),
        // but guard anyway in case future refactors call it async.
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.ban(banReason, Duration.ofDays(30), "Mik GrieferModule");
            player.kick(Component.text()
                    .append(Component.text("[Mik] 您已被自动封禁", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("检测到破坏服务器行为，如有异议请联系管理员申诉", NamedTextColor.GRAY))
                    .append(Component.newline())
                    .append(Component.text("原因: " + triggerReason, NamedTextColor.GRAY))
                    .build());
        });

        Bukkit.broadcast(
                Component.text()
                        .append(Component.text("[GrieferModule] ", NamedTextColor.RED))
                        .append(Component.text("⛔ 已自动封禁 ", NamedTextColor.RED))
                        .append(PlayerDisplay.name(player, NamedTextColor.YELLOW))
                        .append(Component.text(" | " + triggerReason, NamedTextColor.GRAY))
                        .build(),
                STAFF_PERMISSION);

        plugin.getLogger().warning("[GrieferModule] *** AUTO-BAN *** "
                + player.getName() + " | " + triggerReason);
    }

    private void notifyStaff(Player player, int score, String reason) {
        Bukkit.broadcast(
                Component.text()
                        .append(Component.text("[GrieferModule] ", NamedTextColor.GOLD))
                        .append(Component.text("⚠ 可疑玩家: ", NamedTextColor.YELLOW))
                        .append(PlayerDisplay.name(player, NamedTextColor.WHITE))
                        .append(Component.text(" 分数=" + score + "/" + SCORE_BAN, NamedTextColor.YELLOW))
                        .append(Component.text(" | " + reason, NamedTextColor.GRAY))
                        .build(),
                STAFF_PERMISSION);
    }

    private void startDecayTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PlayerData data : players.values()) {
                    if (data.score > 0) {
                        data.score = Math.max(0, data.score - DECAY_AMOUNT);
                        // Reset warn flag once score cools below the warn threshold
                        if (data.score < SCORE_WARN) data.warnSent = false;
                    }
                }
            }
        }.runTaskTimer(plugin, DECAY_INTERVAL_TICKS, DECAY_INTERVAL_TICKS);
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                players.entrySet().removeIf(entry -> {
                    boolean offline = Bukkit.getPlayer(entry.getKey()) == null;
                    boolean stale = (now - entry.getValue().lastSeen) > DATA_MAX_AGE_MS;
                    return offline && stale;
                });
            }
        }.runTaskTimer(plugin, CLEANUP_INTERVAL_TICKS, CLEANUP_INTERVAL_TICKS);
    }

    private boolean shouldMonitor(Player player) {
        return !player.hasPermission(EXEMPT_PERMISSION);
    }

    private PlayerData getData(Player player) {
        return players.computeIfAbsent(player.getUniqueId(), k -> new PlayerData());
    }

    private static long packXYZ(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38) | ((long)(y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    /**
     * Heuristic: is this block likely to have been placed by a player (not world-generated)?
     *
     * <p>Returns {@code true} for crafted/processed materials such as planks, slabs, glass,
     * wool, concrete, bricks, etc. Returns {@code false} for terrain-generation materials
     * (stone variants, ores, dirt, sand, logs, leaves, water, lava, etc.).
     *
     * <p>Note: This is intentionally conservative to reduce false positives. WorldEdit
     * operations on terrain will still be caught by the rate-based detectors.
     */
    private boolean isLikelyPlayerPlaced(Material mat) {
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) return false;
        if (!mat.isBlock() || !mat.isSolid()) return false;

        String name = mat.name();

        // Natural terrain (generated by world or tree growth)
        if (name.endsWith("_ORE"))       return false;
        if (name.endsWith("_LOG"))       return false;
        if (name.endsWith("_LEAVES"))    return false;
        if (name.endsWith("_WOOD") && !name.contains("STRIPPED")) return false;

        return switch (mat) {
            case STONE, GRANITE, DIORITE, ANDESITE,
                 DEEPSLATE, TUFF, CALCITE,
                 DIRT, GRASS_BLOCK, PODZOL, MYCELIUM,
                 COARSE_DIRT, ROOTED_DIRT, MUD,
                 SAND, RED_SAND, GRAVEL,
                 SANDSTONE, RED_SANDSTONE,
                 SNOW, SNOW_BLOCK, ICE, PACKED_ICE, BLUE_ICE,
                 WATER, LAVA,
                 BEDROCK, OBSIDIAN,
                 NETHERRACK, SOUL_SAND, SOUL_SOIL, BASALT,
                 BLACKSTONE, MAGMA_BLOCK,
                 END_STONE,
                 CLAY, PACKED_MUD,
                 MOSS_BLOCK, SCULK,
                 DRIPSTONE_BLOCK, POINTED_DRIPSTONE,
                 MUSHROOM_STEM, BROWN_MUSHROOM_BLOCK, RED_MUSHROOM_BLOCK,
                 NETHER_WART_BLOCK, WARPED_WART_BLOCK,
                 CRIMSON_HYPHAE, WARPED_HYPHAE,
                 CRIMSON_STEM, WARPED_STEM -> false;

            default -> true;
        };
    }

    private enum CooldownKey {
        LAVA, WE, MASS, SCATTER, STRUCTURE
    }

    private static final class PlayerData {

        private final long[] lavaTimes = new long[LAVA_COUNT];
        private int lavaHead = 0, lavaFill = 0;

        private final long[] brkTime = new long[MASS_COUNT];
        private final int[]  brkX    = new int[MASS_COUNT];
        private final int[]  brkZ    = new int[MASS_COUNT];
        private int brkHead = 0, brkFill = 0;

        private final long[] strucTimes = new long[STRUCTURE_COUNT];
        private int strucHead = 0, strucFill = 0;

        private static final int PLACE_BUF = 32;
        private final long[] placeTimes = new long[PLACE_BUF];
        private int placeHead = 0, placeFill = 0;

        private static final int OWN_BLOCK_CAP = 512;
        private final Map<Long, Long> ownBlocks = new HashMap<>();

        final Map<CooldownKey, Long> cooldowns = new EnumMap<>(CooldownKey.class);
        int     score    = 0;
        boolean warnSent = false;
        boolean banned   = false;
        long    lastSeen = System.currentTimeMillis();

        void addLava(long t) {
            lavaTimes[lavaHead] = t;
            lavaHead = (lavaHead + 1) % LAVA_COUNT;
            if (lavaFill < LAVA_COUNT) lavaFill++;
        }

        boolean lavaRateTriggered(long now) {
            if (lavaFill < LAVA_COUNT) return false;
            return (now - lavaTimes[lavaHead]) <= LAVA_WINDOW_MS;
        }

        void addBreak(long t, int x, int z) {
            brkTime[brkHead] = t;
            brkX[brkHead]    = x;
            brkZ[brkHead]    = z;
            brkHead = (brkHead + 1) % MASS_COUNT;
            if (brkFill < MASS_COUNT) brkFill++;
        }

        boolean breakRateTriggered(int count, long windowMs, long now) {
            if (brkFill < count) return false;
            int idx = Math.floorMod(brkHead - count, MASS_COUNT);
            return (now - brkTime[idx]) <= windowMs;
        }

        double recentScatterDiagonal(long now) {
            if (brkFill < SCATTER_COUNT) return 0.0;
            int oldestIdx = Math.floorMod(brkHead - SCATTER_COUNT, MASS_COUNT);
            if ((now - brkTime[oldestIdx]) > SCATTER_WIND_MS) return 0.0;

            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
            int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
            for (int i = 0; i < SCATTER_COUNT; i++) {
                int idx = Math.floorMod(brkHead - 1 - i, MASS_COUNT);
                int x = brkX[idx], z = brkZ[idx];
                if (x < minX) minX = x; if (x > maxX) maxX = x;
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
            }
            long dx = maxX - minX, dz = maxZ - minZ;
            return Math.sqrt(dx * dx + dz * dz);
        }

        void addStructBreak(long t) {
            strucTimes[strucHead] = t;
            strucHead = (strucHead + 1) % STRUCTURE_COUNT;
            if (strucFill < STRUCTURE_COUNT) strucFill++;
        }

        boolean structRateTriggered(long now) {
            if (strucFill < STRUCTURE_COUNT) return false;
            return (now - strucTimes[strucHead]) <= STRUCTURE_WINDOW_MS;
        }

        void addPlace(long t, long packed) {
            placeTimes[placeHead] = t;
            placeHead = (placeHead + 1) % PLACE_BUF;
            if (placeFill < PLACE_BUF) placeFill++;
            if (ownBlocks.size() < OWN_BLOCK_CAP) ownBlocks.put(packed, t);
        }

        boolean wasOwnBlock(long packed, long now) {
            Long t = ownBlocks.get(packed);
            if (t == null) return false;
            if ((now - t) > STRUCTURE_WINDOW_MS) { ownBlocks.remove(packed); return false; }
            return true;
        }

        boolean isLikelyBuilding(long now) {
            long cutoff = now - MASS_WINDOW_MS;
            int breaks = 0;
            for (int i = 0; i < brkFill; i++) {
                if (brkTime[Math.floorMod(brkHead - 1 - i, MASS_COUNT)] < cutoff) break;
                breaks++;
            }
            if (breaks == 0) return false;

            int places = 0;
            for (int i = 0; i < placeFill; i++) {
                if (placeTimes[Math.floorMod(placeHead - 1 - i, PLACE_BUF)] < cutoff) break;
                places++;
            }
            return places * 10 >= breaks * 3;
        }

        int addScore(int pts) {
            lastSeen = System.currentTimeMillis();
            score = Math.min(score + pts, SCORE_CAP);
            return score;
        }

        boolean onCooldown(CooldownKey key) {
            Long until = cooldowns.get(key);
            return until != null && System.currentTimeMillis() < until;
        }

        void setCooldown(CooldownKey key, long durationMs) {
            cooldowns.put(key, System.currentTimeMillis() + durationMs);
        }
    }
}
