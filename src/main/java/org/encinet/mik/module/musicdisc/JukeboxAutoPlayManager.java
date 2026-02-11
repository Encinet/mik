package org.encinet.mik.module.musicdisc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager for jukebox auto-play functionality
 */
public class JukeboxAutoPlayManager {

    private static final int DEFAULT_DURATION_SECONDS = 180; // 3 minutes
    private static final int DELAY_AFTER_TRACK_SECONDS = 2;
    private static final int PLAYER_SEARCH_RADIUS = 50;

    private final JavaPlugin plugin;
    private final JukeboxPlaylistManager playlistManager;
    private final MusicDiscCreator discCreator;
    private final MusicDiscPlayer discPlayer;
    private final Map<Location, BukkitTask> autoPlayTasks = new ConcurrentHashMap<>();

    public JukeboxAutoPlayManager(JavaPlugin plugin, JukeboxPlaylistManager playlistManager,
                                  MusicDiscCreator discCreator, MusicDiscPlayer discPlayer) {
        this.plugin = plugin;
        this.playlistManager = playlistManager;
        this.discCreator = discCreator;
        this.discPlayer = discPlayer;
    }

    /**
     * Schedule autoplay for next track based on current track duration
     */
    public void scheduleNextTrack(Location location, MusicFileLoader.MusicFile currentTrack) {
        // Cancel existing task if any
        cancelScheduledTask(location);

        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);
        if (!data.autoPlay) {
            return;
        }

        // Parse duration and calculate delay
        int durationSeconds = parseDuration(currentTrack.duration());
        if (durationSeconds <= 0) {
            durationSeconds = DEFAULT_DURATION_SECONDS;
        }

        // Schedule task for duration + delay
        long delayTicks = (durationSeconds + DELAY_AFTER_TRACK_SECONDS) * 20L;
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            playNextTrack(location);
        }, delayTicks);

        autoPlayTasks.put(location, task);
    }

    /**
     * Play next track on jukebox
     */
    private void playNextTrack(Location location) {
        JukeboxPlaylistManager.JukeboxData data = playlistManager.getJukeboxData(location);

        if (!data.autoPlay) {
            return;
        }

        Block block = location.getBlock();
        if (!(block.getState() instanceof Jukebox jukebox)) {
            return;
        }

        // Find nearest player to use for the event
        Player nearestPlayer = findNearestPlayer(location);
        if (nearestPlayer == null) {
            data.autoPlay = false;
            return;
        }

        // Get next track
        MusicFileLoader.MusicFile nextTrack = playlistManager.getNextTrack(location);
        if (nextTrack == null) {
            return;
        }

        // Play next track
        ItemStack disc = discCreator.createMusicDisc(nextTrack, false);

        // Remove from playlist if in sequential mode
        if (!data.isRandomMode) {
            data.removeFromPlaylist(nextTrack);
        }

        discPlayer.playDiscOnJukebox(nearestPlayer, jukebox, disc, nextTrack.displayName());

        // Schedule next autoplay
        scheduleNextTrack(location, nextTrack);
    }

    /**
     * Parse duration string (e.g., "3:45" -> 225 seconds)
     * Returns -1 if invalid or null
     */
    private int parseDuration(String duration) {
        if (duration == null || duration.isEmpty()) {
            return -1;
        }

        try {
            String[] parts = duration.split(":");
            if (parts.length == 2) {
                int minutes = Integer.parseInt(parts[0].trim());
                int seconds = Integer.parseInt(parts[1].trim());
                return minutes * 60 + seconds;
            } else if (parts.length == 1) {
                return Integer.parseInt(parts[0].trim());
            }
        } catch (NumberFormatException e) {
            // Invalid format
        }
        return -1;
    }

    /**
     * Find nearest player within search radius
     */
    private Player findNearestPlayer(Location location) {
        if (location.getWorld() == null) {
            return null;
        }

        Player nearest = null;
        double nearestDistanceSquared = PLAYER_SEARCH_RADIUS * PLAYER_SEARCH_RADIUS;

        for (Player player : location.getWorld().getPlayers()) {
            double distSquared = player.getLocation().distanceSquared(location);
            if (distSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distSquared;
                nearest = player;
            }
        }

        return nearest;
    }

    /**
     * Cancel scheduled task for a jukebox
     */
    public void cancelScheduledTask(Location location) {
        BukkitTask task = autoPlayTasks.remove(location);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Stop all autoplay tasks
     */
    public void stopAll() {
        for (BukkitTask task : autoPlayTasks.values()) {
            task.cancel();
        }
        autoPlayTasks.clear();
    }
}
