package org.encinet.mik.feature.entertainment.music.jukebox;

import org.bukkit.entity.Player;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.player.VoiceServerPlayer;

import java.util.Objects;

/** Determines whether a player's client supports the backend used by a track. */
final class JukeboxPlaybackAudience {

    private final PlasmoVoiceServer voiceServer;

    JukeboxPlaybackAudience(PlasmoVoiceServer voiceServer) {
        this.voiceServer = Objects.requireNonNull(voiceServer, "voiceServer");
    }

    boolean canHearBackend(Player player, PlaybackSession.OutputKind kind) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        if (kind != PlaybackSession.OutputKind.ENCODED_AUDIO) {
            return true;
        }
        VoiceServerPlayer voicePlayer = voiceServer.getPlayerManager()
                .getPlayerByInstance(player);
        return canHearBackend(kind, voicePlayer != null
                && voicePlayer.hasVoiceChat() && !voicePlayer.isVoiceDisabled());
    }

    static boolean canHearBackend(PlaybackSession.OutputKind kind, boolean plasmoVoiceAvailable) {
        Objects.requireNonNull(kind, "kind");
        return kind != PlaybackSession.OutputKind.ENCODED_AUDIO || plasmoVoiceAvailable;
    }
}
