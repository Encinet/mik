package org.encinet.mik.feature.community.governance.platform.paper;

import org.encinet.mik.feature.community.governance.GovernanceException;

/** Runs blocking governance use cases away from Paper's main thread. */
@FunctionalInterface
public interface GovernanceTaskExecutor {
    void submit(Task task);

    @FunctionalInterface
    interface Task {
        void run() throws GovernanceException;
    }
}
