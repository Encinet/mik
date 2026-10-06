package org.encinet.mik.module.governance.persistence.sqlite;

import java.sql.SQLException;
import java.sql.Statement;

/** Declarative v1 tables, indexes, and integrity triggers. */
final class SqliteGovernanceSchemaDefinition {
    private SqliteGovernanceSchemaDefinition() {
    }

    static void createSchema(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_players (
                    player_uuid TEXT PRIMARY KEY CHECK(length(player_uuid) = 36),
                    player_name TEXT NOT NULL CHECK(length(trim(player_name)) > 0),
                    first_joined_at INTEGER NOT NULL CHECK(first_joined_at >= 0),
                    member_since INTEGER CHECK(
                        member_since IS NULL OR member_since >= first_joined_at
                    ),
                    last_successful_login_at INTEGER NOT NULL CHECK(
                        last_successful_login_at >= first_joined_at
                    )
                ) WITHOUT ROWID
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_participation (
                    player_uuid TEXT NOT NULL,
                    participation_date TEXT NOT NULL CHECK(
                        length(participation_date) = 10
                        AND date(participation_date) = participation_date
                    ),
                    eligible_seconds INTEGER NOT NULL CHECK(
                        eligible_seconds BETWEEN 0 AND 86400
                    ),
                    PRIMARY KEY(player_uuid, participation_date),
                    FOREIGN KEY(player_uuid) REFERENCES governance_players(player_uuid)
                ) WITHOUT ROWID
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_promotion_pauses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    reason TEXT NOT NULL CHECK(length(trim(reason)) > 0),
                    starts_at INTEGER NOT NULL CHECK(starts_at >= 0),
                    ends_at INTEGER NOT NULL CHECK(ends_at > starts_at),
                    appeal_path TEXT NOT NULL CHECK(length(trim(appeal_path)) > 0),
                    imposed_by TEXT NOT NULL CHECK(length(trim(imposed_by)) > 0),
                    lifted_at INTEGER CHECK(lifted_at IS NULL OR lifted_at >= 0),
                    lifted_by TEXT,
                    lift_reason TEXT,
                    CHECK(
                        (lifted_at IS NULL AND lifted_by IS NULL AND lift_reason IS NULL)
                        OR (lifted_at IS NOT NULL
                            AND length(trim(lifted_by)) > 0
                            AND length(trim(lift_reason)) > 0
                            AND lifted_at >= starts_at)
                    ),
                    FOREIGN KEY(player_uuid) REFERENCES governance_players(player_uuid)
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS governance_pauses_active "
                + "ON governance_promotion_pauses(player_uuid, lifted_at, ends_at)");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_votes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    kind TEXT NOT NULL CHECK(kind IN ('APPOINTMENT', 'REMOVAL', 'BAN')),
                    subject_uuid TEXT NOT NULL,
                    subject_name TEXT NOT NULL CHECK(length(trim(subject_name)) > 0),
                    opens_at INTEGER NOT NULL CHECK(opens_at >= 0),
                    closes_at INTEGER NOT NULL CHECK(closes_at > opens_at),
                    status TEXT NOT NULL CHECK(
                        status IN ('OPEN', 'PASSED', 'FAILED', 'TERMINATED')
                    ),
                    eligible_voters INTEGER NOT NULL CHECK(eligible_voters >= 2),
                    quorum_required INTEGER NOT NULL CHECK(
                        quorum_required BETWEEN 2 AND eligible_voters
                    ),
                    finalized_at INTEGER CHECK(
                        finalized_at IS NULL OR finalized_at >= opens_at
                    ),
                    failure TEXT CHECK(failure IN (
                        'QUORUM', 'MINIMUM_YES', 'TWO_THIRDS_NON_ABSTAINING',
                        'MAJORITY_OF_ALL_BALLOTS'
                    )),
                    termination_reason TEXT CHECK(termination_reason IN (
                        'CANDIDATE_WITHDREW', 'CANDIDATE_INELIGIBLE',
                        'MANAGER_RESIGNED', 'MANAGER_AUTO_REMOVED'
                    )),
                    result_announced_at INTEGER CHECK(
                        result_announced_at IS NULL
                        OR (finalized_at IS NOT NULL AND result_announced_at >= finalized_at)
                    ),
                    CHECK(
                        (status = 'OPEN' AND finalized_at IS NULL AND failure IS NULL
                            AND termination_reason IS NULL AND result_announced_at IS NULL)
                        OR (status = 'PASSED' AND finalized_at >= closes_at AND failure IS NULL
                            AND termination_reason IS NULL)
                        OR (status = 'FAILED' AND finalized_at >= closes_at AND failure IS NOT NULL
                            AND termination_reason IS NULL)
                        OR (status = 'TERMINATED' AND finalized_at < closes_at AND failure IS NULL
                            AND termination_reason IS NOT NULL)
                    ),
                    FOREIGN KEY(subject_uuid) REFERENCES governance_players(player_uuid)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_ban_vote_details (
                    vote_id INTEGER PRIMARY KEY,
                    proposer_uuid TEXT NOT NULL,
                    proposer_name TEXT NOT NULL CHECK(length(trim(proposer_name)) > 0),
                    duration_seconds INTEGER NOT NULL CHECK(duration_seconds > 0),
                    reason TEXT NOT NULL CHECK(length(trim(reason)) > 0),
                    FOREIGN KEY(vote_id) REFERENCES governance_votes(id),
                    FOREIGN KEY(proposer_uuid) REFERENCES governance_players(player_uuid)
                )
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_voters (
                    vote_id INTEGER NOT NULL,
                    voter_uuid TEXT NOT NULL,
                    voter_name TEXT NOT NULL CHECK(length(trim(voter_name)) > 0),
                    PRIMARY KEY(vote_id, voter_uuid),
                    FOREIGN KEY(vote_id) REFERENCES governance_votes(id),
                    FOREIGN KEY(voter_uuid) REFERENCES governance_players(player_uuid)
                ) WITHOUT ROWID
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_ballots (
                    vote_id INTEGER NOT NULL,
                    voter_uuid TEXT NOT NULL,
                    choice TEXT NOT NULL CHECK(choice IN ('YES', 'NO', 'ABSTAIN')),
                    cast_at INTEGER NOT NULL,
                    PRIMARY KEY(vote_id, voter_uuid),
                    FOREIGN KEY(vote_id, voter_uuid)
                        REFERENCES governance_voters(vote_id, voter_uuid)
                ) WITHOUT ROWID
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS governance_votes_open "
                + "ON governance_votes(status, closes_at)");
        statement.execute("CREATE INDEX IF NOT EXISTS governance_votes_history "
                + "ON governance_votes(kind, subject_uuid, status, finalized_at DESC)");
        statement.execute("CREATE INDEX IF NOT EXISTS governance_vote_results "
                + "ON governance_votes(status, result_announced_at, finalized_at DESC)");
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS governance_one_open_vote "
                + "ON governance_votes(kind, subject_uuid) WHERE status = 'OPEN'");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_removal_petitions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    subject_uuid TEXT NOT NULL,
                    subject_name TEXT NOT NULL CHECK(length(trim(subject_name)) > 0),
                    created_at INTEGER NOT NULL CHECK(created_at >= 0),
                    expires_at INTEGER NOT NULL CHECK(expires_at > created_at),
                    sponsors_required INTEGER NOT NULL CHECK(sponsors_required IN (2, 3)),
                    status TEXT NOT NULL DEFAULT 'GATHERING' CHECK(
                        status IN ('GATHERING', 'STARTED', 'EXPIRED')
                    ),
                    vote_id INTEGER UNIQUE,
                    CHECK(
                        (status = 'STARTED' AND vote_id IS NOT NULL)
                        OR (status IN ('GATHERING', 'EXPIRED') AND vote_id IS NULL)
                    ),
                    FOREIGN KEY(subject_uuid) REFERENCES governance_players(player_uuid),
                    FOREIGN KEY(vote_id) REFERENCES governance_votes(id)
                )
                """);
        statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS governance_one_gathering_removal "
                + "ON governance_removal_petitions(subject_uuid) WHERE status = 'GATHERING'");
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_removal_sponsors (
                    petition_id INTEGER NOT NULL,
                    sponsor_uuid TEXT NOT NULL,
                    sponsored_at INTEGER NOT NULL,
                    PRIMARY KEY(petition_id, sponsor_uuid),
                    FOREIGN KEY(petition_id) REFERENCES governance_removal_petitions(id),
                    FOREIGN KEY(sponsor_uuid) REFERENCES governance_players(player_uuid)
                ) WITHOUT ROWID
                """);
        statement.execute("""
                CREATE TABLE IF NOT EXISTS governance_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    action_key TEXT NOT NULL UNIQUE CHECK(length(trim(action_key)) > 0),
                    action_type TEXT NOT NULL CHECK(
                        action_type IN ('APPOINT_MANAGER', 'REMOVE_MANAGER', 'BAN_PLAYER')
                    ),
                    source_vote_id INTEGER NOT NULL UNIQUE,
                    created_at INTEGER NOT NULL CHECK(created_at >= 0),
                    applied_at INTEGER CHECK(applied_at IS NULL OR applied_at >= 0),
                    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK(attempt_count >= 0),
                    last_attempt_at INTEGER CHECK(
                        last_attempt_at IS NULL OR last_attempt_at >= created_at
                    ),
                    last_error TEXT CHECK(
                        last_error IS NULL OR (
                            length(trim(last_error)) BETWEEN 1 AND 4000
                        )
                    ),
                    CHECK(
                        (attempt_count = 0 AND last_attempt_at IS NULL AND last_error IS NULL)
                        OR (attempt_count > 0 AND last_attempt_at IS NOT NULL)
                    ),
                    CHECK(applied_at IS NULL OR applied_at >= created_at),
                    FOREIGN KEY(source_vote_id) REFERENCES governance_votes(id)
                )
                """);
        statement.execute("CREATE INDEX IF NOT EXISTS governance_actions_pending "
                + "ON governance_actions(applied_at, created_at, id)");
        createIntegrityTriggers(statement);
    }

    private static void createIntegrityTriggers(Statement statement) throws SQLException {
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_players_observation_monotonic
                BEFORE UPDATE OF player_uuid, first_joined_at, last_successful_login_at
                ON governance_players
                WHEN NEW.player_uuid <> OLD.player_uuid
                     OR NEW.first_joined_at > OLD.first_joined_at
                     OR NEW.last_successful_login_at < OLD.last_successful_login_at
                BEGIN
                    SELECT RAISE(ABORT, 'player history must be monotonic');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_players_member_since_once
                BEFORE UPDATE OF member_since ON governance_players
                WHEN OLD.member_since IS NOT NULL OR NEW.member_since IS NULL
                BEGIN
                    SELECT RAISE(ABORT, 'member acquisition time is immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_players_no_delete
                BEFORE DELETE ON governance_players
                BEGIN
                    SELECT RAISE(ABORT, 'players are audit records and cannot be deleted');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_participation_monotonic
                BEFORE UPDATE ON governance_participation
                WHEN NEW.player_uuid <> OLD.player_uuid
                     OR NEW.participation_date <> OLD.participation_date
                     OR NEW.eligible_seconds < OLD.eligible_seconds
                BEGIN
                    SELECT RAISE(ABORT, 'effective playtime cannot decrease');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_participation_no_delete
                BEFORE DELETE ON governance_participation
                BEGIN
                    SELECT RAISE(ABORT, 'participation is an audit record and cannot be deleted');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_pauses_snapshot_immutable
                BEFORE UPDATE OF player_uuid, reason, starts_at, ends_at, appeal_path, imposed_by
                ON governance_promotion_pauses
                BEGIN
                    SELECT RAISE(ABORT, 'promotion pause snapshot is immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_pauses_lift_once
                BEFORE UPDATE OF lifted_at, lifted_by, lift_reason
                ON governance_promotion_pauses
                WHEN OLD.lifted_at IS NOT NULL OR NEW.lifted_at IS NULL
                BEGIN
                    SELECT RAISE(ABORT, 'promotion pause can only be lifted once');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_pauses_no_delete
                BEFORE DELETE ON governance_promotion_pauses
                BEGIN
                    SELECT RAISE(ABORT, 'promotion pauses are audit records and cannot be deleted');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_votes_snapshot_immutable
                BEFORE UPDATE OF kind, subject_uuid, subject_name, opens_at, closes_at,
                                 eligible_voters, quorum_required
                ON governance_votes
                BEGIN
                    SELECT RAISE(ABORT, 'vote snapshot is immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_ban_details_valid_vote
                BEFORE INSERT ON governance_ban_vote_details
                WHEN NOT EXISTS (
                    SELECT 1 FROM governance_votes v
                    WHERE v.id = NEW.vote_id AND v.kind = 'BAN' AND v.status = 'OPEN'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'ban details require an open ban vote');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_ban_details_no_update
                BEFORE UPDATE ON governance_ban_vote_details
                BEGIN
                    SELECT RAISE(ABORT, 'ban proposal details are immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_ban_details_no_delete
                BEFORE DELETE ON governance_ban_vote_details
                BEGIN
                    SELECT RAISE(ABORT, 'ban proposal details are audit records');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_petitions_requirement_gathering
                BEFORE UPDATE OF sponsors_required ON governance_removal_petitions
                WHEN OLD.status <> 'GATHERING'
                BEGIN
                    SELECT RAISE(ABORT, 'started petition threshold is immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_petitions_no_delete
                BEFORE DELETE ON governance_removal_petitions
                BEGIN
                    SELECT RAISE(ABORT, 'removal petitions are audit records and cannot be deleted');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_votes_status_transition
                BEFORE UPDATE OF status ON governance_votes
                WHEN OLD.status <> 'OPEN' OR NEW.status NOT IN ('PASSED', 'FAILED', 'TERMINATED')
                BEGIN
                    SELECT RAISE(ABORT, 'invalid vote status transition');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_votes_announce_once
                BEFORE UPDATE OF result_announced_at ON governance_votes
                WHEN OLD.result_announced_at IS NOT NULL
                     OR NEW.result_announced_at IS NULL
                     OR OLD.status = 'OPEN'
                     OR NEW.result_announced_at < OLD.finalized_at
                BEGIN
                    SELECT RAISE(ABORT, 'vote result can only be announced once');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_votes_no_delete
                BEFORE DELETE ON governance_votes
                BEGIN
                    SELECT RAISE(ABORT, 'votes are audit records and cannot be deleted');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_removal_vote_requires_petition
                BEFORE INSERT ON governance_votes
                WHEN NEW.kind = 'REMOVAL' AND NOT EXISTS (
                    SELECT 1 FROM governance_removal_petitions p
                    WHERE p.subject_uuid = NEW.subject_uuid AND p.status = 'GATHERING'
                      AND p.created_at <= NEW.opens_at AND p.expires_at > NEW.opens_at
                      AND (SELECT COUNT(*) FROM governance_removal_sponsors s
                           WHERE s.petition_id = p.id) >= p.sponsors_required
                )
                BEGIN
                    SELECT RAISE(ABORT, 'removal vote requires a completed petition');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_voters_insert_open
                BEFORE INSERT ON governance_voters
                WHEN NOT EXISTS (
                    SELECT 1 FROM governance_votes v
                    WHERE v.id = NEW.vote_id
                      AND v.status = 'OPEN'
                      AND (SELECT COUNT(*) FROM governance_voters existing
                           WHERE existing.vote_id = NEW.vote_id) < v.eligible_voters
                      AND NOT EXISTS (
                          SELECT 1 FROM governance_ballots b WHERE b.vote_id = NEW.vote_id
                      )
                )
                BEGIN
                    SELECT RAISE(ABORT, 'voter roll is frozen');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_voters_no_update
                BEFORE UPDATE ON governance_voters
                BEGIN
                    SELECT RAISE(ABORT, 'voter roll is frozen');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_voters_no_delete
                BEFORE DELETE ON governance_voters
                BEGIN
                    SELECT RAISE(ABORT, 'voter roll is frozen');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_ballots_insert_open
                BEFORE INSERT ON governance_ballots
                WHEN NOT EXISTS (
                    SELECT 1 FROM governance_votes v
                    WHERE v.id = NEW.vote_id AND v.status = 'OPEN'
                      AND NEW.cast_at >= v.opens_at AND NEW.cast_at < v.closes_at
                )
                BEGIN
                    SELECT RAISE(ABORT, 'vote is not open at ballot time');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_ballots_update_open
                BEFORE UPDATE ON governance_ballots
                WHEN NEW.vote_id <> OLD.vote_id OR NEW.voter_uuid <> OLD.voter_uuid
                     OR NEW.cast_at < OLD.cast_at
                     OR NOT EXISTS (
                         SELECT 1 FROM governance_votes v
                         WHERE v.id = NEW.vote_id AND v.status = 'OPEN'
                           AND NEW.cast_at >= v.opens_at AND NEW.cast_at < v.closes_at
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'vote is not open at ballot time');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_ballots_no_delete
                BEFORE DELETE ON governance_ballots
                BEGIN
                    SELECT RAISE(ABORT, 'ballots are audit records and cannot be deleted');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_petitions_snapshot_immutable
                BEFORE UPDATE OF subject_uuid, subject_name, created_at, expires_at
                ON governance_removal_petitions
                BEGIN
                    SELECT RAISE(ABORT, 'removal petition snapshot is immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_petitions_status_transition
                BEFORE UPDATE OF status, vote_id ON governance_removal_petitions
                WHEN OLD.status <> 'GATHERING'
                     OR NEW.status NOT IN ('STARTED', 'EXPIRED')
                     OR (NEW.status = 'STARTED' AND NOT EXISTS (
                         SELECT 1 FROM governance_votes v
                         WHERE v.id = NEW.vote_id AND v.kind = 'REMOVAL'
                           AND v.subject_uuid = OLD.subject_uuid
                     ))
                BEGIN
                    SELECT RAISE(ABORT, 'invalid removal petition transition');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_sponsors_insert_gathering
                BEFORE INSERT ON governance_removal_sponsors
                WHEN NOT EXISTS (
                    SELECT 1 FROM governance_removal_petitions p
                    WHERE p.id = NEW.petition_id AND p.status = 'GATHERING'
                      AND p.subject_uuid <> NEW.sponsor_uuid
                      AND NEW.sponsored_at >= p.created_at
                      AND NEW.sponsored_at < p.expires_at
                )
                BEGIN
                    SELECT RAISE(ABORT, 'removal petition is not gathering sponsors');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_sponsors_no_update
                BEFORE UPDATE ON governance_removal_sponsors
                BEGIN
                    SELECT RAISE(ABORT, 'removal sponsorship is immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_sponsors_delete_gathering
                BEFORE DELETE ON governance_removal_sponsors
                WHEN NOT EXISTS (
                    SELECT 1 FROM governance_removal_petitions p
                    WHERE p.id = OLD.petition_id AND p.status = 'GATHERING'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'started removal sponsorship is immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_actions_valid_source
                BEFORE INSERT ON governance_actions
                WHEN NOT EXISTS (
                    SELECT 1 FROM governance_votes v
                    WHERE v.id = NEW.source_vote_id AND v.status = 'PASSED'
                      AND ((v.kind = 'APPOINTMENT' AND NEW.action_type = 'APPOINT_MANAGER')
                           OR (v.kind = 'REMOVAL' AND NEW.action_type = 'REMOVE_MANAGER')
                           OR (v.kind = 'BAN' AND NEW.action_type = 'BAN_PLAYER'
                               AND EXISTS (
                                   SELECT 1 FROM governance_ban_vote_details d
                                   WHERE d.vote_id = v.id
                               )))
                )
                BEGIN
                    SELECT RAISE(ABORT, 'action does not match a passed vote');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_actions_identity_immutable
                BEFORE UPDATE OF action_key, action_type, source_vote_id, created_at
                ON governance_actions
                BEGIN
                    SELECT RAISE(ABORT, 'action identity is immutable');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_actions_attempt_transition
                BEFORE UPDATE OF applied_at, attempt_count, last_attempt_at, last_error
                ON governance_actions
                WHEN OLD.applied_at IS NOT NULL
                     OR NEW.attempt_count <> OLD.attempt_count + 1
                     OR NEW.last_attempt_at IS NULL
                     OR NEW.last_attempt_at < OLD.created_at
                     OR (NEW.applied_at IS NULL
                         AND length(trim(COALESCE(NEW.last_error, ''))) = 0)
                     OR (NEW.applied_at IS NOT NULL AND NEW.last_error IS NOT NULL)
                BEGIN
                    SELECT RAISE(ABORT, 'invalid action attempt transition');
                END
                """);
        statement.execute("""
                CREATE TRIGGER IF NOT EXISTS governance_actions_no_delete
                BEFORE DELETE ON governance_actions
                BEGIN
                    SELECT RAISE(ABORT, 'actions are audit records and cannot be deleted');
                END
                """);
    }

}

