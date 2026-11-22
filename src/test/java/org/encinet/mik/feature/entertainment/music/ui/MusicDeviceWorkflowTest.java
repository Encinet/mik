package org.encinet.mik.feature.entertainment.music.ui;

import org.encinet.mik.feature.entertainment.music.jukebox.JukeboxPlaybackSnapshot;
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
import org.encinet.mik.feature.entertainment.music.device.DeviceTarget;
import org.encinet.mik.feature.entertainment.music.device.MusicDevice;
import org.encinet.mik.feature.entertainment.music.jukebox.PlaybackStatus;
import org.encinet.mik.feature.entertainment.music.gateway.RemoteMusic;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.FloatingMenuService;
import org.encinet.mik.shared.menu.runtime.input.MenuInputViewTestSupport;
import org.encinet.mik.shared.menu.runtime.MenuWorkspaceTestSupport;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

/** Actual feature panels run inside the shared ownership and composition runtime. */
class MusicDeviceWorkflowTest {
    @Test void managedLibraryKeepsItsNativePanelsTransportAndSingleCloseAction() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.workspace.open(h.player);
            var original = SceneGeometryTestSupport.measure(h.surfaces.rootScene(),1);
            var frame = SceneGeometryTestSupport.measure(h.surfaces.composed(),1);
            assertEquals(original.panels().size(),frame.panels().size());
            assertEquals(original.surfaces().size(),frame.surfaces().size());
            assertEquals(1,frame.elements().stream().filter(node -> node.node().key().value().equals("close")).count());
            assertNotNull(element(frame,"move")); assertNotNull(element(frame,"player-primary"));
            assertEquals(original.elements().stream().filter(node -> node.node().key().value().equals("title")).count(),
                    frame.elements().stream().filter(node -> node.node().key().value().equals("title")).count());
            assertTrue(frame.elements().stream().noneMatch(node -> node.node().key().value().equals("workspace-title")));
            assertTrue(h.surfaces.windows().isEmpty());
        }
    }

    @ParameterizedTest @EnumSource(PlaybackStatus.class)
    void openingTheLibrarySelectsTheNearestPlayingDeviceBeforeIdleDevices(PlaybackStatus status) throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            when(h.playback.snapshot(h.second)).thenReturn(new MusicPlaybackSnapshot(h.second,null,null,null,new JukeboxPlaybackSnapshot(status,0),0,null, null));
            h.workspace.open(h.player);
            UUID selected = status == PlaybackStatus.PLAYING ? h.second : h.first;
            assertEquals(selected,h.state().target());
            assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(h.entry("player-target").label()).contains(h.device(selected).name()));
            assertNotNull(h.entry("player-primary"));
            assertTrue(h.surfaces.windows().isEmpty());
        }
    }
    @Test void openingTheLibraryChoosesTheClosestPlayingOutputAndResolvesItsGroup() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            UUID group = UUID.randomUUID();
            h.data.devices.put(h.first,h.device(h.first).grouped(group));
            h.data.groups.put(group,new MusicState.Group(group,"Room",List.of(h.first),Map.of(),100,0));
            when(h.registry.targets(h.player)).thenReturn(List.of(new DeviceTarget(h.second,"Garden",List.of(h.device(h.second))),
                    new DeviceTarget(group,"Room",List.of(h.device(h.first)))));
            for (UUID id : List.of(group,h.second))
                when(h.playback.snapshot(id)).thenReturn(new MusicPlaybackSnapshot(id,null,null,null,new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING,0),0,null, null));
            h.workspace.open(h.player);
            assertEquals(group,h.state().target());
            assertNotNull(h.entry("player-primary"));
        }
    }
    @Test void automaticSelectionIgnoresInaudibleOfflineAndForbiddenDevicesAndRetainsAnExplicitRemoteTarget() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.state().target(h.second);
            h.data.devices.put(h.first,h.device(h.first).settings("Muted",0,64));
            when(h.registry.online(h.device(h.second))).thenReturn(false);
            h.workspace.open(h.player);
            assertEquals(h.second,h.state().target());
            h.data.devices.put(h.first,h.device(h.first).settings("Nearby",100,64));
            when(h.registry.mayControl(h.player,h.first)).thenReturn(false);
            h.workspace.open(h.player);
            assertEquals(h.second,h.state().target());
            var world = h.player.getWorld();
            when(h.player.getLocation()).thenReturn(new Location(world,300,64,0));
            when(h.registry.online(any())).thenReturn(true);
            when(h.registry.mayControl(eq(h.player),any())).thenReturn(true);
            h.workspace.open(h.player);
            assertEquals(h.second,h.state().target());
        }
    }
    @Test void refreshingDoesNotReplaceThePlayersExplicitChoiceWithANearbyDevice() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.workspace.open(h.player);
            assertEquals(h.first,h.state().target());
            h.workspace.selectTarget(h.player,h.second);
            h.workspace.redraw(h.player);
            h.surfaces.refresh();
            assertEquals(h.second,h.state().target());
        }
    }
    @ParameterizedTest @EnumSource(value=DeviceSection.class,names={"PLAYBACK","QUEUE","SETTINGS"})
    void deviceControlsUseOnlyTheSharedWindowCloseButton(DeviceSection section) throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first);
            h.workspace.panel(h.player,new DevicePanel(h.second,section));
            var window = h.surfaces.current();
            var scene = h.surfaces.composed();
            var frame = SceneGeometryTestSupport.measure(scene,1);
            var controls = frame.elements().stream().filter(item -> item.path().contains("window-"+window.id())).toList();
            assertTrue(controls.stream().noneMatch(item -> item.node().key().value().equals("close-panel")));
            var close = controls.stream().filter(item -> item.node().key().value().equals("close")).toList();
            assertEquals(1,close.size());
            scene.measured(frame).entries().get(close.getFirst().path()).trigger(FloatingMenuInteraction.PRIMARY)
                    .execute(h.player,window,FloatingMenuInteraction.PRIMARY);
            assertEquals(FloatingMenuState.CLOSED,window.state());
            assertEquals(h.handle.id(),h.surfaces.current().id());
            assertEquals(h.first,h.state().target());
        }
    }

    @Test void devicePlaybackHasOneClearPurposeAndNeverControlsTheOtherLibraryTarget() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first);
            var snapshot = new MusicPlaybackSnapshot(h.second,null,UUID.randomUUID(),null,new JukeboxPlaybackSnapshot(PlaybackStatus.PLAYING,0),0,null, null);
            when(h.playback.snapshot(h.second)).thenReturn(snapshot);
            when(h.playback.control(eq(h.player),eq(h.second),any())).thenReturn(CompletableFuture.completedFuture(null));
            h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.PLAYBACK));
            var frame = h.frame();
            for (String key : List.of("player-primary","player-previous","player-next","use-device","section:PLAYBACK","section:QUEUE","section:SETTINGS"))
                assertNotNull(element(frame,key));
            assertEquals(UiIcons.PAUSE,assertInstanceOf(UiContent.Icon.class,element(frame,"player-primary").node().content()).value());
            for (String key : List.of("device-location","device-name","eject","forget","group-with-devices","player-queue"))
                assertTrue(frame.elements().stream().noneMatch(node -> node.node().key().value().equals(key)),key);
            h.click("player-primary");
            verify(h.playback).control(h.player,h.second,MusicPlaybackController.Action.PAUSE);
            assertEquals(h.first,h.state().target());
            h.click("section:SETTINGS");
            assertNotNull(h.entry("eject"));
            assertTrue(h.frame().elements().stream().noneMatch(node -> node.node().key().value().equals("player-primary")));
            h.click("device-playback");
            assertNotNull(h.entry("player-primary"));
        }
    }

    @Test void worldInteractionReplacesAnObsoleteLibraryTargetBeforeSubmittingSongs() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.state().target(h.first);
            doThrow(new MusicProblem(MusicProblem.Code.NOT_FOUND)).when(h.registry).playbackTarget(h.first);
            var box = mock(Jukebox.class);
            when(h.registry.discover(box)).thenReturn(CompletableFuture.completedFuture(h.device(h.second)));
            when(h.registry.bound(h.player.getUniqueId(),h.second)).thenReturn(true);
            h.workspace.at(h.player,box);
            assertEquals(h.second,h.state().target());
            h.surfaces.refresh();
            assertTrue(h.surfaces.rootScene().decorations().containsKey("mark:"+h.second));
            assertTrue(h.surfaces.rootScene().decorations().containsKey("outline:"+h.second));
            h.closeCurrent();
            assertTrue(h.surfaces.rootScene().decorations().containsKey("mark:"+h.second));
            var songs = List.of(MusicWorkspaceStateTest.track("song:first"));
            h.workspace.submit(h.player,songs,PlaybackProgram.Submission.INSERT);
            var request = org.mockito.ArgumentCaptor.forClass(MusicSubmission.class);
            verify(h.playback).submit(eq(h.player),request.capture());
            assertEquals(h.second,request.getValue().target());
            assertEquals(songs,request.getValue().tracks());
        }
    }

    @ParameterizedTest @EnumSource(value=MusicProblem.Code.class,names={"NOT_FOUND","CONFLICT"})
    void anObsoleteLibraryTargetKeepsTheSongsUntilAnotherDeviceIsChosen(MusicProblem.Code failure) throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.state().target(h.first);
            doThrow(new MusicProblem(failure)).when(h.registry).playbackTarget(h.first);
            var songs = List.of(MusicWorkspaceStateTest.track("song:first"),MusicWorkspaceStateTest.track("song:second"));
            h.workspace.submit(h.player,songs,PlaybackProgram.Submission.INSERT);
            assertNull(h.state().target());
            assertNotNull(h.entry("pending"));
            verify(h.playback,never()).submit(any(),any());
            h.click(h.row(h.second,"target"));
            var request = org.mockito.ArgumentCaptor.forClass(MusicSubmission.class);
            verify(h.playback).submit(eq(h.player),request.capture());
            assertEquals(h.second,request.getValue().target());
            assertEquals(songs,request.getValue().tracks());
            assertEquals(PlaybackProgram.Submission.INSERT,request.getValue().placement());
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void worldInteractionUsesMembershipAtOpeningRatherThanTheEarlierDiscoveryResult(boolean grouped) throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            UUID group = UUID.randomUUID();
            var before = grouped ? h.device(h.second) : h.device(h.second).grouped(group);
            if (grouped) {
                h.data.devices.put(h.second,h.device(h.second).grouped(group));
                h.data.groups.put(group,new MusicState.Group(group,"Room",List.of(h.second),Map.of(),100,0));
            }
            var box = mock(Jukebox.class);
            when(h.registry.discover(box)).thenReturn(CompletableFuture.completedFuture(before));
            when(h.registry.bound(eq(h.player.getUniqueId()),any())).thenReturn(true);
            h.workspace.at(h.player,box);
            assertEquals(grouped ? group : h.second,h.state().target());
            assertNotNull(h.entry("use-device"));
            assertTrue(h.surfaces.rootScene().decorations().containsKey("mark:"+h.second));
            assertTrue(h.surfaces.rootScene().decorations().containsKey("outline:"+h.second));
        }
    }

    @Test void deviceNamesRemainDeclaredRegardlessOfWhichWindowIsFocused() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            var box = mock(Jukebox.class);
            when(h.registry.discover(box)).thenReturn(CompletableFuture.completedFuture(h.device(h.first)));
            when(h.registry.bound(h.player.getUniqueId(),h.first)).thenReturn(true);
            h.workspace.at(h.player,box);
            var onsite = (MenuWindowHandle<?>) h.surfaces.current();
            h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.SETTINGS));
            h.workspace.redraw(h.player);
            assertTrue(h.surfaces.rootScene().decorations().containsKey("mark:"+h.first));
            assertTrue(h.surfaces.rootScene().decorations().containsKey("mark:"+h.second));
            onsite.activate();
            h.workspace.redraw(h.player);
            assertTrue(h.surfaces.rootScene().decorations().containsKey("mark:"+h.first));
            assertTrue(h.surfaces.rootScene().decorations().containsKey("mark:"+h.second));
            assertTrue(h.surfaces.rootScene().decorations().containsKey("outline:"+h.first));
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void aLatePersonalRecordCannotReplaceANewerExplicitDeviceChoice(boolean worldInteraction) throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            var firstBox = mock(Jukebox.class);
            when(h.registry.discover(firstBox)).thenReturn(CompletableFuture.completedFuture(h.device(h.first)));
            when(h.registry.mayBind(h.player,h.first)).thenReturn(true);
            var recording = new CompletableFuture<Void>();
            when(h.registry.bind(h.player,h.first,"")).thenReturn(recording);
            h.workspace.at(h.player,firstBox);
            if (worldInteraction) {
                var secondBox = mock(Jukebox.class);
                when(h.registry.discover(secondBox)).thenReturn(CompletableFuture.completedFuture(h.device(h.second)));
                when(h.registry.bound(h.player.getUniqueId(),h.second)).thenReturn(true);
                h.workspace.at(h.player,secondBox);
            } else h.workspace.selectTarget(h.player,h.second);
            var current = h.surfaces.current().id();
            recording.complete(null);
            assertEquals(h.second,h.state().target());
            assertEquals(current,h.surfaces.current().id());
        }
    }

    @Test void independentDeviceWindowsKeepTheirTargetsAndTheRootCollection() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first); h.state().browser.query("kept");
            h.workspace.panel(h.player,new DevicePanel(h.first,DeviceSection.SETTINGS));
            var first = (MenuWindowHandle<?>) h.surfaces.current();
            h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.SETTINGS));
            var second = (MenuWindowHandle<?>) h.surfaces.current();
            assertEquals(2,h.surfaces.windows().size()); assertNotEquals(first.id(),second.id());
            assertEquals(h.first,h.state().target()); assertEquals("kept",h.state().browser.query);
            h.click("eject"); verify(h.playback).eject(h.player,h.second);
            first.activate(); h.click("eject"); verify(h.playback).eject(h.player,h.first);
            h.workspace.panel(h.player,new DevicePanel(h.first,DeviceSection.SETTINGS));
            assertEquals(first.id(),h.surfaces.current().id()); assertEquals(2,h.surfaces.windows().size());
            second.close(); assertTrue(first.focused()); assertEquals(h.first,h.state().target());
        }
    }

    @Test void aliasInputIsAChildWindowAndCancellationKeepsTheOriginalDeviceState() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            when(h.registry.bound(h.player.getUniqueId(),h.second)).thenReturn(true);
            h.open(); h.state().target(h.first);
            h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.SETTINGS));
            var owner = h.surfaces.current(); h.click("toggle");
            var alias = h.frame().elements().stream().filter(item -> item.path().contains("/device-alias/")
                    && item.node().key().equals(UiKey.item("device-alias:value"))).findFirst().orElseThrow();
            h.click(alias);
            assertNotEquals(owner.id(),h.surfaces.current().id());
            assertEquals(MenuInputForm.InputMethod.CHAT,h.forms.getLast().form.fields().getFirst().inputMethod());
            verify(h.forms.getLast().input).edit("value");
            h.closeCurrent(); assertEquals(owner.id(),h.surfaces.current().id());
            assertEquals(h.first,h.state().target()); assertNotNull(h.entry("eject"));
            verify(h.forms.getLast().input).close();
        }
    }

    @Test void membershipSelectionLivesInItsWindowAndKeepsTheLibraryAndPlaybackTarget() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first); h.state().browser.query("retained");
            h.rootClick("create-group");
            var editor = h.surfaces.current();
            h.choose(h.first); h.choose(h.second);
            h.click("create-group");
            verify(h.groups).create(eq(h.player),eq(Set.of(h.first,h.second)),any(),anyString());
            assertEquals(editor.id(),h.surfaces.current().id());
            assertInstanceOf(NoPanel.class,h.state().panel());
            assertEquals("retained",h.state().browser.query); assertEquals(h.first,h.state().target());
        }
    }

    @Test void worldSelectionResumesTheSameEditorInsteadOfReopeningTheWorkspace() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.state().target(h.first); h.rootClick("create-group");
            var editor = h.surfaces.current(); var root = h.handle.id();
            h.click("pick-world-member"); assertTrue(h.surfaces.paused());
            var box = mock(Jukebox.class);
            when(h.registry.discover(box)).thenReturn(CompletableFuture.completedFuture(h.device(h.second)));
            h.workspace.at(h.player,box);
            assertFalse(h.surfaces.paused()); assertEquals(root,h.handle.id());
            assertEquals(editor.id(),h.surfaces.current().id());
            assertEquals(h.first,h.state().target());
            var selected = h.row(h.second,"select");
            assertTrue(h.scene().measured(h.frame()).entries().get(selected.path()).selected());
        }
    }

    @Test void failedWorldSelectionReleasesTheLeaseAndRetainsTheOriginalEditor() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.rootClick("create-group");
            var editor = h.surfaces.current(); h.click("pick-world-member");
            var box = mock(Jukebox.class);
            when(h.registry.discover(box)).thenReturn(CompletableFuture.failedFuture(new MusicProblem(MusicProblem.Code.NOT_FOUND)));
            h.workspace.at(h.player,box);
            assertFalse(h.surfaces.paused()); assertEquals(editor.id(),h.surfaces.current().id());
            assertEquals(FloatingMenuState.ACTIVE,editor.state());
        }
    }
    @Test void reopeningDuringWorldSelectionRestoresTheRetainedTaskAndRejectsLateSelection() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.rootClick("create-group"); var editor = h.surfaces.current();
            h.click("pick-world-member"); assertTrue(h.surfaces.paused());
            var box = mock(Jukebox.class); var pending = new CompletableFuture<MusicDevice>();
            when(h.registry.discover(box)).thenReturn(pending); h.workspace.at(h.player,box);
            h.workspace.open(h.player);
            assertFalse(h.surfaces.paused()); assertEquals(editor.id(),h.surfaces.current().id());
            pending.complete(h.device(h.second));
            assertEquals(editor.id(),h.surfaces.current().id());
            var selected = h.row(h.second,"select");
            assertFalse(h.scene().measured(h.frame()).entries().get(selected.path()).selected());
        }
    }
    @Test void playlistSettingsRetainTheirInstanceAcrossEditingAndFailedDeletion() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            var id = UUID.randomUUID();
            var list = new org.encinet.mik.feature.entertainment.music.playlist.Playlist(id,h.player.getUniqueId(),"Piano","Quiet","MUSIC_DISC_CAT",false,7,List.of());
            when(h.playlists.visible(h.player.getUniqueId(),id)).thenReturn(list);
            when(h.playlists.delete(h.player.getUniqueId(),id,list.version())).thenReturn(CompletableFuture.failedFuture(new MusicProblem(MusicProblem.Code.CONFLICT)));
            h.workspace.browsePlaylist(h.player,id,Source.OWNED); h.rootClick("playlist-settings");
            var settings = h.surfaces.current();
            h.click("edit-playlist"); assertNotNull(h.entry("confirm"));
            h.closeCurrent(); assertEquals(settings.id(),h.surfaces.current().id());
            h.click("delete"); var confirmation = h.surfaces.current(); var apply = h.entry("apply").trigger(FloatingMenuInteraction.PRIMARY);
            h.click("apply"); assertEquals(confirmation.id(),h.surfaces.current().id()); assertNotNull(h.entry("failure"));
            h.closeCurrent(); assertEquals(settings.id(),h.surfaces.current().id());
            apply.execute(h.player,confirmation,FloatingMenuInteraction.PRIMARY);
            verify(h.playlists,times(1)).delete(h.player.getUniqueId(),id,list.version());
            assertEquals(id,h.state().browser.collection.playlist());
        }
    }

    @Test void creationFailureKeepsTheNameFormAndRetryUsesTheSameFixedSongs() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.workspace.browse(h.player,Collection.of(Source.OWNED));
            var songs = List.of(MusicWorkspaceStateTest.track("song:first"));
            when(h.playlists.create(h.player.getUniqueId(),"Retry name",songs)).thenReturn(CompletableFuture.failedFuture(new MusicProblem(MusicProblem.Code.CONFLICT)));
            h.workspace.library.create(h.player,songs);
            var form = h.forms.getLast(); var owner = h.surfaces.current();
            form.form.asynchronousSubmit().submit(h.player,new MenuInputForm.Values(Map.of("name","Retry name")),new MenuInputForm.Completion() {
                public void success() { fail("failed persistence cannot close the form"); }
                public void failure(Component problem) { form.failure = problem; }
            });
            assertNotNull(form.failure); assertEquals(owner.id(),h.surfaces.current().id());
            assertEquals(FloatingMenuState.ACTIVE,owner.state());
            verify(h.playlists).create(h.player.getUniqueId(),"Retry name",songs);
        }
    }

    @Test void songDetailsAndDestinationAndNameHaveSeparateLifetimes() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.workspace.browse(h.player,Collection.of(Source.CATALOG));
            var track = MusicWorkspaceStateTest.track("song:first");
            h.rootClick(MusicTrackTable.detailSource(track).value());
            var details = h.surfaces.current(); h.click("save-details");
            var destination = h.surfaces.current(); assertNotEquals(details.id(),destination.id());
            h.click("new-playlist"); assertNotEquals(destination.id(),h.surfaces.current().id());
            h.closeCurrent(); assertEquals(destination.id(),h.surfaces.current().id());
            h.closeCurrent(); assertEquals(details.id(),h.surfaces.current().id());
            assertInstanceOf(NoPanel.class,h.state().panel());
        }
    }

    @Test void reopeningTheMinimizedMusicWorkspaceRestoresItsExistingDeviceWindows() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            h.open(); h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.SETTINGS));
            var device = h.surfaces.current(); var root = h.surfaces.root();
            root.minimize(); assertEquals(FloatingMenuState.SUSPENDED,root.state());
            h.workspace.open(h.player);
            assertEquals(root.id(),h.surfaces.root().id()); assertEquals(device.id(),h.surfaces.current().id());
            assertNotNull(h.entry("eject"));
        }
    }

    @Test void songDetailsUseAnOverviewAndSideBySideFactsWithoutLosingActionsAtReadingExtremes() throws Exception {
        for (var language : Language.values()) try (var h = new Harness(language)) {
            h.state().target(h.second);
            h.workspace.browse(h.player,Collection.of(Source.CATALOG));
            var track = MusicWorkspaceStateTest.track("song:first");
            h.rootClick(MusicTrackTable.detailSource(track).value());
            for (double font : new double[]{.7,1.8}) for (int width : new int[]{360,1200}) {
                var scene = h.scene().toBuilder().viewport(new UiSize(width,2048)).build();
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.measure(scene,font),language+" / "+font+" / "+width);
                assertDisjoint(frame);
                for (String key : List.of("song","insert","append","save-details","make-disc","output")) assertNotNull(element(frame,key));
                assertTrue(frame.elements().stream().noneMatch(node -> node.node().content() instanceof UiContent.Icon),
                        "The detail view uses content and spacing without decorative icons");
                assertFullTextAvailable(h.workspace.raw(track.details().title()),element(frame,"song"));
                assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(element(frame,"output").node().content().label()).contains(h.device(h.second).name()));
                var duration = frame.elements().stream().filter(node -> node.path().contains("/duration/") && node.node().key().value().equals("value")).findFirst().orElseThrow();
                var availability = frame.elements().stream().filter(node -> node.path().contains("/availability/") && node.node().key().value().equals("value")).findFirst().orElseThrow();
                assertEquals(duration.y(),availability.y(),1e-6,"Short technical facts share a row");
                assertTrue(duration.x() < availability.x());
                if (width == 1200) assertTrue(element(frame,"song").x() < duration.x(),"A wide detail view uses an identity column and a metadata column");
                if (width == 360 && font == 1.8) assertTrue(duration.y() > element(frame,"song").y(),"Narrow views stack complete sections");
            }
        }
    }

    @Test void largeTextAndEveryLanguageRetainDeviceControlsInAnIndependentViewport() throws Exception {
        for (var locale : Language.values()) try (var h = new Harness(locale)) {
            h.open(); h.workspace.panel(h.player,new DevicePanel(h.second,DeviceSection.SETTINGS));
            for (double font : new double[]{.7,1,1.8}) {
                var frame = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(h.scene(),font,new FloatingMenuFieldOfView(70),1),locale+" font="+font);
                assertDisjoint(frame); assertNotNull(element(frame,"eject"));
                for (int angle : new int[]{30,70,110}) {
                    String scope = "window-" + h.surfaces.current().id();
                    boolean found = false;
                    for (int pass = 0; pass < 32; pass++) {
                        h.surfaces.refresh();
                        var combined = assertDoesNotThrow(() -> SceneGeometryTestSupport.readableFrame(h.surfaces.composed(),font,
                                new FloatingMenuFieldOfView(angle),1),locale+" combined font="+font+" fov="+angle);
                        assertTrue(combined.elements().stream().noneMatch(node -> node.path().contains(scope)
                                && node.node().key().value().equals("close-panel")));
                        if (combined.elements().stream().anyMatch(node -> node.path().contains(scope) && node.node().key().value().equals("eject"))) {
                            found = true; break;
                        }
                        var next = combined.pagination().stream().filter(page -> page.path().contains(scope) && page.page().hasNext()).findFirst();
                        assertTrue(next.isPresent(),"device controls must remain reachable in their own window");
                        next.orElseThrow().action(FloatingMenuInteraction.SCROLL_DOWN).execute(h.player,h.surfaces.current(),FloatingMenuInteraction.SCROLL_DOWN);
                    }
                    assertTrue(found);
                    for (int pass = 0; pass < 32; pass++) {
                        h.surfaces.refresh();
                        var combined = SceneGeometryTestSupport.readableFrame(h.surfaces.composed(),font,new FloatingMenuFieldOfView(angle),1);
                        var previous = combined.pagination().stream().filter(page -> page.path().contains(scope) && page.page().hasPrevious()).findFirst();
                        if (previous.isEmpty()) break;
                        previous.orElseThrow().action(FloatingMenuInteraction.SCROLL_UP).execute(h.player,h.surfaces.current(),FloatingMenuInteraction.SCROLL_UP);
                    }
                }
            }
        }
    }

    @Test void nearbyMarkersStillRefreshIndependentlyOfOpenTools() throws Exception {
        try (var h = new Harness(Language.DEFAULT)) {
            when(h.registry.targets(h.player)).thenReturn(List.of());
            var edge = new MusicDevice(UUID.randomUUID(),h.worldId,"world",64,64,0,"Nearby",100,64,null,0);
            h.data.devices.put(edge.id(),edge); var refresh = h.surfaces.rootScene().refresh();
            Object outside = refresh.revision(h.player);
            var world = h.player.getWorld();
            when(h.player.getLocation()).thenReturn(new Location(world,1,64,0));
            assertNotEquals(outside,refresh.revision(h.player));
            refresh.action().execute(h.player,h.handle);
            assertTrue(h.surfaces.rootScene().decorations().containsKey("mark:"+edge.id()));
        }
    }

    private static final class Form {
        final MenuInputForm form;
        final MenuFormSession input = mock(MenuFormSession.class);
        Component failure;
        Form(MenuInputForm form) { this.form = form; }
    }
    private static final class Harness implements AutoCloseable {
        final UUID first = new UUID(1,1), second = new UUID(1,2), worldId = UUID.randomUUID();
        final Player player = mock(Player.class);
        final List<Runnable> delayed = new ArrayList<>();
        final MusicDeviceRegistry registry = mock(MusicDeviceRegistry.class);
        final MusicGroupService groups = mock(MusicGroupService.class);
        final PlaylistService playlists = mock(PlaylistService.class);
        final MusicPlaybackController playback = mock(MusicPlaybackController.class);
        final MusicWorkspace workspace;
        final MusicState.Editor data = MusicState.empty().edit();
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final FloatingMenuService menus = mock(FloatingMenuService.class);
        FloatingMenuHandle handle;
        MenuWorkspaceTestSupport surfaces;
        final List<Form> forms = new ArrayList<>();

        Harness(Language locale) throws Exception {
            try {
                var plugin = mock(JavaPlugin.class);
                when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
                var scheduler = mock(BukkitScheduler.class);
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
                when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),anyLong(),anyLong())).thenReturn(mock(BukkitTask.class));
                when(scheduler.runTaskLater(eq(plugin),any(Runnable.class),anyLong())).thenAnswer(call -> { delayed.add(call.getArgument(1)); return mock(BukkitTask.class); });
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
                when(registry.target(any())).thenAnswer(call -> {
                    UUID id = call.getArgument(0);
                    var group = data.groups.get(id);
                    if (group != null) return new DeviceTarget(id,group.name(),group.members().stream().map(data.devices::get).toList());
                    var device = data.devices.get(id);
                    if (device == null) throw new MusicProblem(MusicProblem.Code.NOT_FOUND);
                    return new DeviceTarget(device.id(),device.name(),List.of(device));
                });
                when(registry.playbackTarget(any())).thenAnswer(call -> registry.target(call.getArgument(0)));
                when(registry.name(eq(player),any())).thenAnswer(call -> registry.target(call.getArgument(1)).name());
                when(registry.alias(eq(player),any())).thenAnswer(call -> call.<MusicDevice>getArgument(1).name());
                when(registry.online(any())).thenReturn(true);
                when(registry.mayControl(eq(player),any())).thenReturn(true);
                when(registry.mayLocate(eq(player),any())).thenReturn(true);
                when(registry.location(any())).thenAnswer(call -> { var device = call.<MusicDevice>getArgument(0); return new Location(world,device.x(),device.y(),device.z()); });
                when(registry.inListeningRange(eq(player),any())).thenCallRealMethod();
                when(registry.bind(eq(player),any(),eq(""))).thenReturn(CompletableFuture.completedFuture(null));
                when(groups.create(eq(player),anySet(),any(),anyString())).thenReturn(new CompletableFuture<>());
                var queries = mock(MusicQueries.class);
                when(queries.snapshot(any())).thenAnswer(_ -> data.snapshot());
                when(playback.program(any())).thenReturn(PlaybackProgram.empty());
                when(playback.submit(eq(player),any())).thenReturn(CompletableFuture.completedFuture(null));
                when(playback.snapshot(any())).thenAnswer(call -> new MusicPlaybackSnapshot(call.getArgument(0),null,null,null,new JukeboxPlaybackSnapshot(PlaybackStatus.STOPPED,0),0,null, null));
                var bundle = FluentTestSupport.bundle(locale);
                var language = mock(LanguageService.class,call -> {
                    Object[] args = call.getRawArguments();
                    if (call.getMethod().getName().equals("text")) return Component.text(FluentTestSupport.text(bundle,(Message)args[1],(Object[])args[3]));
                    if (call.getMethod().getName().equals("t")) return FluentTestSupport.text(bundle,(Message)args[1],(Object[])args[2]);
                    return RETURNS_DEFAULTS.answer(call);
                });
                when(menus.openRoot(eq(player),any())).thenAnswer(call -> {
                    if (surfaces != null) surfaces.close();
                    surfaces = new MenuWorkspaceTestSupport(player.getUniqueId(),call.getArgument(1));
                    handle = surfaces.root(); return handle;
                });
                when(menus.current(eq(player))).thenAnswer(_ -> surfaces == null || surfaces.root().state() == FloatingMenuState.CLOSED
                        ? Optional.empty() : Optional.of(surfaces.current()));
                when(menus.inlineInput(eq(player),any(),any(),any())).thenAnswer(call -> {
                    var capture = new Form(call.getArgument(1)); forms.add(capture);
                    when(capture.input.content(any())).thenReturn(MenuInputViewTestSupport.content(capture.form,Component.text("Empty"),Component.text("Clear")));
                    return capture.input;
                });
                doAnswer(_ -> {
                    var input = mock(MenuQuerySession.class);
                    when(input.content(anyString())).thenAnswer(value -> Ui.text(Component.text(value.<String>getArgument(0))).key("query").build());
                    return input;
                }).when(menus).queryInput(eq(player),any(),anyInt(),any(),any());
                doAnswer(call -> { call.getArgument(0,Runnable.class).run(); return null; }).when(menus).execute(any());
                FloatingMenus.install(menus);
                var gateway = mock(RemoteMusic.class);
                when(gateway.catalog(anyString(), anyBoolean(), any())).thenReturn(CompletableFuture.completedFuture(
                        new RemoteMusic.CatalogPage(List.of(MusicWorkspaceStateTest.track("song:first")), com.google.protobuf.ByteString.EMPTY, false)));
                workspace = new MusicWorkspace(plugin,language,queries,playlists,playback,registry,mock(MusicDeviceTravel.class),
                        groups,gateway,null,null,mock(MusicRooms.class),null);
                workspace.open(player);
                when(queries.ready()).thenReturn(true);
            } catch (Exception | Error failure) { FloatingMenus.uninstall(); bukkit.close(); throw failure; }
        }
        MenuScene scene() { surfaces.refresh(); return surfaces.scene(); }
        MusicDevice device(UUID id) { return Objects.requireNonNull(data.devices.get(id)); }
        MusicWorkspaceState state() { return workspace.state(player); }
        void open() { workspace.showDevices(player); }
        void rootClick(String key) {
            var root = surfaces.rootScene(); var frame = SceneGeometryTestSupport.measure(root,1);
            org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(root.measured(frame),frame,key).trigger(FloatingMenuInteraction.PRIMARY).execute(player,handle,FloatingMenuInteraction.PRIMARY);
        }
        void choose(UUID id) { var row = row(id,"select"); var frame = frame();
            if (!scene().measured(frame).entries().get(row.path()).selected()) click(row);
        }
        void closeCurrent() { surfaces.current().close(); }

        UiFrame frame() { return SceneGeometryTestSupport.measure(scene(),1); }
        MenuScene.Entry entry(String key) { var frame = frame(); return org.encinet.mik.shared.menu.ui.UiSceneTestSupport.entry(scene().measured(frame),frame,key); }
        UiFrame.Element row(UUID id, String key) {
            return frame().elements().stream().filter(item -> item.path().contains("/"+UiKey.item(id.toString()).value()+"/") && item.node().key().value().equals(key))
                    .findFirst().orElseThrow();
        }
        void click(String key) { var frame = frame(); click(element(frame,key)); }
        void click(UiFrame.Element element) {
            var frame = frame(); var entry = scene().measured(frame).entries().get(element.path());
            assertTrue(entry.enabled(),element.path());
            entry.trigger(FloatingMenuInteraction.PRIMARY).execute(player,surfaces.current(),FloatingMenuInteraction.PRIMARY);
        }
        @Override public void close() { try { workspace.close(); if (surfaces != null) surfaces.close(); FloatingMenus.uninstall(); } finally { bukkit.close(); } }
    }
}
