package org.encinet.mik.module.player.address;

import java.net.InetAddress;
import java.util.Optional;
import java.util.UUID;

/** Recent address identity used to personalize a server-list response. */
public interface PlayerAddressIdentityLookup {
    Optional<AddressPlayer> inferPlayerByAddress(InetAddress address);

    record AddressPlayer(UUID playerId, String playerName) {
        public Optional<String> playerNameOptional() {
            return Optional.ofNullable(playerName);
        }
    }
}
