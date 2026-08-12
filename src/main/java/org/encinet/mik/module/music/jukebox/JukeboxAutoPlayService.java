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

    private final JavaPlugin plugin;
    private final JukeboxQueueService queueService;
    private final JukeboxPlayback playback;
    private final JukeboxSettingsStore settingsStore;
    private final Map<Location, BukkitTask> autoPlayTasks = new HashMap<>();

    public JukeboxAutoPlayService(JavaPlugin plugin, JukeboxQueueService queueService,
                                  JukeboxPlayback playback) {
        this(plugin, queueService, playback, new JukeboxSettingsStore());
    }

    public JukeboxAutoPlayService(JavaPlugin plugin, JukeboxQueueService queueService,
                                  JukeboxPlayback playback,
                                  JukeboxSettingsStore settingsStore) {
        this.plugin = plugin;
        this.queueService = queueService;
        this.playback = playback;
        this.settingsStore = settingsStore;
    }

    public void onTrackFinished(Location location, MusicTrack finishedTrack) {
        Location blockLocation = location.getBlock().getLocation();
        cancelScheduledTask(blockLocation);

        JukeboxQueueService.JukeboxState data = queueService.findState(blockLocation);
        if (data == null) {
            return;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(
                plugin, () -> playNextTrack(blockLocation, finishedTrack, null),
                NEXT_TRACK_DELAY_TICKS);
        autoPlayTasks.put(blockLocation, task);
    }

    public boolean playNextTrack(Location location) {
        return playNextTrack(location, null, null);
    }

    public boolean playNextTrack(Location location, Player requestingPlayer) {
        return playNextTrack(location, null, requestingPlayer);
    }

    private boolean playNextTrack(Location location, MusicTrack finishedTrack,
                                  Player requestingPlayer) {
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

        Player nearestPlayer = requestingPlayer == null
                ? findNearestPlayer(blockLocation,
                        settingsStore.read(jukebox).rangeBlocks())
                : requestingPlayer;
        if (finishedTrack != null && nearestPlayer == null) {
            return false;
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

        boolean notifyRequesterIfInaudible = requestingPlayer != null;
        boolean repeatInsertedDisc = finishedTrack != null
                && data.playbackMode() == JukeboxPlaybackMode.REPEAT_ONE
                && finishedTrack.id().equals(MusicDiscKeys.trackId(jukebox.getRecord()));
        boolean accepted = repeatInsertedDisc
                ? playback.playInsertedDisc(nearestPlayer, jukebox,
                        notifyRequesterIfInaudible)
                : playback.playVirtualTrackOnJukebox(
                        nearestPlayer, jukebox, nextTrack, () -> {},
                        notifyRequesterIfInaudible);
        if (!accepted) {
            return false;
        }
        return true;
    }

    static Player findNearestPlayer(Location location, int rangeBlocks) {
        if (location.getWorld() == null) {
            return null;
        }

        Player nearest = null;
        double nearestDistanceSquared = (double) rangeBlocks * rangeBlocks;
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
