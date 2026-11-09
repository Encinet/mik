package org.encinet.mik.feature.entertainment.music.ui;

import org.bukkit.Location;
import org.encinet.mik.feature.entertainment.music.jukebox.MusicDevice;

/** A rounded, viewer-relative reading hint. Computing it never accesses a block or loads a chunk. */
record MusicDeviceLocation(String world, boolean sameWorld, Direction direction, int distance, int height) {
    enum Direction { FRONT, FRONT_RIGHT, RIGHT, BACK_RIGHT, BACK, BACK_LEFT, LEFT, FRONT_LEFT, HERE, ABOVE, BELOW;
        String key() { return "direction-"+name().toLowerCase(java.util.Locale.ROOT).replace('_','-'); }
    }

    static MusicDeviceLocation from(Location viewer, MusicDevice device) {
        if (!device.world().equals(viewer.getWorld().getUID()))
            return new MusicDeviceLocation(device.worldName(),false,Direction.HERE,0,0);
        double x = device.x()+.5-viewer.getX(), z = device.z()+.5-viewer.getZ();
        double horizontal = Math.hypot(x,z), y = device.y()+.5-viewer.getY();
        int height = device.y()-viewer.getBlockY();
        Direction direction;
        if (horizontal < 1.5) direction = height >= 2 ? Direction.ABOVE : height <= -2 ? Direction.BELOW : Direction.HERE;
        else {
            double angle = Math.toDegrees(Math.atan2(-x,z))-viewer.getYaw();
            int sector = Math.floorMod((int)Math.floor((angle+22.5)/45),8);
            direction = Direction.values()[sector];
        }
        return new MusicDeviceLocation(device.worldName(),true,direction,(int)Math.ceil(Math.sqrt(horizontal*horizontal+y*y)),height);
    }
}
