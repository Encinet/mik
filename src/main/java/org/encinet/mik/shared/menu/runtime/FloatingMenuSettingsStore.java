package org.encinet.mik.shared.menu.runtime;

import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Logger;

/** In-memory reads; one owned worker serializes loading and atomic preference writes. */
final class FloatingMenuSettingsStore implements AutoCloseable {
    private final File settingsFile;
    private final Logger logger;
    private final Map<UUID, FloatingMenuPreferences> values = new ConcurrentHashMap<>();
    private ExecutorService io;
    private CompletableFuture<Void> startup;
    // Accessed only by the I/O worker.
    private YamlConfiguration data;
    private boolean enabled;

    FloatingMenuSettingsStore(JavaPlugin plugin) {
        this(new File(Objects.requireNonNull(plugin).getDataFolder(), "menu-settings.yml"), plugin.getLogger());
    }
    FloatingMenuSettingsStore(File settingsFile, Logger logger) {
        this.settingsFile = Objects.requireNonNull(settingsFile);
        this.logger = Objects.requireNonNull(logger);
    }

    CompletableFuture<Void> enable() {
        if (io != null) throw new IllegalStateException("Menu settings have already been started");
        enabled = true;
        io = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("mik-menu-preferences").factory());
        startup = CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(settingsFile.toPath().toAbsolutePath().getParent());
                var loaded = new YamlConfiguration();
                if (settingsFile.isFile()) loaded.load(settingsFile);
                for (String key : loaded.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(key);
                        values.putIfAbsent(id, new FloatingMenuPreferences(
                                FloatingMenuScale.fromId(loaded.getString(key + ".scale")),
                                FloatingMenuTextScale.fromId(loaded.getString(key + ".text-scale")),
                                FloatingMenuFieldOfView.fromDegrees(loaded.getInt(key + ".field-of-view", 70)),
                                loaded.getBoolean(key + ".reduced-motion", false),
                                readTheme(loaded, key)));
                    } catch (IllegalArgumentException ignored) { logger.warning("Invalid player id in menu settings: " + key); }
                }
                data = loaded;
            } catch (Exception error) {
                logger.warning("Failed to load menu settings: " + error.getMessage());
                throw new CompletionException(error);
            }
        }, io);
        return startup;
    }

    FloatingMenuPreferences preferences(UUID playerId) {
        requireEnabled();
        return values.getOrDefault(playerId, FloatingMenuPreferences.DEFAULT);
    }

    void setPreferences(UUID playerId, FloatingMenuPreferences preferences) {
        requireEnabled();
        FloatingMenuPreferences selected = Objects.requireNonNull(preferences);
        if (startup.isDone() && selected.equals(preferences(playerId))) return;
        values.put(playerId, selected);
        io.execute(() -> {
            if (data == null) { logger.warning("Menu preferences were not saved because loading failed"); return; }
            data.set(playerId + ".scale", selected.layout().id());
            data.set(playerId + ".text-scale", selected.text().id());
            data.set(playerId + ".field-of-view", selected.fieldOfView().degrees());
            data.set(playerId + ".reduced-motion", selected.reducedMotion());
            data.set(playerId + ".theme", selected.theme().id());
            data.set(playerId + ".custom-theme", null);
            if (selected.theme() instanceof UiCustomTheme custom) for (var role : UiColorRole.values()) {
                String path = playerId + ".custom-theme." + role.id();
                data.set(path + ".color", String.format(java.util.Locale.ROOT, "#%06X", custom.rgb(role)));
                data.set(path + ".opacity", custom.alpha(role));
            }
            try { save(); }
            catch (IOException error) { logger.warning("Failed to save menu settings: " + error.getMessage()); }
        });
    }

    private UiThemeColors readTheme(YamlConfiguration loaded, String player) {
        if (!"custom".equalsIgnoreCase(loaded.getString(player + ".theme")))
            return UiPalette.fromId(loaded.getString(player + ".theme"));
        try {
            var colors = new java.util.EnumMap<UiColorRole, UiColor.Literal>(UiColorRole.class);
            for (var role : UiColorRole.values()) {
                String path = player + ".custom-theme." + role.id();
                String rgb = loaded.getString(path + ".color", "");
                Object opacity = loaded.get(path + ".opacity");
                if (!rgb.matches("#[0-9a-fA-F]{6}") || !(opacity instanceof Number number))
                    throw new IllegalArgumentException("Missing or invalid " + role.id());
                colors.put(role, new UiColor.Literal(Integer.parseInt(rgb.substring(1), 16), number.doubleValue()));
            }
            return new UiCustomTheme(colors);
        } catch (IllegalArgumentException error) {
            logger.warning("Invalid custom menu theme for " + player + ": " + error.getMessage());
            return UiPalette.DEFAULT;
        }
    }

    private void save() throws IOException {
        var destination = settingsFile.toPath().toAbsolutePath();
        var temporary = Files.createTempFile(destination.getParent(), "menu-settings-", ".tmp");
        try {
            Files.writeString(temporary, data.saveToString());
            try { Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }

    CompletableFuture<Void> flush() {
        requireEnabled();
        return CompletableFuture.runAsync(() -> { }, io);
    }

    @Override public void close() {
        enabled = false;
        if (io == null) return;
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warning("Menu preference writer did not stop within five seconds");
                io.shutdownNow();
            }
        } catch (InterruptedException error) { io.shutdownNow(); Thread.currentThread().interrupt(); }
    }

    private void requireEnabled() {
        if (!enabled) throw new IllegalStateException("Floating menu settings are not enabled");
    }
}
