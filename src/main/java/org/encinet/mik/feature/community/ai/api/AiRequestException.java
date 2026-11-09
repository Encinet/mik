package org.encinet.mik.feature.community.ai.api;

/** Expected gateway rejection that callers may safely map to a localized response. */
public final class AiRequestException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    public AiRequestException(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        DISABLED,
        CLOSED,
        PROMPT_EMPTY,
        PROMPT_TOO_LONG,
        CONVERSATION_BUSY,
        SERVER_BUSY,
        EMPTY_RESPONSE
    }
}
