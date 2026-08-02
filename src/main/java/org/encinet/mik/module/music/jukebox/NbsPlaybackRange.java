package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;

import java.util.Objects;

/** Keeps NBS sounds spatial while extending their native attenuation to the configured range. */
final class NbsPlaybackRange {

    private static final double MIN_NATIVE_RANGE_BLOCKS = 16.0;

    private NbsPlaybackRange() {
    }

    static Location forListener(Location source, Location listener,
                                int configuredRange, float volume) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(listener, "listener");
        double scale = spatialScale(configuredRange, volume);
        if (scale >= 1.0) return source;

        Location result = source.clone();
        result.setX(listener.getX() + (source.getX() - listener.getX()) * scale);
        result.setY(listener.getY() + (source.getY() - listener.getY()) * scale);
        result.setZ(listener.getZ() + (source.getZ() - listener.getZ()) * scale);
        return result;
    }

    static double spatialScale(int configuredRange, float volume) {
        if (configuredRange <= 0 || !Float.isFinite(volume)) return 1.0;
        double nativeRange = Math.max(MIN_NATIVE_RANGE_BLOCKS,
                Math.max(0.0F, volume) * MIN_NATIVE_RANGE_BLOCKS);
        return Math.min(1.0, nativeRange / configuredRange);
    }
}
