package org.encinet.mik.module.social.game;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable server state captured through {@code SocialGameService}. */
public record ServerSnapshot(
        int onlinePlayers,
        int maxPlayers,
        List<String> playerNames,
        int afkPlayers,
        double tpsOneMinute,
        double tpsFiveMinutes,
        double tpsFifteenMinutes,
        double mspt,
        Duration uptime,
        String version
) {
    public ServerSnapshot {
        onlinePlayers = Math.max(0, onlinePlayers);
        maxPlayers = Math.max(0, maxPlayers);
        playerNames = List.copyOf(Objects.requireNonNull(playerNames, "playerNames"));
        afkPlayers = Math.max(0, Math.min(onlinePlayers, afkPlayers));
        uptime = Objects.requireNonNull(uptime, "uptime");
        version = Objects.requireNonNull(version, "version");
    }

    public int activePlayers() {
        return onlinePlayers - afkPlayers;
    }
}
