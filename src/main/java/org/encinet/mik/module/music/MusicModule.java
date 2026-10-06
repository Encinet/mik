package org.encinet.mik.module.music;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.afk.AfkActivityService;
import org.encinet.mik.module.menu.runtime.WorldTextDisplayService;
import org.encinet.mik.module.music.catalog.MusicLibrary;
import org.encinet.mik.module.music.catalog.MusicPlaybackHistory;
import org.encinet.mik.module.music.catalog.MusicTrackSelector;
import org.encinet.mik.module.music.catalog.MusicTrackPool;
import org.encinet.mik.module.music.command.MusicCommandRegistrar;
import org.encinet.mik.module.music.command.MusicReloadReport;
import org.encinet.mik.module.music.command.RandomMusicActions;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.disc.MusicDiscResolver;
import org.encinet.mik.module.music.disc.MusicDiscSigner;
import org.encinet.mik.module.music.jukebox.JukeboxAutoPlayService;
import org.encinet.mik.module.music.jukebox.JukeboxQueueService;
import org.encinet.mik.module.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.module.music.jukebox.NearbyJukeboxPlayback;
import org.encinet.mik.module.music.listener.JukeboxControlListener;
import org.encinet.mik.module.music.listener.MusicBrowserListener;
import org.encinet.mik.module.music.listener.MusicJukeboxListener;
import org.encinet.mik.module.music.lyrics.LyricDisplayService;
import org.encinet.mik.module.music.lyrics.LyricsService;
import org.encinet.mik.module.music.online.LxSourceService;
import org.encinet.mik.module.music.online.OnlineAudioCache;
import org.encinet.mik.module.music.online.OnlineMusicRequestLimiter;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackNotifier;
import org.encinet.mik.module.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.module.music.jukebox.VanillaRecordSilencer;
import org.encinet.mik.module.music.ui.JukeboxControlGui;
import org.encinet.mik.module.music.ui.JukeboxAmbientStatusDisplay;
import org.encinet.mik.module.music.ui.MusicBrowserGui;
import org.encinet.mik.module.music.ui.RhythmCalibrationPrompt;
import org.encinet.mik.module.music.rhythm.RhythmGameService;
import org.encinet.mik.module.music.rhythm.calibration.PlasmoVoiceCalibrationAudio;
import org.encinet.mik.util.ShutdownSequence;
import su.plo.voice.api.server.PlasmoVoiceServer;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Coordinates the music catalog, custom sources, discs, and jukebox playback.
 */
public final class MusicModule {

    private static final long REMOTE_SOURCE_UPDATE_INTERVAL_TICKS = 20L * 60 * 60 * 6;

    private final JavaPlugin plugin;
    private final MusicLibrary musicLibrary;
    private final LxSourceService sourceService;
    private final OnlineAudioCache audioCache;
    private final MusicPlaybackHistory playbackHistory;
    private final LyricsService lyricsService;
    private final VanillaRecordSilencer recordSilencer;
    private final JukeboxPlaybackService playbackService;
    private final JukeboxQueueService queueService;
    private final JukeboxAutoPlayService autoPlayService;
    private final RhythmGameService rhythmGameService;
    private final MusicBrowserListener browserListener;
    private final JukeboxControlListener controlListener;
    private final JukeboxAmbientStatusDisplay ambientStatusDisplay;
    private final MusicJukeboxListener jukeboxListener;
    private final MusicCommandRegistrar commandRegistrar;
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final ExecutorService reloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private BukkitTask remoteSourceUpdateTask;

