package org.encinet.mik.feature.community.governance;

/** Domain failures carry codes; player-facing text belongs to the presentation adapter and Fluent. */
public final class GovernanceException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public enum Code {
        PASSWORD_REQUIRED,
        PLAYER_UNKNOWN,
        PAUSE_END_INVALID,
        PAUSE_ALREADY_ACTIVE,
        PAUSE_ALREADY_MEMBER,
        VOTERS_TOO_FEW,
        VOTE_SUBJECT_UNKNOWN,
        VOTE_ALREADY_OPEN,
        VOTE_COOLDOWN,
        VOTE_UNKNOWN,
        VOTE_CLOSED,
        NOT_VOTER,
        VOTE_TERMINATION_CLOSED,
        CANDIDATE_INELIGIBLE,
        REMOVAL_REQUIRES_PETITION,
        BAN_SELF,
        BAN_PROPOSER_INELIGIBLE,
        BAN_ALREADY_ACTIVE,
        TERMINATION_REASON_INVALID,
        REMOVAL_TARGET_NOT_MODERATOR,
        REMOVAL_SELF,
        REMOVAL_PETITION_CLOSED,
        REMOVAL_SPONSOR_INELIGIBLE,
        REMOVAL_SPONSORS_TOO_FEW,
        REMOVAL_VOTE_OPEN,
        NOMINATION_NOT_OPEN
    }

    private final Code code;

    public GovernanceException(Code code) {
        super(java.util.Objects.requireNonNull(code, "code").name());
        this.code = code;
    }

    public GovernanceException(String message) {
        super(message);
        this.code = null;
    }

    public GovernanceException(String message, Throwable cause) {
        super(message, cause);
        this.code = null;
    }

    public Code code() {
        return code;
    }
}
