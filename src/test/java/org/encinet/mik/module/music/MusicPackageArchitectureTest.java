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
            "catalog", "command", "disc", "jukebox", "listener", "lyrics", "online", "ui");

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
        assertNoImport(MUSIC.resolve("ui"), "org.encinet.mik.module.music.listener.");
        assertNoImport(MUSIC.resolve("listener"), "org.encinet.mik.module.music.online.LxCustomSource");
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
    void browserSessionOwnsSearchAndInventoryState() throws IOException {
        String gui = source("ui/MusicBrowserGui.java");
        String sessions = source("ui/MusicBrowserSessions.java");

        assertFalse(gui.contains("Map<UUID, PlayerState>"));
        assertFalse(gui.contains("playerJukeboxContext"));
        assertTrue(sessions.contains("Map<UUID, Session>"));
        assertTrue(sessions.contains("Map<UUID, JukeboxContext>"));
        assertTrue(sessions.contains("completeSearch("));
        assertTrue(sessions.contains("activeInventory"));
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
    void jukeboxPlaybackModesAlwaysOperateOnTheEditablePlaylist() throws IOException {
        String queue = source("jukebox/JukeboxQueueService.java");
        String gui = source("ui/JukeboxControlGui.java");
        String control = source("listener/JukeboxControlListener.java");

        assertTrue(queue.contains("JukeboxPlaybackMode.REPEAT_ALL"));
        assertTrue(queue.contains("JukeboxPlaybackMode.REPEAT_ONE"));
        assertTrue(queue.contains("JukeboxPlaybackMode.SHUFFLE"));
        assertTrue(queue.contains("trackSelector.select(queue"));
        assertFalse(queue.contains("trackPool.tracks()"));
        assertFalse(gui.contains("createDisabledQueueItem"));
        assertFalse(control.contains("removeFromQueue(track);\n"
                + "                player.sendMessage"));
        assertTrue(source("jukebox/JukeboxAutoPlayService.java")
                .contains("playback.playInsertedDisc(nearestPlayer, jukebox)"));
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

        assertTrue(gui.contains("createVanillaRecordItem(player, jukebox)"));
        assertTrue(gui.contains("ItemStack record = jukebox.getRecord().asOne()"));
        assertTrue(gui.contains("PlaybackStatus status = jukebox.isPlaying()"));
        assertTrue(gui.contains("&& !MusicDiscKeys.isCustomDisc(jukebox.getRecord())"));
        assertTrue(gui.contains("if (jukebox.hasRecord())"));
        assertTrue(listener.contains("if (!jukebox.hasRecord())"));
        assertTrue(playback.contains("|| !jukebox.hasRecord()"));
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

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
