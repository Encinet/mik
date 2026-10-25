package org.encinet.mik.feature.entertainment.music.rhythm;

import org.bukkit.entity.Player;

/** Read-only player readiness used by rhythm-game entry points. */
@FunctionalInterface
public interface RhythmCalibrationStatus {
    boolean hasCompletedLatencyCalibration(Player player);
}
