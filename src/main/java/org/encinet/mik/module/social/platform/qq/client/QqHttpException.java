package org.encinet.mik.module.social.platform.qq.client;

final class QqHttpException extends RuntimeException {

    private final int statusCode;
    private final long errorCode;
    private final String traceId;

    QqHttpException(String operation, int statusCode, long errorCode,
                    String message, String traceId) {
        super(describe(operation, statusCode, errorCode, message, traceId));
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.traceId = traceId;
    }

    int statusCode() {
        return statusCode;
    }

    long errorCode() {
        return errorCode;
    }

    String traceId() {
        return traceId;
    }

    private static String describe(String operation, int statusCode, long errorCode,
                                   String message, String traceId) {
        StringBuilder result = new StringBuilder(operation)
                .append(" failed (HTTP ").append(statusCode);
        if (errorCode != 0) {
            result.append(", err_code ").append(errorCode);
        }
        result.append(')');
        if (message != null && !message.isBlank()) {
            result.append(": ").append(limit(message, 300));
        }
        if (traceId != null && !traceId.isBlank()) {
            result.append(" [trace_id=").append(limit(traceId, 100)).append(']');
        }
        return result.toString();
    }

    private static String limit(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length) + "…";
    }
}
