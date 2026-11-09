package org.encinet.mik.feature.entertainment.music;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.entertainment.music.application.MusicDeviceRegistry;
import org.encinet.mik.feature.entertainment.music.application.MusicDeviceTravel;
import org.encinet.mik.feature.entertainment.music.application.MusicGroupService;
import org.encinet.mik.feature.entertainment.music.application.MusicMainThread;
import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackController;
import org.encinet.mik.feature.entertainment.music.application.MusicQueries;
import org.encinet.mik.feature.entertainment.music.application.MusicRooms;
import org.encinet.mik.feature.entertainment.music.application.PlaylistService;
import org.encinet.mik.feature.entertainment.music.catalog.MusicLibrary;
import org.encinet.mik.feature.entertainment.music.catalog.MusicPlaybackHistory;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrackPool;
import org.encinet.mik.feature.entertainment.music.command.MusicCommandRegistrar;
import org.encinet.mik.feature.entertainment.music.command.MusicReloadReport;
import org.encinet.mik.feature.entertainment.music.config.MusicSettings;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscFactory;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscResolver;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscSigner;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackNotifier;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.jukebox.VanillaRecordSilencer;
import org.encinet.mik.feature.entertainment.music.listener.MusicDeviceListener;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricDisplayService;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricsService;
import org.encinet.mik.feature.entertainment.music.online.LxSourceService;
import org.encinet.mik.feature.entertainment.music.online.OnlineAudioCache;
import org.encinet.mik.feature.entertainment.music.online.OnlineMusicRequestLimiter;
import org.encinet.mik.feature.entertainment.music.rhythm.RhythmGameService;
import org.encinet.mik.feature.entertainment.music.rhythm.calibration.PlasmoVoiceCalibrationAudio;
import org.encinet.mik.feature.entertainment.music.storage.MusicStore;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspace;
import org.encinet.mik.feature.player.afk.AfkActivityService;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.util.ShutdownSequence;
import su.plo.voice.api.server.PlasmoVoiceServer;

/** Composition root. Features interact through explicitly supplied capabilities. */
public final class MusicModule {
    private final JavaPlugin plugin;
    private final MusicDataPaths paths;
    private final MusicLibrary musicLibrary;
    private final LxSourceService sourceService;
    private final OnlineAudioCache audioCache;
    private final MusicPlaybackHistory playbackHistory;
    private final LyricsService lyricsService;
    private final VanillaRecordSilencer recordSilencer;
    private final JukeboxPlaybackService playbackService;
    private final RhythmGameService rhythmGameService;
    private final MusicStore store;
    private final MusicPlaybackController devices;
    private final MusicRooms rooms;
    private final MusicMainThread mainThread;
    private final MusicDeviceTravel travel;
    private final MusicWorkspace workspace;
    private final MusicDeviceListener listener;
    private final MusicCommandRegistrar commands;
    private final AtomicBoolean enabled = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final ExecutorService reloadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private BukkitTask sourceUpdates;
    public MusicModule(JavaPlugin plugin, LanguageService language, PlasmoVoiceServer voice,
            AfkActivityService afk, BiPredicate<Player, Location> permission) {
        this.plugin = plugin;
        paths = MusicDataPaths.in(plugin.getDataFolder().toPath());
        musicLibrary = new MusicLibrary(paths.libraryDirectory(), reloadExecutor, plugin.getLogger()::info,
                plugin.getLogger()::warning);
        var signer = new MusicDiscSigner(paths.discSigningKey());
        var resolver = new MusicDiscResolver(musicLibrary, signer);
        var discs = new MusicDiscFactory(language, signer);
        playbackHistory = new MusicPlaybackHistory(paths.statisticsFile(), plugin.getLogger()::warning);
        sourceService = new LxSourceService(
                paths.sourcesDirectory(), plugin.getLogger()::info, plugin.getLogger()::warning);
        audioCache = new OnlineAudioCache(
                paths.audioCacheDirectory(), sourceService, plugin.getLogger()::warning, playbackHistory);
        lyricsService =
                new LyricsService(paths.lyricsCacheDirectory(), sourceService, plugin.getLogger()::warning);
        var limiter = new OnlineMusicRequestLimiter();
        recordSilencer = new VanillaRecordSilencer();
        var settings = new JukeboxSettingsStore();
        playbackService = new JukeboxPlaybackService(plugin, voice, resolver, audioCache,
                new JukeboxPlaybackNotifier(language, voice), recordSilencer, settings,
                playbackHistory::recordPlayback, new LyricDisplayService(plugin, lyricsService), limiter);
        rhythmGameService = new RhythmGameService(plugin, playbackService, playbackService,
                new PlasmoVoiceCalibrationAudio(plugin, voice), language, afk);
        store = new MusicStore();
        var registry = new MusicDeviceRegistry(store, settings, permission);
        mainThread = new MusicMainThread(plugin);
        travel = new MusicDeviceTravel(registry,mainThread);
        devices = new MusicPlaybackController(plugin, store, playbackService, settings, registry, mainThread);
        var pool = new MusicTrackPool(musicLibrary::tracks, audioCache::cachedTracks);
        var groups = new MusicGroupService(store, registry, devices, mainThread);
        rooms = new MusicRooms(devices, registry, playbackService, settings, rhythmGameService, permission);
        workspace = new MusicWorkspace(plugin, language, new MusicQueries(store), new PlaylistService(store),
                devices, registry, travel, groups, pool::tracks, sourceService, limiter, settings,
                rooms, discs);
        listener = new MusicDeviceListener(
                plugin, devices, playbackService, workspace, recordSilencer, permission, rooms);
        commands = new MusicCommandRegistrar(
                workspace, language, sourceService, this::reloadAsync, this::runOnMainThread);
    }
    public void enable() {
        if (!enabled.compareAndSet(false, true))
            return;
        CompletableFuture.supplyAsync(this::loadStartupSettings, reloadExecutor)
                .whenComplete((settings, error) -> {
                    if (!enabled.get())
                        return;
                    if (error != null) {
                        plugin.getLogger().severe("Music startup failed at " + paths.settingsFile() + ": "
                                + rootMessage(error));
                        return;
                    }
                    runOnMainThread(() -> start(settings));
                });
    }

