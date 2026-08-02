package org.encinet.mik.module.music.ui;

import org.bukkit.entity.Player;
import org.encinet.mik.module.music.catalog.MusicTrack;

public interface MusicBrowserActionHandler {
    void track(Player player, MusicTrack track, boolean alternate);
    void previousPage(Player player);
    void nextPage(Player player);
    void library(Player player);
    void search(Player player);
    void cycleSort(Player player);
    void cycleSection(Player player);
    void random(Player player, boolean alternate);
    void back(Player player);
}
