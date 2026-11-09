package org.encinet.mik.feature.community.ai.knowledge.adapter.markdown;

/** Signals invalid or unavailable canonical knowledge storage. */
public final class KnowledgeRepositoryException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public KnowledgeRepositoryException(String message) {
        super(message);
    }

    public KnowledgeRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
