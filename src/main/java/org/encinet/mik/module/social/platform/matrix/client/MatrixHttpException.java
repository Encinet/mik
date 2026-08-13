package org.encinet.mik.module.social.platform.matrix.client;

/** Matrix standard error response with its HTTP status and retry hint retained. */
public final class MatrixHttpException extends RuntimeException {
    private final int statusCode;
    private final String errorCode;
    private final long retryAfterMillis;

    MatrixHttpException(
            String operation,
            int statusCode,
            String errorCode,
            String message,
            long retryAfterMillis
    ) {
        super(describe(operation, statusCode, errorCode, message));
        this.statusCode = statusCode;
        this.errorCode = errorCode == null ? "" : errorCode;
        this.retryAfterMillis = Math.max(0, retryAfterMillis);
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }

    public boolean isRateLimited() {
        return statusCode == 429 || "M_LIMIT_EXCEEDED".equals(errorCode);
    }

    private static String describe(
            String operation,
            int statusCode,
            String errorCode,
            String message
    ) {
        StringBuilder result = new StringBuilder(operation)
                .append(" failed (HTTP ").append(statusCode);
        if (errorCode != null && !errorCode.isBlank()) {
            result.append(", ").append(limit(errorCode, 80));
        }
        result.append(')');
        if (message != null && !message.isBlank()) {
            result.append(": ").append(limit(message.strip(), 300));
        }
        return result.toString();
    }

    private static String limit(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length) + "…";
    }
}
