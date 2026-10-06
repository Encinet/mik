package org.encinet.mik.module.ai.tool.game;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Immutable main-thread snapshot exposed to read-only game tool packs. */
public record AiGameSnapshot(
        Instant capturedAt,
        String serverZoneId,
        String minecraftVersion,
        int onlinePlayers,
        int maximumPlayers,
        double tpsOneMinute,
        double averageTickMillis,
        Duration uptime,
        Memory memory,
        Optional<UUID> requesterId,
        boolean nearbyEntitiesAvailable,
        List<PlayerInfo> players,
        List<NearbyEntity> nearbyEntities,
        List<WorldInfo> worlds,
        List<RegistryEntry> registry
) {
    private static final double NEARBY_RADIUS = 32.0;
    private static final int MAXIMUM_NEARBY_ENTITIES = 128;

    public AiGameSnapshot {
        Objects.requireNonNull(capturedAt, "capturedAt");
        Objects.requireNonNull(serverZoneId, "serverZoneId");
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(uptime, "uptime");
        Objects.requireNonNull(memory, "memory");
        requesterId = Objects.requireNonNull(requesterId, "requesterId");
        players = List.copyOf(players);
        nearbyEntities = List.copyOf(nearbyEntities);
        worlds = List.copyOf(worlds);
        registry = List.copyOf(registry);
    }

    public static AiGameSnapshot capture(
            JavaPlugin plugin,
            boolean includePlayerLocations,
            long startedAtNanos,
            UUID requesterId
    ) {
        Objects.requireNonNull(plugin, "plugin");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("AI game snapshots must be captured on the main thread");
        }
        List<PlayerInfo> players = Bukkit.getOnlinePlayers().stream()
                .map(player -> player(player, includePlayerLocations,
                        Objects.equals(requesterId, player.getUniqueId())))
                .sorted(Comparator.comparing(PlayerInfo::name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<WorldInfo> worlds = Bukkit.getWorlds().stream()
                .map(AiGameSnapshot::world)
                .sorted(Comparator.comparing(WorldInfo::name,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
        Player requester = requesterId == null ? null : Bukkit.getPlayer(requesterId);
        boolean nearbyAvailable = includePlayerLocations && requester != null;
        List<NearbyEntity> nearby = nearbyAvailable
                ? nearbyEntities(requester) : List.of();
        double[] tps = plugin.getServer().getTPS();
        Runtime runtime = Runtime.getRuntime();
        return new AiGameSnapshot(
                Instant.now(), ZoneId.systemDefault().getId(), Bukkit.getMinecraftVersion(),
                players.size(), plugin.getServer().getMaxPlayers(),
                tps.length == 0 ? 0.0 : tps[0], Bukkit.getAverageTickTime(),
                Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAtNanos)),
                new Memory(runtime.totalMemory() - runtime.freeMemory(), runtime.maxMemory()),
                Optional.ofNullable(requesterId), nearbyAvailable, players, nearby, worlds,
                captureRegistry());
    }

    private static PlayerInfo player(
            Player player,
            boolean includeLocation,
            boolean requester
    ) {
        Location location = includeLocation ? player.getLocation() : null;
        Position position = location == null ? null
                : new Position(location.getX(), location.getY(), location.getZ());
        return new PlayerInfo(
                player.getName(), player.getUniqueId(), player.getGameMode().name(),
                player.getWorld().getName(), Optional.ofNullable(position), player.getHealth(),
                player.getFoodLevel(), player.getLevel(), player.getPing(),
                statistics(player), requester ? Optional.of(inventory(player)) : Optional.empty());
    }

    private static PlayerStatistics statistics(Player player) {
        return new PlayerStatistics(
                duration(player, Statistic.PLAY_ONE_MINUTE),
                statistic(player, Statistic.DEATHS),
                statistic(player, Statistic.MOB_KILLS),
                statistic(player, Statistic.PLAYER_KILLS),
                statistic(player, Statistic.JUMP),
                statistic(player, Statistic.WALK_ONE_CM) / 100.0,
                statistic(player, Statistic.SPRINT_ONE_CM) / 100.0,
                statistic(player, Statistic.FLY_ONE_CM) / 100.0);
    }

    private static Duration duration(Player player, Statistic statistic) {
        return Duration.ofMillis(Math.max(0L, statistic(player, statistic)) * 50L);
    }

    private static int statistic(Player player, Statistic statistic) {
        try {
            return Math.max(0, player.getStatistic(statistic));
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    private static List<InventoryEntry> inventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        List<InventoryEntry> result = new ArrayList<>();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            addItem(result, slot < 9 ? "hotbar_" + slot : "inventory_" + slot,
                    storage[slot]);
        }
        ItemStack[] armor = inventory.getArmorContents();
        String[] armorSlots = {"boots", "leggings", "chestplate", "helmet"};
        for (int slot = 0; slot < Math.min(armor.length, armorSlots.length); slot++) {
            addItem(result, armorSlots[slot], armor[slot]);
        }
        addItem(result, "offhand", inventory.getItemInOffHand());
        return List.copyOf(result);
    }

    private static void addItem(List<InventoryEntry> result, String slot, ItemStack item) {
        if (item == null || item.getType().isLegacy() || item.getType().isAir()
                || item.getAmount() <= 0) {
            return;
        }
        Map<String, Integer> enchantments = new TreeMap<>();
        for (Map.Entry<Enchantment, Integer> enchantment : item.getEnchantments().entrySet()) {
            enchantments.put(enchantment.getKey().toString(), enchantment.getValue());
        }
        result.add(new InventoryEntry(slot, item.getType().getKey().toString(),
                item.getAmount(), enchantments));
    }

    private static List<NearbyEntity> nearbyEntities(Player requester) {
        Location origin = requester.getLocation();
        return requester.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS).stream()
                .map(entity -> nearbyEntity(origin, entity))
                .sorted(Comparator.comparingDouble(NearbyEntity::distance))
                .limit(MAXIMUM_NEARBY_ENTITIES)
                .toList();
    }

    private static NearbyEntity nearbyEntity(Location origin, Entity entity) {
        Location location = entity.getLocation();
        return new NearbyEntity(
                entity.getType().getKey().toString(), entity.getName(),
                Math.sqrt(origin.distanceSquared(location)),
                new Position(location.getX(), location.getY(), location.getZ()));
    }

    private static WorldInfo world(World world) {
        Map<String, String> rules = new TreeMap<>();
        for (String rule : world.getGameRules()) {
            Object value = world.getGameRuleValue(rule);
            if (value != null) {
                rules.put(rule, String.valueOf(value));
            }
        }
        return new WorldInfo(
                world.getName(), world.getEnvironment().name(), world.getDifficulty().name(),
                world.getTime(), world.hasStorm(), world.isThundering(),
                world.getPlayers().size(), world.getLoadedChunks().length,
                world.getWorldBorder().getSize(), rules);
    }

    private static List<RegistryEntry> captureRegistry() {
        List<RegistryEntry> entries = new ArrayList<>();
        for (Material material : Material.values()) {
            String id = materialKey(material);
            if (id == null) continue;
            if (material.isItem()) {
                entries.add(new RegistryEntry("item", id));
            }
            if (material.isBlock()) {
                entries.add(new RegistryEntry("block", id));
            }
        }
        for (EntityType type : EntityType.values()) {
            if (type != EntityType.UNKNOWN) {
                entries.add(new RegistryEntry("entity", type.getKey().toString()));
            }
        }
        entries.sort(Comparator.comparing(RegistryEntry::category)
                .thenComparing(RegistryEntry::id));
        return List.copyOf(entries);
    }

    static String materialKey(Material material) {
        // Paper keeps legacy enum constants for migration, but they have no
        // namespaced key. Check before calling getKey(), which rejects them.
        return material.isLegacy() ? null : material.getKey().toString();
    }

    public record Memory(long usedBytes, long maximumBytes) {
    }

    public record PlayerInfo(
            String name,
            UUID id,
            String gameMode,
            String world,
            Optional<Position> position,
            double health,
            int food,
            int experienceLevel,
            int pingMillis,
            PlayerStatistics statistics,
            Optional<List<InventoryEntry>> requesterInventory
    ) {
        public PlayerInfo {
            position = Objects.requireNonNull(position, "position");
            Objects.requireNonNull(statistics, "statistics");
            requesterInventory = Objects.requireNonNull(requesterInventory,
                    "requesterInventory").map(List::copyOf);
        }
    }

    public record PlayerStatistics(
            Duration playTime,
            int deaths,
            int mobKills,
            int playerKills,
            int jumps,
            double walkedMetres,
            double sprintedMetres,
            double flownMetres
    ) {
    }

    public record InventoryEntry(
            String slot,
            String item,
            int amount,
            Map<String, Integer> enchantments
    ) {
        public InventoryEntry {
            enchantments = Map.copyOf(enchantments);
        }
    }

    public record NearbyEntity(
            String type,
            String name,
            double distance,
            Position position
    ) {
    }

    public record Position(double x, double y, double z) {
    }

    public record WorldInfo(
            String name,
            String environment,
            String difficulty,
            long time,
            boolean storm,
            boolean thunder,
            int onlinePlayers,
            int loadedChunks,
            double worldBorderSize,
            Map<String, String> gameRules
    ) {
        public WorldInfo {
            gameRules = Map.copyOf(gameRules);
        }
    }

    public record RegistryEntry(String category, String id) {
    }
}
