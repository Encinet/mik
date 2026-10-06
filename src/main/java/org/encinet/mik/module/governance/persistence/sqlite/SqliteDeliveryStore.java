package org.encinet.mik.module.governance.persistence.sqlite;

import org.encinet.mik.module.governance.delivery.GovernanceAction;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** SQLite statements for durable announcements and approved side effects. */
final class SqliteDeliveryStore {
    private final Connection connection;

    SqliteDeliveryStore(Connection connection) {
        this.connection = connection;
    }

    List<GovernanceVote> pendingVoteAnnouncements() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.*,
                       d.proposer_uuid, d.proposer_name, d.duration_seconds, d.reason,
                       COALESCE(SUM(CASE WHEN b.choice = 'YES' THEN 1 ELSE 0 END), 0) AS yes_count,
                       COALESCE(SUM(CASE WHEN b.choice = 'NO' THEN 1 ELSE 0 END), 0) AS no_count,
                       COALESCE(SUM(CASE WHEN b.choice = 'ABSTAIN' THEN 1 ELSE 0 END), 0) AS abstain_count
                FROM governance_votes v
                LEFT JOIN governance_ban_vote_details d ON d.vote_id = v.id
                LEFT JOIN governance_ballots b ON b.vote_id = v.id
                WHERE v.status <> 'OPEN' AND v.result_announced_at IS NULL
                GROUP BY v.id
                ORDER BY v.finalized_at, v.id
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                List<GovernanceVote> votes = new ArrayList<>();
                while (result.next()) votes.add(SqliteGovernanceRows.readVote(result));
                return List.copyOf(votes);
            }
        }
    }

    void markVoteResultAnnounced(long voteId, Instant announcedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE governance_votes SET result_announced_at = ?
                WHERE id = ? AND status <> 'OPEN' AND result_announced_at IS NULL
                """)) {
            statement.setLong(1, announcedAt.toEpochMilli());
            statement.setLong(2, voteId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Vote " + voteId + " has no pending result announcement");
            }
        }
    }

    List<GovernanceAction> pendingActions() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT a.id AS action_id, a.action_key, a.action_type,
                       a.source_vote_id, a.created_at AS action_created_at,
                       a.attempt_count, a.last_attempt_at, a.last_error,
                       v.kind, v.subject_uuid, v.subject_name,
                       d.proposer_uuid, d.proposer_name, d.duration_seconds, d.reason
                FROM governance_actions a
                JOIN governance_votes v ON v.id = a.source_vote_id
                LEFT JOIN governance_ban_vote_details d ON d.vote_id = v.id
                WHERE a.applied_at IS NULL
                ORDER BY a.created_at, a.id
                """);
             ResultSet result = statement.executeQuery()) {
            List<GovernanceAction> actions = new ArrayList<>();
            while (result.next()) actions.add(SqliteGovernanceRows.readAction(result));
            return List.copyOf(actions);
        }
    }

    void markActionApplied(long actionId, Instant appliedAt)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE governance_actions
                SET applied_at = ?, attempt_count = attempt_count + 1,
                    last_attempt_at = ?, last_error = NULL
                WHERE id = ? AND applied_at IS NULL
                """)) {
            statement.setLong(1, appliedAt.toEpochMilli());
            statement.setLong(2, appliedAt.toEpochMilli());
            statement.setLong(3, actionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Action " + actionId + " is not pending");
            }
        }
    }

    void markActionFailed(long actionId, Instant attemptedAt, String error)
            throws SQLException {
        String message = SqliteGovernanceRows.requireText(error, "error");
        if (message.length() > 4_000) message = message.substring(0, 4_000);
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE governance_actions
                SET attempt_count = attempt_count + 1,
                    last_attempt_at = ?, last_error = ?
                WHERE id = ? AND applied_at IS NULL
                """)) {
            statement.setLong(1, attemptedAt.toEpochMilli());
            statement.setString(2, message);
            statement.setLong(3, actionId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Action " + actionId + " is not pending");
            }
        }
    }

}

