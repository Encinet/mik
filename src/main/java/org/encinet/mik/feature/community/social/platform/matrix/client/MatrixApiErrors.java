package org.encinet.mik.feature.community.social.platform.matrix.client;

import org.encinet.mik.feature.community.social.api.SocialDeliveryResult;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Shared interpretation of synchronous and asynchronous Matrix request failures. */
public final class MatrixApiErrors {
    private MatrixApiErrors() { }

    public static SocialDeliveryResult.Status deliveryStatus(Throwable error) {
        Throwable cause = unwrap(error);
        return cause instanceof MatrixHttpException http && http.statusCode() >= 400
                && http.statusCode() < 500 && http.statusCode() != 408
                ? SocialDeliveryResult.Status.NOT_SENT : SocialDeliveryResult.Status.UNKNOWN;
    }

    public static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    public static String describe(Throwable error) {
        Throwable cause = unwrap(error);
        String message = cause.getMessage();
        String description = message == null || message.isBlank()
                ? cause.getClass().getSimpleName() : message.strip();
        return description.length() <= 300 ? description : description.substring(0, 300);
    }
}
