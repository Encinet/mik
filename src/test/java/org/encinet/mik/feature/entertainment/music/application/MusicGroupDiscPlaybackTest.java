package org.encinet.mik.feature.entertainment.music.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.entertainment.music.catalog.*;
import org.encinet.mik.feature.entertainment.music.disc.MusicDiscResolver;
import org.encinet.mik.feature.entertainment.music.jukebox.*;
import org.encinet.mik.feature.entertainment.music.playback.*;
import org.encinet.mik.feature.entertainment.music.storage.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MusicGroupDiscPlaybackTest {
    @TempDir Path directory;
    final UUID group = UUID.randomUUID(), worldId = UUID.randomUUID();
    final Player player = mock(Player.class);
    final World world = mock(World.class);
    final MusicDeviceRegistry registry = mock(MusicDeviceRegistry.class);
    final JukeboxPlaybackService backend = mock(JukeboxPlaybackService.class);
    final MusicDiscResolver discs = mock(MusicDiscResolver.class);
    final JukeboxSettingsStore settings = mock(JukeboxSettingsStore.class);
    final List<MusicDevice> members = new ArrayList<>();
    final List<Jukebox> boxes = new ArrayList<>();
    final Map<Block,MusicTrack> playing = new HashMap<>();
    final MusicMainThread main = new MusicMainThread(Runnable::run);
    MusicStore store;
    MusicPlaybackController controller;
    JukeboxPlaybackService.CompletionListener completion;
    org.mockito.MockedStatic<Bukkit> bukkit;

    @BeforeEach void open() {
        bukkit = mockStatic(Bukkit.class); bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        var plugin = mock(JavaPlugin.class); when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(world.getUID()).thenReturn(worldId);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        for (int i=0;i<2;i++) {
            var member = new MusicDevice(UUID.randomUUID(),worldId,"world",i*40,64,0,"Speaker "+i,100,64,group,0);
            members.add(member);
            var block = mock(Block.class); var box = mock(Jukebox.class); boxes.add(box);
            var at = new Location(world,member.x(),64,0);
            var data = mock(PersistentDataContainer.class);
            when(data.get(new NamespacedKey("mik","music_device_id"),PersistentDataType.STRING)).thenReturn(member.id().toString());
            when(box.getPersistentDataContainer()).thenReturn(data);
            when(box.getLocation()).thenReturn(at); when(box.getBlock()).thenReturn(block);
            when(block.getState()).thenReturn(box); when(block.getLocation()).thenReturn(at);
            when(world.getBlockAt(at)).thenReturn(block);
            when(registry.location(member)).thenReturn(at); when(registry.block(member)).thenReturn(box);
            when(settings.readExperienceMode(box)).thenReturn(JukeboxExperienceMode.MUSIC);
        }
        when(Bukkit.getOnlinePlayers()).thenReturn(List.of());
        var target = new DeviceTarget(group,"Room",members);
        when(registry.target(group)).thenReturn(target); when(registry.playbackTarget(group)).thenReturn(target);
        doAnswer(call -> { completion = call.getArgument(0); return null; }).when(backend).setCompletionListener(any());
        when(backend.playRecordOutput(any(),any(),any(),any(),anyBoolean(),any())).thenAnswer(call -> {
            playing.put(call.<Jukebox>getArgument(1).getBlock(),call.getArgument(2)); return true;
        });
        when(backend.isPlaying(any())).thenAnswer(call -> playing.containsKey(call.getArgument(0)));
        when(backend.snapshot(any())).thenAnswer(call -> new JukeboxPlaybackSnapshot(
                playing.containsKey(call.getArgument(0)) ? PlaybackStatus.PLAYING : PlaybackStatus.STOPPED,12000));
        doAnswer(call -> { playing.remove(call.getArgument(0)); return null; }).when(backend).stop(any());
        store = new MusicStore(); store.open(directory.resolve("music.sqlite")).join();
        store.transact(editor -> {
            members.forEach(device -> editor.devices.put(device.id(),device));
            editor.groups.put(group,new MusicState.Group(group,"Room",members.stream().map(MusicDevice::id).toList(),Map.of(),100,0));
            editor.programs.put(group,PlaybackProgram.empty().submit(List.of(QueueEntry.create("queued",player.getUniqueId())),PlaybackProgram.Submission.INSERT));
            return null;
        }).join();
        controller = new MusicPlaybackController(plugin,store,backend,settings,registry,main,discs);
    }
    @AfterEach void close() {
        try { controller.close(); main.close(); store.close(); } finally { bukkit.close(); }
    }
    MusicTrack insert(int source, boolean vanilla) {
        var track = new MusicTrack(vanilla ? "record:minecraft:cat" : "custom",
                new TrackDetails("Song",null,null,"record",new AudioProperties(null,null,Duration.ofMinutes(3))),
                vanilla ? new TrackTarget.NativeRecord("minecraft:music_disc.cat") : new TrackTarget.LocalFile(Path.of("/tmp/song.ogg")));
        var item = mock(ItemStack.class);
        when(boxes.get(source).getRecord()).thenReturn(item); when(boxes.get(source).hasRecord()).thenReturn(true);
        when(discs.resolve(item)).thenReturn(track);
        assertTrue(controller.playInsertedDisc(player,boxes.get(source),PlaybackOrigin.PLAYER));
        return track;
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void eitherMemberStartsTheWholeGroupWithOnePhysicalSourceAndLeavesTheQueueIntact(boolean vanilla) {
        var before = controller.program(group);
        var track = insert(1,vanilla);
        var clock = org.mockito.ArgumentCaptor.forClass(UUID.class);
        verify(backend).playRecordOutput(eq(player),eq(boxes.get(1)),eq(track),clock.capture(),eq(true),eq(PlaybackOrigin.PLAYER));
        verify(backend).playRecordOutput(eq(player),eq(boxes.get(0)),eq(track),eq(clock.getValue()),eq(false),eq(PlaybackOrigin.AUTOMATIC));
        var snapshot = controller.snapshot(group);
        assertEquals(track,snapshot.track()); assertNull(snapshot.entry()); assertNotNull(snapshot.generation());
        assertEquals(before,controller.program(group));
        completion.ended(boxes.get(0).getLocation(),track,false);
        assertTrue(playing.isEmpty()); assertEquals(before,controller.program(group));
    }
    @Test void takingTheDiscOutThroughAnEmptyMemberStopsEveryOutput() {
        insert(1,false);
        assertFalse(boxes.getFirst().hasRecord());
        assertTrue(controller.ejectInsertedDisc(player,boxes.getFirst()));
        assertTrue(playing.isEmpty());
        verify(backend).stopAndEject(boxes.get(1).getBlock());
        assertFalse(boxes.getFirst().hasRecord());
    }
    @Test void stoppingAPhysicalRecordPreservesThePreviousDigitalCheckpoint() {
        var current = controller.program(group).current();
        var saved = new MusicState.Checkpoint(current.id(),40000,true,1);
        store.transact(editor -> { editor.checkpoints.put(group,saved); return null; }).join();
        insert(0,true);
        controller.control(player,group,MusicPlaybackController.Action.STOP).join();
        assertEquals(saved,store.snapshot().checkpoints().get(group));
    }
    @Test void deniedGroupAuthorizationDoesNotStopTheCurrentPlaybackOrAcceptTheInsertedItem() {
        insert(0,false); var before = new HashMap<>(playing);
        doThrow(new MusicProblem(MusicProblem.Code.FORBIDDEN)).when(registry).authorize(eq(player),any());
        assertThrows(MusicProblem.class,() -> controller.playInsertedDisc(player,boxes.get(1),PlaybackOrigin.PLAYER));
        assertEquals(before,playing);
    }
    @Test void removingTheSourceRecordStopsFollowersAndDoesNotAdvanceTheSavedProgram() {
        insert(0,false); var before = controller.program(group);
        when(boxes.getFirst().hasRecord()).thenReturn(false);
        controller.recordRemoved(boxes.getFirst());
        assertTrue(playing.isEmpty()); assertEquals(before,controller.program(group));
    }
    @Test void removingTheMemberHoldingTheRecordStopsTheGroupBeforeItsSourceBecomesIndependent() {
        insert(0,false);
        controller.departed(members.getFirst());
        assertTrue(playing.isEmpty());
        controller.loaded(boxes.get(1));
        assertTrue(playing.isEmpty());
    }
    @Test void aReloadedVanillaMemberRestartsTheGroupTogetherInsteadOfJoiningAtTheWrongPosition() {
        var track = insert(0,true); var previous = controller.snapshot(group).generation();
        controller.unavailable(boxes.get(1).getLocation(),false);
        controller.loaded(boxes.get(1));
        var next = controller.snapshot(group);
        assertEquals(track,next.track()); assertNotEquals(previous,next.generation());
        assertEquals(2,playing.size());
    }
}
