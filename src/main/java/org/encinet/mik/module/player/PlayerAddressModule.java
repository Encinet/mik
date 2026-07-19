package org.encinet.mik.module.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class PlayerAddressModule implements Listener, PlayerAddressLookup {

    private static final String DATA_FILE_NAME = "player-addresses.tsv";
    private static final String HEADER = "# address\tplayer-id\tcount\tlast-seen-at";
    private static final long SAVE_DELAY_TICKS = 20L * 30L;
    private static final Duration REVERSE_LOOKUP_WINDOW = Duration.ofDays(30);

    private final JavaPlugin plugin;
    private final Map<String, AddressHistory> historiesByAddress = new ConcurrentHashMap<>();
    private final Map<UUID, String> displayNamesByPlayer = new ConcurrentHashMap<>();
    private final ReentrantLock saveLock = new ReentrantLock();
    private final Object saveTaskLock = new Object();

    private File dataFile;
    private final AtomicReference<BukkitTask> saveTask = new AtomicReference<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private final AtomicLong changeVersion = new AtomicLong();
    private volatile boolean enabled;
    private volatile long savedVersion;

    public PlayerAddressModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        dataFile = new File(plugin.getDataFolder(), DATA_FILE_NAME);
        load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            rememberDisplayName(player.getUniqueId(), player.getName());
        }
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        enabled = false;
        synchronized (saveTaskLock) {
            BukkitTask task = saveTask.getAndSet(null);
            if (task != null) {
                task.cancel();
            }
        }
        saveScheduled.set(false);
        if (changeVersion.get() != savedVersion) {
            saveSnapshot(changeVersion.get(), snapshot());
        }
        displayNamesByPlayer.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        rememberDisplayName(player.getUniqueId(), player.getName());
        recordLogin(player.getUniqueId(), joinedAddress(player), Instant.now());
    }

    public Optional<AddressPlayer> inferPlayerByAddress(InetAddress address) {
        if (address == null) {
            return Optional.empty();
        }

        String addressText = addressKey(address);
        Optional<PlayerAddressRecord> record = inferPlayerRecordByAddress(addressText, Instant.now());
        return record
                .map(value -> new AddressPlayer(value.playerId(), displayName(value.playerId()).orElse(null)));
    }

    @Override
    public List<AddressUse> recentPlayersByAddress(InetAddress address, Instant notBefore) {
        if (address == null || notBefore == null) {
            return List.of();
        }
        AddressHistory history = historiesByAddress.get(addressKey(address));
        if (history == null) {
            return List.of();
        }
        return history.records().values().stream()
                .filter(record -> !record.lastSeenAt().isBefore(notBefore))
                .sorted(Comparator.comparing(PlayerAddressRecord::lastSeenAt).reversed())
                .map(record -> new AddressUse(record.playerId(), record.lastSeenAt()))
                .toList();
    }

    private void recordLogin(UUID playerId, InetAddress address, Instant now) {
        if (playerId == null || address == null) {
            return;
        }

        String addressText = addressKey(address);
        AddressHistory history = historiesByAddress.computeIfAbsent(addressText, ignored -> new AddressHistory());
        PlayerAddressRecord updated = history.record(addressText, playerId, now);
        if (updated == null) {
            return;
        }
        changeVersion.incrementAndGet();
        scheduleSave();

        plugin.getLogger().fine("Recorded login address " + addressText + " for " + updated.playerId());
    }

    private Optional<PlayerAddressRecord> inferPlayerRecordByAddress(String address, Instant now) {
        AddressHistory history = historiesByAddress.get(address);
        if (history == null) {
            return Optional.empty();
        }
        return history.latest()
                .filter(record -> !record.lastSeenAt().plus(REVERSE_LOOKUP_WINDOW).isBefore(now));
    }

    private String addressKey(InetAddress address) {
        return address.getHostAddress();
    }

    private Optional<String> displayName(UUID playerId) {
        return Optional.ofNullable(displayNamesByPlayer.get(playerId));
    }

    private void rememberDisplayName(UUID playerId, String playerName) {
        if (playerId == null || !isValidPlayerName(playerName)) {
            return;
        }
        displayNamesByPlayer.put(playerId, playerName);
    }

    private boolean isValidPlayerName(String playerName) {
        if (playerName == null || playerName.length() > 16 || playerName.isEmpty()) {
            return false;
        }
        for (int i = 0; i < playerName.length(); i++) {
            char c = playerName.charAt(i);
            boolean valid = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_';
            if (!valid) {
                return false;
            }
        }
        return true;
    }

    private InetAddress joinedAddress(Player player) {
        InetSocketAddress socketAddress = player.getAddress();
        return socketAddress == null ? null : socketAddress.getAddress();
    }

    private void scheduleSave() {
        if (!enabled) {
            return;
        }
        if (!saveScheduled.compareAndSet(false, true)) {
            return;
        }
        AtomicReference<BukkitTask> scheduledTask = new AtomicReference<>();
        BukkitTask task = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            long snapshotVersion = changeVersion.get();
            Map<String, AddressHistory> snapshot = snapshot();
            saveSnapshot(snapshotVersion, snapshot);
            saveTask.compareAndSet(scheduledTask.get(), null);
            saveScheduled.set(false);
            if (enabled && changeVersion.get() != snapshotVersion) {
                scheduleSave();
            }
        }, SAVE_DELAY_TICKS);
        scheduledTask.set(task);
        synchronized (saveTaskLock) {
            if (!enabled) {
                task.cancel();
                saveScheduled.set(false);
                return;
            }
            saveTask.set(task);
        }
    }

    private Map<String, AddressHistory> snapshot() {
        Map<String, AddressHistory> snapshot = new HashMap<>();
        historiesByAddress.forEach((address, history) -> snapshot.put(address, history.copy()));
        return snapshot;
    }

    private void load() {
        historiesByAddress.clear();

        if (!dataFile.exists()) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber += 1;
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                readRecord(line, lineNumber).ifPresent(record -> {
                    historiesByAddress
                            .computeIfAbsent(record.address(), ignored -> new AddressHistory())
                            .put(record);
                });
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load " + DATA_FILE_NAME + ": " + e.getMessage());
        }
    }

    private Optional<PlayerAddressRecord> readRecord(String line, int lineNumber) {
        String[] parts = line.split("\\t", -1);
        if (parts.length != 4) {
            plugin.getLogger().warning("Invalid row in " + DATA_FILE_NAME + " at line " + lineNumber);
            return Optional.empty();
        }

        try {
            String address = parts[0];
            UUID playerId = UUID.fromString(parts[1]);
            int count = Integer.parseInt(parts[2]);
            Instant lastSeenAt = Instant.parse(parts[3]);
            if (address.isBlank() || count <= 0) {
                return Optional.empty();
            }
            return Optional.of(new PlayerAddressRecord(address, playerId, count, lastSeenAt));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Invalid row in " + DATA_FILE_NAME + " at line " + lineNumber + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private void saveSnapshot(long snapshotVersion, Map<String, AddressHistory> snapshot) {
        if (saveNow(snapshot)) {
            savedVersion = snapshotVersion;
        }
    }

    private boolean saveNow(Map<String, AddressHistory> snapshot) {
        saveLock.lock();
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Failed to create plugin data folder for " + DATA_FILE_NAME);
                return false;
            }

            Path target = dataFile.toPath();
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(HEADER);
                writer.newLine();
                for (AddressHistory history : snapshot.values()) {
                    for (PlayerAddressRecord record : history.records().values()) {
                        writer.write(record.address());
                        writer.write('\t');
                        writer.write(record.playerId().toString());
                        writer.write('\t');
                        writer.write(Integer.toString(record.count()));
                        writer.write('\t');
                        writer.write(record.lastSeenAt().toString());
                        writer.newLine();
                    }
                }
            }
            moveIntoPlace(temp, target);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + DATA_FILE_NAME + ": " + e.getMessage());
            return false;
        } finally {
            saveLock.unlock();
        }
    }

    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record PlayerAddressRecord(String address, UUID playerId, int count, Instant lastSeenAt) {
    }

    public record AddressPlayer(UUID playerId, String playerName) {
        public Optional<String> playerNameOptional() {
            return Optional.ofNullable(playerName);
        }
    }

    private static final class AddressHistory {
        private final Map<UUID, PlayerAddressRecord> records = new ConcurrentHashMap<>();
        private volatile PlayerAddressRecord latest;

        PlayerAddressRecord record(String address, UUID playerId, Instant now) {
            PlayerAddressRecord updated = records.compute(playerId, (ignored, current) -> new PlayerAddressRecord(
                        address,
                        playerId,
                        loginCount(current, now),
                        now));
            rememberLatest(updated);
            return updated;
        }

        private int loginCount(PlayerAddressRecord previous, Instant now) {
            if (previous == null) {
                return 1;
            }
            return previous.count() + 1;
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
