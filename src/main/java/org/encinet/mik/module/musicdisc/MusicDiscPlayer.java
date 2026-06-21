package org.encinet.mik.module.musicdisc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player for music discs on jukeboxes
 */
public class MusicDiscPlayer {

    private static final NamespacedKey IDENTIFIER_KEY = new NamespacedKey("pv-addon-discs", "identifier");
    private static final int BROADCAST_RADIUS = 50;
    private static final int JUKEBOX_SEARCH_RADIUS = 50;

    private final MusicFileLoader musicFileLoader;
    private final MusicDiscCreator discCreator;
    private final LanguageService languageService;
    private final Map<String, MusicFileLoader.MusicFile> musicFileCache = new ConcurrentHashMap<>();

    public MusicDiscPlayer(MusicFileLoader musicFileLoader, MusicDiscCreator discCreator,
                           LanguageService languageService) {
        this.musicFileLoader = musicFileLoader;
        this.discCreator = discCreator;
        this.languageService = languageService;
    }

    /**
     * Clear the music file cache (call when reloading music files)
     */
    public void clearCache() {
        musicFileCache.clear();
    }

    /**
     * Play a music disc on the nearest jukebox
     */
    public void playDisc(Player player, ItemStack disc, String musicName) {
        Block nearestJukebox = findNearestJukebox(player, JUKEBOX_SEARCH_RADIUS);

        if (nearestJukebox == null) {
            player.sendMessage(languageService.text(player, Message.MUSIC_NEAREST_JUKEBOX_MISSING,
                    NamedTextColor.RED, JUKEBOX_SEARCH_RADIUS));
            return;
        }

        if (!(nearestJukebox.getState() instanceof Jukebox jukebox)) {
            return;
        }

        MusicFileLoader.MusicFile currentMusicFile = getMusicFileFromDisc(disc);
        if (currentMusicFile != null) {
            disc = discCreator.createMusicDisc(currentMusicFile, false, player);
        }

        playDiscOnJukebox(player, jukebox, disc, musicName);
    }

    /**
     * Play a disc directly on a specific jukebox
     */
    public void playDiscOnJukebox(Player player, Jukebox jukebox, ItemStack disc, String musicName) {
        MusicFileLoader.MusicFile currentMusicFile = getMusicFileFromDisc(disc);

        // Clear the jukebox first
        jukebox.setRecord(new ItemStack(Material.AIR));
        jukebox.update();

        // Trigger player interact event to let other plugins handle it
        PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, disc,
                jukebox.getBlock(), org.bukkit.block.BlockFace.UP
        );
        Bukkit.getPluginManager().callEvent(interactEvent);

        // If the event didn't place the disc, place it manually
        if (jukebox.getRecord().getType() == Material.AIR) {
            jukebox.setRecord(disc);
            jukebox.update();
        }

