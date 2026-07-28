package org.encinet.mik.module.music.jukebox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.disc.MusicDiscKeys;

import java.util.Map;
import java.util.HashMap;

/**
 * Advances queues on the Bukkit main thread after playback reports a track ending.
 */
public class JukeboxAutoPlayService {

    private static final long NEXT_TRACK_DELAY_TICKS = 40L;
    private static final int PLAYER_SEARCH_RADIUS = 65;

    private final JavaPlugin plugin;
    private final JukeboxQueueService queueService;
    private final JukeboxPlayback playback;
    private final Map<Location, BukkitTask> autoPlayTasks = new HashMap<>();

    public JukeboxAutoPlayService(JavaPlugin plugin, JukeboxQueueService queueService,
                                  JukeboxPlayback playback) {
        this.plugin = plugin;
        this.queueService = queueService;
        this.playback = playback;
    }

    public void onTrackFinished(Location location, MusicTrack finishedTrack) {
        Location blockLocation = location.getBlock().getLocation();
        cancelScheduledTask(blockLocation);

        JukeboxQueueService.JukeboxState data = queueService.findState(blockLocation);
        if (data == null) {
            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin, () -> playNextTrack(blockLocation, finishedTrack), NEXT_TRACK_DELAY_TICKS);
        autoPlayTasks.put(blockLocation, task);
    }

    public boolean playNextTrack(Location location) {
        return playNextTrack(location, null);
    }

    private boolean playNextTrack(Location location, MusicTrack finishedTrack) {
        Location blockLocation = location.getBlock().getLocation();
        cancelScheduledTask(blockLocation);

        JukeboxQueueService.JukeboxState data = finishedTrack == null
                ? queueService.state(blockLocation)
                : queueService.findState(blockLocation);
        if (data == null) {
            return false;
        }
        Block block = blockLocation.getBlock();
        if (!(block.getState() instanceof Jukebox jukebox)) {
            return false;
        }

        if (finishedTrack != null) {
            if (playback.isPlaying(block)
                    || !finishedTrack.id().equals(MusicDiscKeys.trackId(jukebox.getRecord()))) {
                return false;
            }
        }

        MusicTrack currentTrack = finishedTrack != null
                ? finishedTrack
                : queueService.trackById(
                        blockLocation, MusicDiscKeys.trackId(jukebox.getRecord()));
        MusicTrack nextTrack = queueService.nextTrack(
                blockLocation, currentTrack, finishedTrack != null);
        if (nextTrack == null) {
            return false;
        }

        Player nearestPlayer = findNearestPlayer(blockLocation);
        boolean repeatInsertedDisc = finishedTrack != null
                && data.playbackMode() == JukeboxPlaybackMode.REPEAT_ONE
                && finishedTrack.id().equals(MusicDiscKeys.trackId(jukebox.getRecord()));
        boolean accepted = repeatInsertedDisc
                ? playback.playInsertedDisc(nearestPlayer, jukebox)
                : playback.playVirtualTrackOnJukebox(
                        nearestPlayer, jukebox, nextTrack, () -> {});
        if (!accepted) {
            return false;
        }
        return true;
    }

    private Player findNearestPlayer(Location location) {
        if (location.getWorld() == null) {
            return null;
        }

        Player nearest = null;
        double nearestDistanceSquared = PLAYER_SEARCH_RADIUS * PLAYER_SEARCH_RADIUS;
        for (Player player : location.getWorld().getPlayers()) {
            double distanceSquared = player.getLocation().distanceSquared(location);
            if (distanceSquared <= nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = player;
            }
        }
        return nearest;
    }

    public void cancelScheduledTask(Location location) {
        BukkitTask task = autoPlayTasks.remove(location.getBlock().getLocation());
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAll() {
        for (BukkitTask task : autoPlayTasks.values()) {
            task.cancel();
        }
        autoPlayTasks.clear();
    }

    public void removeWorld(World world) {
        if (world == null) {
            return;
        }
        for (Map.Entry<Location, BukkitTask> entry : Map.copyOf(autoPlayTasks).entrySet()) {
            if (world.equals(entry.getKey().getWorld())
                    && autoPlayTasks.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().cancel();
            }
        }
    }
}
