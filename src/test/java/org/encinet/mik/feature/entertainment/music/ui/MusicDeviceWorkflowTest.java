package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
import org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.Collection;
import static org.encinet.mik.shared.menu.ui.UiSceneTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.entertainment.music.application.*;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import org.encinet.mik.feature.entertainment.music.jukebox.*;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.playback.QueueEntry;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.FloatingMenuService;
import org.encinet.mik.shared.menu.runtime.MenuInputViewTestSupport;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Exercise the actual host: an auxiliary task used to hide every member selection control. */
class MusicDeviceWorkflowTest {
    @Test void groupDetailsExplainSharedControlsAndEjectThroughTheGroupAcrossLanguages() throws Exception {
        for (var locale : Language.values()) try (var h = new Harness(locale)) {
            var group = UUID.randomUUID();
            h.data.devices.replaceAll((_,device) -> device.grouped(group));
            h.data.groups.put(group,new MusicState.Group(group,"Garden speakers",List.of(h.first,h.second),Map.of(),100,0));
            var target = new DeviceTarget(group,"Garden speakers",List.copyOf(h.data.devices.values()));
            doReturn(target).when(h.registry).target(group);
            doReturn("Garden speakers").when(h.registry).name(h.player,group);
            h.open(); h.workspace.panel(h.player,new DevicePanel(group,DeviceSection.SETTINGS));
            for (double font : new double[]{.7,1,1.8}) {
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(h.scene,font,
                        new FloatingMenuFieldOfView(70),1),locale+" font="+font);
                assertDisjoint(frame);
            }
            boolean help = false;
            boolean ejected = false;
            for (int pass=0;pass<12;pass++) {
                var frame = h.frame();
                help |= frame.elements().stream().anyMatch(item -> item.node().key().equals(UiKey.item("group-control-help")));
                var eject = frame.elements().stream().filter(item -> item.node().key().value().equals("eject")).findFirst();
                if (!ejected && eject.isPresent()) { h.click(eject.get()); ejected = true; }
                var page = frame.pagination().stream().filter(item -> item.path().contains("/task:")).findFirst();
                if (page.isEmpty() || !page.get().page().hasNext()) break;
                page.get().selectPage().accept(page.get().page().next().index());
            }
            assertTrue(help,locale.toString());
            assertTrue(ejected,locale.toString());
            verify(h.playback).eject(h.player,group);
        }
    }

    @Test void nearbyMarkersInvalidateTheWorkspaceWhenRangeOrAccessChangesOutsideTheDeviceDirectory() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            when(h.registry.targets(h.player)).thenReturn(List.of());
            when(h.menus.queryInput(eq(h.player),any(),anyInt(),any(),any())).thenAnswer(_ -> {
                var input = mock(MenuQuerySession.class);
                when(input.content(anyString())).thenReturn(Ui.column().build());
                return input;
            });
            var edge = new MusicDevice(UUID.randomUUID(),h.worldId,"world",64,64,0,"Nearby",100,64,null,0);
            h.data.devices.put(edge.id(),edge);
            var refresh = h.scene.refresh();
            Object outside = refresh.revision(h.player);
            var world = h.player.getWorld();
            when(h.player.getLocation()).thenReturn(new Location(world,1,64,0));
            Object inside = refresh.revision(h.player);
            assertNotEquals(outside,inside);
            refresh.action().execute(h.player,h.handle);
            assertTrue(h.scene.decorations().containsKey("mark:"+edge.id()));
            when(h.registry.mayLocate(h.player,edge)).thenReturn(false);
            assertNotEquals(inside,refresh.revision(h.player));
            refresh.action().execute(h.player,h.handle);
            assertFalse(h.scene.decorations().containsKey("mark:"+edge.id()));
        }
    }

    @Test void editingThePersonalDeviceAliasKeepsSettingsVisibleAndDisposesTheInput() throws Exception {
        for (Language locale : Language.values()) try (var h = new Harness(locale)) {
            when(h.registry.bound(h.player.getUniqueId(),h.second)).thenReturn(true);
            doAnswer(call -> {
                var session = mock(MenuFormSession.class);
                when(session.content(any())).thenReturn(MenuInputViewTestSupport.content(
                        call.getArgument(1),Component.text("Empty"),Component.text("Clear")));
                doAnswer(_ -> { call.getArgument(2,Runnable.class).run(); return null; }).when(session).focus(anyString());
                return session;
            }).when(h.menus).inlineInput(eq(h.player),any(),any(),any());
            h.open(); h.state().target(h.first);
            h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.SETTINGS));
            h.click("toggle");
            var alias = h.frame().elements().stream().filter(item -> item.path().contains("/device-alias/")
                    && item.node().key().equals(UiKey.item("device-alias:value"))).findFirst().orElseThrow();
            h.click(alias);
            for (double font : new double[]{.7,1,1.8}) for (double scale : new double[]{.7,1,1.2,1.3})
                for (int fov : new int[]{30,70,110}) {
                    var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(h.scene,font,
                            new FloatingMenuFieldOfView(fov),scale),locale+" font="+font+" scale="+scale+" fov="+fov);
                    assertDisjoint(frame);
                    for (String key : List.of("player-target","close")) assertNotNull(element(frame,key));
                    assertEquals(new DevicePanel(h.second,DeviceSection.SETTINGS),h.state().panel());
                    var popup = SceneGeometryTestSupport.readableFrame(h.popover.render(),font,new FloatingMenuFieldOfView(fov),scale);
                    assertDisjoint(popup);
                    for (String key : List.of("confirm","cancel")) assertNotNull(element(popup,key));
                }
            var input = h.popover;
            h.workspace.panel(h.player,new DevicePanel(h.first,DeviceSection.QUEUE));
            assertNotSame(input,h.popover);
        }
    }

    @Test void reopeningWithLongPlayingMetadataKeepsTheTaskAndPlayerVisible() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first);
            boolean compact = false;
            for (int lines : new int[]{1,4,12,24,48}) {
                var source = MusicWorkspaceStateTest.track("Song 歌曲");
                var song = new MusicTrack(source.id(),new TrackDetails(source.details().title(),
                                "Artist 歌手\n".repeat(lines),null,"mp3",source.details().audio()),source.target());
                doAnswer(call -> new MusicPlaybackSnapshot(call.getArgument(0),new UUID(2,1),new UUID(2,2),
                        song,PlaybackStatus.PLAYING,0,180000,true,null)).when(h.playback).snapshot(any());
                h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.QUEUE));
                h.handle.close(); h.workspace.open(h.player);
                var frame = SceneGeometryTestSupport.readableFrame(h.scene,.7,new FloatingMenuFieldOfView(70),1.2);
                assertDisjoint(frame);
                for (String key : List.of("close-panel","player-target","close")) assertNotNull(element(frame,key));
                compact |= frame.elements().stream().noneMatch(item -> item.node().key().value().equals("source:CATALOG"));
            }
            assertTrue(compact,"Overflowing playback metadata must leave room for the restored task");
        }
    }

    @Test void auxiliaryTasksFitWithNavigationAndTransportAtReadingBoundaries() throws Exception {
        for (Language locale : Language.values()) try (var h = new Harness(locale)) {
            h.open(); h.state().target(h.first);
            for (Panel panel : List.of(new DevicePanel(h.second,DeviceSection.SETTINGS),
                    new DevicePanel(h.second,DeviceSection.QUEUE),
                    new Import(null,ImportPage.SOURCE),new Import(null,ImportPage.PLATFORMS),new RoomPicker())) {
                h.workspace.panel(h.player,panel);
                for (double font : new double[]{.7,1,1.8}) for (double scale : new double[]{.7,1,1.3})
                    for (int fov : new int[]{30,70,110}) {
                        var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(h.scene,font,
                                new FloatingMenuFieldOfView(fov),scale),locale+" "+panel+" font="+font+" scale="+scale+" fov="+fov);
                        assertDisjoint(frame);
                        assertNotNull(element(frame,"close-panel"));
                        assertNotNull(element(frame,"player-target"));
                        assertNotNull(element(frame,"close"));
                    }
            }
        }
    }

    @Test void compactDevicePagesKeepEverySettingReachableAndRejectOldPageCallbacks() throws Exception {
        try (var h = new Harness(Language.RU_RU)) {
            h.open(); h.state().target(h.first);
            h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.SETTINGS));
            var seen = new HashSet<String>();
            java.util.function.IntConsumer previous = null;
            for (int pass = 0; pass < 12; pass++) {
                var frame = SceneGeometryTestSupport.readableFrame(h.scene,1.8,new FloatingMenuFieldOfView(30),1.3);
                assertDisjoint(frame);
                for (var item : frame.elements()) seen.add(item.node().key().value());
                var page = frame.pagination().stream().filter(item -> item.path().contains("/task:")).findFirst().orElseThrow();
                assertTrue(page.page().count() > 1);
                previous = page.selectPage();
                if (!page.page().hasNext()) break;
                previous.accept(page.page().next().index());
            }
            assertTrue(seen.containsAll(List.of("back-devices","use-device","group-with-devices","toggle","eject",
                    "sound:"+h.second+":volume","sound:"+h.second+":range")),seen.toString());
            h.workspace.panel(h.player,new DevicePanel(h.first,DeviceSection.SETTINGS));
            assertNotNull(previous);
            previous.accept(1);
            assertEquals(0,h.state().panelPage);
        }
    }

    @Test void managementAndPlaybackSelectionAreIndependentAndTheDirectoryRemainsReachable() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open();
            h.click(h.row(h.first,"use"));
            assertEquals(h.first,h.state().target());
            assertInstanceOf(NoPanel.class,h.state().panel());
            h.click(h.row(h.second,"manage"));
            assertEquals(new DevicePanel(h.second,DeviceSection.SETTINGS),h.state().panel());
            assertEquals(h.first,h.state().target());
            h.click("back-devices");
            assertInstanceOf(NoPanel.class,h.state().panel());
            assertNotNull(h.row(h.second,"manage"));
            h.click("devices");
            assertInstanceOf(NoPanel.class,h.state().panel());
            assertEquals(h.first,h.state().target());
        }
    }

    @Test void groupingFromTheInspectedDeviceShowsMembersAndSubmitsOnlyTheChosenDevices() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first);
            h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.SETTINGS));
            h.click("group-with-devices");
            var draft = ((GroupEditor)h.state().panel()).draft();
            assertEquals(Set.of(h.second),draft.members);
            assertFalse(draft.name.isBlank());
            assertFalse(h.entry("create-group").enabled());
            h.click(h.row(h.first,"select"));
            assertEquals(Set.of(h.first,h.second),draft.members);
            assertEquals(h.first,h.state().target());
            assertTrue(h.entry("create-group").enabled());
            h.click("create-group");
            verify(h.groups).create(h.player,Set.of(h.first,h.second),h.second,draft.name);
            verify(h.playback,never()).control(any(),any(),any());
        }
    }

    @Test void worldPickingReturnsToTheSameDraftAndRecordsTheDeviceWithoutChangingPlayback() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first); h.click("create-group");
            var draft = ((GroupEditor)h.state().panel()).draft();
            h.click("pick-world-member");
            assertTrue(draft.pickingInWorld);
            var box = mock(Jukebox.class);
            when(h.registry.discover(box)).thenReturn(CompletableFuture.completedFuture(h.device(h.second)));
            h.workspace.at(h.player,box);
            assertSame(draft,((GroupEditor)h.state().panel()).draft());
            assertFalse(draft.pickingInWorld);
            assertEquals(Set.of(h.first,h.second),draft.members);
            assertEquals(h.first,h.state().target());
            verify(h.registry).bind(h.player,h.second,"");
            h.click("cancel");
            assertInstanceOf(NoPanel.class,h.state().panel());
            verify(h.groups,never()).create(any(),anySet(),any(),anyString());
        }
    }

    @Test void groupSelectionFitsTranslatedReadingSizesInsideTheRealHost() throws Exception {
        for (Language locale : Language.values()) try (var h = new Harness(locale)) {
            h.open(); h.state().target(h.first);
            var queued = PlaybackProgram.empty().submit(List.of(QueueEntry.create("song",h.player.getUniqueId())),PlaybackProgram.Submission.APPEND);
            when(h.playback.program(h.first)).thenReturn(queued);
            h.click("create-group");
            for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) {
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(h.scene,font,new FloatingMenuFieldOfView(fov),1),locale+" @ "+font+" / "+fov);
                assertDisjoint(frame);
                assertNotNull(element(frame,"select"),locale.toString());
                assertNotNull(element(frame,"create-group"));
                assertNotNull(element(frame,"pick-world-member"));
                assertNotNull(element(frame,"cancel"));
                assertNotNull(element(frame,"group-source"));
                assertNotNull(element(frame,"create-play"));
            }
        }
    }

    @Test void pickingAnExistingGroupMemberReturnsToTheDraftWithoutAddingOrRecordingIt() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first); h.click("create-group");
            var draft = ((GroupEditor)h.state().panel()).draft();
            var member = h.device(h.second).grouped(UUID.randomUUID());
            h.click("pick-world-member");
            var box = mock(Jukebox.class);
            when(h.registry.discover(box)).thenReturn(CompletableFuture.completedFuture(member));
            h.workspace.at(h.player,box);
            assertSame(draft,((GroupEditor)h.state().panel()).draft());
            assertEquals(Set.of(h.first),draft.members); assertFalse(draft.pickingInWorld);
            assertEquals(h.first,h.state().target());
            verify(h.registry,never()).bind(h.player,h.second,"");
            verify(h.player,times(2)).sendActionBar(any(Component.class));
        }
    }

    @Test void choosingGroupContentUsesTheSelectedSourceAndClosedPickerActionsExpire() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            var content = PlaybackProgram.empty().submit(List.of(QueueEntry.create("song",h.player.getUniqueId())),PlaybackProgram.Submission.APPEND);
            when(h.playback.program(h.first)).thenReturn(content);
            when(h.playback.program(h.second)).thenReturn(content);
            h.open(); h.state().target(h.first); h.click("create-group"); h.click(h.row(h.second,"select"));
            var draft = ((GroupEditor)h.state().panel()).draft();
            assertEquals(h.first,draft.source);
            h.click("group-source");
            var popup = h.popover;
            var scene = popup.render(); var frame = SceneGeometryTestSupport.measure(scene,1);
            assertDisjoint(frame);
            var key = UiKey.item("source:"+h.second).value();
            var entry = org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(scene.measured(frame),frame,key);
            assertFalse(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(entry.label()).contains("没有歌曲"));
            var choose = entry.trigger(FloatingMenuInteraction.PRIMARY);
            popup.close(); choose.execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            assertEquals(h.first,draft.source);
            h.click("group-source");
            scene = h.popover.render(); frame = SceneGeometryTestSupport.measure(scene,1);
            entry = org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(scene.measured(frame),frame,key);
            entry.trigger(FloatingMenuInteraction.PRIMARY).execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            assertEquals(h.second,draft.source);
            h.click("create-group");
            verify(h.groups).create(h.player,Set.of(h.first,h.second),h.second,draft.name);
        }
    }

    @Test void playlistSettingsReturnFromEditingAndDeletionAndRetireOldConfirmations() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            var id = UUID.randomUUID();
            var list = new org.encinet.mik.feature.entertainment.music.playlist.Playlist(id,h.player.getUniqueId(),"Piano", "Quiet songs", "MUSIC_DISC_CAT",false,7,List.of());
            when(h.playlists.visible(h.player.getUniqueId(),id)).thenReturn(list);
            when(h.playlists.delete(h.player.getUniqueId(),id,list.version())).thenReturn(
                    CompletableFuture.failedFuture(new MusicProblem(MusicProblem.Code.CONFLICT)));
            h.workspace.browsePlaylist(h.player,id,Source.OWNED);
            h.click("playlist-settings");
            var settings = h.popover.render().title();
            var input = mock(MenuFormSession.class);
            var draft = new java.util.concurrent.atomic.AtomicReference<MenuInputForm>();
            var finished = new java.util.concurrent.atomic.AtomicReference<Runnable>();
            doAnswer(call -> {
                draft.set(call.getArgument(1)); finished.set(call.getArgument(3));
                when(input.content(any())).thenReturn(MenuInputViewTestSupport.content(draft.get(),Component.text("Empty"),Component.text("Clear")));
                return input;
            }).when(h.menus).inlineInput(eq(h.player),any(),any(),any());
            h.popupClick("edit-playlist");
            assertNotNull(entry(h.popover.render(),"confirm"));
            assertInstanceOf(NoPanel.class,h.state().panel());
            assertEquals(id,h.state().browser.collection.playlist());
            var back = MenuScene.screen("tools").content(h.popover.headerActions()).build();
            entry(back,"back-task").trigger(FloatingMenuInteraction.PRIMARY).execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            verify(input).close();
            assertEquals(settings,h.popover.render().title());
            when(h.playlists.update(h.player.getUniqueId(),id,list.version(),"New name","Draft description",list.icon(),list.published()))
                    .thenReturn(CompletableFuture.failedFuture(new MusicProblem(MusicProblem.Code.CONFLICT)));
            h.popupClick("edit-playlist");
            var submitted = draft.get();
            finished.get().run();
            submitted.onSubmit().accept(h.player,new MenuInputForm.Values(Map.of("name","New name","description","Draft description")));
            h.popupClick("edit-playlist");
            assertEquals("New name",draft.get().fields().getFirst().initial());
            assertEquals("Draft description",draft.get().fields().get(1).initial());
            back = MenuScene.screen("tools").content(h.popover.headerActions()).build();
            entry(back,"back-task").trigger(FloatingMenuInteraction.PRIMARY).execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            h.popupClick("delete");
            assertTrue(h.decision.modal());
            var oldApply = entry(h.popupScene(),"apply").trigger(FloatingMenuInteraction.PRIMARY);
            h.popupClick("cancel");
            assertEquals(settings,h.popover.render().title());
            oldApply.execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            verify(h.playlists,never()).delete(any(),any(),anyLong());
            assertEquals(settings,h.popover.render().title());
            h.popupClick("delete"); h.popupClick("apply");
            verify(h.playlists).delete(h.player.getUniqueId(),id,list.version());
            assertEquals(settings,h.popover.render().title(),"A failed deletion keeps settings and its recovery actions available");
            assertEquals(id,h.state().browser.collection.playlist());
            assertTrue(h.popover.render().entries().keySet().stream().anyMatch(key -> key.contains("task-status")));
        }
    }

    @Test void songDetailsKeepTheCollectionAndReturnThroughPlaylistDestinations() throws Exception {
        for (var locale : Language.values()) try (var h = new Harness(locale)) {
            h.workspace.browse(h.player,Collection.of(Source.CATALOG));
            var browser = h.state().browser;
            browser.query("song");
            h.state().results = List.of(MusicWorkspaceStateTest.track("song:first"),MusicWorkspaceStateTest.track("song:second"));
            h.workspace.redraw(h.player);
            var song = MusicWorkspaceStateTest.track("song:first");
            h.click(MusicTrackTable.detailSource(song).value());
            assertInstanceOf(NoPanel.class,h.state().panel());
            assertEquals("song",browser.query);
            var details = h.popover.render();
            var save = entry(details,"save-details").trigger(FloatingMenuInteraction.PRIMARY);
            for (double font : new double[]{.7,1.8}) for (int fov : new int[]{30,110}) {
                var popup = details.toBuilder().content(details.content()).build();
                var host = h.scene.toBuilder().popover(MenuPopover.attached(h.popoverSource,popup.title(),popup.content(),Component.text("Close"))
                        .withActions(h.popover.headerActions() == null ? Ui.column().build() : h.popover.headerActions())).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.popoverFrame(host,font,new FloatingMenuFieldOfView(fov)),locale.toString());
                assertNotNull(frame); assertDisjoint(frame);
                assertNotNull(element(frame,"save-details"));
            }
            save.execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            assertNotEquals(details.title(),h.popover.render().title());
            var destinations = h.popover.render().title();
            save.execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            assertEquals(destinations,h.popover.render().title(),"A previous detail action cannot navigate the current window");
            var back = MenuScene.screen("tools").content(h.popover.headerActions()).build();
            entry(back,"back-task").trigger(FloatingMenuInteraction.PRIMARY).execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            assertEquals(details.title(),h.popover.render().title());
            assertEquals("song",browser.query);
            assertInstanceOf(NoPanel.class,h.state().panel());
            h.handle.closePopover();
            save.execute(h.player,h.handle,FloatingMenuInteraction.PRIMARY);
            assertNull(h.popover);
        }
    }

    private static final class Harness implements AutoCloseable {
        final UUID first = new UUID(1,1), second = new UUID(1,2), worldId = UUID.randomUUID();
        final Player player = mock(Player.class);
        final MusicDeviceRegistry registry = mock(MusicDeviceRegistry.class);
        final MusicGroupService groups = mock(MusicGroupService.class);
        final PlaylistService playlists = mock(PlaylistService.class);
        final MusicPlaybackController playback = mock(MusicPlaybackController.class);
        final MusicWorkspace workspace;
        final MusicState.Editor data = MusicState.empty().edit();
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final FloatingMenuService menus = mock(FloatingMenuService.class);
        FloatingMenuHandle handle;
        MenuPanel popover;
        UiKey popoverSource;
        MenuPopover decision;
        MenuScene scene;

        Harness(Language locale) throws Exception {
            try {
                var plugin = mock(JavaPlugin.class);
                var scheduler = mock(BukkitScheduler.class);
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenReturn(mock(BukkitTask.class));
                var world = mock(World.class);
                when(world.getUID()).thenReturn(worldId);
                when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.isOnline()).thenReturn(true);
                when(player.getWorld()).thenReturn(world);
                when(player.getLocation()).thenReturn(new Location(world,0,64,0));
                when(player.getEyeLocation()).thenReturn(new Location(world,0,65.6,0));
                for (UUID id : List.of(first,second)) data.devices.put(id,new MusicDevice(id,worldId,"world",id.equals(first) ? 1 : 3,64,0,
                        id.equals(first) ? "Living room 客厅" : "Garden 花园",100,64,null,0));
                when(registry.snapshot()).thenAnswer(_ -> data.snapshot());
                when(registry.targets(player)).thenAnswer(_ -> data.devices.values().stream().map(device -> new DeviceTarget(device.id(),device.name(),List.of(device))).toList());
                when(registry.target(any())).thenAnswer(call -> { var device = device(call.getArgument(0)); return new DeviceTarget(device.id(),device.name(),List.of(device)); });
                when(registry.playbackTarget(any())).thenAnswer(call -> registry.target(call.getArgument(0)));
                when(registry.name(eq(player),any())).thenAnswer(call -> device(call.getArgument(1)).name());
                when(registry.alias(eq(player),any())).thenAnswer(call -> call.<MusicDevice>getArgument(1).name());
                when(registry.online(any())).thenReturn(true);
                when(registry.mayControl(eq(player),any())).thenReturn(true);
                when(registry.mayLocate(eq(player),any())).thenReturn(true);
                when(registry.location(any())).thenAnswer(call -> { var device = call.<MusicDevice>getArgument(0); return new Location(world,device.x(),device.y(),device.z()); });
                when(registry.bind(eq(player),any(),eq(""))).thenReturn(CompletableFuture.completedFuture(null));
                when(groups.create(eq(player),anySet(),any(),anyString())).thenReturn(new CompletableFuture<>());
                var queries = mock(MusicQueries.class);
                when(queries.snapshot(any())).thenAnswer(_ -> data.snapshot());
                when(playback.program(any())).thenReturn(PlaybackProgram.empty());
                when(playback.snapshot(any())).thenAnswer(call -> new MusicPlaybackSnapshot(call.getArgument(0),null,null,null,PlaybackStatus.STOPPED,0,0,false,null));
                var bundle = FluentTestSupport.bundle(locale);
                var language = mock(LanguageService.class,call -> {
                    Object[] args = call.getRawArguments();
                    if (call.getMethod().getName().equals("text")) return Component.text(FluentTestSupport.text(bundle,(Message)args[1],(Object[])args[3]));
                    if (call.getMethod().getName().equals("t")) return FluentTestSupport.text(bundle,(Message)args[1],(Object[])args[2]);
                    return RETURNS_DEFAULTS.answer(call);
                });
                when(menus.openRoot(eq(player),any())).thenAnswer(call -> {
                    scene = call.getArgument(1); handle = mock(FloatingMenuHandle.class);
                    when(handle.state()).thenReturn(FloatingMenuState.ACTIVE);
                    doAnswer(update -> { scene = update.getArgument(0); return null; }).when(handle).update(any());
                    doAnswer(open -> { if (popover != null) popover.close(); decision = null; popoverSource = open.getArgument(0); popover = open.<java.util.function.Function<Runnable,MenuPanel>>getArgument(1).apply(() -> {}); popover.attach(handle); return null; })
                            .when(handle).openPopover(any(UiKey.class),any());
                    doAnswer(open -> {
                        if (popover != null) { popover.close(); popover = null; }
                        decision = open.getArgument(0); popoverSource = decision.source(); return null;
                    }).when(handle).openPopover(any(MenuPopover.class));
                    doAnswer(_ -> {
                        if (popover != null) { popover.close(); popover = null; }
                        var previous = decision; decision = null;
                        if (previous != null) previous.dismissed().execute(player,handle);
                        return null;
                    }).when(handle).closePopover();
                    doAnswer(_ -> { when(handle.state()).thenReturn(FloatingMenuState.CLOSED); return null; }).when(handle).close();
                    return handle;
                });
                doAnswer(_ -> {
                    var input = mock(MenuQuerySession.class);
                    when(input.content(anyString())).thenAnswer(value -> Ui.text(Component.text(value.<String>getArgument(0))).key("query").build());
                    return input;
                }).when(menus).queryInput(eq(player),any(),anyInt(),any(),any());
                doAnswer(call -> { call.getArgument(0,Runnable.class).run(); return null; }).when(menus).execute(any());
                FloatingMenus.install(menus);
                workspace = new MusicWorkspace(plugin,language,queries,playlists,playback,registry,mock(MusicDeviceTravel.class),
                        groups,() -> List.of(MusicWorkspaceStateTest.track("song:first"),MusicWorkspaceStateTest.track("song:second")),null,null,null,mock(MusicRooms.class),null);
                workspace.open(player);
                when(queries.ready()).thenReturn(true);
            } catch (Exception | Error failure) { FloatingMenus.uninstall(); bukkit.close(); throw failure; }
        }
        MenuScene popupScene() {
            return decision == null ? popover.render() : MenuScene.screen("confirmation",decision.title()).content(decision.content()).build();
        }
        void popupClick(String key) {
            var popup = popupScene();
            var frame = SceneGeometryTestSupport.measure(popup,1);
            org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(popup.measured(frame),frame,key)
                    .trigger(FloatingMenuInteraction.PRIMARY).execute(player,handle,FloatingMenuInteraction.PRIMARY);
        }
        MusicDevice device(UUID id) { return Objects.requireNonNull(data.devices.get(id)); }
        MusicWorkspaceState state() { return workspace.state(player); }
        void open() { workspace.showDevices(player); }
        UiFrame frame() { return SceneGeometryTestSupport.measure(scene,1); }
        MenuScene.Entry entry(String key) { var frame = frame(); return org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(scene.measured(frame),frame,key); }
        UiFrame.Element row(UUID id, String key) {
            return frame().elements().stream().filter(item -> item.path().contains("/"+UiKey.item(id.toString()).value()+"/") && item.node().key().value().equals(key))
                    .findFirst().orElseThrow();
        }
        void click(String key) { var frame = frame(); click(element(frame,key)); }
        void click(UiFrame.Element element) {
            var frame = frame(); var entry = scene.measured(frame).entries().get(element.path());
            assertTrue(entry.enabled(),element.path());
            entry.trigger(FloatingMenuInteraction.PRIMARY).execute(player,handle,FloatingMenuInteraction.PRIMARY);
        }
        @Override public void close() { try { workspace.close(); FloatingMenus.uninstall(); } finally { bukkit.close(); } }
    }
}
