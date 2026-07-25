package org.encinet.mik.module.music.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.MusicTrackPool;
import org.encinet.mik.module.music.catalog.MusicTrackSelector;
import org.encinet.mik.module.music.disc.MusicDiscFactory;
import org.encinet.mik.module.music.jukebox.NearbyJukeboxPlayback;

/** Player-facing random track operations shared by commands and GUI actions. */
public final class RandomMusicActions {

    private final MusicTrackPool trackPool;
    private final MusicTrackSelector trackSelector;
    private final MusicDiscFactory discFactory;
    private final NearbyJukeboxPlayback nearbyPlayback;
    private final LanguageService languageService;

    public RandomMusicActions(MusicTrackPool trackPool, MusicTrackSelector trackSelector,
                              MusicDiscFactory discFactory, NearbyJukeboxPlayback nearbyPlayback,
                              LanguageService languageService) {
        this.trackPool = trackPool;
        this.trackSelector = trackSelector;
        this.discFactory = discFactory;
        this.nearbyPlayback = nearbyPlayback;
        this.languageService = languageService;
    }

    public void giveRandomDisc(Player player) {
        MusicTrack track = trackSelector.select(trackPool.tracks());
        if (track == null) {
            player.sendMessage(languageService.text(player, Message.MUSIC_NO_FILES, NamedTextColor.RED));
            return;
        }

        ItemStack disc = discFactory.createPersistentDisc(track, player);
        if (player.getInventory().addItem(disc).isEmpty()) {
            player.sendMessage(musicMessage(player, Message.MUSIC_RANDOM_DISC_GOT_RICH,
                    NamedTextColor.GREEN, track, NamedTextColor.YELLOW));
        } else {
            player.sendMessage(languageService.text(player, Message.MUSIC_INVENTORY_FULL,
                    NamedTextColor.RED));
        }
    }

    public void playRandomDisc(Player player) {
        MusicTrack track = trackSelector.select(trackPool.tracks());
        if (track == null) {
            player.sendMessage(languageService.text(player, Message.MUSIC_NO_FILES, NamedTextColor.RED));
            return;
        }

        nearbyPlayback.play(player, track);
    }

    private Component musicMessage(Player player, Message message, NamedTextColor baseColor,
                                   MusicTrack track, NamedTextColor trackColor) {
        return languageService.rich(player, message, baseColor,
                RichArg.component("music", Component.text(track.details().title(), trackColor),
                        track.details().title()));
    }
}
