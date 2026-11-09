package org.encinet.mik.feature.community.social.platform.qq.join;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

/** RFC 6238: SHA-1, six digits, Unix epoch and a 30-second time step. */
public final class QqTotp {
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecretKeySpec key;

    public QqTotp(String secret) {
        key = new SecretKeySpec(decodeSecret(secret), "HmacSHA1");
    }

    public boolean matches(String answer, Instant appliedAt) {
        if (answer == null || appliedAt.getEpochSecond() < 0) {
            return false;
        }
        String code = answer.strip();
        if (code.length() != 6 || !code.matches("[0-9]{6}")) {
            return false;
        }
        long counter = appliedAt.getEpochSecond() / 30;
        byte[] supplied = code.getBytes(StandardCharsets.US_ASCII);
        boolean current = MessageDigest.isEqual(supplied, code(counter));
        boolean previous = counter > 0
                && MessageDigest.isEqual(supplied, code(counter - 1));
        return current || previous;
    }

    public boolean hasSameSecret(QqTotp other) {
        return key.equals(other.key);
    }

    private byte[] code(long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(key);
            byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES)
                    .putLong(counter).array());
            int offset = digest[digest.length - 1] & 0x0f;
            int value = ByteBuffer.wrap(digest, offset, Integer.BYTES).getInt()
                    & 0x7fffffff;
            return String.format(Locale.ROOT, "%06d", value % 1_000_000)
                    .getBytes(StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("TOTP HmacSHA1 is unavailable", error);
        }
    }

    private static byte[] decodeSecret(String secret) {
        String encoded = secret == null ? "" : secret.strip().toUpperCase(Locale.ROOT);
        int padding = encoded.indexOf('=');
        String digits = padding < 0 ? encoded : encoded.substring(0, padding);
        if (encoded.length() > 128 || digits.isEmpty()
                || (padding >= 0 && (encoded.length() % 8 != 0
                || !encoded.substring(padding).matches("={1,6}")))) {
            throw invalidSecret();
        }
        int remainder = digits.length() % 8;
        if (remainder == 1 || remainder == 3 || remainder == 6) {
            throw invalidSecret();
        }
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int index = 0; index < digits.length(); index++) {
            int value = BASE32.indexOf(digits.charAt(index));
            if (value < 0) {
                throw invalidSecret();
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                decoded.write((buffer >> bits) & 0xff);
            }
        }
        if ((buffer & ((1 << bits) - 1)) != 0 || decoded.size() < 16) {
            throw invalidSecret();
        }
        return decoded.toByteArray();
    }

    private static IllegalArgumentException invalidSecret() {
        return new IllegalArgumentException(
                "group-join totp-secret must be Base32 encoding at least 16 bytes");
    }
}
