package org.encinet.mik.module.ai.knowledge.adapter.markdown;

/** Signals invalid or unavailable canonical knowledge storage. */
public final class KnowledgeRepositoryException extends RuntimeException {
    public KnowledgeRepositoryException(String message) {
        super(message);
    }

    public KnowledgeRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
