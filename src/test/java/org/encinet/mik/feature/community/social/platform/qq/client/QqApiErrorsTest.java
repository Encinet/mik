package org.encinet.mik.feature.community.social.platform.qq.client;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import javax.net.ssl.SSLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QqApiErrorsTest {
    @Test
    void nestedAsyncNetworkFailuresHaveUsefulSummariesWithoutRemoteMessages() {
        Exception[] errors = {new HttpTimeoutException("private-token"), new UnknownHostException("private-token"),
                new ConnectException("private-token"), new SSLException("private-token"),
                new IllegalArgumentException("answer 005924")};
        String[] summaries = {"HTTP request timed out", "DNS lookup failed", "Could not connect to QQ API",
                "TLS connection failed", "IllegalArgumentException"};
        for (int index = 0; index < errors.length; index++) {
            assertEquals(summaries[index], QqApiErrors.describe(new ExecutionException(new CompletionException(errors[index]))));
        }
    }

    @Test
    void sanitizationRemovesOverlappingEscapedAndEncodedSecretsBeforeTruncation() {
        String message = "secret-token secret a\\\"b a%22b 005924\nline\u2028line";
        String safe = QqApiErrors.safeMessage(message, "secret", "secret-token", "a\"b");
        for (String value : new String[]{"secret", "-token", "a\\\"b", "a%22b", "005924", "\n", "\u2028"}) {
            assertFalse(safe.contains(value), safe);
        }
        assertEquals(301, QqApiErrors.safeMessage("x".repeat(400)).length());
    }
}
