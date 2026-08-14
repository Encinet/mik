package org.encinet.mik.module.social.game;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable server state captured through {@code SocialGameService}. */
public record ServerSnapshot(
        int onlinePlayers,
        int maxPlayers,
        List<PlayerSummary> players,
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
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        afkPlayers = Math.clamp(afkPlayers, 0, onlinePlayers);
        uptime = Objects.requireNonNull(uptime, "uptime");
        version = Objects.requireNonNull(version, "version");
    }

    public int activePlayers() {
        return onlinePlayers - afkPlayers;
    }

    /** One online player captured together with presence used by list commands. */
    public record PlayerSummary(String name, boolean afk) {
        public PlayerSummary {
            name = Objects.requireNonNull(name, "name");
        }
    }
}
