package org.encinet.mik.feature.community.social.platform.matrix.crypto;

import java.util.List;
import java.util.Optional;

/** SDK-independent device verification snapshots; presentation owns all wording. */
public sealed interface MatrixCryptoResult {
    record Status(String userId, String deviceId, String signingKey, Verification verification,
                  Optional<String> backupVersion, Optional<Transaction> transaction,
                  Optional<Target> target) implements MatrixCryptoResult { }

    record Transaction(String id, String userId, String deviceId, Phase state,
                       List<Integer> decimals, List<String> emojis) {
        public Transaction {
            decimals = List.copyOf(decimals);
            emojis = List.copyOf(emojis);
        }
    }

    record Target(String userId, String deviceId, Trust trust, boolean verified) { }
    record Requested(String transactionId) implements MatrixCryptoResult { }
    record Updated() implements MatrixCryptoResult { }

    enum Verification { INITIALIZING, VERIFIED, UNVERIFIED, NO_CROSS_SIGNING }
    enum Phase { REQUESTED, READY, STARTED, COMPARISON, WAITING, DONE, CANCELLED, UNKNOWN }
    enum Trust { VALID, CROSS_SIGNED, NOT_CROSS_SIGNED, BLOCKED, INVALID, UNKNOWN }
}
