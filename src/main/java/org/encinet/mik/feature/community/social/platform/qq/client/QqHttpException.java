package org.encinet.mik.feature.community.social.platform.qq.client;

/** Carries only sanitized diagnostics, so ordinary exception logging remains safe. */
public final class QqHttpException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final int statusCode;
    private final long code;
    private final long errorCode;
    private final long retryAfterMillis;

    QqHttpException(String operation, int statusCode, long code, long errorCode,
                    String message, String traceId, long retryAfterMillis) {
        super(describe(operation, statusCode, code, errorCode, message, traceId, retryAfterMillis));
        this.statusCode = statusCode;
        this.code = code;
        this.errorCode = errorCode;
        this.retryAfterMillis = retryAfterMillis;
    }

    public int statusCode() { return statusCode; }
    public long code() { return code; }
    public long errorCode() { return errorCode; }
    public long retryAfterMillis() { return retryAfterMillis; }

    private static String describe(String operation, int statusCode, long code, long errorCode,
                                   String message, String traceId, long retryAfterMillis) {
        StringBuilder result = new StringBuilder(operation).append(" failed (HTTP ").append(statusCode);
        if (code != 0) result.append(", code=").append(code);
        if (errorCode != 0) result.append(", err_code=").append(errorCode);
        result.append("): ").append(message);
        if (!traceId.isBlank()) result.append(" [trace_id=").append(traceId).append(']');
        if (retryAfterMillis > 0) result.append(" [Retry-After=").append(retryAfterMillis).append(" ms]");
        return result.toString();
    }
}