        // Broadcast the playing message
        broadcastPlayingMessage(jukebox.getLocation(), musicName, currentMusicFile);
    }

    /**
     * Get music file from disc item (with caching)
     */
    private MusicFileLoader.MusicFile getMusicFileFromDisc(ItemStack disc) {
        ItemMeta meta = disc.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String identifier = pdc.get(IDENTIFIER_KEY, PersistentDataType.STRING);

            if (identifier != null && identifier.startsWith("local://")) {
                String fileName = identifier.substring(8);

                // Check cache first
                return musicFileCache.computeIfAbsent(fileName, fn ->
                    musicFileLoader.getMusicFiles().stream()
                        .filter(m -> m.fileName().equals(fn))
                        .findFirst()
                        .orElse(null)
                );
            }
        }
        return null;
    }

    /**
     * Find the nearest jukebox within specified radius
     */
    private Block findNearestJukebox(Player player, int radius) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        int radiusSquared = radius * radius;
        Block nearestJukebox = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int distance = 0; distance <= radius; distance++) {
            if (nearestJukebox != null && distance > nearestDistance) {
                break;
            }

            for (int x = -distance; x <= distance; x++) {
                for (int y = -distance; y <= distance; y++) {
                    for (int z = -distance; z <= distance; z++) {
                        if (Math.abs(x) < distance && Math.abs(y) < distance && Math.abs(z) < distance) {
                            continue;
                        }

                        int distSquared = x * x + y * y + z * z;
                        if (distSquared > radiusSquared) {
                            continue;
                        }

                        Block block = world.getBlockAt(
                                playerLoc.getBlockX() + x,
                                playerLoc.getBlockY() + y,
                                playerLoc.getBlockZ() + z
                        );

                        if (block.getType() == Material.JUKEBOX) {
                            double actualDistance = Math.sqrt(distSquared);
                            if (actualDistance < nearestDistance) {
                                nearestDistance = actualDistance;
                                nearestJukebox = block;
                            }
                        }
                    }
                }
            }
        }

        return nearestJukebox;
    }

    /**
     * Broadcast playing message to nearby players
     */
    private void broadcastPlayingMessage(Location jukeboxLoc, String musicName,
                                        MusicFileLoader.MusicFile musicFile) {
        String tpCommand = String.format("/tp @s %d %d %d",
                jukeboxLoc.getBlockX(), jukeboxLoc.getBlockY(), jukeboxLoc.getBlockZ());

        World world = jukeboxLoc.getWorld();
        if (world != null) {
            double radiusSquared = BROADCAST_RADIUS * BROADCAST_RADIUS;
            for (Player nearbyPlayer : world.getPlayers()) {
                if (nearbyPlayer.getLocation().distanceSquared(jukeboxLoc) <= radiusSquared) {
                    Component musicNameComponent = Component.text(musicName).color(NamedTextColor.YELLOW);
                    if (musicFile != null) {
                        musicNameComponent = musicNameComponent.hoverEvent(
                                net.kyori.adventure.text.event.HoverEvent.showText(
                                        buildMusicInfoHover(nearbyPlayer, musicFile)
                                )
                        );
                    }
                    Component locationComponent = Component.text(String.format("(%d, %d, %d)",
                                    jukeboxLoc.getBlockX(), jukeboxLoc.getBlockY(), jukeboxLoc.getBlockZ()))
                            .color(NamedTextColor.AQUA)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(tpCommand))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    Component.text(languageService.t(nearbyPlayer, Message.MUSIC_JUKEBOX_TP_HOVER),
                                            NamedTextColor.GRAY)
                            ));
                    Component message = languageService.rich(nearbyPlayer, Message.MUSIC_NOW_PLAYING_RICH,
                            NamedTextColor.GREEN,
                            RichArg.component("music", musicNameComponent, musicName),
                            RichArg.component("location", locationComponent, plainTextLocation(jukeboxLoc)));
                    nearbyPlayer.sendMessage(message);
                }
            }
        }
    }

    /**
     * Build hover text for music info
     */
    private Component buildMusicInfoHover(Player player, MusicFileLoader.MusicFile musicFile) {
        List<Component> hoverLines = new ArrayList<>();
        hoverLines.add(Component.text(musicFile.displayName())
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true));
        hoverLines.add(Component.text(""));

        String extension = musicFile.fileName().substring(
                musicFile.fileName().lastIndexOf('.') + 1).toUpperCase();
        hoverLines.add(Component.text(languageService.t(player, Message.MUSIC_FORMAT, extension)).color(NamedTextColor.GRAY));

        if (musicFile.fileSize() != null) {
            hoverLines.add(Component.text(languageService.t(player, Message.MUSIC_SIZE, musicFile.fileSize()))
                    .color(NamedTextColor.GRAY));
        }
        if (musicFile.sampleRate() != null) {
            hoverLines.add(Component.text(languageService.t(player, Message.MUSIC_SAMPLE_RATE, musicFile.sampleRate()))
                    .color(NamedTextColor.GRAY));
        }
        if (musicFile.duration() != null) {
            hoverLines.add(Component.text(languageService.t(player, Message.MUSIC_DURATION, musicFile.duration()))
                    .color(NamedTextColor.GRAY));
        }

        Component hoverText = Component.empty();
        for (int i = 0; i < hoverLines.size(); i++) {
            if (i > 0) hoverText = hoverText.append(Component.newline());
            hoverText = hoverText.append(hoverLines.get(i));
        }

        return hoverText;
    }

    private String plainTextLocation(Location location) {
        return String.format("(%d, %d, %d)",
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
