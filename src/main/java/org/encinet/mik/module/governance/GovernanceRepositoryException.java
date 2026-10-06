package org.encinet.mik.module.governance;

/** Storage failure reported through a capability-owned repository port. */
public final class GovernanceRepositoryException extends Exception {
    public GovernanceRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
