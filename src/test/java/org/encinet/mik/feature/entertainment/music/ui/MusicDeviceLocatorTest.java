package org.encinet.mik.feature.entertainment.music.ui;

import java.util.*;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.encinet.mik.shared.i18n.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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

    @Test void focusAddsAPreciseOutlineAndBeaconWithinABoundedPrivateParticleBudget() {
        var normal = MusicDeviceLocator.outline(false);
        var focused = MusicDeviceLocator.outline(true);
        assertEquals(8,normal.size());
        assertEquals(23,focused.size());
        assertTrue(focused.containsAll(normal));
        assertEquals(focused.size(),new HashSet<>(focused).size());
        assertTrue(focused.stream().anyMatch(point -> point.y()>1.5));
        assertTrue(focused.size()*MusicDeviceLocator.MAX_MARKERS <= 96);
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
