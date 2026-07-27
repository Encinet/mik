package org.encinet.mik.module.music.jukebox;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.disc.MusicDiscKeys;
import org.encinet.mik.module.music.disc.MusicDiscResolver;
import org.encinet.mik.module.music.lyrics.LyricDisplayService;
import org.encinet.mik.module.music.online.OnlineAudioCache;
import su.plo.voice.api.server.PlasmoVoiceServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Coordinates jukebox discs, playback state, notifications, and backend lifecycle. */
public class JukeboxPlaybackService implements JukeboxPlaybackStatus, JukeboxPlayback {

    private final JavaPlugin plugin;
    private final MusicDiscResolver discResolver;
    private final MusicDiscFactory discFactory;
    private final JukeboxSettingsStore settingsStore;
    private final JukeboxPlaybackNotifier notifier;
    private final VanillaRecordSilencer recordSilencer;
    private final AudioPlaybackEngine audioEngine;
    private final NbsPlaybackEngine nbsEngine;
    private final LyricDisplayService lyricDisplay;
    private final Consumer<MusicTrack> playbackRecorder;
    private final Map<JukeboxKey, Playback> playbacks = new ConcurrentHashMap<>();
    private final AtomicBoolean enabled = new AtomicBoolean();
    private volatile BiConsumer<Location, MusicTrack> trackFinishedListener;

    public JukeboxPlaybackService(JavaPlugin plugin, PlasmoVoiceServer voiceServer,
                       MusicDiscResolver discResolver, OnlineAudioCache audioCache,
                       MusicDiscFactory discFactory, JukeboxPlaybackNotifier notifier,
                       VanillaRecordSilencer recordSilencer,
                       JukeboxSettingsStore settingsStore,
                       Consumer<MusicTrack> playbackRecorder,
                       LyricDisplayService lyricDisplay) {
        this.plugin = plugin;
        this.discResolver = discResolver;
        this.discFactory = discFactory;
        this.notifier = notifier;
        this.recordSilencer = recordSilencer;
        this.settingsStore = java.util.Objects.requireNonNull(settingsStore, "settingsStore");
        this.playbackRecorder = java.util.Objects.requireNonNull(
                playbackRecorder, "playbackRecorder");
        this.lyricDisplay = java.util.Objects.requireNonNull(lyricDisplay, "lyricDisplay");

        this.audioEngine = new AudioPlaybackEngine(plugin, voiceServer, audioCache);
        this.nbsEngine = new NbsPlaybackEngine(plugin);
    }

    public void enable() {
        if (!enabled.compareAndSet(false, true)) {
            return;
        }
    }

    public void setTrackFinishedListener(BiConsumer<Location, MusicTrack> listener) {
        this.trackFinishedListener = listener;
    }

    /** Replaces the current record and starts a non-droppable disc created by a GUI or command. */
    public boolean playVirtualTrackOnJukebox(
            Player player, Jukebox jukebox, MusicTrack track) {
        return playVirtualTrackOnJukebox(player, jukebox, track, () -> {});
    }

    /** Runs {@code onStarted} once playback has actually acquired its output. */
    @Override
    public boolean playVirtualTrackOnJukebox(
            Player player, Jukebox jukebox, MusicTrack track, Runnable onStarted) {
        return startTrack(player, jukebox, track, track.details().title(), true, true, onStarted);
    }

    /**
     * Starts a MIK disc that Minecraft already inserted into the jukebox.
     */
    public boolean playInsertedDisc(Player player, Jukebox jukebox) {
        ItemStack disc = jukebox.getRecord();
        MusicTrack music = discResolver.resolve(disc);
        if (music == null) {
            jukebox.stopPlaying();
            jukebox.update(true, false);
            if (player != null) {
                notifier.unavailableDisc(player);
            }
            return false;
        }
        return startTrack(player, jukebox, music, music.details().title(), false, true, () -> {});
    }

