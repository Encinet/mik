package org.encinet.mik.module.player.address;

import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PlayerAddressLookup {

    List<AddressUse> recentPlayersByAddress(InetAddress address, Instant notBefore);

    record AddressUse(UUID playerId, Instant lastSeenAt) {
    }
}
