package org.encinet.mik.module.governance.persistence;

import org.encinet.mik.module.governance.GovernanceRepositoryException;

/** Lifecycle of the single governance database connection. */
public interface GovernanceDatabase extends AutoCloseable {
    void open() throws GovernanceRepositoryException;

    @Override
    void close() throws GovernanceRepositoryException;
}
