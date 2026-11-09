package org.encinet.mik.feature.entertainment.music.ui;

import static org.encinet.mik.feature.entertainment.music.ui.MusicWorkspaceState.*;
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
import org.encinet.mik.feature.entertainment.music.jukebox.*;
import org.encinet.mik.feature.entertainment.music.playback.PlaybackProgram;
import org.encinet.mik.feature.entertainment.music.playback.QueueEntry;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.runtime.FloatingMenuService;
import org.encinet.mik.shared.menu.runtime.SceneGeometryTestSupport;
import org.encinet.mik.shared.menu.ui.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Exercise the actual host: an auxiliary task used to hide every member selection control. */
class MusicDeviceWorkflowTest {
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

    private static final class Harness implements AutoCloseable {
        final UUID first = new UUID(1,1), second = new UUID(1,2), worldId = UUID.randomUUID();
        final Player player = mock(Player.class);
        final MusicDeviceRegistry registry = mock(MusicDeviceRegistry.class);
        final MusicGroupService groups = mock(MusicGroupService.class);
        final MusicPlaybackController playback = mock(MusicPlaybackController.class);
        final MusicWorkspace workspace;
        final MusicState.Editor data = MusicState.empty().edit();
        final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        final FloatingMenuService menus = mock(FloatingMenuService.class);
        FloatingMenuHandle handle;
        MenuPanel popover;
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
                    doAnswer(open -> { popover = open.<java.util.function.Function<Runnable,MenuPanel>>getArgument(1).apply(() -> {}); return null; })
                            .when(handle).openPopover(any(UiKey.class),any());
                    doAnswer(_ -> { if (popover != null) popover.close(); return null; }).when(handle).closePopover();
                    doAnswer(_ -> { when(handle.state()).thenReturn(FloatingMenuState.CLOSED); return null; }).when(handle).close();
                    return handle;
                });
                doAnswer(call -> { call.getArgument(0,Runnable.class).run(); return null; }).when(menus).execute(any());
                FloatingMenus.install(menus);
                workspace = new MusicWorkspace(plugin,language,queries,mock(PlaylistService.class),playback,registry,mock(MusicDeviceTravel.class),
                        groups,List::of,null,null,null,mock(MusicRooms.class),null);
                workspace.open(player);
                when(queries.ready()).thenReturn(true);
            } catch (Exception | Error failure) { FloatingMenus.uninstall(); bukkit.close(); throw failure; }
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
