package org.encinet.mik.feature.entertainment.music.jukebox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.encinet.mik.feature.entertainment.music.catalog.*;
import org.junit.jupiter.api.Test;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSoundEffect;

class NativeRecordPlaybackEngineTest {
    @Test void nativeOutputsStartOnTheSameTickUseOneOutputPerListenerAndReleaseTheirSoundsAndTasks() {
        PacketEventsAPI<?> previous = PacketEvents.getAPI();
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        var server = mock(ServerManager.class); var packets = mock(PlayerManager.class);
        when(api.getServerManager()).thenReturn(server); when(api.getPlayerManager()).thenReturn(packets);
        when(server.getVersion()).thenReturn(ServerVersion.V_1_21_4); PacketEvents.setAPI(api);
        try (var bukkit = mockStatic(Bukkit.class)) {
            var plugin = mock(JavaPlugin.class); var scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var ticks = new ArrayList<Runnable>(); var tasks = new ArrayList<BukkitTask>();
            when(scheduler.runTaskTimer(eq(plugin),any(Runnable.class),eq(1L),eq(1L))).thenAnswer(call -> {
                ticks.add(call.getArgument(1)); var task = mock(BukkitTask.class); tasks.add(task); return task;
            });
            var world = mock(World.class); var player = mock(Player.class); var id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id); when(player.getLocation()).thenReturn(new Location(world,1,64,0));
            when(world.getPlayers()).thenReturn(List.of(player)); bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            var track = new MusicTrack("record:minecraft:cat",new TrackDetails("Cat",null,null,"record",
                    new AudioProperties(null,null,Duration.ofSeconds(2))),new TrackTarget.NativeRecord("minecraft:music_disc.cat"));
            var time = new AtomicLong(1_000_000_000); var group = new JukeboxPlaybackGroup(time::get);
            var callbacks = mock(PlaybackCallbacks.class); when(callbacks.isValid()).thenReturn(true);
            try (var engine = new NativeRecordPlaybackEngine(plugin,_ -> true)) {
                engine.setOutputAudience((at,_) -> at.getX() == 0);
                var first = engine.create(new Location(world,0,64,0),track,JukeboxSoundSettings.defaults(),callbacks,group);
                var second = engine.create(new Location(world,40,64,0),track,JukeboxSoundSettings.defaults(),callbacks,group);
                first.prepare(); second.prepare(); first.start(); second.start();
                verifyNoInteractions(packets);
                ticks.forEach(Runnable::run);
                var sent = org.mockito.ArgumentCaptor.forClass(WrapperPlayServerSoundEffect.class);
                verify(packets,times(1)).sendPacket(eq(player),sent.capture());
                assertEquals("minecraft:music_disc.cat",sent.getValue().getSound().getSoundId().toString());
                assertEquals(64F,sent.getValue().getSound().getRange());
                assertEquals(1F,sent.getValue().getVolume());
                assertEquals(new com.github.retrooper.packetevents.util.Vector3d(.5,64.5,.5),sent.getValue().getPosition());
                time.addAndGet(1_500_000_000); ticks.forEach(Runnable::run);
                assertEquals(1500,first.positionMillis()); assertEquals(first.positionMillis(),second.positionMillis());
                time.addAndGet(1_000_000_000); ticks.forEach(Runnable::run);
                assertEquals(PlaybackStatus.FINISHED,first.status()); assertEquals(PlaybackStatus.FINISHED,second.status());
                assertFalse(first.canSeek());
                verify(player,times(1)).stopSound("minecraft:music_disc.cat",SoundCategory.RECORDS);
                tasks.forEach(task -> verify(task).cancel());
                verify(callbacks,times(2)).finished();
            }
        } finally { PacketEvents.setAPI(previous); }
    }
}
