package org.encinet.mik.feature.player.client;

import org.bukkit.entity.Player;

import java.util.Optional;

/** Main-thread client protocol lookup, independent of guidance and its presentation. */
@FunctionalInterface
public interface ClientVersions {
    Optional<Version> version(Player player);

    record Version(String name, String serverVersion, boolean outdated) {}
}