    private boolean startTrack(Player player, Jukebox jukebox, MusicTrack music, String musicName,
                               boolean replaceRecord, boolean announce, Runnable onStarted) {
        Location location = jukebox.getLocation().clone();
        JukeboxKey key = JukeboxKey.of(location);
        ItemStack storedDisc = null;

        if (replaceRecord) {
            storedDisc = player == null
                    ? discFactory.createPersistentDisc(music)
                    : discFactory.createPersistentDisc(music, player);
            MusicDiscKeys.markInternal(storedDisc);
        } else if (!music.id().equals(MusicDiscKeys.trackId(jukebox.getRecord()))) {
            return false;
        }

        if (replaceRecord && jukebox.hasRecord()) {
            if (MusicDiscKeys.isInternal(jukebox.getRecord())) {
                jukebox.setRecord(new ItemStack(Material.AIR));
            } else if (!jukebox.eject()) {
                if (player != null) {
                    notifier.jukeboxUnavailable(player);
                }
                return false;
            }
        }

        stop(key, false);
        if (replaceRecord) {
            jukebox.setRecord(storedDisc);
        }
        Playback playback = new Playback(key, location, music,
                player == null ? null : player.getUniqueId(),
                musicName == null || musicName.isBlank() ? music.details().title() : musicName,
                settingsStore.read(jukebox), announce, onStarted);
        playbacks.put(key, playback);
        recordSilencer.playbackStarted(location);
        jukebox.stopPlaying();
        jukebox.update(true, false);

        startBackend(playback);
        return !playback.failed.get();
    }

    private void startBackend(Playback playback) {
        try {
            PlaybackCallbacks callbacks = callbacks(playback);
            playback.session = playback.music.target() instanceof TrackTarget.NbsFile nbs
                    ? nbsEngine.create(playback.location, nbs, playback.settings, callbacks)
                    : audioEngine.create(playback.location, playback.music,
                            playback.musicName, playback.settings, callbacks);
            playback.session.start();
        } catch (RuntimeException exception) {
            backendFailed(playback, exception);
        }
    }

    private PlaybackCallbacks callbacks(Playback playback) {
        return new PlaybackCallbacks() {
            @Override
            public boolean isValid() {
                if (!enabled.get() || playbacks.get(playback.key) != playback
                        || playback.stopped.get()) {
                    return false;
                }
                Block block = playback.location.getBlock();
                return block.getState() instanceof Jukebox jukebox
                        && hasPlaybackDisc(jukebox, playback);
            }

            @Override
            public void started() {
                playback.notifyStarted(plugin);
                PlaybackSession session = playback.session;
                if (session != null) {
                    LyricDisplayService.PlaybackLyrics lyrics = lyricDisplay.start(
                            playback.location, playback.music,
                            session::positionMillis, this::isValid,
                            () -> playback.settings.rangeBlocks());
                    if (!playback.lyrics.compareAndSet(null, lyrics)) {
                        lyrics.close();
                    } else if (playback.cleaned.get()) {
                        LyricDisplayService.PlaybackLyrics stale =
                                playback.lyrics.getAndSet(null);
                        if (stale != null) {
                            stale.close();
                        }
                    }
                }
                if (playback.announce) {
                    notifier.broadcastStarted(playback.location,
                            playback.musicName, playback.music,
                            playback.settings.rangeBlocks());
                }
            }

            @Override
            public void failed(Throwable error) {
                backendFailed(playback, error);
            }

            @Override
            public void finished() {
                playback.recordPlaybackIfQualified(true, playbackRecorder, plugin);
                finishPlayback(playback);
            }

            @Override
            public void cancelled() {
                stop(playback);
            }
        };
    }

    private void backendFailed(Playback playback, Throwable error) {
        playback.failed.set(true);
        plugin.getLogger().warning("Failed to play track " + playback.music.id()
                + ": " + rootMessage(error));
        finishPlayback(playback);
    }

    public void stop(Block block) {
        stop(JukeboxKey.of(block.getLocation()), false);
    }

