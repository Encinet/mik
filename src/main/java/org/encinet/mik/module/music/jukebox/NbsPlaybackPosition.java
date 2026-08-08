package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Objects;

/** Resolves NBS stereo panning relative to each listener's orientation. */
final class NbsPlaybackPosition {

    private static final double MAX_PANNING_OFFSET_BLOCKS = 2.0;

    private NbsPlaybackPosition() {
    }

    static Location forListener(Location jukebox, Location listener, int panning) {
        Objects.requireNonNull(jukebox, "jukebox");
        Objects.requireNonNull(listener, "listener");
        Location source = jukebox.clone().add(0.5, 1.0, 0.5);
        if (panning == 0) {
            return source;
        }

        Vector forward = listener.getDirection();
        double horizontalLength = Math.hypot(forward.getX(), forward.getZ());
        if (horizontalLength < 1.0e-9) {
            return source;
        }
        double offset = Math.max(-100, Math.min(100, panning)) / 100.0
                * MAX_PANNING_OFFSET_BLOCKS;
        // NBS stores 0 as right and 200 as left, so positive internal
        // panning moves toward the listener's left-hand side.
        double leftX = forward.getZ() / horizontalLength;
        double leftZ = -forward.getX() / horizontalLength;
        return source.add(leftX * offset, 0.0, leftZ * offset);
    }
}
