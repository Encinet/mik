package org.encinet.mik.module.music.ui;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.encinet.mik.module.menu.FloatingMenuInteraction;
import org.encinet.mik.module.music.catalog.MusicTrack;

/** Semantic actions exposed by the jukebox scene; no inventory-slot protocol leaks out. */
public interface JukeboxControlActionHandler {
    void selectMusic(Player player, Location jukebox);

    void openPage(Player player, Location jukebox, int page);

    void queueTrack(Player player, Location jukebox, MusicTrack track,
                    FloatingMenuInteraction interaction);

    void stopAndEject(Player player, Location jukebox);

    void adjustVolume(Player player, Location jukebox,
                      FloatingMenuInteraction interaction);

    void adjustRange(Player player, Location jukebox,
                     FloatingMenuInteraction interaction);

    void cycleMode(Player player, Location jukebox);

    void cycleExperienceMode(Player player, Location jukebox);

    void playNext(Player player, Location jukebox);

    void clearQueue(Player player, Location jukebox);

    void openRhythmGame(Player player, Location jukebox);

    void openLatencyCalibration(Player player, Location jukebox);

    void resetLatencyCalibration(Player player, Location jukebox);

    void close(Player player);
}
