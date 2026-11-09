package org.encinet.mik.feature.community.ai.provider;

/** Provider or protocol failure safe to summarize to a command sender. */
public final class AiProviderException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public AiProviderException(String message) {
        super(message);
    }

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
