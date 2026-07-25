package org.encinet.mik.module.music.jukebox;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.music.catalog.MusicTrack;

import java.util.Objects;

/** Finds the nearest loaded jukebox and starts a player-requested virtual track. */
public final class NearbyJukeboxPlayback {

    public static final int SEARCH_RADIUS = 50;

    private final JukeboxPlayback playback;
    private final LanguageService languageService;

    public NearbyJukeboxPlayback(JukeboxPlayback playback, LanguageService languageService) {
        this.playback = Objects.requireNonNull(playback, "playback");
        this.languageService = Objects.requireNonNull(languageService, "languageService");
    }

    public boolean play(Player player, MusicTrack track) {
        Block nearest = findNearestJukebox(player, SEARCH_RADIUS);
        if (nearest == null || !(nearest.getState() instanceof Jukebox jukebox)) {
            player.sendMessage(languageService.text(player, Message.MUSIC_NEAREST_JUKEBOX_MISSING,
                    NamedTextColor.RED, SEARCH_RADIUS));
            return false;
        }
        return playback.playVirtualTrackOnJukebox(player, jukebox, track, () -> {});
    }

    static Block findNearestJukebox(Player player, int radius) {
        Location playerLocation = player.getLocation();
        World world = playerLocation.getWorld();
        if (world == null) {
            return null;
        }

        Block nearest = null;
        int nearestDistanceSquared = Integer.MAX_VALUE;
        int radiusSquared = radius * radius;
        int minChunkX = (playerLocation.getBlockX() - radius) >> 4;
        int maxChunkX = (playerLocation.getBlockX() + radius) >> 4;
        int minChunkZ = (playerLocation.getBlockZ() - radius) >> 4;
        int maxChunkZ = (playerLocation.getBlockZ() + radius) >> 4;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                for (org.bukkit.block.BlockState state
                        : world.getChunkAt(chunkX, chunkZ).getTileEntities(false)) {
                    if (!(state instanceof Jukebox jukebox)) {
                        continue;
                    }
                    Block block = jukebox.getBlock();
                    int x = block.getX() - playerLocation.getBlockX();
                    int y = block.getY() - playerLocation.getBlockY();
                    int z = block.getZ() - playerLocation.getBlockZ();
                    int distanceSquared = x * x + y * y + z * z;
                    if (distanceSquared <= radiusSquared
                            && distanceSquared < nearestDistanceSquared) {
                        nearestDistanceSquared = distanceSquared;
                        nearest = block;
                    }
                }
            }
        }
        return nearest;
    }
}
