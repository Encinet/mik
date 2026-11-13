package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.sound.StaticSound;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;

/** Starts client record sounds together on the main thread, without making inventory copies. */
final class NativeRecordPlaybackEngine implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Predicate<UUID> audible;
    private final Set<Session> sessions = new HashSet<>();
    private BiPredicate<Location,UUID> audience = (_,_) -> true;
    NativeRecordPlaybackEngine(JavaPlugin plugin, Predicate<UUID> audible) {
        this.plugin = plugin; this.audible = audible;
    }
    void setOutputAudience(BiPredicate<Location,UUID> filter) { audience = filter; }
    PlaybackSession create(Location location, MusicTrack track, JukeboxSoundSettings settings,
                           PlaybackCallbacks callbacks, JukeboxPlaybackGroup group) {
        var session = new Session(location.clone(),track,settings,callbacks,group);
        sessions.add(session);
        return session;
    }
    @Override public void close() { List.copyOf(sessions).forEach(Session::stop); }

    private final class Session implements PlaybackSession {
        private final Location location;
        private final MusicTrack track;
        private final String sound;
        private final PlaybackCallbacks callbacks;
        private final JukeboxPlaybackGroup group;
        private final Set<UUID> listeners = new HashSet<>();
        private JukeboxSoundSettings settings;
        private BukkitTask task;
        private PlaybackStatus status = PlaybackStatus.LOADING;
        private long position;
        private boolean stopped;
        Session(Location location, MusicTrack track, JukeboxSoundSettings settings,
                PlaybackCallbacks callbacks, JukeboxPlaybackGroup group) {
            this.location = location; this.track = track; this.settings = settings;
            this.callbacks = callbacks; this.group = group;
            sound = ((TrackTarget.NativeRecord)track.target()).sound();
        }
        public void prepare() { }
        public void start() {
            if (task == null && !stopped) task = Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
        }
        private void tick() {
            if (stopped) return;
            if (!callbacks.isValid()) { stop(); callbacks.cancelled(); return; }
            if (status == PlaybackStatus.LOADING) {
                status = PlaybackStatus.PLAYING;
                for (var player : location.getWorld().getPlayers()) {
                    var id = player.getUniqueId();
                    var at = player.getLocation();
                    if (settings.volumePercent() == 0 || !audible.test(id) || !audience.test(location,id)
                            || at.distanceSquared(location) > settings.rangeBlocks()*(double)settings.rangeBlocks()) continue;
                    // An inline sound event gives long records their real fixed attenuation range,
                    // while leaving gain independent and the source fixed as listeners move.
                    var packet = new WrapperPlayServerSoundEffect(new StaticSound(new ResourceLocation(sound),(float)settings.rangeBlocks()),
                            com.github.retrooper.packetevents.protocol.sound.SoundCategory.RECORD,
                            new Vector3d(location.getX()+.5,location.getY()+.5,location.getZ()+.5),settings.volumePercent()/100F,1F);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player,packet);
                    listeners.add(id);
                }
                callbacks.started();
            }
            position = group.synchronizedPositionMillis();
            if (position >= track.details().audio().duration().toMillis()) {
                stop(); status = PlaybackStatus.FINISHED; callbacks.finished();
            }
        }
        public PlaybackStatus status() { return status; }
        public long positionMillis() { return position; }
        public void updateSettings(JukeboxSoundSettings value) { settings = value; }
        public void stop() {
            if (stopped) return;
            stopped = true;
            if (task != null) task.cancel();
            for (UUID id : listeners) {
                var player = Bukkit.getPlayer(id);
                if (player != null) player.stopSound(sound,SoundCategory.RECORDS);
            }
            listeners.clear(); sessions.remove(this); status = PlaybackStatus.STOPPED;
        }
    }
}