    public MusicModule(JavaPlugin plugin, LanguageService languageService,
                       PlasmoVoiceServer voiceServer,
                       AfkActivityService afkActivityService,
                       WorldTextDisplayService worldTextDisplays) {
        this.plugin = plugin;
        this.musicLibrary = new MusicLibrary(
                plugin.getDataFolder().toPath().resolve("music"), reloadExecutor,
                message -> plugin.getLogger().info(message),
                message -> plugin.getLogger().warning(message));
        MusicDiscSigner discSigner = new MusicDiscSigner(
                plugin.getDataFolder().toPath().resolve("state/music-disc.key"));
        this.playbackHistory = new MusicPlaybackHistory(
                plugin.getDataFolder().toPath().resolve("state/music-playback.json"),
                message -> plugin.getLogger().warning(message));
        MusicDiscResolver discResolver = new MusicDiscResolver(musicLibrary, discSigner);
        var lxDirectory = plugin.getDataFolder().toPath().resolve("lxmusic");
        this.sourceService = new LxSourceService(lxDirectory,
                message -> plugin.getLogger().info(message),
                message -> plugin.getLogger().warning(message));
        this.audioCache = new OnlineAudioCache(
                plugin.getDataFolder().toPath().resolve("cache/music"), sourceService,
                message -> plugin.getLogger().warning(message), playbackHistory);
        this.lyricsService = new LyricsService(
                plugin.getDataFolder().toPath().resolve("cache/lyrics"), sourceService,
                message -> plugin.getLogger().warning(message));
        OnlineMusicRequestLimiter requestLimiter = new OnlineMusicRequestLimiter();
        MusicDiscFactory discFactory = new MusicDiscFactory(languageService, discSigner);
        MusicTrackSelector trackSelector = new MusicTrackSelector();
        MusicTrackPool trackPool = new MusicTrackPool(
                musicLibrary::tracks, audioCache::cachedTracks);
        this.recordSilencer = new VanillaRecordSilencer();
        JukeboxPlaybackNotifier playbackNotifier = new JukeboxPlaybackNotifier(
                languageService, voiceServer);
        LyricDisplayService lyricDisplay = new LyricDisplayService(plugin, lyricsService);
        JukeboxSettingsStore settingsStore = new JukeboxSettingsStore();
        this.playbackService = new JukeboxPlaybackService(plugin, voiceServer, discResolver,
                audioCache, discFactory, playbackNotifier, recordSilencer,
                settingsStore, playbackHistory::recordPlayback, lyricDisplay, requestLimiter);
        this.rhythmGameService = new RhythmGameService(
                plugin, playbackService, playbackService,
                new PlasmoVoiceCalibrationAudio(plugin, voiceServer), languageService,
                afkActivityService);
        NearbyJukeboxPlayback nearbyPlayback = new NearbyJukeboxPlayback(
                playbackService, languageService);
        MusicBrowserGui browserGui = new MusicBrowserGui(plugin, musicLibrary, trackPool,
                sourceService, requestLimiter,
                track -> track.target() instanceof org.encinet.mik.module.music.catalog.TrackTarget.Lx lx
                        && audioCache.isCached(lx),
                playbackHistory, languageService, discFactory);
        this.queueService = new JukeboxQueueService(trackSelector, trackPool::tracks);
        JukeboxControlGui jukeboxControlGui = new JukeboxControlGui(
                queueService, discFactory, discResolver, playbackService,
                settingsStore, rhythmGameService, languageService);
        this.autoPlayService = new JukeboxAutoPlayService(
                plugin, queueService, playbackService, settingsStore);
        RandomMusicActions randomActions = new RandomMusicActions(
                trackPool, trackSelector, discFactory, nearbyPlayback, languageService);
        this.browserListener = new MusicBrowserListener(
                trackPool, discFactory, nearbyPlayback, browserGui,
                queueService, jukeboxControlGui, playbackService, autoPlayService,
                languageService, trackSelector, randomActions);
        browserGui.setActionHandler(browserListener);
        this.controlListener = new JukeboxControlListener(
                playbackService, browserGui, queueService,
                jukeboxControlGui, autoPlayService, settingsStore, languageService,
                rhythmGameService,
                new RhythmCalibrationPrompt(languageService));
        jukeboxControlGui.setActionHandler(controlListener);
        this.ambientStatusDisplay = new JukeboxAmbientStatusDisplay(
                plugin, languageService, discResolver, playbackService,
                settingsStore, worldTextDisplays);
        queueService.setStateChangedListener(jukeboxControlGui::refreshViewers);
        settingsStore.setStateChangedListener(jukeboxControlGui::refreshViewers);
        playbackService.setStateChangedListener(jukeboxControlGui::refreshViewers);
        this.jukeboxListener = new MusicJukeboxListener(
                plugin, musicLibrary, playbackService, recordSilencer,
                queueService, autoPlayService, jukeboxControlGui);
        this.commandRegistrar = new MusicCommandRegistrar(
                languageService, browserGui, randomActions, sourceService, audioCache,
                this::reloadAsync,
                jukeboxListener::restorePlaybackInLoadedChunks, this::runOnMainThread);

        playbackService.setTrackFinishedListener(autoPlayService::onTrackFinished);
    }

