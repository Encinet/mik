package org.encinet.mik.module.governance.persistence.sqlite;

import org.encinet.mik.module.governance.delivery.GovernanceAction;
import org.encinet.mik.module.governance.delivery.GovernanceActionType;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.membership.promotion.PromotionPause;
import org.encinet.mik.module.governance.removal.RemovalPetition;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTally;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Maps normalized SQLite rows into immutable governance snapshots. */
final class SqliteGovernanceRows {
    private SqliteGovernanceRows() {
    }

    static PlayerProfile readPlayer(ResultSet result) throws SQLException {
        long memberSince = result.getLong("member_since");
        boolean noMemberSince = result.wasNull();
        return new PlayerProfile(
                UUID.fromString(result.getString("player_uuid")),
                result.getString("player_name"),
                Instant.ofEpochMilli(result.getLong("first_joined_at")),
                noMemberSince ? null : Instant.ofEpochMilli(memberSince),
                Instant.ofEpochMilli(result.getLong("last_successful_login_at")));
    }

    static PromotionPause readPause(ResultSet result) throws SQLException {
        return new PromotionPause(
                result.getLong("id"),
                result.getString("reason"),
                Instant.ofEpochMilli(result.getLong("starts_at")),
                Instant.ofEpochMilli(result.getLong("ends_at")),
                result.getString("appeal_path"),
                result.getString("imposed_by"));
    }

    static GovernanceVote readVote(ResultSet result) throws SQLException {
        long finalizedAt = result.getLong("finalized_at");
        boolean notFinalized = result.wasNull();
        String failure = result.getString("failure");
        String terminationReason = result.getString("termination_reason");
        return new GovernanceVote(
                result.getLong("id"),
                readProposal(result),
                Instant.ofEpochMilli(result.getLong("opens_at")),
                Instant.ofEpochMilli(result.getLong("closes_at")),
                VoteStatus.valueOf(result.getString("status")),
                result.getInt("eligible_voters"),
                result.getInt("quorum_required"),
                new VoteTally(result.getInt("yes_count"), result.getInt("no_count"),
                        result.getInt("abstain_count")),
                notFinalized ? null : Instant.ofEpochMilli(finalizedAt),
                failure == null ? VoteFailure.NONE : VoteFailure.valueOf(failure),
                terminationReason == null
                        ? null : VoteTerminationReason.fromStorageId(terminationReason));
    }

    static RemovalPetition readPetition(ResultSet result) throws SQLException {
        long voteId = result.getLong("vote_id");
        boolean noVote = result.wasNull();
        return new RemovalPetition(
                result.getLong("id"),
                UUID.fromString(result.getString("subject_uuid")),
                result.getString("subject_name"),
                Instant.ofEpochMilli(result.getLong("created_at")),
                Instant.ofEpochMilli(result.getLong("expires_at")),
                result.getInt("sponsors_required"),
                result.getInt("sponsor_count"),
                noVote ? null : voteId);
    }

    static GovernanceAction readAction(ResultSet result) throws SQLException {
        long sourceVoteId = result.getLong("source_vote_id");
        long lastAttemptAt = result.getLong("last_attempt_at");
        boolean noLastAttempt = result.wasNull();
        return new GovernanceAction(
                result.getLong("action_id"),
                result.getString("action_key"),
                GovernanceActionType.fromStorageId(result.getString("action_type")),
                readProposal(result),
                sourceVoteId,
                Instant.ofEpochMilli(result.getLong("action_created_at")),
                result.getInt("attempt_count"),
                noLastAttempt ? null : Instant.ofEpochMilli(lastAttemptAt),
                result.getString("last_error"));
    }

    static VoteProposal readProposal(ResultSet result) throws SQLException {
        VoteKind kind = VoteKind.valueOf(result.getString("kind"));
        UUID subjectId = UUID.fromString(result.getString("subject_uuid"));
        String subjectName = result.getString("subject_name");
        return switch (kind) {
            case APPOINTMENT -> new VoteProposal.Appointment(subjectId, subjectName);
            case REMOVAL -> new VoteProposal.Removal(subjectId, subjectName);
            case BAN -> new VoteProposal.Ban(
                    subjectId,
                    subjectName,
                    UUID.fromString(result.getString("proposer_uuid")),
                    result.getString("proposer_name"),
                    Duration.ofSeconds(result.getLong("duration_seconds")),
                    result.getString("reason"));
        };
    }

    static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }

}
