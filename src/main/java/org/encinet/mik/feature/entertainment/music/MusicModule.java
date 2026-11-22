package org.encinet.mik.feature.entertainment.music;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.application.MusicDeviceRegistry;
import org.encinet.mik.feature.entertainment.music.application.MusicDeviceTravel;
import org.encinet.mik.feature.entertainment.music.application.MusicGroupService;
import org.encinet.mik.feature.entertainment.music.application.MusicMainThread;
import org.encinet.mik.feature.entertainment.music.application.MusicPlaybackController;
import org.encinet.mik.feature.entertainment.music.application.MusicQueries;
import org.encinet.mik.feature.entertainment.music.application.MusicRooms;
import org.encinet.mik.feature.entertainment.music.application.PlaylistService;
import org.encinet.mik.feature.entertainment.music.device.JukeboxSettingsStore;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscFactory;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscResolver;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscSigner;
import org.encinet.mik.feature.entertainment.music.entry.MusicCommandRegistrar;
import org.encinet.mik.feature.entertainment.music.entry.MusicDeviceListener;
import org.encinet.mik.feature.entertainment.music.gateway.MusicRequestLimiter;
import org.encinet.mik.feature.entertainment.music.gateway.RemoteMusic;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackNotifier;
import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackService;
import org.encinet.mik.feature.entertainment.music.jukebox.VanillaRecordSilencer;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricDisplayService;
import org.encinet.mik.feature.entertainment.music.lyrics.LyricsService;
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
    private final RemoteMusic gateway;
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
    public MusicModule(JavaPlugin plugin, LanguageService language, PlasmoVoiceServer voice,
            AfkActivityService afk, BiPredicate<Player, Location> permission,
            RemoteMusic gateway) {
        this.plugin = plugin;
        paths = MusicDataPaths.in(plugin.getDataFolder().toPath());
        store = new MusicStore();
        var signer = new MusicDiscSigner(paths.discSigningKey());
        var resolver = new MusicDiscResolver(id -> {
            var stored = store.snapshot().tracks().get(id);
            return stored == null ? null : stored.track();
        }, signer);
        var discs = new MusicDiscFactory(language, signer);
        this.gateway = gateway;
        lyricsService = new LyricsService(gateway::lyrics);
        var limiter = new MusicRequestLimiter();
        recordSilencer = new VanillaRecordSilencer();
        var settings = new JukeboxSettingsStore();
        playbackService = new JukeboxPlaybackService(plugin, voice, resolver, discs, gateway,
                new JukeboxPlaybackNotifier(language, voice), recordSilencer, settings,
                new LyricDisplayService(lyricsService), limiter);
        rhythmGameService = new RhythmGameService(plugin, playbackService, playbackService,
                new PlasmoVoiceCalibrationAudio(plugin, voice), language, afk);
        var registry = new MusicDeviceRegistry(store, settings, permission);
        mainThread = new MusicMainThread(plugin);
        travel = new MusicDeviceTravel(registry,mainThread);
        devices = new MusicPlaybackController(plugin, store, playbackService, settings, registry, mainThread, resolver);
        var groups = new MusicGroupService(store, registry, devices, mainThread);
        rooms = new MusicRooms(devices, registry, playbackService, settings, rhythmGameService, permission);
        workspace = new MusicWorkspace(plugin, language, new MusicQueries(store), new PlaylistService(store),
                devices, registry, travel, groups, gateway, limiter, settings,
                rooms, discs);
        listener = new MusicDeviceListener(
                plugin, devices, playbackService, workspace, recordSilencer, permission, rooms);
        commands = new MusicCommandRegistrar(workspace, language);
    }
    public void enable() {
        if (!enabled.compareAndSet(false, true))
            return;
        start();
    }

    private void start() {
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
    }
    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        commands.register(manager);
    }
    public void disable() {
        enabled.set(false);
        ShutdownSequence shutdown = new ShutdownSequence();
        shutdown.attempt("device listener", () -> HandlerList.unregisterAll(listener));
        shutdown.attempt("workspace", workspace::close);
        shutdown.attempt("device visits", travel::close);
        shutdown.attempt("main-thread completions", mainThread::close);
        shutdown.attempt("rooms", rooms::close);
        shutdown.attempt("rhythm", rhythmGameService::close);
        shutdown.attempt("devices", devices::close);
        shutdown.attempt("playback", playbackService::close);
        shutdown.attempt("silencer", recordSilencer::close);
        shutdown.attempt("lyrics", lyricsService::close);
        shutdown.attempt("music gateway", gateway::close);
        shutdown.attempt("storage", store::close);
        shutdown.finish("music");
    }
    private static String rootMessage(Throwable error) {
        while (error.getCause() != null && error.getCause() != error) error = error.getCause();
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
