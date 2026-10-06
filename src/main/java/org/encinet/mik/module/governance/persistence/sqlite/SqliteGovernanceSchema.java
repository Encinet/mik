package org.encinet.mik.module.governance.persistence.sqlite;

import org.encinet.mik.module.governance.voting.model.VotingPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Owns schema creation, integrity triggers, and startup validation. */
final class SqliteGovernanceSchema {
    private static final int SCHEMA_VERSION = 1;

    private final Connection connection;

    SqliteGovernanceSchema(Connection connection) {
        this.connection = connection;
    }

    void initialize(Statement statement) throws SQLException {
        int version;
        try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            version = result.next() ? result.getInt(1) : 0;
        }
        if (version == 0) {
            if (hasGovernanceObjects(statement)) {
                throw new SQLException(
                        "Unversioned governance tables exist; automatic schema upgrades "
                                + "are intentionally unsupported");
            }
            createInitialSchema(statement);
        } else if (version != SCHEMA_VERSION) {
            throw new SQLException("Unsupported governance database schema " + version
                    + "; expected " + SCHEMA_VERSION);
        }
        validateSchema();
    }

    private static boolean hasGovernanceObjects(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("""
                SELECT 1 FROM sqlite_master
                WHERE name GLOB 'governance_*'
                LIMIT 1
                """)) {
            return result.next();
        }
    }

    private void createInitialSchema(Statement statement) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            SqliteGovernanceSchemaDefinition.createSchema(statement);
            statement.execute("PRAGMA user_version=" + SCHEMA_VERSION);
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void validateSchema() throws SQLException {
        requireSchemaObjects("table",
                "governance_players",
                "governance_participation",
                "governance_promotion_pauses",
                "governance_votes",
                "governance_ban_vote_details",
                "governance_voters",
                "governance_ballots",
                "governance_removal_petitions",
                "governance_removal_sponsors",
                "governance_actions");
        requireSchemaObjects("index",
                "governance_pauses_active",
                "governance_votes_open",
                "governance_votes_history",
                "governance_vote_results",
                "governance_one_open_vote",
                "governance_one_gathering_removal",
                "governance_actions_pending");
        requireSchemaObjects("trigger",
                "governance_players_observation_monotonic",
                "governance_players_member_since_once",
                "governance_players_no_delete",
                "governance_participation_monotonic",
                "governance_participation_no_delete",
                "governance_pauses_snapshot_immutable",
                "governance_pauses_lift_once",
                "governance_pauses_no_delete",
                "governance_votes_snapshot_immutable",
                "governance_ban_details_valid_vote",
                "governance_ban_details_no_update",
                "governance_ban_details_no_delete",
                "governance_votes_status_transition",
                "governance_votes_announce_once",
                "governance_votes_no_delete",
                "governance_removal_vote_requires_petition",
                "governance_voters_insert_open",
                "governance_voters_no_update",
                "governance_voters_no_delete",
                "governance_ballots_insert_open",
                "governance_ballots_update_open",
                "governance_ballots_no_delete",
                "governance_petitions_snapshot_immutable",
                "governance_petitions_status_transition",
                "governance_petitions_requirement_gathering",
                "governance_petitions_no_delete",
                "governance_sponsors_insert_gathering",
                "governance_sponsors_no_update",
                "governance_sponsors_delete_gathering",
                "governance_actions_valid_source",
                "governance_actions_identity_immutable",
                "governance_actions_attempt_transition",
                "governance_actions_no_delete");
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_keys")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new SQLException("SQLite foreign-key enforcement is disabled");
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                throw new SQLException("Governance database integrity check failed");
            }
        }
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
            if (result.next()) {
                throw new SQLException("Governance database contains broken foreign keys");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.id, v.eligible_voters, v.quorum_required,
                       COUNT(vr.voter_uuid) AS voter_count
                FROM governance_votes v
                LEFT JOIN governance_voters vr ON vr.vote_id = v.id
                GROUP BY v.id
                """);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                int eligibleVoters = result.getInt("eligible_voters");
                if (result.getInt("voter_count") != eligibleVoters) {
                    throw new SQLException("Vote " + result.getLong("id")
                            + " has an incomplete frozen voter roll");
                }
                if (result.getInt("quorum_required")
                        != VotingPolicy.quorum(eligibleVoters)) {
                    throw new SQLException("Vote " + result.getLong("id")
                            + " has an invalid quorum snapshot");
                }
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.id FROM governance_votes v
                LEFT JOIN governance_actions a ON a.source_vote_id = v.id
                WHERE v.status = 'PASSED' AND a.id IS NULL
                LIMIT 1
                """);
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                throw new SQLException("Passed vote " + result.getLong(1)
                        + " has no durable governance action");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.id FROM governance_votes v
                LEFT JOIN governance_ban_vote_details d ON d.vote_id = v.id
                WHERE (v.kind = 'BAN' AND d.vote_id IS NULL)
                   OR (v.kind <> 'BAN' AND d.vote_id IS NOT NULL)
                LIMIT 1
                """);
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                throw new SQLException("Vote " + result.getLong(1)
                        + " has inconsistent type-specific proposal data");
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.id FROM governance_votes v
                LEFT JOIN governance_removal_petitions p ON p.vote_id = v.id
                WHERE v.kind = 'REMOVAL' AND p.id IS NULL
                LIMIT 1
                """);
             ResultSet result = statement.executeQuery()) {
            if (result.next()) {
                throw new SQLException("Removal vote " + result.getLong(1)
                        + " has no co-sponsor petition");
            }
        }
    }

    private void requireSchemaObjects(String type, String... names) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM sqlite_master WHERE type = ? AND name = ?
                """)) {
            for (String name : names) {
                statement.setString(1, type);
                statement.setString(2, name);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        throw new SQLException("Governance schema is missing " + type
                                + " " + name + "; automatic upgrades are unsupported");
                    }
                }
            }
        }
    }

}
