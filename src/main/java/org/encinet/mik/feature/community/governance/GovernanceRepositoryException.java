package org.encinet.mik.feature.community.governance;

/** Storage failure reported through a capability-owned repository port. */
public final class GovernanceRepositoryException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public GovernanceRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
