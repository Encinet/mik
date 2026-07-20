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

public final class PlayerAddressModule implements Listener, PlayerAddressLookup, PlayerAssociationLookup {

    private static final String DATA_FILE_NAME = "player-addresses.tsv";
    private static final String HEADER = "# address\tplayer-id\tcount\tlast-seen-at";
    private static final long SAVE_DELAY_TICKS = 20L * 30L;
    private static final Duration REVERSE_LOOKUP_WINDOW = Duration.ofDays(30);

    private final JavaPlugin plugin;
    private final PlayerAddressStore addressStore = new PlayerAddressStore();
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
        Optional<PlayerAddressStore.PlayerAddressRecord> record = inferPlayerRecordByAddress(addressText, Instant.now());
        return record
                .map(value -> new AddressPlayer(value.playerId(), displayName(value.playerId()).orElse(null)));
    }

    @Override
    public List<AddressUse> recentPlayersByAddress(InetAddress address, Instant notBefore) {
        if (address == null || notBefore == null) {
            return List.of();
        }
        return addressStore.recordsByAddress(addressKey(address)).stream()
                .filter(record -> !record.lastSeenAt().isBefore(notBefore))
                .sorted(Comparator.comparing(PlayerAddressStore.PlayerAddressRecord::lastSeenAt).reversed())
                .map(record -> new AddressUse(record.playerId(), record.lastSeenAt()))
                .toList();
    }

    @Override
    public List<PlayerAssociation> findAssociations(UUID playerId) {
        return addressStore.findAssociations(playerId);
    }

    private void recordLogin(UUID playerId, InetAddress address, Instant now) {
        if (playerId == null || address == null) {
            return;
        }

        String addressText = addressKey(address);
        PlayerAddressStore.PlayerAddressRecord updated = addressStore.recordLogin(addressText, playerId, now);
        changeVersion.incrementAndGet();
        scheduleSave();

        plugin.getLogger().fine("Recorded login address history for " + updated.playerId());
    }

    private Optional<PlayerAddressStore.PlayerAddressRecord> inferPlayerRecordByAddress(String address, Instant now) {
        return addressStore.latestByAddress(address)
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
            List<PlayerAddressStore.PlayerAddressRecord> snapshot = snapshot();
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

    private List<PlayerAddressStore.PlayerAddressRecord> snapshot() {
        return addressStore.snapshotRecords();
    }

    private void load() {
        addressStore.clear();

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
                readRecord(line, lineNumber).ifPresent(addressStore::put);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load " + DATA_FILE_NAME + ": " + e.getMessage());
        }
    }

    private Optional<PlayerAddressStore.PlayerAddressRecord> readRecord(String line, int lineNumber) {
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
            return Optional.of(new PlayerAddressStore.PlayerAddressRecord(address, playerId, count, lastSeenAt));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Invalid row in " + DATA_FILE_NAME + " at line " + lineNumber + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    private void saveSnapshot(long snapshotVersion, List<PlayerAddressStore.PlayerAddressRecord> snapshot) {
        if (saveNow(snapshot)) {
            savedVersion = snapshotVersion;
        }
    }

    private boolean saveNow(List<PlayerAddressStore.PlayerAddressRecord> snapshot) {
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
                for (PlayerAddressStore.PlayerAddressRecord record : snapshot) {
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

    public record AddressPlayer(UUID playerId, String playerName) {
        public Optional<String> playerNameOptional() {
            return Optional.ofNullable(playerName);
        }
    }
}
