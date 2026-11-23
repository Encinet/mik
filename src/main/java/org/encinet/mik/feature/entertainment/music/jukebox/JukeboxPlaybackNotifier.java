package org.encinet.mik.feature.entertainment.music.jukebox;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.i18n.RichArg;
import org.encinet.mik.feature.entertainment.music.catalog.MusicTrack;
import org.encinet.mik.feature.entertainment.music.catalog.TrackDetails;
import su.plo.voice.api.server.PlasmoVoiceServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Renders player-facing jukebox playback notices and failures. */
public final class JukeboxPlaybackNotifier {

    private final LanguageService languageService;
    private final JukeboxPlaybackAudience audience;

    public JukeboxPlaybackNotifier(LanguageService languageService,
                                   PlasmoVoiceServer voiceServer) {
        this.languageService = Objects.requireNonNull(languageService, "languageService");
        this.audience = new JukeboxPlaybackAudience(voiceServer);
    }

    void unavailableDisc(Player player) {
        player.sendMessage(languageService.text(player, Message.MUSIC_UNAVAILABLE_DISC,
                NamedTextColor.RED));
    }

    void overlappingPlayback(Player player) {
        player.sendMessage(languageService.text(player,
                Message.MUSIC_JUKEBOX_AUDIBLE_FIELD_CONFLICT, NamedTextColor.RED));
    }

    void requestRateLimited(Player player, long retryAfterSeconds) {
        player.sendMessage(languageService.text(player, Message.MUSIC_REQUEST_RATE_LIMITED,
                NamedTextColor.YELLOW, retryAfterSeconds));
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

    void playbackInaudible(UUID requestingPlayer, PlaybackSession.OutputKind kind) {
        if (requestingPlayer == null) {
            return;
        }
        Player player = Bukkit.getPlayer(requestingPlayer);
        if (player != null && player.isOnline() && !audience.canHearBackend(player, kind)) {
            player.sendMessage(languageService.text(player,
                    Message.MUSIC_PLASMO_VOICE_UNAVAILABLE, NamedTextColor.YELLOW));
        }
    }

    void broadcastStarted(Location jukeboxLocation, String musicName, MusicTrack track, PlaybackSession.OutputKind kind,
                          int rangeBlocks) {
        World world = jukeboxLocation.getWorld();
        if (world == null) {
            return;
        }
        String teleportCommand = String.format("/tp @s %d %d %d",
                jukeboxLocation.getBlockX(), jukeboxLocation.getBlockY(),
                jukeboxLocation.getBlockZ());
        double radiusSquared = (double) rangeBlocks * rangeBlocks;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(jukeboxLocation) > radiusSquared) {
                continue;
            }
            if (!audience.canHearBackend(player, kind)) {
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
                                    Message.MUSIC_JUKEBOX_TP_HOVER), NamedTextColor.WHITE)));
            player.sendMessage(languageService.rich(player, Message.MUSIC_NOW_PLAYING_RICH,
                    NamedTextColor.GREEN,
                    RichArg.component("music", name),
                    RichArg.component("location", location)));
        }
    }

    private Component musicInfo(Player player, MusicTrack track) {
        TrackDetails details = track.details();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(details.title(), NamedTextColor.YELLOW)
                .decoration(TextDecoration.BOLD, true));
        lines.add(Component.empty());
        add(lines, player, Message.MUSIC_ARTIST, details.artist());
        add(lines, player, Message.MUSIC_ORIGINAL_AUTHOR, details.originalAuthor());
        add(lines, player, Message.MUSIC_ALBUM, details.album());

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
                    NamedTextColor.WHITE));
        }
    }

    private static String plainLocation(Location location) {
        return String.format("(%d, %d, %d)", location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
    }
}
