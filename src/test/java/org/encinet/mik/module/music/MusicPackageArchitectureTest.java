package org.encinet.mik.module.music;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPackageArchitectureTest {

    private static final Path MUSIC = Path.of("src/main/java/org/encinet/mik/module/music");
    private static final Set<String> CAPABILITIES = Set.of(
            "catalog", "command", "disc", "jukebox", "listener", "lyrics", "online",
            "rhythm", "ui");

    @Test
    void rootOnlyContainsTheModuleCompositionRootAndCapabilityDirectories() throws IOException {
        try (Stream<Path> paths = Files.list(MUSIC)) {
            assertEquals(List.of("MusicModule.java"), paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString()).sorted().toList());
        }
        try (Stream<Path> paths = Files.list(MUSIC)) {
            assertEquals(CAPABILITIES, paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
        assertFalse(Files.exists(MUSIC.resolve("playback")));
    }

    @Test
    void capabilityDependenciesRemainDirected() throws IOException {
        assertNoImport(MUSIC.resolve("catalog"), "org.encinet.mik.module.music.disc.");
        assertNoImport(MUSIC.resolve("catalog"), "org.encinet.mik.module.music.online.");
        assertNoImport(MUSIC.resolve("jukebox"), "org.encinet.mik.module.music.ui.");
        assertNoImport(MUSIC.resolve("online"), "org.encinet.mik.module.music.ui.");
        assertNoImport(MUSIC.resolve("online"), "org.encinet.mik.module.music.listener.");
        assertNoImport(MUSIC.resolve("lyrics"), "org.encinet.mik.module.music.jukebox.");
        assertNoImport(MUSIC.resolve("rhythm"), "org.encinet.mik.module.music.ui.");
        assertNoImport(MUSIC.resolve("rhythm"), "org.encinet.mik.module.music.listener.");
        assertNoImport(MUSIC.resolve("ui"), "org.encinet.mik.module.music.listener.");
        assertNoImport(MUSIC.resolve("listener"), "org.encinet.mik.module.music.online.LxCustomSource");
    }

    @Test
    void rhythmModesOwnRealPackagesInsteadOfCrowdingTheRhythmRoot()
            throws IOException {
        Path modes = MUSIC.resolve("rhythm/mode");
        try (Stream<Path> paths = Files.list(modes)) {
            assertEquals(Set.of("falling", "radial", "spatial"),
                    paths.filter(Files::isDirectory)
                            .map(path -> path.getFileName().toString())
                            .collect(java.util.stream.Collectors.toSet()));
        }
        try (Stream<Path> paths = Files.list(MUSIC.resolve("rhythm"))) {
            assertTrue(paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .noneMatch(name -> name.startsWith("RhythmSpatial")
                            || name.equals("RhythmRadialPath.java")
                            || name.equals("RhythmWorldAim.java")));
        }

        String falling = source("rhythm/mode/falling/RhythmFallingLayout.java");
        String pointer = source("rhythm/input/RhythmWorldAim.java");
        String timestamps = source(
                "rhythm/input/RhythmInputTimestampSource.java");
        assertTrue(falling.contains("public final class RhythmFallingLayout"));
        assertTrue(pointer.contains("public final class RhythmWorldAim"));
        assertTrue(timestamps.contains(
                "public final class RhythmInputTimestampSource"));
        assertFalse(pointer.contains(".mode."));
        assertFalse(timestamps.contains(".mode."));
        assertNoImport(modes,
                "org.encinet.mik.module.music.rhythm.RhythmGameService");
        assertNoImport(modes.resolve("falling"),
                "org.encinet.mik.module.music.rhythm.mode.radial.");
        assertNoImport(modes.resolve("falling"),
                "org.encinet.mik.module.music.rhythm.mode.spatial.");
        assertNoImport(modes.resolve("radial"),
                "org.encinet.mik.module.music.rhythm.mode.spatial.");
        assertNoImport(MUSIC.resolve("rhythm/input"),
                "org.encinet.mik.module.music.rhythm.mode.");
    }

    @Test
    void trackModelOnlyDescribesMetadataAndMediaTargets() throws IOException {
        String track = source("catalog/MusicTrack.java");
        String target = source("catalog/TrackTarget.java");

        assertFalse(track.contains("NbsSong"));
        assertFalse(track.contains("java.nio.file.Files"));
        assertFalse(target.contains("NbsSong"));
        assertFalse(target.contains("java.nio.file.Files"));
        assertFalse(target.contains("validatedPath"));
        assertTrue(target.contains("record LocalFile"));
        assertTrue(target.contains("record NbsFile"));
        assertTrue(target.contains("record Lx"));
    }

    @Test
    void staticLibraryDoesNotOwnOnlineSearchState() throws IOException {
        String library = source("catalog/MusicLibrary.java");
        String pool = source("catalog/MusicTrackPool.java");
        String browser = source("ui/MusicBrowserGui.java");

        assertFalse(library.contains("onlineTracks"));
        assertFalse(library.contains("registerOnlineTracks"));
        assertFalse(pool.contains("OnlineAudioCache"));
        assertFalse(browser.contains("registerOnlineTracks"));
        assertFalse(Files.exists(MUSIC.resolve("catalog/MusicSearchService.java")));
        assertTrue(Files.exists(MUSIC.resolve("online/MusicSearchService.java")));
    }

    @Test
    void browserSessionOwnsSearchStateWithoutInventoryCoupling() throws IOException {
        String gui = source("ui/MusicBrowserGui.java");
        String sessions = source("ui/MusicBrowserSessions.java");

        assertFalse(gui.contains("Map<UUID, PlayerState>"));
        assertFalse(gui.contains("playerJukeboxContext"));
        assertFalse(sessions.contains("Map<UUID, Session>"));
        assertTrue(gui.contains("FloatingMenuScreen<MusicBrowserSessions.Session>"));
        assertTrue(gui.contains("FloatingMenuLayouts.sidecar("));
        assertTrue(gui.contains("menu.item(\"selected-track\""));
        assertTrue(gui.contains("Message.MUSIC_BROWSER_LEFT_ADD_QUEUE"));
        assertTrue(gui.contains("Message.MUSIC_BROWSER_RIGHT_PLAY_NEARBY"));
        assertTrue(sessions.contains("Map<UUID, JukeboxContext>"));
        assertTrue(sessions.contains("focusedTrackOnPage("));
        assertTrue(sessions.contains("completeSearch("));
        assertFalse(sessions.contains("activeInventory"));
        assertFalse(gui.contains("createInventory("));
    }

    @Test
    void listenersRemainBukkitAdaptersWithoutCrossListenerState() throws IOException {
        for (String listener : List.of(
                "JukeboxControlListener.java", "MusicBrowserListener.java",
                "MusicJukeboxListener.java")) {
            String value = source("listener/" + listener);
            assertFalse(value.contains("new HashMap<"), listener);
            assertFalse(value.contains("new ConcurrentHashMap<"), listener);
            for (String other : List.of(
                    "JukeboxControlListener", "MusicBrowserListener", "MusicJukeboxListener")) {
                if (!listener.startsWith(other)) {
                    assertFalse(value.contains(other), listener + " depends on " + other);
                }
            }
        }
    }

    @Test
    void lxServiceIsTheOnlyPublicLxLifecycleFacade() throws IOException {
        assertTrue(source("online/LxSourceService.java").contains("public final class LxSourceService"));
        for (String implementation : List.of(
                "LxCustomSourceResolver.java", "LxSubscriptionManager.java",
                "LxCustomSourceRuntime.java", "LxMusicTrackMapper.java", "LxTrackResolver.java",
                "CachedOnlineTrackCatalog.java")) {
            String value = source("online/" + implementation);
            assertFalse(value.contains("public final class"), implementation);
            assertFalse(value.contains("public interface"), implementation);
        }
        for (String caller : List.of("MusicModule.java", "command/MusicCommandRegistrar.java")) {
            String value = source(caller);
            assertFalse(value.contains("LxCustomSourceResolver"), caller);
            assertFalse(value.contains("LxSubscriptionManager"), caller);
        }
    }

    @Test
    void onlineCacheOwnsPersistentLeasesAndCachedTrackMetadata() throws IOException {
        String cache = source("online/OnlineAudioCache.java");
        String catalog = source("online/CachedOnlineTrackCatalog.java");
        assertTrue(cache.contains("isCached(TrackTarget.Lx target)"));
        assertTrue(cache.contains("acquire(TrackTarget.Lx target)"));
        assertTrue(cache.contains("invalidate(TrackTarget.Lx target)"));
        assertTrue(cache.contains("cachedTracks()"));
        assertTrue(cache.contains("indexAsync(MusicTrack track)"));
        assertFalse(cache.contains("acquire(MusicTrack"));
        assertTrue(cache.contains("activeEntries"));
        assertTrue(cache.contains(".part"));
        assertTrue(cache.contains("trackCatalog"));
        assertTrue(catalog.contains(".track.json"));
        assertTrue(catalog.contains("OnlineTrackSnapshot.deserialize"));
    }

    @Test
    void randomActionsUseTheDynamicTrackPoolInsteadOfOnlyTheLocalLibrary() throws IOException {
        String random = source("command/RandomMusicActions.java");
        String queue = source("jukebox/JukeboxQueueService.java");
        String browserListener = source("listener/MusicBrowserListener.java");

        assertTrue(random.contains("MusicTrackPool"));
        assertFalse(random.contains("MusicLibrary"));
        assertFalse(queue.contains("MusicTrackPool"));
        assertFalse(queue.contains("MusicLibrary"));
        assertTrue(browserListener.contains("trackPool.tracks()"));
    }

    @Test
    void jukeboxPlaybackModesUseTheirDocumentedTrackSources() throws IOException {
        String queue = source("jukebox/JukeboxQueueService.java");
        String gui = source("ui/JukeboxControlGui.java");
        String control = source("listener/JukeboxControlListener.java");

        assertTrue(queue.contains("JukeboxPlaybackMode.REPEAT_ALL"));
        assertTrue(queue.contains("JukeboxPlaybackMode.REPEAT_ONE"));
        assertTrue(queue.contains("JukeboxPlaybackMode.SHUFFLE"));
        assertTrue(queue.contains("JukeboxPlaybackMode.LIBRARY_SHUFFLE"));
        assertTrue(queue.contains("trackSelector.select(queue"));
        assertTrue(queue.contains("trackSelector.select(libraryTracks"));
        assertFalse(queue.contains("trackPool.tracks()"));
        assertFalse(gui.contains("createDisabledQueueItem"));
        assertFalse(control.contains("removeFromQueue(track);\n"
                + "                player.sendMessage"));
        assertTrue(source("jukebox/JukeboxAutoPlayService.java")
                .contains("playback.playInsertedDisc(nearestPlayer, jukebox)"));
    }

    @Test
    void listeningAndRhythmModesExposeDifferentControlsAndReadiness()
            throws IOException {
        String settings = source("jukebox/JukeboxSettingsStore.java");
        String playback = source("jukebox/JukeboxPlaybackService.java");
        String status = source("jukebox/JukeboxPlaybackStatus.java");
        String audio = source("jukebox/AudioPlaybackEngine.java");
        String gui = source("ui/JukeboxControlGui.java");
        String actions = source("ui/JukeboxControlActionHandler.java");
        String listener = source("listener/JukeboxControlListener.java");
        String rhythmJoin = method(playback,
                "public Optional<Participation> join(",
                "/** Stops and discards the song clock");
        String musicControls = method(gui,
                "private void addMusicModeControls(",
                "private void addQueue(");
        String rhythmControls = method(gui,
                "private void addRhythmModeControls(",
                "private void addSoundControls(");

        assertTrue(settings.contains("readExperienceMode(Jukebox jukebox)"));
        assertTrue(settings.contains("writeExperienceMode(Jukebox jukebox"));
        assertTrue(listener.contains("playbackService.playInsertedDisc(player, jukebox)"));
        assertTrue(playback.contains("playback.experienceMode"));
        assertTrue(playback.contains("Optional<Participation> join(Block block"));
        assertTrue(rhythmJoin.contains("prepareBackend(playback)"));
        assertFalse(rhythmJoin.contains("startBackend(playback)"));
        assertTrue(rhythmJoin.contains("startRhythmPlayback(playback, playbackId)"));
        assertTrue(playback.contains("resetRhythmPlaybackToWaiting(playback)"));
        assertTrue(playback.contains("JukeboxRhythmReadiness.WAITING_FOR_PLAYER"));
        assertTrue(playback.contains("backendStarted.compareAndSet(false, true)"));
        assertTrue(playback.contains("if (playback.experienceMode == JukeboxExperienceMode.MUSIC)"));
        assertTrue(playback.contains("JukeboxRhythmReadiness rhythmReadiness"));
        assertTrue(status.contains("rhythmReadiness(Block block)"));
        assertTrue(audio.contains("experienceMode.waitsForRhythmAnalysis()"));
        assertTrue(audio.contains("rhythmAnalysis.completion().whenComplete"));
        assertTrue(gui.contains("\"experience-mode\""));
        assertTrue(musicControls.contains("\"playback-mode\""));
        assertTrue(musicControls.contains("\"play-next\""));
        assertTrue(musicControls.contains("\"queue:clear\""));
        assertFalse(musicControls.contains("\"rhythm-game\""));
        assertFalse(rhythmControls.contains("\"prepare-rhythm-track\""));
        assertTrue(rhythmControls.contains("\"rhythm-game\""));
        assertTrue(gui.contains("MUSIC_RHYTHM_START_GAME"));
        assertTrue(gui.contains("MUSIC_RHYTHM_WAITING_FOR_PLAYER"));
        assertFalse(actions.contains("prepareRhythmTrack"));
        assertTrue(rhythmControls.contains("\"latency-calibration\""));
        assertFalse(rhythmControls.contains("\"source-latency-calibration\""));
        assertFalse(actions.contains("SourceLatencyCalibration"));
        assertFalse(rhythmControls.contains("\"playback-mode\""));
        assertFalse(rhythmControls.contains("\"queue:clear\""));
        assertFalse(gui.contains("queue:add-all"));
        assertFalse(actions.contains("void addAll("));
    }

    @Test
    void spatialControlsContainNoInventoryMetadataContracts() throws IOException {
        String gui = source("ui/JukeboxControlGui.java");
        String browser = source("ui/MusicBrowserGui.java");

        String legacyDisplayMetadata = "Tooltip" + "Display";
        assertFalse(gui.contains(legacyDisplayMetadata));
        assertFalse(browser.contains(legacyDisplayMetadata));
    }

    @Test
    void jukeboxStateOwnersInvalidateEveryMatchingViewer() throws IOException {
        String module = source("MusicModule.java");
        String gui = source("ui/JukeboxControlGui.java");
        String queue = source("jukebox/JukeboxQueueService.java");
        String settings = source("jukebox/JukeboxSettingsStore.java");
        String playback = source("jukebox/JukeboxPlaybackService.java");

        assertTrue(gui.contains("FloatingMenuScreen<ViewState>"));
        assertTrue(gui.contains("screen.updateWhere("));
        assertTrue(gui.contains("screen.closeWhere("));
        assertTrue(queue.contains("stateChanged.run()"));
        assertTrue(settings.contains("stateChangedListener.accept("));
        assertTrue(playback.contains("notifyStateChanged("));
        assertTrue(module.contains("queueService.setStateChangedListener("));
        assertTrue(module.contains("settingsStore.setStateChangedListener("));
        assertTrue(module.contains("playbackService.setStateChangedListener("));
    }

    @Test
    void jukeboxControlsConsumeAndRefreshTheLivePlaybackClock() throws IOException {
        String status = source("jukebox/JukeboxPlaybackStatus.java");
        String playback = source("jukebox/JukeboxPlaybackService.java");
        String gui = source("ui/JukeboxControlGui.java");

        assertTrue(status.contains("default JukeboxPlaybackSnapshot snapshot(Block block)"));
        assertTrue(playback.contains("public JukeboxPlaybackSnapshot snapshot(Block block)"));
        assertTrue(playback.contains("session.positionMillis()"));
        assertTrue(gui.contains(".refreshWhenChanged(PLAYBACK_PROGRESS_REFRESH_TICKS"));
        assertTrue(gui.contains("playback.positionMillis()"));
        assertTrue(gui.contains("completedProgressSegments("));
    }

    @Test
    void jukeboxCoordinatorContainsNoDecoderOrPacketDetails() throws IOException {
        String coordinator = source("jukebox/JukeboxPlaybackService.java");
        for (String forbidden : List.of(
                "com.sedmelluq.discord.lavaplayer", "NbsPlaybackCursor",
                "NbsPlaybackVolume", "NbsInstruments", "ServerProximitySource",
                "AudioSender", "PacketEvents", "WrapperPlayServerEffect",
                "findNearestJukebox", "MUSIC_NOW_PLAYING_RICH")) {
            assertFalse(coordinator.contains(forbidden), forbidden);
        }
        assertFalse(source("jukebox/AudioPlaybackEngine.java").contains("NbsPlaybackCursor"));
        assertFalse(source("jukebox/NbsPlaybackEngine.java").contains("lavaplayer"));
        assertFalse(source("jukebox/NbsPlaybackEngine.java").contains("PlasmoVoice"));
    }

    @Test
    void plasmoMusicSourceUsesFullDefaultVolume() throws IOException {
        String engine = source("jukebox/AudioPlaybackEngine.java");
        assertTrue(engine.contains(".setDefaultVolume(1.0)"));
        assertFalse(engine.contains(".setDefaultVolume(0.5)"));
    }

    @Test
    void nbsIsParsedAtPlaybackAndNeverStoredInTheTarget() throws IOException {
        String engine = source("jukebox/NbsPlaybackEngine.java");
        assertTrue(engine.contains("parser.parse(mediaPreparer.prepare(target))"));
        assertTrue(engine.contains("CompletableFuture.supplyAsync"));
        assertFalse(source("catalog/TrackTarget.java").contains("NbsSong"));
    }

    @Test
    void noOnlinePlaylistOrOldImplementationNamesRemain() throws IOException {
        String all = allSources();
        for (String removed : List.of(
                "MusicCatalog", "MusicAudioCache", "MusicPlayer",
                "JukeboxQueueManager", "JukeboxAutoPlayManager",
                "AudioPlaybackBackend", "NbsPlaybackBackend",
                "VanillaRecordSoundSuppressor", "LxRemoteSourceManager",
                "AudioMetadataParser", "songListSearch", "songListDetail", "SONG_LIST")) {
            assertFalse(all.contains(removed), removed);
        }
        assertFalse(Files.exists(Path.of("src/main/java/org/encinet/mik/module/musicdisc")));
        assertFalse(Files.exists(Path.of("src/main/resources/music/languages")));
        assertFalse(Files.exists(Path.of("src/main/resources/music.languages")));
    }

    @Test
    void runtimeDependenciesAreOwnedByMikWithoutPvAddonLibraries() throws IOException {
        String loader = Files.readString(Path.of("src/main/java/org/encinet/mik/MikLoader.java"));
        String build = Files.readString(Path.of("build.gradle"));
        assertTrue(loader.contains("org.graalvm.polyglot:js:pom:25.1.3"));
        assertTrue(loader.contains("net.jthink:jaudiotagger:3.0.1"));
        assertFalse(loader.contains("pv-addon-discs"));
        assertFalse(loader.contains("pv-addon-lavaplayer-lib"));
        assertFalse(build.contains("pv-addon-discs"));
        assertFalse(build.contains("pv-addon-lavaplayer-lib"));
    }

    @Test
    void generatedStateUsesDocumentedDedicatedDirectoriesWithoutMusicConfig() throws IOException {
        String module = source("MusicModule.java");
        assertTrue(module.contains("resolve(\"music\")"));
        assertTrue(module.contains("resolve(\"lxmusic\")"));
        assertTrue(module.contains("resolve(\"cache/music\")"));
        assertTrue(module.contains("resolve(\"state/music-disc.key\")"));
        assertFalse(module.contains("getConfig()"));
        assertFalse(module.contains("saveDefaultConfig()"));
    }

    @Test
    void everyMusicMessageHasAProductionCallSite() throws IOException {
        String production = allSources();
        List<String> unused = Arrays.stream(org.encinet.mik.module.i18n.Message.values())
                .map(Enum::name).filter(name -> name.startsWith("MUSIC_"))
                .filter(name -> !production.contains("Message." + name)).sorted().toList();
        assertEquals(List.of(), unused, () -> "Unused music message keys: " + unused);
    }

    @Test
    void reloadCommandDistinguishesPartialSuccessFromCompleteFailure() throws IOException {
        String command = source("command/MusicCommandRegistrar.java");
        assertTrue(command.contains("if (!report.anySuccessful())"));
        assertTrue(command.contains("Message.MUSIC_RELOAD_PARTIAL"));
        assertTrue(command.contains("Message.MUSIC_RELOAD_FAILED"));
    }

    @Test
    void delayedRecordRemovalCanOnlyStopTheCapturedPlaybackAttempt() throws IOException {
        String listener = source("listener/MusicJukeboxListener.java");
        String playback = source("jukebox/JukeboxPlaybackService.java");
        String callback = method(listener, "private void stopAfterConfirmedRemoval(",
                "@EventHandler(priority = EventPriority.HIGH");

        assertTrue(listener.contains("playbackService.activePlayback(block)"));
        assertTrue(listener.contains("playbackService.activePlayback(sourceLocation.getBlock())"));
        assertTrue(callback.contains("playbackService.stopIfCurrent(block, movedPlayback)"));
        assertFalse(callback.contains("playbackService.stop(block)"));
        assertFalse(callback.contains("stopJukebox(block)"));
        assertTrue(playback.contains("public PlaybackHandle activePlayback(Block block)"));
        assertTrue(playback.contains("public boolean stopIfCurrent(Block block, PlaybackHandle expected)"));
        assertFalse(playback.contains("stopIfTrack("));
    }

    @Test
    void customDiscsNeverDelegatePlaybackToTheirVanillaMaterial() throws IOException {
        String factory = source("disc/MusicDiscFactory.java");
        String listener = source("listener/MusicJukeboxListener.java");
        String insertion = method(listener, "public void onCustomJukeboxInteract(",
                "@EventHandler(priority = EventPriority.MONITOR");

        assertTrue(factory.contains("disc.unsetData(DataComponentTypes.JUKEBOX_PLAYABLE)"));
        assertFalse(factory.contains("prepareForJukebox"));
        assertTrue(insertion.contains("event.setCancelled(true)"));
        assertTrue(insertion.contains("interactionItem.asOne()"));
        assertTrue(insertion.contains("jukebox.setRecord(inserted)"));
        assertTrue(insertion.contains("playbackService.playInsertedDisc(event.getPlayer(), jukebox)"));
    }

    @Test
    void automatedCustomDiscInsertionIsTransactionalAndKeepsVanillaBehavior() throws IOException {
        String listener = source("listener/MusicJukeboxListener.java");
        String inventoryMove = method(listener, "public void onAutomatedJukeboxMove(",
                "@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)\n"
                        + "    public void onAutomatedJukeboxDispense(");
        String dispenser = method(listener, "public void onAutomatedJukeboxDispense(",
                "private void stopAfterConfirmedRemoval(");
        String insertion = method(listener, "private void insertAutomatedDisc(",
                "private static int findSimilarItem(");

        assertTrue(inventoryMove.contains("event.setCancelled(true)"));
        assertTrue(inventoryMove.contains("scheduleAutomatedInsertion("));
        assertTrue(dispenser.contains("event.getBlock().getRelative(directional.getFacing())"));
        assertTrue(dispenser.contains("event.setCancelled(true)"));
        assertTrue(dispenser.contains("DataComponentTypes.JUKEBOX_PLAYABLE"));
        assertTrue(dispenser.contains(", event.getItem(), customDisc)"));
        assertTrue(insertion.contains("playbackService.playInsertedDisc(null, jukebox)"));
        assertTrue(insertion.indexOf("playbackService.playInsertedDisc(null, jukebox)")
                < insertion.indexOf("consumeOne(source, sourceSlot, disc)"));
        assertTrue(insertion.contains("playbackService.stopAndClear(block)"));
        assertTrue(listener.contains("currentSourceInventory(source, sourceLocation, sourceType)"));
        assertTrue(listener.contains("instanceof BlockInventoryHolder holder"));
        assertTrue(listener.contains("sourceLocation.getBlock().getType() == sourceType"));
        assertTrue(listener.contains("MusicDiscKeys.isInternal(event.getItem())"));
        assertFalse(listener.contains("event.getItem().getType().isRecord()"));
    }

    @Test
    void jukeboxControlsDisplayAndEjectVanillaRecords() throws IOException {
        String gui = source("ui/JukeboxControlGui.java");
        String listener = source("listener/JukeboxControlListener.java");
        String playback = source("jukebox/JukeboxPlaybackService.java");

        assertTrue(gui.contains("jukebox.getRecord().asOne()"));
        assertTrue(gui.contains("return jukebox.isPlaying() ? PlaybackStatus.PLAYING"));
        assertTrue(gui.contains("MusicDiscKeys.isCustomDisc(jukebox.getRecord())"));
        assertTrue(gui.contains("worldItemDecoration(\"disc\""));
        assertTrue(gui.contains("FloatingMenuDecoration.Motion.SPIN"));
        assertTrue(gui.contains("FloatingMenuAppearance.TRANSPARENT"));
        assertFalse(gui.contains("MUSIC_JUKEBOX_TITLE"));
        assertFalse(gui.contains("element(\"current\""));
        assertTrue(gui.contains("if (control.jukebox().hasRecord())"));
        assertTrue(listener.contains("!jukebox.hasRecord()"));
        assertTrue(playback.contains("|| !jukebox.hasRecord()"));
    }

    @Test
    void rhythmInputUsesHotbarOneThroughFourWithNineAsTheNeutralSlot()
            throws IOException {
        String rhythm = source("rhythm/RhythmGameService.java");
        String calibration = source("rhythm/RhythmLatencyCalibration.java");

        assertTrue(rhythm.contains(
                ".movementPolicy(FloatingMenuMovementPolicy.CAPTURED_INPUT)"));
        assertFalse(rhythm.contains("menu.on(FloatingMenuInteraction.HOTKEY,"));
        assertTrue(rhythm.contains("Component.keybind(\"key.sneak\""));
        assertTrue(rhythm.contains("if (exitPressed)"));
        assertTrue(rhythm.contains("public void onHeldItemChange(PlayerItemHeldEvent event)"));
        assertTrue(rhythm.contains("RhythmInput.fromHotbarSlot(event.getNewSlot())"));
        assertTrue(rhythm.contains("NEUTRAL_HOTBAR_SLOT = 8"));
        assertTrue(rhythm.contains("selectNeutralHotbarSlot(player)"));
        assertTrue(rhythm.contains("expected.heldSlotBeforeGame"));
        assertTrue(rhythm.contains("public void onSwapHandItems(PlayerSwapHandItemsEvent event)"));
        assertFalse(rhythm.contains("List.of(RhythmInput.FOUR)"));
        assertTrue(rhythm.contains("game.mode != RhythmGameMode.FALLING"));
        assertTrue(rhythm.contains("event.setCancelled(true)"));
        assertFalse(rhythm.contains("event.setTo(locked)"));
        assertTrue(rhythm.contains("player.setVelocity(player.getVelocity().zero())"));
        assertTrue(rhythm.contains("game.latency.inputPosition("));
        assertTrue(rhythm.contains("game.latency.missPosition("));
        assertTrue(rhythm.contains("RhythmLatencyProfile profile = calibrationProfiles(player)"));
        assertTrue(rhythm.contains(".forChannel(playback.audioChannel())"));
        assertTrue(rhythm.contains("profile.judgementOffsetMillis()"));
        assertTrue(rhythm.contains("profile.animationOffsetMillis()"));
        assertTrue(rhythm.contains("rhythm_minecraft_judgement_ms"));
        assertTrue(rhythm.contains("rhythm_minecraft_animation_ms"));
        assertTrue(rhythm.contains("rhythm_plasmo_judgement_ms"));
        assertTrue(rhythm.contains("rhythm_plasmo_animation_ms"));
        assertFalse(rhythm.contains("rhythm_latency_base_calibration_ms"));
        assertFalse(rhythm.contains("rhythm_animation_calibration_ms"));
        assertFalse(rhythm.contains("rhythm_latency_calibration_ms"));
        assertFalse(rhythm.contains("rhythm_latency_nbs_calibration_ms"));
        assertTrue(rhythm.contains("PersistentDataType.INTEGER"));
        assertTrue(rhythm.contains(
                ".currentNetworkRttMillis(player.getPing())"));
        assertTrue(rhythm.contains(
                "rawErrorMillis, networkRttMillis,"));
        assertFalse(rhythm.contains("calibration.latency"));
        assertTrue(calibration.contains(
                "(long) rawErrorMillis - networkRttMillis"));
        assertTrue(rhythm.contains("inputTimestamps.claimHotbar("));
        assertTrue(rhythm.contains("game.playbackClock.positionAt("));
        assertTrue(rhythm.contains("game.latency.visualPosition("));
        assertTrue(rhythm.contains("game.latency.visualNetworkPosition("));
        assertTrue(rhythm.contains("game.latency.totalCompensationMillis()"));
        assertFalse(rhythm.contains("- Math.clamp(player.getPing()"));
    }

    @Test
    void rhythmGameRequiresOneCompletedLatencyTestAndHasNoSongFineTuning()
            throws IOException {
        String rhythm = source("rhythm/RhythmGameService.java");
        String gui = source("ui/JukeboxControlGui.java");
        String actions = source("ui/JukeboxControlActionHandler.java");

        assertTrue(rhythm.contains("implements Listener, AutoCloseable,"));
        assertTrue(rhythm.contains("RhythmCalibrationStatus"));
        assertTrue(rhythm.contains(
                "public boolean hasCompletedLatencyCalibration(Player player)"));
        assertEquals(2, occurrences(rhythm,
                "if (!requireCompletedCalibration(player))"));
        assertTrue(gui.contains(
                "calibrationStatus.hasCompletedLatencyCalibration(player)"));
        assertTrue(gui.contains("MUSIC_RHYTHM_CALIBRATION_REQUIRED"));
        assertTrue(gui.contains("MUSIC_RHYTHM_CALIBRATION_COMPLETE"));
        assertEquals(1, occurrences(gui, "\"latency-calibration\""));
        assertFalse(gui.contains("source-latency-calibration"));
        assertFalse(actions.contains("SourceLatencyCalibration"));
        assertFalse(rhythm.contains("SOURCE_AUDIO"));
        assertFalse(rhythm.contains("sourceFineTune"));
        assertFalse(rhythm.contains("openSourceCalibration"));
    }

    @Test
    void calibrationAndGameplayShareHighLatencyAdmissionAndRuntimeGuards()
            throws IOException {
        String rhythm = source("rhythm/RhythmGameService.java");
        String guard = source("rhythm/RhythmNetworkLatencyGuard.java");

        assertEquals(4, occurrences(rhythm,
                "if (!requirePlayableNetwork(player))"));
        assertTrue(rhythm.contains(
                "networkLatencyExceeded(player, game.networkLatency"));
        assertTrue(rhythm.contains(
                "networkLatencyExceeded(player, calibration.networkLatency"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_NETWORK_TOO_HIGH"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_NETWORK_WARNING"));
        assertTrue(guard.contains("MAXIMUM_PLAYABLE_RTT_MILLIS = 350"));
        assertTrue(guard.contains("REQUIRED_CONSECUTIVE_HIGH_SAMPLES = 3"));
        assertTrue(guard.contains("SAMPLE_INTERVAL_NANOS = 1_000_000_000L"));
        assertTrue(guard.contains("consecutiveHighSamples = 0"));
    }

    @Test
    void uncalibratedStartUsesAFloatingConfirmationWithoutWeakeningServiceGuards()
            throws IOException {
        String gui = source("ui/JukeboxControlGui.java");
        String listener = source("listener/JukeboxControlListener.java");
        String prompt = source("ui/RhythmCalibrationPrompt.java");
        String rhythm = source("rhythm/RhythmGameService.java");
        String module = source("MusicModule.java");
        String controls = method(gui, "private void addRhythmModeControls(",
                "private void addSoundControls(");

        assertEquals(1, occurrences(controls, "rhythmControl.disabled("),
                "only an unavailable rhythm track may disable Start Game");
        assertTrue(listener.contains(
                "if (!rhythmGameService.hasCompletedLatencyCalibration(player))"));
        assertTrue(listener.contains("calibrationPrompt.show(player,"));
        assertTrue(listener.contains("confirmLatencyCalibration(confirmed, target)"));
        assertTrue(listener.contains("resolveJukebox(player, location)"));
        assertTrue(prompt.contains("FloatingMenuScreen<PromptState>"));
        assertTrue(prompt.contains("\"rhythm-calibration-prompt\""));
        assertTrue(prompt.contains("screen.open(player, new PromptState(confirmAction))"));
        assertTrue(prompt.contains("requireSpatialPresentation()"));
        assertTrue(prompt.contains("stableAnchor()"));
        assertTrue(prompt.contains("FloatingMenuLayouts.information(\"information\")"));
        assertTrue(prompt.contains("FloatingMenuLayouts.actions(\"actions\", 2)"));
        assertTrue(prompt.contains("Material.LIME_CONCRETE"));
        assertTrue(prompt.contains("Material.GRAY_CONCRETE"));
        assertTrue(prompt.contains("state.confirmAction().accept(confirmed)"));
        assertTrue(prompt.contains("if (handle.depth() > 0) handle.back()"));
        assertTrue(prompt.contains("MUSIC_RHYTHM_CALIBRATION_PROMPT_REASON"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_CALIBRATION_STAGE_VISUAL"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_CALIBRATION_STAGE_MINECRAFT"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_CALIBRATION_STAGE_PLASMO"));
        assertFalse(prompt.contains("io.papermc.paper.dialog"));
        assertFalse(prompt.contains("DialogType"));
        assertFalse(prompt.contains("showDialog("));
        assertFalse(prompt.contains("Bukkit"));
        assertFalse(prompt.contains("sendActionBar"));
        assertEquals(2, occurrences(rhythm,
                "if (!requireCompletedCalibration(player))"));
        assertTrue(module.contains(
                "new RhythmCalibrationPrompt(languageService)"));
    }

    @Test
    void latencyTestSilencesEveryJukeboxBackendOnlyForItsPlayer()
            throws IOException {
        String playback = source("jukebox/JukeboxPlaybackService.java");
        String audio = source("jukebox/AudioPlaybackEngine.java");
        String nbs = source("jukebox/NbsPlaybackEngine.java");
        String rhythm = source("rhythm/RhythmGameService.java");

        assertTrue(playback.contains("RhythmPlaybackGateway, RhythmPlaybackIsolation"));
        assertTrue(playback.contains("audioAudience::canHear"));
        assertTrue(playback.contains("SilenceLease silenceFor(Player player)"));
        assertTrue(audio.contains("proximitySource.<VoicePlayer>addFilter"));
        assertTrue(audio.contains("audibleToPlayer.test("));
        assertTrue(nbs.contains(
                ".filter(player -> audibleToPlayer.test(player.getUniqueId()))"));
        assertTrue(rhythm.contains("playbackIsolation.silenceFor(player)"));
        assertTrue(rhythm.contains("releaseCalibrationResources(expected)"));
        assertTrue(rhythm.contains("calibration.silenceLease.close()"));
    }

    @Test
    void completeLatencyTestMeasuresMinecraftAndPlasmoVoiceIndependently()
            throws IOException {
        String rhythm = source("rhythm/RhythmGameService.java");
        String calibrationAudio = source(
                "rhythm/calibration/PlasmoVoiceCalibrationAudio.java");
        String frameProvider = source(
                "rhythm/calibration/LoopingAudioFrameProvider.java");
        String pattern = source(
                "rhythm/calibration/RhythmCalibrationPattern.java");
        String stage = source("rhythm/calibration/CalibrationStage.java");
        String calibrationResult = source(
                "rhythm/RhythmCalibrationResult.java");
        String acceptsInput = method(stage,
                "public boolean acceptsInput(boolean pointerInput)",
                "public boolean requiresPlasmoVoice()");
        String calibrationView = method(rhythm,
                "private FloatingMenuDefinition renderCalibration(",
                "private FloatingMenuDefinition unavailableCalibration(");
        String calibrationProgress = method(rhythm,
                "private Component calibrationProgress(",
                "private static String signedMillis(");
        String advancePlasmo = method(rhythm,
                "private CalibrationAdvance advancePlasmo(",
                "private void restartPlasmo(");
        String playback = source("jukebox/JukeboxPlaybackService.java");
        String module = source("MusicModule.java");

        assertTrue(rhythm.contains("MINECRAFT_LISTEN"));
        assertTrue(rhythm.contains("MINECRAFT_AUDIO"));
        assertTrue(rhythm.contains("TRANSITION_TO_PLASMO"));
        assertTrue(rhythm.contains("PLASMO_LISTEN"));
        assertTrue(rhythm.contains("PLASMO_AUDIO"));
        assertTrue(rhythm.contains("TRANSITION_TO_VISUAL"));
        assertTrue(rhythm.contains("VISUAL_LISTEN"));
        assertFalse(rhythm.contains("TRANSITION_TO_VALIDATION"));
        assertFalse(rhythm.contains("VALIDATE_MINECRAFT"));
        assertFalse(rhythm.contains("VALIDATE_PLASMO_LISTEN"));
        assertFalse(rhythm.contains("VALIDATE_PLASMO"));
        assertFalse(rhythm.contains("VALIDATION_COMPLETE"));
        assertTrue(rhythm.contains("SoundCategory.RECORDS"));
        assertTrue(calibrationResult.contains(
                "RhythmCalibrationProfiles.fromTests("));
        assertTrue(rhythm.contains("RhythmCalibrationPattern.fixed()"));
        assertTrue(rhythm.contains("pattern.durationMillis()"));
        assertTrue(rhythm.contains("playMinecraftCues(player, 0L, false)"));
        assertTrue(rhythm.contains("playMinecraftCues(player, 0L, true)"));
        assertTrue(rhythm.contains("output.play(player, pattern)"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_CALIBRATION_LISTEN"));
        assertFalse(acceptsInput.contains("MINECRAFT_LISTEN"));
        assertFalse(acceptsInput.contains("PLASMO_LISTEN"));
        assertTrue(acceptsInput.contains("MINECRAFT_AUDIO"));
        assertTrue(acceptsInput.contains("PLASMO_AUDIO"));
        assertTrue(acceptsInput.contains("POINTER_VISUAL"));
        assertFalse(calibrationView.contains("signedMillis("));
        assertFalse(calibrationView.contains("medianDeviationMillis"));
        assertFalse(calibrationView.contains("compensationMillis()"));
        assertFalse(calibrationView.contains("MAD"));
        assertTrue(calibrationView.contains("\"calibration-target\""));
        assertTrue(calibrationView.contains("Material.GLASS"));
        assertFalse(calibrationView.contains("Material.TARGET"));
        assertFalse(calibrationProgress.contains("REQUIRED_SAMPLES"));
        assertFalse(calibrationProgress.contains("sampleCount()"));
        assertFalse(calibrationProgress.contains("observationCount()"));
        assertFalse(calibrationProgress.contains("rejectedCount()"));
        assertFalse(rhythm.contains("MUSIC_RHYTHM_CALIBRATION_REJECTED"));
        assertFalse(rhythm.contains("CALIBRATION_CUE_INTERVAL_MILLIS"));
        assertFalse(rhythm.contains("CALIBRATION_PRESENTED_CUES"));
        assertFalse(rhythm.contains("stageExpired()"));
        assertFalse(rhythm.contains("restartCalibrationStage"));
        assertTrue(rhythm.contains("calibration.closeStagePlayback()"));
        assertTrue(rhythm.contains("== CalibrationAdvance.START_PLASMO"));
        assertTrue(advancePlasmo.contains(
                "plasmoMeasurement.advanceToCycle(completed);"));
        assertTrue(advancePlasmo.contains("return CalibrationAdvance.NONE;"));
        assertFalse(advancePlasmo.contains(
                "plasmoMeasurement.advanceToCycle(completed);\n"
                        + "            return CalibrationAdvance.START_PLASMO;"));
        assertTrue(rhythm.contains("calibrationAudioOutput.available(player)"));
        assertTrue(frameProvider.contains(
                "RhythmCalibrationDrumSynth.timeline(pattern)"));
        assertTrue(calibrationAudio.contains("sourceLine.createDirectSource("));
        assertTrue(calibrationAudio.contains("voicePlayer, false"));
        assertTrue(calibrationAudio.contains("source.setSender(voicePlayer)"));
        assertFalse(calibrationAudio.contains("createPlayerSource("));
        assertFalse(calibrationAudio.contains("addFilter("));
        assertTrue(calibrationAudio.contains("public boolean active()"));
        assertTrue(calibrationAudio.contains("drainPresentations()"));
        assertTrue(calibrationAudio.contains("PlaybackProgress progress()"));
        assertEquals(1, occurrences(calibrationAudio, "createAudioSender("));
        assertTrue(frameProvider.contains("implements AudioFrameProvider"));
        assertTrue(frameProvider.contains("MONO_FRAME_SAMPLES = 960"));
        assertTrue(frameProvider.contains("new AudioFrameResult.Provided(frame)"));
        assertTrue(frameProvider.contains("lastFrameAtNanos"));
        assertTrue(frameProvider.contains("completedCycles"));
        assertFalse(frameProvider.contains("EndOfStream"));
        assertFalse(frameProvider.contains("addEnd("));
        assertTrue(pattern.contains("PHRASE_BEATS = 8"));
        assertTrue(pattern.contains("BEAT_INTERVAL_MILLIS = 800L"));
        assertTrue(pattern.contains("next - previous != BEAT_INTERVAL_MILLIS"));
        assertTrue(pattern.contains("cueIndexNearest("));
        assertTrue(playback.contains("RhythmAudioChannel.MINECRAFT"));
        assertTrue(playback.contains("RhythmAudioChannel.PLASMO_VOICE"));
        assertTrue(module.contains("new PlasmoVoiceCalibrationAudio("));
    }

    @Test
    void rhythmModesShareOneChartWhileOwningIndependentPresentations()
            throws IOException {
        String rhythm = source("rhythm/RhythmGameService.java");
        String radial = source("rhythm/mode/radial/RhythmRadialPath.java");
        String spatial = source("rhythm/mode/spatial/RhythmSpatialPath.java");
        String arena = source("rhythm/mode/spatial/RhythmSpatialArena.java");
        String slider = source("rhythm/mode/spatial/RhythmSpatialSlider.java");
        String spatialGameplay = source(
                "rhythm/mode/spatial/RhythmSpatialGameplay.java");
        String timestamps = source(
                "rhythm/input/RhythmInputTimestampSource.java");
        String open = method(rhythm, "public boolean open(", "private void start(");
        String start = method(rhythm, "private void start(",
                "private static void preparePlayerForCapturedInput(");

        assertTrue(rhythm.contains("new FloatingMenuScreen<>(\"jukebox-rhythm-mode\""));
        assertTrue(rhythm.contains("case FALLING -> renderFallingScene("));
        assertTrue(rhythm.contains("case RADIAL -> renderRadialScene("));
        assertTrue(rhythm.contains("case SPATIAL_AIM -> renderSpatialScene("));
        assertEquals(1, occurrences(rhythm,
                "new RhythmChartView(playback.timeline(), difficulty"));
        assertFalse(open.contains("playbackGateway.join("));
        assertTrue(start.contains("playbackGateway.join("));
        assertTrue(rhythm.contains("game.participation.close()"));
        assertTrue(rhythm.contains("GAME_JOIN_DELAY_MILLIS = 3_000L"));
        assertTrue(rhythm.contains("new RhythmGamePreRoll(GAME_JOIN_DELAY_MILLIS)"));
        assertTrue(rhythm.contains("game.participation::startPlayback"));
        assertTrue(rhythm.contains("renderGameOverlay(menu, player, playback"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_CHART_PREPARING"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_GET_READY"));
        assertTrue(rhythm.contains("MUSIC_RHYTHM_GO"));
        assertTrue(radial.contains("MAXIMUM_TURN_DEGREES = 28.0"));
        assertTrue(radial.contains("MAXIMUM_TURN_ACCELERATION_DEGREES = 8.0"));
        assertTrue(radial.contains("cuesUntilNewTarget = 3 +"));
        assertTrue(radial.contains("static Location point(Location anchor"));
        assertTrue(rhythm.contains("FloatingMenuDecoration.worldBlock("));
        assertTrue(rhythm.contains("public void onRadialSwing(PlayerAnimationEvent event)"));
        assertTrue(rhythm.contains("public void onRadialInteract(PlayerInteractEvent event)"));
        assertTrue(rhythm.contains("game.session.hit("));
        assertTrue(rhythm.contains("RhythmGameResult.from("));
        assertTrue(rhythm.contains("pointerInputDelta(player)"));
        assertTrue(spatial.contains("maximumTurnDegrees(interval)"));
        assertTrue(arena.contains("inspectPlayableVolume()"));
        assertTrue(slider.contains("never require a held-button state"));
        assertTrue(slider.contains("MAXIMUM_INTERVAL_MILLIS = 1_400L"));
        assertTrue(slider.contains("MAXIMUM_CONSECUTIVE_LINKS = 3"));
        assertTrue(slider.contains("ENDPOINT_SETTLE_MILLIS = 50L"));
        assertTrue(slider.contains("difficulty.goodWindowMillis() * 2L"));
        assertTrue(slider.contains("static final class State"));
        assertTrue(rhythm.contains("renderSpatialSlider("));
        assertTrue(rhythm.contains("new RhythmSpatialGameplay("));
        assertFalse(rhythm.contains("new RhythmSpatialPath("));
        assertFalse(rhythm.contains("new RhythmSpatialSlider("));
        assertTrue(spatialGameplay.contains("new RhythmSpatialPath("));
        assertTrue(spatialGameplay.contains("new RhythmSpatialSlider("));
        assertTrue(spatialGameplay.contains("void hit("));
        assertTrue(spatialGameplay.contains("void miss("));
        assertTrue(spatialGameplay.contains("Presentation.preview("));
        assertTrue(spatialGameplay.contains("sliderStillVisible("));
        assertTrue(spatialGameplay.contains("sliderLinks.values().removeIf("));
        assertTrue(spatialGameplay.contains("void framePresented("));
        assertTrue(spatialGameplay.contains("boolean presentationFair("));
        assertFalse(rhythm.contains("Map<SliderKey"));
        assertTrue(timestamps.contains("movement.hasRotationChanged()"));
        assertFalse(rhythm.contains("RADIAL_CENTER"));
        assertFalse(rhythm.contains("radial:target:"));
    }

    @Test
    void audioRhythmAnalysisUsesOnlyCompleteMediaAndPublishesAtomically()
            throws IOException {
        String engine = source("jukebox/AudioPlaybackEngine.java");
        String analyzer = source("jukebox/OfflineRhythmAnalyzer.java");
        String loader = source("jukebox/AudioTrackLoader.java");

        assertTrue(engine.contains("rhythmAnalyzer.analyze(music, rhythmTimeline)"));
        assertFalse(engine.contains("setFilterFactory("));
        assertTrue(loader.contains("resources::acquireComplete"));
        assertTrue(analyzer.contains("loader.loadComplete(music)"));
        assertTrue(analyzer.contains("new RhythmTimeline(destination.seed())"));
        assertTrue(analyzer.contains("WholeTrackRhythmExtractor::new"));
        assertTrue(analyzer.indexOf("drain(decoder)") < analyzer.indexOf("publish(track);"));
    }

    private static void assertNoImport(Path directory, String forbidden) throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(directory)) {
            violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "import " + forbidden))
                    .map(Path::toString).sorted().toList();
        }
        assertEquals(List.of(), violations,
                () -> "Forbidden package dependency " + forbidden + " in " + violations);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MUSIC.resolve(relative));
    }

    private static String allSources() throws IOException {
        StringBuilder result = new StringBuilder();
        try (Stream<Path> files = Files.walk(MUSIC)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }

    private static String method(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, () -> "Missing source marker: " + startMarker);
        assertTrue(end > start, () -> "Missing source marker after method: " + endMarker);
        return source.substring(start, end);
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(value, cursor)) >= 0) {
            count++;
            cursor += value.length();
        }
        return count;
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
