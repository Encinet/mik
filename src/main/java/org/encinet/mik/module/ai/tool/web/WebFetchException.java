package org.encinet.mik.module.ai.tool.web;

/** Expected fetch rejection that can be returned to the model without a stack trace. */
final class WebFetchException extends RuntimeException {
    private final String code;

    WebFetchException(String code, String message) {
        super(message);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    WebFetchException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = java.util.Objects.requireNonNull(code, "code");
    }

    String code() {
        return code;
    }
}