    private MusicSettings loadStartupSettings() {
        try {
            MusicSettings settings = MusicSettings.load(paths.settingsFile());
            audioCache.initialize(settings.maximumAudioFileBytes(), settings.maximumAudioCacheBytes());
            lyricsService.configureCacheLimit(settings.maximumLyricsCacheBytes());
            return settings;
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void start(MusicSettings settings) {
        if (!started.compareAndSet(false, true))
            return;
        recordSilencer.enable();
        playbackService.enable();
        rhythmGameService.enable();
        devices.enable();
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        store.open(paths.databaseFile()).whenComplete((_, error) -> {
            if (error != null)
                plugin.getLogger().severe("Music storage unavailable: " + rootMessage(error));
        });
        reloadAsync().whenComplete((_, error) -> {
            if (error != null)
                plugin.getLogger().warning("Music reload failed: " + rootMessage(error));
        });
        long refreshTicks = settings.sourceRefreshInterval().toSeconds() * 20L;
        if (refreshTicks > 0) {
            sourceUpdates = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                    ()
                            -> sourceService.refreshSubscriptionsAsync().exceptionally(error -> {
                        if (enabled.get())
                            plugin.getLogger().warning("Music source refresh failed: " + rootMessage(error));
                        return null;
                    }),
                    refreshTicks, refreshTicks);
        }
    }
    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        commands.register(manager);
    }
    /** Reloads each independently published music state and reports partial success. */
    public CompletableFuture<MusicReloadReport> reloadAsync() {
        if (!enabled.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Music module is disabled"));
        }
        if (!started.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Music module is not initialized"));
        }
        if (!reloading.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Music reload is already in progress"));
        }
        try {
            CompletableFuture<LxSourceService.ReloadResult> sources =
                    sourceService.reloadAsync().exceptionally(error
                            -> new LxSourceService.ReloadResult(
                                    new LxSourceService.SubscriptionReload(false,
                                            sourceService.subscriptionStatuses().size(), 0, 0,
                                            rootMessage(error)),
                                    new LxSourceService.RuntimeReload(false,
                                            (int) sourceService.statuses()
                                                    .stream()
                                                    .filter(LxSourceService.SourceStatus::available)
                                                    .count(),
                                            sourceService.statuses().size(), rootMessage(error))));
            CompletableFuture<MusicLibrary.ReloadResult> library =
                    musicLibrary.reloadAsync().exceptionally(error
                            -> new MusicLibrary.ReloadResult(
                                    false, musicLibrary.tracks().size(), rootMessage(error)));
            return library.thenCombine(sources, MusicReloadReport::new)
                    .whenComplete((_, _) -> reloading.set(false));
        } catch (RuntimeException exception) {
            reloading.set(false);
            return CompletableFuture.failedFuture(exception);
        }
    }

    public void disable() {
        enabled.set(false);
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("device listener", () -> HandlerList.unregisterAll(listener));
        if (sourceUpdates != null)
            shutdown.attempt("source updates", sourceUpdates::cancel);
        shutdown.attempt("workspace", workspace::close);
        shutdown.attempt("device visits", travel::close);
        shutdown.attempt("main-thread completions", mainThread::close);
        shutdown.attempt("rooms", rooms::close);
        shutdown.attempt("rhythm", rhythmGameService::close);
        shutdown.attempt("devices", devices::close);
        shutdown.attempt("playback", playbackService::close);
        shutdown.attempt("silencer", recordSilencer::close);
        shutdown.attempt("library", musicLibrary::close);
        shutdown.attempt("audio cache", audioCache::close);
        shutdown.attempt("lyrics", lyricsService::close);
        shutdown.attempt("statistics", playbackHistory::close);
        shutdown.attempt("sources", sourceService::close);
        shutdown.attempt("storage", store::close);
        shutdown.attempt("reload executor", reloadExecutor::shutdownNow);
        shutdown.finish("music");
    }
    private void runOnMainThread(Runnable action) {
        if (enabled.get() && plugin.isEnabled())
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (enabled.get() && plugin.isEnabled())
                    action.run();
            });
    }
    private static String rootMessage(Throwable error) {
        while (error.getCause() != null && error.getCause() != error) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
