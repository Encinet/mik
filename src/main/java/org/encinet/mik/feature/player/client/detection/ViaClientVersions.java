package org.encinet.mik.feature.player.client.detection;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.player.client.ClientVersions;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Loaded only when ViaVersion is available; consumers depend on ClientVersions. */
public final class ViaClientVersions implements ClientVersions {
    private final String serverVersion;
    private final ProtocolVersion serverProtocol;
    private final Function<UUID, ProtocolVersion> clientProtocol;

    public ViaClientVersions(JavaPlugin plugin) {
        this(Bukkit.getMinecraftVersion(), resolveServerProtocol(Bukkit.getMinecraftVersion()),
                id -> Via.isLoaded() ? Via.getAPI().getPlayerProtocolVersion(id) : ProtocolVersion.unknown);
        if (!serverProtocol.isKnown()) {
            plugin.getLogger().warning("Server protocol is unknown; client version comparisons are unavailable.");
        }
    }

    ViaClientVersions(String serverVersion, ProtocolVersion serverProtocol,
                      Function<UUID, ProtocolVersion> clientProtocol) {
        this.serverVersion = serverVersion;
        this.serverProtocol = serverProtocol;
        this.clientProtocol = clientProtocol;
    }

    @Override
    public Optional<Version> version(Player player) {
        ProtocolVersion client = clientProtocol.apply(player.getUniqueId());
        if (!client.isKnown()) return Optional.empty();
        return Optional.of(new Version(client.getName(), serverVersion,
                serverProtocol.isKnown() && client.olderThan(serverProtocol)));
    }

    private static ProtocolVersion resolveServerProtocol(String serverVersionName) {
        if (!Via.isLoaded()) return ProtocolVersion.unknown;
        var serverVersion = Via.getAPI().getServerVersion();
        if (serverVersion.isKnown()) return serverVersion.highestSupportedProtocolVersion();
        ProtocolVersion closest = ProtocolVersion.getClosest(serverVersionName);
        return closest != null ? closest : ProtocolVersion.unknown;
    }
}
