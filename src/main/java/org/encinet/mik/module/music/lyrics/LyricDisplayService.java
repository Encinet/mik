package org.encinet.mik.module.music.lyrics;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.music.catalog.MusicTrack;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/** Keeps lyrics synchronized in the action bar for nearby listeners. */
public final class LyricDisplayService {

    private static final long UPDATE_INTERVAL_TICKS = 5;
    private static final int REFRESH_AFTER_TICKS = 40;
    private static final int MAX_DISPLAY_CHARACTERS = 240;

    private final JavaPlugin plugin;
    private final LyricsService lyricsService;

    public LyricDisplayService(JavaPlugin plugin, LyricsService lyricsService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lyricsService = Objects.requireNonNull(lyricsService, "lyricsService");
    }

    public PlaybackLyrics start(Location location, MusicTrack track,
                                LongSupplier positionMillis, BooleanSupplier active,
                                IntSupplier rangeBlocks) {
        Handle handle = new Handle(location.clone(), positionMillis, active, rangeBlocks);
        lyricsService.load(track).whenComplete((lyrics, error) -> {
            if (error != null || lyrics == null || lyrics.isEmpty()) {
                handle.close();
                return;
            }
            if (handle.closed.get()) {
                return;
            }
            try {
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> startTask(handle, lyrics.get()));
            } catch (IllegalStateException ignored) {
                handle.close();
            }
        });
        return handle;
    }

    private void startTask(Handle handle, Lyrics lyrics) {
        if (handle.closed.get() || !handle.active.getAsBoolean()) {
            handle.close();
            return;
        }
        handle.lyrics = lyrics;
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> tick(handle), 0L, UPDATE_INTERVAL_TICKS);
        handle.task = task;
        if (handle.closed.get()) {
            task.cancel();
        }
    }

    private void tick(Handle handle) {
        if (handle.closed.get() || !handle.active.getAsBoolean()) {
            handle.close();
            return;
        }
        Optional<LyricLine> current = handle.lyrics.lineAt(
                Math.max(0, handle.positionMillis.getAsLong()));
        if (current.isEmpty()) {
            handle.clear(handle.audience);
            handle.audience = Set.of();
            return;
        }
        LyricLine line = current.get();
        if (line.text().isBlank()) {
            handle.clear(handle.audience);
            handle.audience = Set.of();
            return;
        }
        Component message = component(line);
        Set<UUID> audience = new HashSet<>();
        World world = handle.location.getWorld();
        if (world == null) {
            handle.close();
            return;
        }
        int rangeBlocks = Math.max(0, handle.rangeBlocks.getAsInt());
        double rangeSquared = (double) rangeBlocks * rangeBlocks;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(handle.location) <= rangeSquared) {
                audience.add(player.getUniqueId());
            }
        }
        Set<UUID> departed = new HashSet<>(handle.audience);
        departed.removeAll(audience);
        handle.clear(departed);
        Set<UUID> arrived = new HashSet<>(audience);
        arrived.removeAll(handle.audience);
        handle.audience = Set.copyOf(audience);

        boolean lineChanged = line.timestampMillis() != handle.lastLineTimestamp;
        boolean refresh = ++handle.ticksSinceDisplay
                >= REFRESH_AFTER_TICKS / UPDATE_INTERVAL_TICKS;
        if (!lineChanged && !refresh && arrived.isEmpty()) {
            return;
        }
        handle.lastLineTimestamp = line.timestampMillis();
        handle.ticksSinceDisplay = 0;
        for (UUID playerId : audience) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) {
                player.sendActionBar(message);
            }
        }
    }

    static Component component(LyricLine line) {
        String primary = truncate(line.text(), MAX_DISPLAY_CHARACTERS);
        String secondary = line.translation() != null
                ? line.translation() : line.romanization();
        Component result = Component.text(primary, NamedTextColor.WHITE);
        if (secondary != null && !secondary.equals(primary)) {
            int remaining = Math.max(1, MAX_DISPLAY_CHARACTERS - primary.length() - 3);
            result = result.append(Component.text(" / ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(truncate(secondary, remaining), NamedTextColor.GRAY));
        }
        return result;
    }

    private static String truncate(String value, int maximum) {
        int length = value.codePointCount(0, value.length());
        if (length <= maximum) {
            return value;
        }
        int end = value.offsetByCodePoints(0, Math.max(1, maximum - 3));
        return value.substring(0, end).stripTrailing() + "...";
    }

    public interface PlaybackLyrics extends AutoCloseable {
        @Override
        void close();
    }

    private final class Handle implements PlaybackLyrics {
        private final Location location;
        private final LongSupplier positionMillis;
        private final BooleanSupplier active;
        private final IntSupplier rangeBlocks;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile Lyrics lyrics;
        private volatile BukkitTask task;
        private volatile Set<UUID> audience = Set.of();
        private long lastLineTimestamp = -1;
        private int ticksSinceDisplay;

        private Handle(Location location, LongSupplier positionMillis, BooleanSupplier active,
                       IntSupplier rangeBlocks) {
            this.location = Objects.requireNonNull(location, "location");
            this.positionMillis = Objects.requireNonNull(positionMillis, "positionMillis");
            this.active = Objects.requireNonNull(active, "active");
            this.rangeBlocks = Objects.requireNonNull(rangeBlocks, "rangeBlocks");
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            BukkitTask currentTask = task;
            task = null;
            if (currentTask != null) {
                currentTask.cancel();
            }
            Set<UUID> previousAudience = audience;
            audience = Set.of();
            if (Bukkit.isPrimaryThread()) {
                clear(previousAudience);
            } else {
                try {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> clear(previousAudience));
                } catch (IllegalStateException ignored) {
                    // The server is already shutting down; action bars will expire naturally.
                }
            }
        }

        private void clear(Set<UUID> players) {
            for (UUID playerId : players) {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    player.sendActionBar(Component.empty());
                }
            }
        }
    }
}
