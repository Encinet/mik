package org.encinet.mik.module.governance;

import org.encinet.mik.module.governance.delivery.GovernanceActionType;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GovernanceStorageIdentifiersTest {
    @Test
    void existingQueuedActionsAndVotesRemainReadable() {
        assertEquals(GovernanceActionType.APPOINT_MODERATOR,
                GovernanceActionType.fromStorageId("APPOINT_MANAGER"));
        assertEquals(GovernanceActionType.REMOVE_MODERATOR,
                GovernanceActionType.fromStorageId("REMOVE_MANAGER"));
        assertEquals(VoteTerminationReason.MODERATOR_RESIGNED,
                VoteTerminationReason.fromStorageId("MANAGER_RESIGNED"));
        assertEquals(VoteTerminationReason.MODERATOR_AUTO_REMOVED,
                VoteTerminationReason.fromStorageId("MANAGER_AUTO_REMOVED"));
        assertEquals("MANAGER_RESIGNED", VoteTerminationReason.MODERATOR_RESIGNED.storageId());
    }
}
