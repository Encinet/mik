package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.feature.entertainment.music.application.MusicDeviceRegistry;
import org.encinet.mik.feature.entertainment.music.storage.MusicState;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.UiTextMetrics;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MusicDeviceLocatorTest {
    @Test void deviceCodesAndColorsSurviveDistanceSortingRenamingAndRegrouping() {
        UUID id = UUID.randomUUID(), world = UUID.randomUUID();
        var ids = java.util.stream.IntStream.range(0,16).mapToObj(index -> new UUID(0,index+1)).toList();
        var firstColors = new HashSet<Integer>();
        var codes = new HashSet<String>();
        for (int i=15;i>=0;i--) {
            var device = new MusicDevice(ids.get(i),world,"world",i,64,0,"Device",100,64,id,0);
            var marker = MusicDeviceLocator.marker(device,false);
            assertTrue(codes.add(marker.code()));
            if (i<8) assertTrue(firstColors.add(marker.color()));
            var renamed = device.settings("A renamed device",70,32);
            assertEquals(marker.color(),MusicDeviceLocator.marker(renamed,true).color());
            assertEquals(marker.code(),MusicDeviceLocator.marker(renamed.grouped(UUID.randomUUID()),true).code());
            assertEquals(marker.color(),MusicDeviceLocator.marker(renamed.grouped(null),true).color());
            assertTrue(MusicDeviceLocator.marker(renamed,true).focused());
        }
    }

    @Test void movingTheViewerDoesNotReorientOrRecreateTheSingleLineMarkerAndMatchingOutline() {
        var world = mock(World.class);
        var worldId = UUID.randomUUID(); when(world.getUID()).thenReturn(worldId);
        var player = mock(Player.class); when(player.getWorld()).thenReturn(world);
        var registry = mock(MusicDeviceRegistry.class);
        var id = UUID.randomUUID();
        var device = new MusicDevice(id,worldId,"world",2,64,3,"Device",100,64,null,0);
        var data = MusicState.empty().edit(); data.devices.put(id,device);
        when(registry.snapshot()).thenAnswer(_ -> data.snapshot());
        when(registry.mayLocate(player,device)).thenReturn(true);
        when(registry.alias(player,device)).thenReturn("很长的个人别名 Long name\n".repeat(20));
        when(registry.location(device)).thenAnswer(_ -> new Location(world,2,64,3));
        var state = new MusicWorkspaceState(); state.target(id);
        var locator = new MusicDeviceLocator(registry,mock(LanguageService.class));
        Map<String,FloatingMenuDecoration> previous = null;
        for (var at : List.of(new Location(world,0,64,0,0,0),new Location(world,-3,65,1,110,30))) {
            when(player.getLocation()).thenReturn(at);
            var builder = MenuScene.screen("markers"); locator.decorate(player,state,builder);
            var markers = builder.build().decorations();
            assertEquals(2,markers.size());
            var label = assertInstanceOf(FloatingMenuDecoration.Text.class,markers.get("mark:"+id).content());
            assertTrue(label.billboard()); assertTrue(label.seeThrough());
            assertEquals(1,UiTextMetrics.measure(label.text(),192).lines());
            assertTrue(UiTextMetrics.measure(label.text(),192).widthPixels() <= 192);
            var outline = markers.get("outline:"+id);
            var cube = assertInstanceOf(FloatingMenuDecoration.BlockOutline.class,outline.content());
            assertEquals(Material.JUKEBOX,cube.material());
            assertEquals(MusicDeviceLocator.marker(device,true).color(),cube.color());
            assertTrue(cube.size() < 1);
            var center = assertInstanceOf(FloatingMenuPlacement.World.class,outline.placement()).location();
            assertEquals(new Location(world,2.5,64.5,3.5),center);
            if (previous != null) assertEquals(previous,markers,"Client billboards must not require server yaw updates");
            previous = markers;
        }
        when(registry.mayLocate(player,device)).thenReturn(false);
        var removed = MenuScene.screen("markers"); locator.decorate(player,state,removed);
        assertTrue(removed.build().decorations().isEmpty());
    }

    @Test void marksAllNearbyDevicesAndOtherGroupsWithoutChoosingAPlaybackTarget() {
        var h = new Harness();
        var group = UUID.randomUUID();
        for (int i=0;i<16;i++) {
            var device = h.add(i*3,64,0);
            if (i>=8) h.data.devices.put(device.id(),device.grouped(group));
        }
        assertNull(h.state.target());
        h.data.groups.put(group,new MusicState.Group(group,"Garden speakers",List.of(),Map.of(),100,0));
        when(h.registry.name(h.player,group)).thenReturn("Garden speakers");
        assertEquals(16,h.locator.visible(h.player,h.state).size());
        var builder = MenuScene.screen("nearby"); h.locator.decorate(h.player,h.state,builder);
        var decorations = builder.build().decorations();
        assertEquals(32,decorations.size());
        for (UUID id : h.data.devices.keySet()) {
            assertTrue(decorations.containsKey("mark:"+id));
            assertTrue(decorations.containsKey("outline:"+id));
            if (h.data.devices.get(id).group() != null) {
                var label = assertInstanceOf(FloatingMenuDecoration.Text.class,decorations.get("mark:"+id).content());
                assertTrue(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(label.text()).contains("Garden speakers"));
            }
        }
    }

    @Test void rangeUsesTheBlockCenterAndRefreshesAcrossItsBoundaryWithoutCheckingDistantBlocks() {
        var h = new Harness();
        var edge = h.add(64,64,0);
        var outside = h.add(65,64,0);
        var above = h.add(0,129,0);
        var otherWorld = new MusicDevice(UUID.randomUUID(),UUID.randomUUID(),"other",0,64,0,"Other",100,64,null,0);
        h.data.devices.put(otherWorld.id(),otherWorld);
        assertEquals(List.of(edge),h.locator.visible(h.player,h.state).stream().map(MusicDeviceLocator.Marker::device).toList());
        verify(h.registry,never()).mayLocate(h.player,outside);
        verify(h.registry,never()).mayLocate(h.player,above);
        verify(h.registry,never()).mayLocate(h.player,otherWorld);
        when(h.player.getLocation()).thenReturn(new Location(h.world,.49,64.5,.5));
        assertTrue(h.locator.visible(h.player,h.state).isEmpty());
        when(h.player.getLocation()).thenReturn(new Location(h.world,1.5,64.5,.5));
        assertEquals(List.of(edge,outside),h.locator.visible(h.player,h.state).stream().map(MusicDeviceLocator.Marker::device).toList());
    }

    @Test void denseAreasKeepTheManagedMemberAndDraftSelectionsBeforeTheNearestDevices() {
        var h = new Harness();
        for (int i=0;i<70;i++) h.add(i%10,64,i/10);
        var focus = h.add(50,64,0);
        assertEquals(64,h.locator.visible(h.player,h.state).size());
        assertFalse(h.locator.visible(h.player,h.state).stream().anyMatch(marker -> marker.device().equals(focus)));
        h.state.panel(new MusicWorkspaceState.MemberOutput(UUID.randomUUID(),focus.id(),MusicSourcePosition.START));
        var markers = h.locator.visible(h.player,h.state);
        assertEquals(64,markers.size());
        assertEquals(focus,markers.getFirst().device()); assertTrue(markers.getFirst().focused());
        var draft = new MusicWorkspaceState.GroupDraft(null,null); draft.members.add(focus.id());
        h.state.panel(new MusicWorkspaceState.GroupEditor(draft));
        assertEquals(focus,h.locator.visible(h.player,h.state).getFirst().device());
        when(h.registry.mayLocate(h.player,focus)).thenReturn(false);
        assertEquals(64,h.locator.visible(h.player,h.state).size());
        assertFalse(h.locator.visible(h.player,h.state).stream().anyMatch(marker -> marker.device().equals(focus)));
    }

    private static final class Harness {
        final UUID worldId = UUID.randomUUID();
        final World world = mock(World.class);
        final Player player = mock(Player.class);
        final MusicDeviceRegistry registry = mock(MusicDeviceRegistry.class);
        final MusicState.Editor data = MusicState.empty().edit();
        final MusicWorkspaceState state = new MusicWorkspaceState();
        final MusicDeviceLocator locator = new MusicDeviceLocator(registry,mock(LanguageService.class));
        Harness() {
            when(world.getUID()).thenReturn(worldId);
            when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenReturn(new Location(world,.5,64.5,.5));
            when(registry.snapshot()).thenAnswer(_ -> data.snapshot());
            when(registry.mayLocate(eq(player),any())).thenReturn(true);
            when(registry.alias(eq(player),any())).thenAnswer(call -> call.<MusicDevice>getArgument(1).name());
            when(registry.location(any())).thenAnswer(call -> {
                var device = call.<MusicDevice>getArgument(0);
                return new Location(world,device.x(),device.y(),device.z());
            });
        }
        MusicDevice add(int x, int y, int z) {
            var device = new MusicDevice(UUID.randomUUID(),worldId,"world",x,y,z,"Device",100,64,null,0);
            data.devices.put(device.id(),device);
            return device;
        }
    }

    @Test void locationAndElevationMessagesFormatRealValuesInEveryLanguage() throws Exception {
        for (var language : Language.values()) {
            var bundle = FluentTestSupport.bundle(language);
            for (var direction : MusicDeviceLocation.Direction.values()) {
                String label = FluentTestSupport.text(bundle,Message.MUSIC_SPACE_LABEL,direction.key());
                assertFalse(label.isBlank());
                String value = FluentTestSupport.text(bundle,Message.MUSIC_SPACE_DEVICE_DISTANCE,label,12);
                assertTrue(value.contains("12"));
                assertFalse(value.contains("arg"));
            }
            for (String height : List.of("above","below"))
                assertTrue(FluentTestSupport.text(bundle,Message.MUSIC_SPACE_DEVICE_HEIGHT,height,3).contains("3"));
            String coordinates = FluentTestSupport.text(bundle,Message.MUSIC_SPACE_DEVICE_COORDINATES,"world",-12,64,120);
            assertTrue(coordinates.contains("world"));
            assertTrue(coordinates.contains("64"));
        }
    }
}
