package org.encinet.mik.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

public final class HmacTimestamp {

    private static final int PERIOD = 30;
    private static final int SKEW   = 1;

    private HmacTimestamp() {}

    public static boolean verify(String secret, String token) {
        if (secret == null || token == null || token.length() != 64) return false;
        long step = Instant.now().getEpochSecond() / PERIOD;
        for (int i = -SKEW; i <= SKEW; i++) {
            if (generate(secret, step + i).equals(token)) return true;
        }
        return false;
    }

    private static String generate(String secret, long step) {
        try {
            byte[] key = secret.getBytes(StandardCharsets.UTF_8);
            byte[] msg = Long.toString(step).getBytes(StandardCharsets.UTF_8);

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));

            return HexFormat.of().formatHex(mac.doFinal(msg));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC generation failed", e);
        }
    }
}
