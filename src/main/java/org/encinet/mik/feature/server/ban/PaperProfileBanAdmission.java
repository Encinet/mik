package org.encinet.mik.feature.server.ban;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.connection.PaperPlayerConfigurationConnection;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import io.papermc.paper.event.player.PlayerServerFullCheckEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.server.players.NameAndId;
import org.bukkit.Server;
import org.bukkit.craftbukkit.CraftServer;

import java.util.List;

/** Keeps native admission checks and legacy login handling consistent with prison entry. */
final class PaperProfileBanAdmission {
    private PaperProfileBanAdmission() { }

    static boolean isProfileBan(Component message) {
        return message instanceof TranslatableComponent translation
                && translation.key().equals("multiplayer.disconnect.banned.reason");
    }

    static void recheck(Server server, PlayerConnectionValidateLoginEvent event, PlayerProfile profile) {
        if (profile.getName() == null || profile.getId() == null) return;
        var players = ((CraftServer) server).getHandle();
        var account = new NameAndId(profile.getId(), profile.getName());
        var whitelist = players.isWhiteListedLogin(account);
        if (!whitelist.isAllowed()) {
            event.kickMessage(PaperAdventure.asAdventure(whitelist.message()));
            return;
        }
        // Paper checks the effective client address, including connections forwarded by a proxy.
        if (players.getIpBans().isBanned(event.getConnection().getClientAddress())) return;
        var capacity = new PlayerServerFullCheckEvent(profile,
                Component.translatable("multiplayer.disconnect.server_full"),
                players.getPlayerCount() >= players.getMaxPlayers() && !players.canBypassPlayerLimit(account));
        capacity.callEvent();
        if (capacity.isAllowed()) event.allow();
        else event.kickMessage(capacity.kickMessage());
    }

    /** Paper applies a cached legacy allow after modern configuration listeners have run. */
    static void keepFinalRefusal(Server server, PlayerConfigurationConnection configuration) {
        if (!(configuration instanceof PaperPlayerConfigurationConnection paper)
                || !(server instanceof CraftServer craft)) return;
        keepFinalRefusal(craft.getServer().getConnection().getConnections(), paper);
    }

    static void keepFinalRefusal(List<Connection> connections, PaperPlayerConfigurationConnection paper) {
        synchronized (connections) {
            for (var connection : connections) {
                if (connection.legacySavedLoginEventResultOverride == null
                        || connection.legacySavedLoginEventResultOverride.isPresent()) continue;
                if (connection.getPacketListener() instanceof ServerConfigurationPacketListenerImpl listener
                        && listener.paperConnection() == paper) {
                    connection.legacySavedLoginEventResultOverride = null;
                    return;
                }
            }
        }
    }
}
