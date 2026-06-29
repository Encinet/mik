package org.encinet.mik.module.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerAddressModule implements Listener {

    private static final String DATA_FILE_NAME = "player-addresses.yml";
    private static final long SAVE_DELAY_TICKS = 20L * 30L;

    private final JavaPlugin plugin;
    private final Map<String, AddressHistory> historiesByAddress = new ConcurrentHashMap<>();
    private final Map<UUID, String> latestAddressByPlayer = new ConcurrentHashMap<>();

    private File dataFile;
    private volatile BukkitTask saveTask;
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private final AtomicLong changeVersion = new AtomicLong();
    private volatile long savedVersion;

    public PlayerAddressModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        dataFile = new File(plugin.getDataFolder(), DATA_FILE_NAME);
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveScheduled.set(false);
        if (changeVersion.get() != savedVersion) {
            long snapshotVersion = changeVersion.get();
            if (saveNow(snapshot())) {
                savedVersion = snapshotVersion;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            return;
        }

        InetAddress address = event.getRealAddress();
        if (address == null) {
            address = event.getAddress();
        }
        recordLogin(event.getPlayer(), address, Instant.now());
    }

    public Optional<PlayerAddressRecord> resolveLatestByAddress(InetAddress address) {
        if (address == null) {
            return Optional.empty();
        }
        return resolveLatestByAddress(addressKey(address));
    }

    public Optional<PlayerAddressRecord> resolveLatestByPlayer(UUID playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        String address = latestAddressByPlayer.get(playerId);
        if (address == null) {
            return Optional.empty();
        }
        return resolveLatestByAddress(address);
    }

    public boolean hasAddressHistory(InetAddress address) {
        return address != null && historiesByAddress.containsKey(addressKey(address));
    }

    private void recordLogin(Player player, InetAddress address, Instant now) {
        if (player == null || address == null) {
            return;
        }

        String addressText = addressKey(address);
        UUID playerId = player.getUniqueId();
        AddressHistory history = historiesByAddress.computeIfAbsent(addressText, ignored -> new AddressHistory());
        PlayerAddressRecord updated = history.record(playerId, player.getName(), now);
        latestAddressByPlayer.put(playerId, addressText);
        changeVersion.incrementAndGet();
        scheduleSave();

        plugin.getLogger().fine("Recorded login address " + addressText + " for " + updated.playerName());
    }

    private Optional<PlayerAddressRecord> resolveLatestByAddress(String address) {
        AddressHistory history = historiesByAddress.get(address);
        if (history == null) {
            return Optional.empty();
        }
        return history.latest();
    }

    private String addressKey(InetAddress address) {
        return address.getHostAddress();
    }

    private void scheduleSave() {
        if (!saveScheduled.compareAndSet(false, true)) {
            return;
        }
        saveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            long snapshotVersion = changeVersion.get();
            Map<String, AddressHistory> snapshot = snapshot();
            if (saveNow(snapshot)) {
                savedVersion = snapshotVersion;
            }
            saveTask = null;
            saveScheduled.set(false);
            if (changeVersion.get() != snapshotVersion) {
                scheduleSave();
            }
        }, SAVE_DELAY_TICKS);
    }

    private Map<String, AddressHistory> snapshot() {
        Map<String, AddressHistory> snapshot = new HashMap<>();
        historiesByAddress.forEach((address, history) -> snapshot.put(address, history.copy()));
        return snapshot;
    }

    private void load() {
        historiesByAddress.clear();
        latestAddressByPlayer.clear();

        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection addresses = config.getConfigurationSection("addresses");
        if (addresses == null) {
            return;
        }

        for (String encodedAddress : addresses.getKeys(false)) {
            String address = decodeAddress(encodedAddress);
            ConfigurationSection players = addresses.getConfigurationSection(encodedAddress + ".players");
            if (players == null) {
                continue;
            }

            AddressHistory history = new AddressHistory();
            for (String uuidText : players.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidText);
                    String base = encodedAddress + ".players." + uuidText;
                    String playerName = addresses.getString(base + ".name", fallbackPlayerName(playerId));
                    int count = Math.max(0, addresses.getInt(base + ".count", 0));
                    Instant lastSeenAt = parseInstant(addresses.getString(base + ".last-seen-at"));
                    if (count <= 0 || lastSeenAt == null) {
                        continue;
                    }
                    history.put(new PlayerAddressRecord(playerId, playerName, count, lastSeenAt));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid UUID in " + DATA_FILE_NAME + ": " + uuidText);
                }
            }

            if (history.latest().isPresent()) {
                historiesByAddress.put(address, history);
                history.records().values().forEach(record -> rememberLatestAddress(record.playerId(), address, record.lastSeenAt()));
            }
        }
    }

    private void rememberLatestAddress(UUID playerId, String address, Instant lastSeenAt) {
        latestAddressByPlayer.merge(playerId, address, (current, next) -> {
            Instant currentSeen = Optional.ofNullable(historiesByAddress.get(current))
                    .flatMap(history -> Optional.ofNullable(history.records().get(playerId)))
                    .map(PlayerAddressRecord::lastSeenAt)
                    .orElse(Instant.EPOCH);
            return lastSeenAt.isAfter(currentSeen) ? next : current;
        });
    }

    private boolean saveNow(Map<String, AddressHistory> snapshot) {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for " + DATA_FILE_NAME);
            return false;
        }

        YamlConfiguration config = new YamlConfiguration();
        snapshot.forEach((address, history) -> history.records().forEach((playerId, record) -> {
            String base = "addresses." + encodeAddress(address) + ".players." + playerId;
            config.set(base + ".name", record.playerName());
            config.set(base + ".count", record.count());
            config.set(base + ".last-seen-at", record.lastSeenAt().toString());
        }));

        try {
            config.save(dataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + DATA_FILE_NAME + ": " + e.getMessage());
            return false;
        }
    }

    private String fallbackPlayerName(UUID playerId) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
        return player.getName() != null ? player.getName() : playerId.toString();
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String encodeAddress(String address) {
        return address.replace(".", "_").replace(":", "-");
    }

    private String decodeAddress(String encoded) {
        return encoded.replace("_", ".").replace("-", ":");
    }

    public record PlayerAddressRecord(UUID playerId, String playerName, int count, Instant lastSeenAt) {
    }

    private static final class AddressHistory {
        private final Map<UUID, PlayerAddressRecord> records = new ConcurrentHashMap<>();
        private volatile PlayerAddressRecord latest;

        PlayerAddressRecord record(UUID playerId, String playerName, Instant now) {
            PlayerAddressRecord updated = records.compute(playerId, (ignored, previous) -> new PlayerAddressRecord(
                    playerId,
                    playerName,
                    previous == null ? 1 : previous.count() + 1,
                    now));
            rememberLatest(updated);
            return updated;
        }

        void put(PlayerAddressRecord record) {
            records.put(record.playerId(), record);
            rememberLatest(record);
        }

        Optional<PlayerAddressRecord> latest() {
            return Optional.ofNullable(latest);
        }

        Map<UUID, PlayerAddressRecord> records() {
            return records;
        }

        AddressHistory copy() {
            AddressHistory copy = new AddressHistory();
            copy.records.putAll(records);
            copy.latest = latest;
            return copy;
        }

        private synchronized void rememberLatest(PlayerAddressRecord candidate) {
            PlayerAddressRecord current = latest;
            if (current == null || candidate.lastSeenAt().isAfter(current.lastSeenAt())) {
                latest = candidate;
            }
        }
    }
}
