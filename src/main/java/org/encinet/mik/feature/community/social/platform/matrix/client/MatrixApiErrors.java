package org.encinet.mik.feature.community.social.platform.matrix.client;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/** Shared interpretation of synchronous and asynchronous Matrix request failures. */
public final class MatrixApiErrors {
    private MatrixApiErrors() { }

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
