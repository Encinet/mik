package org.encinet.mik.module.player.address;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
import java.util.logging.Level;

public final class PlayerAddressModule implements Listener, PlayerAddressLookup,
        PlayerAddressIdentityLookup, PlayerAssociationLookup {

    private static final String DATA_FILE_NAME = "player-addresses.tsv";
    private static final long SAVE_DELAY_TICKS = 20L * 30L;
    private static final Duration REVERSE_LOOKUP_WINDOW = Duration.ofDays(30);

    private final JavaPlugin plugin;
    private final PlayerAddressStore addressStore = new PlayerAddressStore();
    private final PlayerAddressFile addressFile;
    private final Map<UUID, String> displayNamesByPlayer = new ConcurrentHashMap<>();
    private final Object saveTaskLock = new Object();

    private final AtomicReference<BukkitTask> saveTask = new AtomicReference<>();
    private final AtomicBoolean saveScheduled = new AtomicBoolean();
    private final AtomicLong changeVersion = new AtomicLong();
    private volatile boolean enabled;
    private boolean loaded;

    public PlayerAddressModule(JavaPlugin plugin) {
        this.plugin = plugin;
        addressFile = new PlayerAddressFile(plugin.getDataFolder().toPath().resolve(DATA_FILE_NAME));
    }

    public void enable() {
        loaded = false;
        List<PlayerAddressStore.PlayerAddressRecord> records;
        try {
            records = addressFile.load();
        } catch (IOException error) {
            throw new IllegalStateException("Could not load " + DATA_FILE_NAME
                    + "; original file was left untouched", error);
        }
        addressStore.clear();
        records.forEach(addressStore::put);
        loaded = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            rememberDisplayName(player.getUniqueId(), player.getName());
        }
        enabled = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void disable() {
        enabled = false;
        HandlerList.unregisterAll(this);
        synchronized (saveTaskLock) {
            BukkitTask task = saveTask.getAndSet(null);
            if (task != null) {
                task.cancel();
            }
        }
        saveScheduled.set(false);
        if (loaded && changeVersion.get() > addressFile.savedVersion()) {
            saveSnapshot(changeVersion.get(), snapshot());
        }
        loaded = false;
        displayNamesByPlayer.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        rememberDisplayName(player.getUniqueId(), player.getName());
        recordLogin(player.getUniqueId(), joinedAddress(player), Instant.now());
    }

    @Override
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
            if (enabled && changeVersion.get() > addressFile.savedVersion()) {
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

    private void saveSnapshot(long snapshotVersion, List<PlayerAddressStore.PlayerAddressRecord> snapshot) {
        try {
            addressFile.saveIfNewer(snapshotVersion, snapshot);
        } catch (IOException error) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + DATA_FILE_NAME, error);
        }
    }

}
