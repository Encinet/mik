package org.encinet.mik.module.ban;

import org.encinet.mik.module.player.PlayerAddressLookup;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class BanAdmissionChecker {

    static final Duration ADDRESS_LINK_WINDOW = Duration.ofDays(365);

    private final BanManager banManager;
    private final PlayerAddressLookup addressLookup;
    private final Clock clock;

    BanAdmissionChecker(BanManager banManager, PlayerAddressLookup addressLookup) {
        this(banManager, addressLookup, Clock.systemUTC());
    }

    BanAdmissionChecker(BanManager banManager, PlayerAddressLookup addressLookup, Clock clock) {
        this.banManager = banManager;
        this.addressLookup = addressLookup;
        this.clock = clock;
    }

    Optional<AdmissionBan> find(UUID playerUuid, String playerName, InetAddress address) {
        Optional<BanRecord> direct = banManager.active(playerUuid, playerName);
        if (direct.isPresent()) {
            return Optional.of(new AdmissionBan(direct.get(), MatchType.DIRECT));
        }
        if (address == null) {
            return Optional.empty();
        }

        Instant notBefore = clock.instant().minus(ADDRESS_LINK_WINDOW);
        return addressLookup.recentPlayersByAddress(address, notBefore).stream()
                .map(PlayerAddressLookup.AddressUse::playerId)
                .filter(candidateId -> !candidateId.equals(playerUuid))
                .map(candidateId -> banManager.active(candidateId, null))
                .flatMap(Optional::stream)
                .findFirst()
                .map(record -> new AdmissionBan(record, MatchType.ADDRESS_LINK));
    }

    record AdmissionBan(BanRecord record, MatchType matchType) {
    }

    enum MatchType {
        DIRECT,
        ADDRESS_LINK
    }
}
