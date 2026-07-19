package org.encinet.mik.module.ban;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public integration point for plugins that want their bans recorded by Mik.
 * Mutating methods must be called from the server thread.
 */
public interface BanManager {

    Optional<BanRecord> active(UUID playerUuid, String playerName);

    List<BanRecord> activeRecords();

    List<BanRecord> allRecords();

    List<BanRecord> history(UUID playerUuid, String playerName);

    BanRecord ban(UUID playerUuid, String playerName, String reason, String source,
                  Instant expiresAt, BanRecord.Origin origin) throws BanServiceException;

    BanRecord editReason(UUID playerUuid, String playerName, String reason, String operator)
            throws BanServiceException;

    BanRecord editExpiration(UUID playerUuid, String playerName, Instant expiresAt, String operator)
            throws BanServiceException;

    BanRecord pardon(UUID playerUuid, String playerName, String operator, String reason)
            throws BanServiceException;
}
