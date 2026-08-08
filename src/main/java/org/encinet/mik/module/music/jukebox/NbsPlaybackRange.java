package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;

import java.util.Objects;

/** Keeps gain independent from the configured spatial attenuation range. */
final class NbsPlaybackRange {

    private static final double MIN_NATIVE_RANGE_BLOCKS = 16.0;

    private NbsPlaybackRange() {
    }

    static Location forListener(Location source, Location listener,
                                int configuredRange) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(listener, "listener");
        double scale = spatialScale(configuredRange);
        if (scale >= 1.0) return source;

        Location result = source.clone();
        result.setX(listener.getX() + (source.getX() - listener.getX()) * scale);
        result.setY(listener.getY() + (source.getY() - listener.getY()) * scale);
        result.setZ(listener.getZ() + (source.getZ() - listener.getZ()) * scale);
        return result;
    }

    static double spatialScale(int configuredRange) {
        if (configuredRange <= 0) return 1.0;
        return Math.min(1.0, MIN_NATIVE_RANGE_BLOCKS / configuredRange);
    }
}
