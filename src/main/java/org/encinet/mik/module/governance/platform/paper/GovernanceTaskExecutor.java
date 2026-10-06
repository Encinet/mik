package org.encinet.mik.module.governance.platform.paper;

import org.encinet.mik.module.governance.GovernanceException;

/** Runs blocking governance use cases away from Paper's main thread. */
@FunctionalInterface
public interface GovernanceTaskExecutor {
    void submit(Task task);

    @FunctionalInterface
    interface Task {
        void run() throws GovernanceException;
    }
}
