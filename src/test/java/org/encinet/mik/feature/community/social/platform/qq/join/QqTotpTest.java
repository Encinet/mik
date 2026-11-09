package org.encinet.mik.feature.community.social.platform.qq.join;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqTotpTest {
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private final QqTotp totp = new QqTotp(RFC_SECRET);

    @Test
    void matchesRfc6238Sha1VectorsReducedToSixDigits() {
        long[] seconds = {59, 1_111_111_109, 1_111_111_111, 1_234_567_890,
                2_000_000_000, 20_000_000_000L};
        String[] codes = {"287082", "081804", "050471", "005924", "279037", "353130"};
        for (int index = 0; index < seconds.length; index++) {
            assertTrue(totp.matches(codes[index], Instant.ofEpochSecond(seconds[index])));
        }
    }

    @Test
    void acceptsCurrentAndPreviousStepsButNeverFutureOrTwoStepsBack() {
        // RFC 4226 counter 0..3, with counter = Unix seconds / 30.
        assertTrue(totp.matches("755224", Instant.EPOCH));
        assertTrue(totp.matches("287082", Instant.ofEpochSecond(59, 999_999_999)));
        assertFalse(totp.matches("359152", Instant.ofEpochSecond(59, 999_999_999)));
        assertTrue(totp.matches("287082", Instant.ofEpochSecond(60)));
        assertTrue(totp.matches("359152", Instant.ofEpochSecond(60)));
        assertFalse(totp.matches("755224", Instant.ofEpochSecond(60)));
        assertFalse(totp.matches("969429", Instant.ofEpochSecond(60)));
        assertTrue(totp.matches("287082", Instant.ofEpochSecond(89, 999_999_999)));
        assertFalse(totp.matches("287082", Instant.ofEpochSecond(90)));
        assertFalse(totp.matches("287082", Instant.ofEpochSecond(-1)));
    }

    @Test
    void requiresOneCompleteSixDigitAnswerAndPreservesLeadingZeroes() {
        Instant time = Instant.ofEpochSecond(1_111_111_109);
        assertTrue(totp.matches(" 081804 ", time));
        for (String invalid : new String[]{"81804", "0081804", "code: 081804",
                "０８１８０４", "081804 000000", "", "000000"}) {
            assertFalse(totp.matches(invalid, time));
        }
        assertFalse(totp.matches(null, time));
    }

    @Test
    void decodesBase32CaseAndPaddingAndRejectsInvalidSecrets() {
        assertTrue(totp.hasSameSecret(new QqTotp(RFC_SECRET.toLowerCase(java.util.Locale.ROOT))));
        assertTrue(new QqTotp("JBSWY3DPEHPK3PXPJBSWY3DPEE======")
                .hasSameSecret(new QqTotp("JBSWY3DPEHPK3PXPJBSWY3DPEE")));
        for (String invalid : new String[]{"", "JBSWY3DP", RFC_SECRET + "!",
                RFC_SECRET + "=", "JBSWY3DPEHPK3PXPJBSWY3DPEF", "1".repeat(32)}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new QqTotp(invalid));
            if (!invalid.isEmpty()) {
                assertFalse(error.getMessage().contains(invalid));
            }
        }
    }
}
