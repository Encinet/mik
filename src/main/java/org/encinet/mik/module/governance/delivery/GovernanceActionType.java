package org.encinet.mik.module.governance.delivery;

import org.encinet.mik.module.governance.voting.model.VoteKind;

public enum GovernanceActionType {
    APPOINT_MODERATOR(VoteKind.APPOINTMENT, "APPOINT_MANAGER"),
    REMOVE_MODERATOR(VoteKind.REMOVAL, "REMOVE_MANAGER"),
    BAN_PLAYER(VoteKind.BAN, "BAN_PLAYER");

    private final VoteKind voteKind;
    private final String storageId;

    GovernanceActionType(VoteKind voteKind, String storageId) {
        this.voteKind = voteKind;
        this.storageId = storageId;
    }

    public VoteKind voteKind() {
        return voteKind;
    }

    /** SQLite ids remain stable so queued actions survive a plugin update. */
    public String storageId() { return storageId; }

    public static GovernanceActionType fromStorageId(String value) {
        for (GovernanceActionType type : values())
            if (type.storageId.equals(value)) return type;
        throw new IllegalArgumentException("Unknown governance action type: " + value);
    }
}
