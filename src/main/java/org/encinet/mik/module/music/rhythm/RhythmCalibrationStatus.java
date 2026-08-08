package org.encinet.mik.module.music.rhythm;

import org.bukkit.entity.Player;

/** Read-only player readiness used by rhythm-game entry points. */
@FunctionalInterface
public interface RhythmCalibrationStatus {
    boolean hasCompletedLatencyCalibration(Player player);
}
