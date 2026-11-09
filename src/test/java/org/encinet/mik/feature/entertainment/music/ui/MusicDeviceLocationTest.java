package org.encinet.mik.feature.entertainment.music.ui;

import java.lang.reflect.Proxy;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MusicDeviceLocationTest {
    private final UUID worldId = UUID.randomUUID();
    private final World world = (World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},
            (_,method,_) -> {
                if (method.getName().equals("getUID")) return worldId;
                throw new AssertionError("Location hints must not query world state: "+method.getName());
            });

    private MusicDevice device(int x, int y, int z) {
        return new MusicDevice(UUID.randomUUID(),worldId,"world",x,y,z,"Room",100,64,null,0);
    }

    @Test void directionFollowsTheViewerRatherThanWorldAxesAndWrapsYaw() {
        var viewer = new Location(world,.5,64,.5);
        int[][] offsets = {{0,10},{-10,10},{-10,0},{-10,-10},{0,-10},{10,-10},{10,0},{10,10}};
        for (int i=0;i<offsets.length;i++) {
            var target = device(offsets[i][0],64,offsets[i][1]);
            assertEquals(MusicDeviceLocation.Direction.values()[i],MusicDeviceLocation.from(viewer,target).direction());
            viewer.setYaw(360);
            assertEquals(MusicDeviceLocation.Direction.values()[i],MusicDeviceLocation.from(viewer,target).direction());
            viewer.setYaw(0);
        }
        viewer.setYaw(90);
        assertEquals(MusicDeviceLocation.Direction.FRONT,MusicDeviceLocation.from(viewer,device(-10,64,0)).direction());
        assertEquals(MusicDeviceLocation.Direction.RIGHT,MusicDeviceLocation.from(viewer,device(0,64,-10)).direction());
        viewer.setYaw(-90);
        assertEquals(MusicDeviceLocation.Direction.FRONT,MusicDeviceLocation.from(viewer,device(10,64,0)).direction());
    }

    @Test void floorsAndOtherWorldsRemainExplicitWithoutReadingOrLoadingAnyBlock() {
        var viewer = new Location(world,.5,64,.5);
        var above = MusicDeviceLocation.from(viewer,device(0,70,0));
        assertEquals(MusicDeviceLocation.Direction.ABOVE,above.direction());
        assertEquals(6,above.height());
        assertEquals(7,above.distance());
        assertEquals(MusicDeviceLocation.Direction.BELOW,MusicDeviceLocation.from(viewer,device(0,60,0)).direction());
        assertEquals(MusicDeviceLocation.Direction.HERE,MusicDeviceLocation.from(viewer,device(0,64,0)).direction());
        var other = new MusicDevice(UUID.randomUUID(),UUID.randomUUID(),"nether",0,64,0,"Other",100,64,null,0);
        var hint = MusicDeviceLocation.from(viewer,other);
        assertFalse(hint.sameWorld());
        assertEquals("nether",hint.world());
    }
}