    /** Applies changed block settings to an active MIK playback without restarting it. */
    public void updateSettings(Block block, JukeboxSoundSettings settings) {
        java.util.Objects.requireNonNull(block, "block");
        java.util.Objects.requireNonNull(settings, "settings");
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || playback.stopped.get()) {
            return;
        }
        playback.settings = settings;
        PlaybackSession session = playback.session;
        if (session != null) {
            session.updateSettings(settings);
        }
    }

    /** Captures the exact playback attempt currently owned by this jukebox. */
    public PlaybackHandle activePlayback(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        return playback == null ? null : new PlaybackHandle(playback);
    }

    /** Stops this jukebox only while it still owns the captured playback attempt. */
    public boolean stopIfCurrent(Block block, PlaybackHandle expected) {
        if (expected == null) {
            return false;
        }
        Playback playback = expected.playback;
        if (!playback.key.equals(JukeboxKey.of(block.getLocation()))
                || playbacks.get(playback.key) != playback) {
            return false;
        }
        stop(playback);
        return playback.stopped.get();
    }

    public void stopAndClear(Block block) {
        stop(JukeboxKey.of(block.getLocation()), true);
    }

    public boolean stopAndEject(Block block) {
        stop(JukeboxKey.of(block.getLocation()), false);
        if (!(block.getState() instanceof Jukebox jukebox) || !jukebox.hasRecord()) {
            return false;
        }
        jukebox.stopPlaying();
        if (MusicDiscKeys.isInternal(jukebox.getRecord())) {
            jukebox.setRecord(new ItemStack(Material.AIR));
            jukebox.update(true, false);
            return true;
        }
        return jukebox.eject();
    }

    public void stopAll() {
        if (!enabled.compareAndSet(true, false)) {
            return;
        }
        for (JukeboxKey key : List.copyOf(playbacks.keySet())) {
            stop(key, false);
        }
        nbsEngine.close();
        audioEngine.close();
    }

    public void removeWorld(World world) {
        if (world == null) {
            return;
        }
        java.util.UUID worldId = world.getUID();
        for (JukeboxKey key : List.copyOf(playbacks.keySet())) {
            if (worldId.equals(key.world())) {
                stop(key, false);
            }
        }
    }

    @Override
    public boolean isPlaying(Block block) {
        return playbacks.containsKey(JukeboxKey.of(block.getLocation()));
    }

    @Override
    public PlaybackStatus status(Block block) {
        Playback playback = playbacks.get(JukeboxKey.of(block.getLocation()));
        if (playback == null || playback.stopped.get()) {
            return PlaybackStatus.STOPPED;
        }
        PlaybackSession session = playback.session;
        return session == null ? PlaybackStatus.LOADING : session.status();
    }

    public boolean shouldRestore(Block block, ItemStack record) {
        return MusicDiscKeys.isCustomDisc(record) && !isPlaying(block);
    }

    private void finishPlayback(Playback playback) {
        if (!playback.finishing.compareAndSet(false, true)) {
            return;
        }
        cleanup(playback);

        if (!enabled.get()) {
            removePlayback(playback);
            return;
        }
        try {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!removePlayback(playback)) {
                    return;
                }
                Block block = playback.location.getBlock();
                if (block.getState() instanceof Jukebox jukebox
                        && hasPlaybackDisc(jukebox, playback)) {
                    jukebox.stopPlaying();
                    if (playback.failed.get()) {
                        removeFailedDisc(jukebox);
                    } else {
                        jukebox.update(true, false);
                    }
                }

                notifyFailure(playback);

                if (!playback.stopped.get() && !playback.failed.get()) {
                    BiConsumer<Location, MusicTrack> listener = trackFinishedListener;
                    if (listener != null) {
                        listener.accept(playback.location.clone(), playback.music);
                    }
                }
            });
        } catch (IllegalStateException ignored) {
            removePlayback(playback);
            // Plugin shutdown won the race after the enabled check.
        }
    }

    private void stop(JukeboxKey key, boolean removeRecord) {
        Playback playback = playbacks.remove(key);
        if (playback != null) {
            recordSilencer.playbackStopped(playback.location);
            playback.stopped.set(true);
            playback.recordPlaybackIfQualified(false, playbackRecorder, plugin);
            cleanup(playback);
        }

        if (removeRecord) {
            World world = Bukkit.getWorld(key.world());
            if (world == null) {
                return;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (block.getState() instanceof Jukebox jukebox
                    && MusicDiscKeys.trackId(jukebox.getRecord()) != null) {
                jukebox.setRecord(new ItemStack(Material.AIR));
                jukebox.update(true, false);
            }
        }
    }

    private void stop(Playback playback) {
        if (!playbacks.remove(playback.key, playback)) {
            return;
        }
        recordSilencer.playbackStopped(playback.location);
        playback.stopped.set(true);
        playback.recordPlaybackIfQualified(false, playbackRecorder, plugin);
        cleanup(playback);
    }

    private void cleanup(Playback playback) {
        if (!playback.cleaned.compareAndSet(false, true)) {
            return;
        }
        PlaybackSession session = playback.session;
        LyricDisplayService.PlaybackLyrics lyrics = playback.lyrics.getAndSet(null);
        if (lyrics != null) {
            lyrics.close();
        }
        if (session != null) {
            session.stop();
        }
    }

    private boolean hasPlaybackDisc(Jukebox jukebox, Playback playback) {
        return playback.music.id().equals(MusicDiscKeys.trackId(jukebox.getRecord()));
    }

    private static void removeFailedDisc(Jukebox jukebox) {
        if (MusicDiscKeys.isInternal(jukebox.getRecord())) {
            jukebox.setRecord(new ItemStack(Material.AIR));
            jukebox.update(true, false);
        } else {
            jukebox.eject();
        }
    }

    private void notifyFailure(Playback playback) {
        if (!playback.failed.get() || playback.requestingPlayer == null
                || !playback.failureNotified.compareAndSet(false, true)) {
            return;
        }
        notifier.playbackFailed(playback.requestingPlayer, playback.music);
    }

    private boolean removePlayback(Playback playback) {
        if (!playbacks.remove(playback.key, playback)) {
            return false;
        }
        recordSilencer.playbackStopped(playback.location);
        return true;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }

    private static final class Playback {
        private final JukeboxKey key;
        private final Location location;
        private final MusicTrack music;
        private final java.util.UUID requestingPlayer;
        private final String musicName;
        private final boolean announce;
        private final Runnable onStarted;
        private volatile JukeboxSoundSettings settings;
        private final AtomicBoolean stopped = new AtomicBoolean();
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean finishing = new AtomicBoolean();
        private final AtomicBoolean failed = new AtomicBoolean();
        private final AtomicBoolean cleaned = new AtomicBoolean();
        private final AtomicBoolean failureNotified = new AtomicBoolean();
        private final AtomicBoolean playbackRecorded = new AtomicBoolean();
        private volatile PlaybackSession session;
        private final java.util.concurrent.atomic.AtomicReference<
                LyricDisplayService.PlaybackLyrics> lyrics = new java.util.concurrent.atomic.AtomicReference<>();

        private Playback(JukeboxKey key, Location location, MusicTrack music,
                         java.util.UUID requestingPlayer,
                         String musicName, JukeboxSoundSettings settings,
                         boolean announce, Runnable onStarted) {
            this.key = key;
            this.location = location;
            this.music = music;
            this.requestingPlayer = requestingPlayer;
            this.musicName = musicName;
            this.settings = java.util.Objects.requireNonNull(settings, "settings");
            this.announce = announce;
            this.onStarted = java.util.Objects.requireNonNull(onStarted, "onStarted");
        }

        private void notifyStarted(JavaPlugin plugin) {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            try {
                onStarted.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Music playback start callback failed: "
                        + exception.getMessage());
            }
        }

        private void recordPlaybackIfQualified(boolean finishedNaturally,
                                               Consumer<MusicTrack> playbackRecorder,
                                               JavaPlugin plugin) {
            if (failed.get() || !started.get() || playbackRecorded.get()) {
                return;
            }
            PlaybackSession activeSession = session;
            long playedMillis = activeSession == null ? 0 : activeSession.positionMillis();
            if (!PlaybackCountPolicy.qualifies(finishedNaturally, playedMillis,
                    music.details().audio().duration())) {
                return;
            }
            if (!playbackRecorded.compareAndSet(false, true)) {
                return;
            }
            try {
                playbackRecorder.accept(music);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Failed to record music playback: "
                        + exception.getMessage());
            }
        }
    }

    /** Opaque identity used by delayed Bukkit event callbacks. */
    public static final class PlaybackHandle {
        private final Playback playback;

        private PlaybackHandle(Playback playback) {
            this.playback = playback;
        }
    }

    private record JukeboxKey(java.util.UUID world, int x, int y, int z) {
        private static JukeboxKey of(Location location) {
            return new JukeboxKey(location.getWorld().getUID(), location.getBlockX(),
                    location.getBlockY(), location.getBlockZ());
        }
    }

}
