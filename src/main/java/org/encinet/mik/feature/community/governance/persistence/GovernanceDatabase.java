package org.encinet.mik.feature.community.governance.persistence;

import org.encinet.mik.feature.community.governance.GovernanceRepositoryException;

/** Lifecycle of the single governance database connection. */
public interface GovernanceDatabase extends AutoCloseable {
    void open() throws GovernanceRepositoryException;

    @Override
    void close() throws GovernanceRepositoryException;
}
