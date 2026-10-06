package org.encinet.mik.module.governance.voting.model;

public enum VoteTerminationReason {
    CANDIDATE_WITHDREW("CANDIDATE_WITHDREW"),
    CANDIDATE_INELIGIBLE("CANDIDATE_INELIGIBLE"),
    MODERATOR_RESIGNED("MANAGER_RESIGNED"),
    MODERATOR_AUTO_REMOVED("MANAGER_AUTO_REMOVED");

    private final String storageId;

    VoteTerminationReason(String storageId) { this.storageId = storageId; }

    /** SQLite ids remain stable so existing vote records can still be read. */
    public String storageId() { return storageId; }

    public static VoteTerminationReason fromStorageId(String value) {
        for (VoteTerminationReason reason : values())
            if (reason.storageId.equals(value)) return reason;
        throw new IllegalArgumentException("Unknown vote termination reason: " + value);
    }
}
