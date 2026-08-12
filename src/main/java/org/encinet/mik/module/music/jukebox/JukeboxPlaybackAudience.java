package org.encinet.mik.module.music.jukebox;

import org.bukkit.entity.Player;
import org.encinet.mik.module.music.catalog.MusicTrack;
import org.encinet.mik.module.music.catalog.TrackTarget;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.player.VoiceServerPlayer;

import java.util.Objects;

/** Determines whether a player's client supports the backend used by a track. */
final class JukeboxPlaybackAudience {

    private final PlasmoVoiceServer voiceServer;

    JukeboxPlaybackAudience(PlasmoVoiceServer voiceServer) {
        this.voiceServer = Objects.requireNonNull(voiceServer, "voiceServer");
    }

    boolean canHearBackend(Player player, MusicTrack track) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(track, "track");
        if (track.target() instanceof TrackTarget.NbsFile) {
            return true;
        }
        VoiceServerPlayer voicePlayer = voiceServer.getPlayerManager()
                .getPlayerByInstance(player);
        return canHearBackend(track.target(), voicePlayer != null
                && voicePlayer.hasVoiceChat() && !voicePlayer.isVoiceDisabled());
    }

    static boolean canHearBackend(TrackTarget target, boolean plasmoVoiceAvailable) {
        Objects.requireNonNull(target, "target");
        return target instanceof TrackTarget.NbsFile || plasmoVoiceAvailable;
    }
}
