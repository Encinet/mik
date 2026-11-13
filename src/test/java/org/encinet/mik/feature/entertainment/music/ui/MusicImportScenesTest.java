package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.entertainment.music.application.*;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.jukebox.*;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.playlist.Playlist;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.runtime.FloatingMenuService;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;

class MusicImportScenesTest {
    @Test void largeImportPreviewsFitTheRealWorkspaceWithTaskFeedbackAndRestoreBrowsing() throws Exception {
        List<MusicTrack> tracks = IntStream.range(0,5000).mapToObj(index -> MusicWorkspaceStateTest.track("Imported song 歌曲 " + index)).toList();
        for (var locale : Language.values()) try (var bukkit = mockStatic(Bukkit.class)) {
            var plugin = mock(JavaPlugin.class);
            var scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenReturn(mock(BukkitTask.class));
            var bundle = FluentTestSupport.bundle(locale);
            var language = mock(LanguageService.class, call -> {
                Object[] args = call.getRawArguments();
                if (call.getMethod().getName().equals("text"))
                    return Component.text(FluentTestSupport.text(bundle,(Message)args[1],(Object[])args[3]));
                if (call.getMethod().getName().equals("t"))
                    return FluentTestSupport.text(bundle,(Message)args[1],(Object[])args[2]);
                return RETURNS_DEFAULTS.answer(call);
            });
            var player = mock(Player.class);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            var world = mock(World.class);
            when(world.getUID()).thenReturn(UUID.randomUUID());
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world,0,64,0));
            var playback = mock(MusicPlaybackController.class);
            var registry = mock(MusicDeviceRegistry.class);
            when(registry.snapshot()).thenReturn(MusicState.empty());
            var target = UUID.randomUUID();
            var device = new MusicDevice(target,UUID.randomUUID(),"world",0,64,0,"Living room 客厅",100,64,null,0);
            when(playback.snapshot(target)).thenReturn(new MusicPlaybackSnapshot(target,UUID.randomUUID(),UUID.randomUUID(),tracks.getFirst(),PlaybackStatus.PLAYING,0,0,false,null));
            when(playback.program(target)).thenReturn(PlaybackProgram.empty());
            when(registry.playbackTarget(target)).thenReturn(new DeviceTarget(target,device.name(),List.of(device)));
            when(registry.target(target)).thenReturn(new DeviceTarget(target,device.name(),List.of(device)));
            when(registry.name(player,target)).thenReturn(device.name());
            when(registry.online(device)).thenReturn(true);
            var queries = mock(MusicQueries.class);
            when(queries.ready()).thenReturn(true);
            when(queries.snapshot(any())).thenReturn(MusicState.empty());
            var playlists = mock(PlaylistService.class);
            var destinations = IntStream.range(0,20).mapToObj(index -> new Playlist(UUID.randomUUID(),player.getUniqueId(),
                    "Destination 歌单 " + index,"",null,false,1,List.of())).toList();
            when(playlists.owned(player.getUniqueId())).thenReturn(destinations);
            var menus = mock(FloatingMenuService.class);
            when(menus.queryInput(eq(player),any(),anyInt(),any(),any())).thenAnswer(_ -> {
                var query = mock(MenuQuerySession.class);
                when(query.content(anyString())).thenReturn(Ui.column().build());
                return query;
            });
            FloatingMenus.install(menus);
            try (var workspace = new MusicWorkspace(plugin,language,queries,playlists,
                    playback,registry,mock(MusicDeviceTravel.class),
                    mock(MusicGroupService.class),List::of,null,null,null,mock(MusicRooms.class),null)) {
                for (var page : List.of(ImportPage.SOURCE,ImportPage.MERGE)) for (boolean partial : new boolean[]{false,true})
                    for (boolean busy : new boolean[]{false,true}) for (boolean playing : new boolean[]{false,true}) {
                    var state = new MusicWorkspaceState();
                    if (playing) state.target(target);
                    var task = new Import(new Imported("wy","123","Imported playlist 歌单",tracks,
                            partial ? 6000 : tracks.size(),UUID.randomUUID()),page);
                    state.panel(task);
                    if (busy) state.begin(TaskKey.of(Work.IMPORT));
                    var scene = workspace.render(player,state);
                    for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) for (double scale : new double[]{.7,1.3}) {
                        String context = locale + " " + page + " font=" + font + " fov=" + fov + " scale=" + scale + " partial=" + partial + " busy=" + busy + " playing=" + playing;
                        var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(scene,font,new FloatingMenuFieldOfView(fov),scale),context);
                        assertDisjoint(frame);
                        for (String key : List.of("close-panel","player-target","close"))
                            assertNotNull(element(frame,key),context);
                        for (String key : List.of("import-new","import-view","retry-import"))
                            assertEquals(!busy,frame.elements().stream().anyMatch(item -> item.node().key().value().equals(key)),context);
                        if (busy) assertTrue(frame.elements().stream().anyMatch(item -> item.node().content().label()
                                .equals(workspace.label(player,"saving"))),context);
                        if (page == ImportPage.SOURCE) assertTrue(frame.pagination().getFirst().page().hasNext(),context);
                        assertEquals(page == ImportPage.SOURCE ? tracks.size() : destinations.size(),frame.pagination().getFirst().page().itemCount());
                        assertTrue(frame.elements().size() < UiWindowedList.MAX_VISIBLE_ITEMS,context);
                        assertFalse(frame.elements().stream().anyMatch(item -> item.node().key().value().startsWith("source:")
                                || item.node().key().value().equals("player-primary")),context);
                        if (partial) {
                            var warning = element(frame,UiKey.item("partial").value());
                            var full = workspace.label(player,"partial-import");
                            assertEquals(full,warning.node().input().tooltip().map(UiTooltip::text).orElse(warning.node().content().label()));
                        }
                    }
                    state.panel(new NoPanel());
                    var restored = SceneGeometryTestSupport.measure(workspace.render(player,state),1);
                    assertDisjoint(restored);
                    assertNotNull(element(restored,"source:CATALOG"));
                    if (playing) assertNotNull(element(restored,"player-primary"));
                }
            } finally { FloatingMenus.uninstall(); }
        }
    }
}
