package org.encinet.mik.feature.player.profile;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.encinet.mik.feature.player.afk.AfkService;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.GameProfileQuery;
import org.encinet.mik.protocol.GameProfileView;

import java.time.Instant;
import java.util.UUID;

public final class GameProfileFacts {
    private final AfkService afk;

    public GameProfileFacts(AfkService afk) {
        this.afk = afk;
    }

    public GameProfileView profile(GameProfileQuery query) {
        OfflinePlayer target = switch (query.getTargetCase()) {
            case PLAYER_UUID -> Bukkit.getOfflinePlayer(UUID.fromString(query.getPlayerUuid()));
            case PLAYER_NAME -> {
                var online = Bukkit.getPlayerExact(query.getPlayerName());
                yield online != null ? online : Bukkit.getOfflinePlayerIfCached(query.getPlayerName());
            }
            case TARGET_NOT_SET -> null;
        };
        if (target == null || target.getName() == null || (!target.isOnline() && !target.hasPlayedBefore())) return null;
        var online = target.getPlayer();
        boolean concealed = online != null && (!online.isVisibleByDefault()
                || Bukkit.getOnlinePlayers().stream().anyMatch(observer -> !observer.canSee(online)));
        var presence = online == null || concealed ? GameProfileView.Presence.OFFLINE
                : afk.isAfk(target.getUniqueId()) ? GameProfileView.Presence.AFK : GameProfileView.Presence.ONLINE;
        var skin = target.getPlayerProfile().getTextures().getSkin();
        var view = GameProfileView.newBuilder()
                .setPlayer(ChatPlayer.newBuilder().setId(target.getUniqueId().toString()).setName(target.getName()))
                .setPresence(presence).setPlaySeconds(Math.max(0, target.getStatistic(Statistic.PLAY_ONE_MINUTE)) / 20L)
                .setFirstJoined(instant(target.getFirstPlayed()));
        if (!concealed) view.setLastSeen(instant(target.getLastSeen()));
        if (skin != null) view.setSkinTextureUrl(skin.toExternalForm());
        return view.build();
    }

    private static String instant(long millis) {
        return millis > 0 ? Instant.ofEpochMilli(millis).toString() : "";
    }
}