    public void enable() {
        if (!enabled.compareAndSet(false, true)) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(browserListener, plugin);
        Bukkit.getPluginManager().registerEvents(controlListener, plugin);
        Bukkit.getPluginManager().registerEvents(jukeboxListener, plugin);
        enableMusicChests();
        recordSilencer.enable();
        playbackService.enable();
        ambientStatusDisplay.enable();
        rhythmGameService.enable();
        remoteSourceUpdateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::updateRemoteSourcesAutomatically,
                REMOTE_SOURCE_UPDATE_INTERVAL_TICKS, REMOTE_SOURCE_UPDATE_INTERVAL_TICKS);
        reloadAsync().whenComplete((report, error) -> {
            if (error != null) {
                plugin.getLogger().severe("Initial music load failed: " + rootMessage(error));
                return;
            }
            if (!report.successful()) {
                plugin.getLogger().warning("Initial music load completed with issues: "
                        + String.join("; ", report.errors()));
            }
            runOnMainThread(jukeboxListener::restorePlaybackInLoadedChunks);
        });
    }

    /** Enable music chest locations. */
    private void enableMusicChests() {
        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null) {
            plugin.getLogger().severe("World 'world' not found!");
            return;
        }

        Location musicChest1 = new Location(mainWorld, 49, 55, 120).getBlock().getLocation();
        Location musicChest2 = new Location(mainWorld, 49, 55, 121).getBlock().getLocation();

        Set<Location> musicChestLocations = Set.of(musicChest1, musicChest2);
        browserListener.setMusicChestLocations(musicChestLocations);

        plugin.getLogger().info("Music module enabled with " + musicChestLocations.size()
                + " music chests");
    }

    /**
     * Register commands
     */
    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        commandRegistrar.register(lifecycleManager);
    }

    /** Reloads each independently published music state and reports partial success. */
    public CompletableFuture<MusicReloadReport> reloadAsync() {
        if (!enabled.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Music module is disabled"));
        }
        if (!reloading.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Music reload is already in progress"));
        }
        try {
            CompletableFuture<LxSourceService.ReloadResult> sources = sourceService.reloadAsync()
                    .exceptionally(error -> new LxSourceService.ReloadResult(
                            new LxSourceService.SubscriptionReload(false,
                                    sourceService.subscriptionStatuses().size(), 0, 0,
                                    rootMessage(error)),
                            new LxSourceService.RuntimeReload(false,
                                    (int) sourceService.statuses().stream()
                                            .filter(LxSourceService.SourceStatus::available).count(),
                                    sourceService.statuses().size(), rootMessage(error))));
            CompletableFuture<MusicLibrary.ReloadResult> library = musicLibrary.reloadAsync()
                    .exceptionally(error -> new MusicLibrary.ReloadResult(
                            false, musicLibrary.tracks().size(), rootMessage(error)));
            return library.thenCombine(sources, MusicReloadReport::new)
                    .whenComplete((ignored, error) -> reloading.set(false));
        } catch (RuntimeException exception) {
            reloading.set(false);
            return CompletableFuture.failedFuture(exception);
        }
    }

    public void disable() {
        enabled.set(false);
        BukkitTask updateTask = remoteSourceUpdateTask;
        remoteSourceUpdateTask = null;
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("browser listener", () -> HandlerList.unregisterAll(browserListener));
        shutdown.attempt("control listener", () -> HandlerList.unregisterAll(controlListener));
        shutdown.attempt("jukebox listener", () -> HandlerList.unregisterAll(jukeboxListener));
        shutdown.attempt("ambient display", ambientStatusDisplay::disable);
        if (updateTask != null) shutdown.attempt("remote update task", updateTask::cancel);
        shutdown.attempt("reload executor", reloadExecutor::shutdownNow);
        shutdown.attempt("autoplay", autoPlayService::stopAll);
        shutdown.attempt("rhythm game", rhythmGameService::close);
        shutdown.attempt("playback", playbackService::stopAll);
        shutdown.attempt("record silencer", recordSilencer::close);
        shutdown.attempt("audio cache", audioCache::close);
        shutdown.attempt("lyrics", lyricsService::close);
        shutdown.attempt("playback history", playbackHistory::close);
        shutdown.attempt("music library", musicLibrary::close);
        shutdown.attempt("source service", sourceService::close);
        shutdown.attempt("queue", queueService::clear);
        shutdown.finish("music module");
    }

    private void updateRemoteSourcesAutomatically() {
        sourceService.refreshSubscriptionsAsync().whenComplete((result, error) -> {
            if (!enabled.get()) {
                return;
            }
            if (error != null) {
                plugin.getLogger().warning("Automatic LX source update failed: " + rootMessage(error));
            } else if (result.changed() > 0 || result.failed() > 0) {
                plugin.getLogger().info("Automatic LX source update checked " + result.total()
                        + " sources: " + result.changed() + " changed, "
                        + result.failed() + " failed");
            }
        });
    }

    private void runOnMainThread(Runnable task) {
        if (enabled.get() && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
