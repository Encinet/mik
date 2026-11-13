package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.feature.entertainment.music.application.MusicDeviceRegistry;
import org.encinet.mik.feature.entertainment.music.jukebox.DeviceTarget;
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
        when(registry.target(id)).thenReturn(new DeviceTarget(id,"Device",List.of(device)));
        when(registry.online(device)).thenReturn(true);
        when(registry.mayControl(player,id)).thenReturn(true);
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
        when(registry.mayControl(player,id)).thenReturn(false);
        var removed = MenuScene.screen("markers"); locator.decorate(player,state,removed);
        assertTrue(removed.build().decorations().isEmpty());
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
