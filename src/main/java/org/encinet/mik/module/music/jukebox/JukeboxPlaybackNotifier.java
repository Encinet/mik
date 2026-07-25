package org.encinet.mik.module.music.jukebox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.music.catalog.AudioProperties;
import org.encinet.mik.module.music.catalog.AudioPropertiesFormatter;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Renders player-facing jukebox playback notices and failures. */
public final class JukeboxPlaybackNotifier {

    private static final int BROADCAST_RADIUS = 50;

    private final LanguageService languageService;

    public JukeboxPlaybackNotifier(LanguageService languageService) {
        this.languageService = Objects.requireNonNull(languageService, "languageService");
    }

    void unavailableDisc(Player player) {
        player.sendMessage(languageService.text(player, Message.MUSIC_UNAVAILABLE_DISC,
                NamedTextColor.RED));
    }

    void jukeboxUnavailable(Player player) {
        player.sendMessage(languageService.text(player, Message.MUSIC_JUKEBOX_UNAVAILABLE,
                NamedTextColor.RED));
    }

    void playbackFailed(UUID requestingPlayer, MusicTrack track) {
        if (requestingPlayer == null) {
            return;
        }
        Player player = Bukkit.getPlayer(requestingPlayer);
        if (player != null && player.isOnline()) {
            player.sendMessage(languageService.text(player, Message.MUSIC_PLAYBACK_FAILED,
                    NamedTextColor.RED, track.details().title()));
        }
    }

    void broadcastStarted(Location jukeboxLocation, String musicName, MusicTrack track) {
        World world = jukeboxLocation.getWorld();
        if (world == null) {
            return;
        }
        String teleportCommand = String.format("/tp @s %d %d %d",
                jukeboxLocation.getBlockX(), jukeboxLocation.getBlockY(),
                jukeboxLocation.getBlockZ());
        double radiusSquared = BROADCAST_RADIUS * BROADCAST_RADIUS;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(jukeboxLocation) > radiusSquared) {
                continue;
            }
            Component name = Component.text(musicName, NamedTextColor.YELLOW)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            musicInfo(player, track)));
            String locationText = plainLocation(jukeboxLocation);
            Component location = Component.text(locationText, NamedTextColor.AQUA)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(teleportCommand))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                            Component.text(languageService.t(player,
                                    Message.MUSIC_JUKEBOX_TP_HOVER), NamedTextColor.GRAY)));
            player.sendMessage(languageService.rich(player, Message.MUSIC_NOW_PLAYING_RICH,
                    NamedTextColor.GREEN,
                    RichArg.component("music", name, musicName),
                    RichArg.component("location", location, locationText)));
        }
    }

    private Component musicInfo(Player player, MusicTrack track) {
        TrackDetails details = track.details();
        AudioProperties audio = details.audio();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(details.title(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true));
        lines.add(Component.empty());
        lines.add(Component.text(languageService.t(player, Message.MUSIC_FORMAT, details.format()),
                NamedTextColor.GRAY));
        add(lines, player, Message.MUSIC_ARTIST, details.artist());
        add(lines, player, Message.MUSIC_ALBUM, details.album());
        add(lines, player, Message.MUSIC_SIZE,
                AudioPropertiesFormatter.fileSize(audio.fileSizeBytes()));
        add(lines, player, Message.MUSIC_SAMPLE_RATE,
                AudioPropertiesFormatter.sampleRate(audio.sampleRateHz()));
        add(lines, player, Message.MUSIC_DURATION,
                AudioPropertiesFormatter.duration(audio.duration()));

        Component result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                result = result.append(Component.newline());
            }
            result = result.append(lines.get(index));
        }
        return result;
    }

    private void add(List<Component> lines, Player player, Message message, String value) {
        if (value != null) {
            lines.add(Component.text(languageService.t(player, message, value),
                    NamedTextColor.GRAY));
        }
    }

    private static String plainLocation(Location location) {
        return String.format("(%d, %d, %d)", location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
    }
}
