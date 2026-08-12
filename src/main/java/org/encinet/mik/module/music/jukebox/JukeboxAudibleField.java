package org.encinet.mik.module.music.jukebox;

import org.bukkit.Location;

import java.util.Objects;

/** Models the inner area where a jukebox remains clearly audible. */
final class JukeboxAudibleField {

    private JukeboxAudibleField() {
    }

    static double clearRadius(JukeboxSoundSettings settings) {
        Objects.requireNonNull(settings, "settings");
        if (settings.volumePercent() <= 0) {
            return 0.0;
        }
        double volumeScale = Math.sqrt(settings.volumePercent() / 100.0);
        return settings.rangeBlocks() * 0.5 * volumeScale;
    }

    static boolean overlaps(Location firstLocation, JukeboxSoundSettings firstSettings,
                            Location secondLocation, JukeboxSoundSettings secondSettings) {
        Objects.requireNonNull(firstLocation, "firstLocation");
        Objects.requireNonNull(secondLocation, "secondLocation");
        if (firstLocation.getWorld() == null
                || !firstLocation.getWorld().equals(secondLocation.getWorld())) {
            return false;
        }
        double firstRadius = clearRadius(firstSettings);
        double secondRadius = clearRadius(secondSettings);
        if (firstRadius <= 0.0 || secondRadius <= 0.0) {
            return false;
        }
        double combinedRadius = firstRadius + secondRadius;
        return firstLocation.distanceSquared(secondLocation)
                <= combinedRadius * combinedRadius;
    }
}
