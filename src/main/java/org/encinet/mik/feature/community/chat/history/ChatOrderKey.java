package org.encinet.mik.feature.community.chat.history;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.UUID;

/** Per-conversation monotonic UUIDv7, including across restarts and clock rollback (RFC 9562 §6.2). */
final class ChatOrderKey {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final BigInteger MASK = BigInteger.ONE.shiftLeft(62).subtract(BigInteger.ONE);
    private ChatOrderKey() { }

    static String next(String previous, long now) {
        long millis = Math.max(0, now);
        BigInteger random;
        if (previous != null && millis <= (UUID.fromString(previous).getMostSignificantBits() >>> 16)) {
            UUID old = UUID.fromString(previous);
            millis = old.getMostSignificantBits() >>> 16;
            random = BigInteger.valueOf(old.getMostSignificantBits() & 0xfff).shiftLeft(62)
                    .or(BigInteger.valueOf(old.getLeastSignificantBits() & Long.MAX_VALUE).and(MASK))
                    .add(BigInteger.valueOf(RANDOM.nextLong(1, 1L << 32)));
            if (random.bitLength() > 74) { millis++; random = new BigInteger(74, RANDOM); }
        } else random = new BigInteger(74, RANDOM);
        if (millis >= (1L << 48)) throw new IllegalStateException("Chat ordering timestamp exhausted");
        return new UUID((millis << 16) | 0x7000 | random.shiftRight(62).longValue(),
                Long.MIN_VALUE | random.and(MASK).longValue()).toString();
    }
}
