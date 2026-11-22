package org.encinet.mik.feature.entertainment.music.jukebox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSoundSettings;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackTarget;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.sound.StaticSound;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;

/** Starts client's unseekable record sounds together from one main-thread session. */
final class NativeRecordPlaybackEngine implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Predicate<UUID> audible;
    private final Map<JukeboxPlaybackGroup, Session> sessions = new HashMap<>();
    private Function<Location, Set<UUID>> audience = _ -> Set.of();
    private BiPredicate<Location, UUID> listenerPolicy = (_, _) -> true;

    NativeRecordPlaybackEngine(JavaPlugin plugin, Predicate<UUID> audible) {
        this.plugin = plugin;
        this.audible = audible;
    }

    void setOutputAudience(Function<Location, Set<UUID>> snapshot) { audience = snapshot; }

    void setListenerPolicy(BiPredicate<Location, UUID> policy) { listenerPolicy = policy; }

    PlaybackSession create(Location location, MusicTrack track, JukeboxSoundSettings settings,
                           PlaybackCallbacks callbacks, JukeboxPlaybackGroup group) {
        var session = sessions.computeIfAbsent(group, key -> new Session(track, key));
        var output = new Output(session, location.clone(), settings, callbacks);
        session.outputs.add(output);
        return output;
    }

    @Override public void close() { List.copyOf(sessions.values()).forEach(session -> session.finish(false)); }

    private final class Session {
        private final JukeboxPlaybackGroup group;
        private final String sound;
        private final long duration;
        private final Set<Output> outputs = new LinkedHashSet<>();
        private BukkitTask task;
        private boolean started;
        private long startedAt;
        private long position;

        private Session(MusicTrack track, JukeboxPlaybackGroup group) {
            this.group = group;
            this.sound = ((TrackTarget.NativeRecord) track.target()).sound();
            this.duration = track.details().durationHint().toMillis();
            group.follow(this::snapshot);
        }

        private JukeboxPlaybackSnapshot snapshot() {
            return new JukeboxPlaybackSnapshot(started ? PlaybackStatus.PLAYING : PlaybackStatus.LOADING,
                    position, duration, false);
        }

        private void start() {
            if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
        }

        private void tick() {
            long now = group.nanoTime();
            for (Output output : List.copyOf(outputs)) {
                if (!output.callbacks.isValid()) {
                    output.stop();
                    output.callbacks.cancelled();
                } else {
                    try {
                        if (output.requested && output.status == PlaybackStatus.LOADING) {
                            if (!started) output.play();
                            output.status = PlaybackStatus.PLAYING;
                            output.callbacks.started();
                        } else if (!output.listeners.isEmpty()) {
                            output.reconcileListeners();
                        }
                    } catch (RuntimeException failure) {
                        output.stop();
                        output.callbacks.failed(failure);
                    }
                }
            }
            if (outputs.isEmpty()) return;
            if (!started) {
                started = true;
                startedAt = now;
            }
            position = Math.max(0L, (now - startedAt) / 1_000_000L);
            if (position >= duration) finish(true);
        }

        private void detach(Output output) {
            outputs.remove(output);
            if (outputs.isEmpty()) finish(false);
        }

        private void finish(boolean natural) {
            if (sessions.remove(group, this)) group.releaseClock();
            if (task != null) task.cancel();
            var ending = List.copyOf(outputs);
            outputs.clear();
            for (Output output : ending) {
                output.release(natural ? PlaybackStatus.FINISHED : PlaybackStatus.STOPPED);
                if (natural) output.callbacks.finished();
            }
        }
    }

    private final class Output implements PlaybackSession {
        private final Session session;
        private final Location location;
        private final PlaybackCallbacks callbacks;
        private final Set<UUID> listeners = new HashSet<>();
        private JukeboxSoundSettings settings;
        private PlaybackStatus status = PlaybackStatus.LOADING;
        private boolean requested;

        private Output(Session session, Location location, JukeboxSoundSettings settings, PlaybackCallbacks callbacks) {
            this.session = session;
            this.location = location;
            this.settings = settings;
            this.callbacks = callbacks;
        }

        @Override public void prepare() { }

        @Override public void start() {
            if (status != PlaybackStatus.LOADING || requested) return;
            requested = true;
            session.start();
        }

        private void play() {
            if (settings.volumePercent() == 0) return;
            var packet = new WrapperPlayServerSoundEffect(
                    new StaticSound(new ResourceLocation(session.sound), (float) settings.rangeBlocks()),
                    com.github.retrooper.packetevents.protocol.sound.SoundCategory.RECORD,
                    new Vector3d(location.getX() + .5, location.getY() + .5, location.getZ() + .5),
                    settings.volumePercent() / 100F, 1F);
            for (UUID id : audience.apply(location)) {
                var player = Bukkit.getPlayer(id);
                if (!audible.test(id) || !listenerPolicy.test(location, id)
                        || player == null || !player.getWorld().equals(location.getWorld())) continue;
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
                listeners.add(id);
            }
        }

        private void reconcileListeners() {
            listeners.removeIf(id -> {
                var player = Bukkit.getPlayer(id);
                if (player == null || !player.getWorld().equals(location.getWorld())) return true;
                if (settings.volumePercent() > 0 && audible.test(id) && listenerPolicy.test(location, id)) return false;
                player.stopSound(session.sound, SoundCategory.RECORDS);
                return true;
            });
        }

        @Override public JukeboxPlaybackSnapshot snapshot() { return session.snapshot().withStatus(status); }
        @Override public void updateSettings(JukeboxSoundSettings updated) { settings = updated; }

        @Override public void stop() {
            if (status == PlaybackStatus.STOPPED || status == PlaybackStatus.FINISHED) return;
            release(PlaybackStatus.STOPPED);
            session.detach(this);
        }

        private void release(PlaybackStatus finalStatus) {
            status = finalStatus;
            for (UUID id : listeners) {
                var player = Bukkit.getPlayer(id);
                if (player != null) player.stopSound(session.sound, SoundCategory.RECORDS);
            }
            listeners.clear();
        }
    }
}
