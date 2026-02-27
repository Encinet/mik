package org.encinet.mik.module.musicdisc;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Jukebox;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manager for jukebox playlists and playback modes
 */
public class JukeboxPlaylistManager {

    private static final NamespacedKey IDENTIFIER_KEY = new NamespacedKey("pv-addon-discs", "identifier");

    private final Map<Location, JukeboxData> jukeboxDataMap = new ConcurrentHashMap<>();
    private final MusicFileLoader musicFileLoader;

    public JukeboxPlaylistManager(MusicFileLoader musicFileLoader) {
        this.musicFileLoader = musicFileLoader;
    }

    /**
     * Get or create jukebox data
     */
    public JukeboxData getJukeboxData(Location location) {
        return jukeboxDataMap.computeIfAbsent(location, k -> new JukeboxData());
    }

    /**
     * Get current disc in jukebox
     */
    public MusicFileLoader.MusicFile getCurrentDisc(Jukebox jukebox) {
        if (jukebox.getRecord().getType().toString().startsWith("MUSIC_DISC_")) {
            var meta = jukebox.getRecord().getItemMeta();
            if (meta != null) {
                var pdc = meta.getPersistentDataContainer();
                String identifier = pdc.get(IDENTIFIER_KEY, PersistentDataType.STRING);

                if (identifier != null && identifier.startsWith("local://")) {
                    String fileName = identifier.substring(8);
                    return musicFileLoader.getMusicFiles().stream()
                            .filter(m -> m.fileName().equals(fileName))
                            .findFirst()
                            .orElse(null);
                }
            }
        }
        return null;
    }

    /**
     * Play next track in playlist or random from all songs
     */
    public MusicFileLoader.MusicFile getNextTrack(Location location) {
        JukeboxData data = getJukeboxData(location);

        if (data.isRandomMode) {
            List<MusicFileLoader.MusicFile> allSongs = musicFileLoader.getMusicFiles();
            if (allSongs.isEmpty()) {
                return null;
            }
            return allSongs.get(ThreadLocalRandom.current().nextInt(allSongs.size()));
        } else {
            if (data.playlist.isEmpty()) {
                return null;
            }
            return data.playlist.getFirst();
        }
    }

    /**
     * Jukebox data container
     */
    public static class JukeboxData {
        public List<MusicFileLoader.MusicFile> playlist = new ArrayList<>();
        public boolean isRandomMode = false;
        public int currentIndex = 0;
        public boolean autoPlay = false;

        public void addToPlaylist(MusicFileLoader.MusicFile music) {
            if (!playlist.contains(music)) {
                playlist.add(music);
            }
        }

        public void removeFromPlaylist(MusicFileLoader.MusicFile music) {
            playlist.remove(music);
        }

        public void moveInPlaylist(int fromIndex, int toIndex) {
            if (fromIndex < 0 || fromIndex >= playlist.size() || toIndex < 0 || toIndex >= playlist.size()) {
                return;
            }
            MusicFileLoader.MusicFile music = playlist.remove(fromIndex);
            playlist.add(toIndex, music);
        }

        public void clearPlaylist() {
            playlist.clear();
            currentIndex = 0;
        }

        public void toggleRandomMode() {
            isRandomMode = !isRandomMode;
        }

        public void toggleAutoPlay() {
            autoPlay = !autoPlay;
        }
    }
}
