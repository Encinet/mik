package org.encinet.mik.module.governance.persistence.sqlite;

import org.encinet.mik.module.governance.delivery.GovernanceActionType;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.removal.RemovalPetition;
import org.encinet.mik.module.governance.removal.RemovalSponsorship;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteResult;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.module.governance.voting.model.VotingPolicy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQLite transactions owned by voting and removal petitions. */
final class SqliteVotingStore {
    private final Connection connection;

    SqliteVotingStore(Connection connection) {
        this.connection = connection;
    }

    GovernanceVote createVote(
            VoteProposal proposal,
            Instant opensAt,
            Collection<PlayerProfile> voters
    ) throws SQLException {
        List<PlayerProfile> voterRoll = List.copyOf(voters);
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long voteId = insertVote(proposal, opensAt, voterRoll);
            connection.commit();
            return vote(voteId).orElseThrow();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    RemovalSponsorship sponsorRemoval(
            long petitionId,
            UUID subjectId,
            String subjectName,
            UUID sponsorId,
            Instant opensAt,
            int sponsorsRequired,
            Collection<PlayerProfile> voters
    ) throws SQLException {
        List<PlayerProfile> voterRoll = List.copyOf(voters);
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement requirement = connection.prepareStatement("""
                    UPDATE governance_removal_petitions SET sponsors_required = ?
                    WHERE id = ? AND subject_uuid = ? AND status = 'GATHERING'
                      AND expires_at > ?
                    """)) {
                requirement.setInt(1, sponsorsRequired);
                requirement.setLong(2, petitionId);
                requirement.setString(3, subjectId.toString());
                requirement.setLong(4, opensAt.toEpochMilli());
                if (requirement.executeUpdate() != 1) {
                    throw new SQLException("Removal petition is no longer gathering sponsors");
                }
            }
            boolean newlyAdded;
            try (PreparedStatement sponsor = connection.prepareStatement("""
                    INSERT OR IGNORE INTO governance_removal_sponsors(
                        petition_id, sponsor_uuid, sponsored_at
                    ) VALUES (?, ?, ?)
                    """)) {
                sponsor.setLong(1, petitionId);
                sponsor.setString(2, sponsorId.toString());
                sponsor.setLong(3, opensAt.toEpochMilli());
                newlyAdded = sponsor.executeUpdate() == 1;
            }
            int sponsorCount;
            try (PreparedStatement count = connection.prepareStatement("""
                    SELECT COUNT(*) FROM governance_removal_sponsors WHERE petition_id = ?
                    """)) {
                count.setLong(1, petitionId);
                try (ResultSet result = count.executeQuery()) {
                    if (!result.next()) throw new SQLException("Could not count removal sponsors");
                    sponsorCount = result.getInt(1);
                }
            }
            Long voteId = null;
            if (sponsorCount >= sponsorsRequired) {
                voteId = insertVote(new VoteProposal.Removal(subjectId, subjectName),
                        opensAt, voterRoll);
                try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE governance_removal_petitions
                        SET status = 'STARTED', vote_id = ?
                        WHERE id = ? AND status = 'GATHERING'
                        """)) {
                    statement.setLong(1, voteId);
                    statement.setLong(2, petitionId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException("Could not link removal petition to vote");
                    }
                }
            }
            connection.commit();
            return new RemovalSponsorship(
                    removalPetition(petitionId).orElseThrow(),
                    voteId == null ? null : vote(voteId).orElseThrow(),
                    newlyAdded);
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private long insertVote(
            VoteProposal proposal,
            Instant opensAt,
            List<PlayerProfile> voterRoll
    ) throws SQLException {
        int quorum = VotingPolicy.quorum(voterRoll.size());
        try (PreparedStatement vote = connection.prepareStatement("""
                INSERT INTO governance_votes(
                    kind, subject_uuid, subject_name, opens_at, closes_at, status,
                    eligible_voters, quorum_required
                ) VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            vote.setString(1, proposal.kind().name());
            vote.setString(2, proposal.subjectId().toString());
            vote.setString(3, SqliteGovernanceRows.requireText(proposal.subjectName(), "subjectName"));
            vote.setLong(4, opensAt.toEpochMilli());
            vote.setLong(5, opensAt.plus(VotingPolicy.VOTE_DURATION).toEpochMilli());
            vote.setInt(6, voterRoll.size());
            vote.setInt(7, quorum);
            vote.executeUpdate();
            long voteId;
            try (ResultSet keys = vote.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite returned no vote id");
                voteId = keys.getLong(1);
            }
            if (proposal instanceof VoteProposal.Ban ban) {
                try (PreparedStatement details = connection.prepareStatement("""
                        INSERT INTO governance_ban_vote_details(
                            vote_id, proposer_uuid, proposer_name, duration_seconds, reason
                        ) VALUES (?, ?, ?, ?, ?)
                        """)) {
                    details.setLong(1, voteId);
                    details.setString(2, ban.proposerId().toString());
                    details.setString(3, SqliteGovernanceRows.requireText(ban.proposerName(), "proposerName"));
                    details.setLong(4, ban.duration().toSeconds());
                    details.setString(5, SqliteGovernanceRows.requireText(ban.reason(), "reason"));
                    details.executeUpdate();
                }
            }
            try (PreparedStatement voter = connection.prepareStatement("""
                    INSERT INTO governance_voters(vote_id, voter_uuid, voter_name)
                    VALUES (?, ?, ?)
                    """)) {
                for (PlayerProfile player : voterRoll) {
                    voter.setLong(1, voteId);
                    voter.setString(2, player.playerId().toString());
                    voter.setString(3, SqliteGovernanceRows.requireText(player.playerName(), "voterName"));
                    voter.addBatch();
                }
                voter.executeBatch();
            }
            return voteId;
        }
    }

    boolean isVoter(long voteId, UUID voterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT 1 FROM governance_voters WHERE vote_id = ? AND voter_uuid = ?
                """)) {
            statement.setLong(1, voteId);
            statement.setString(2, voterId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    void castBallot(
            long voteId,
            UUID voterId,
            VoteChoice choice,
            Instant castAt
    )
            throws SQLException {
        if (!isVoter(voteId, voterId)) {
            throw new SQLException("Player is not on frozen voter roll");
        }
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO governance_ballots(vote_id, voter_uuid, choice, cast_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(vote_id, voter_uuid) DO UPDATE SET
                    choice = excluded.choice, cast_at = excluded.cast_at
                """)) {
            statement.setLong(1, voteId);
            statement.setString(2, voterId.toString());
            statement.setString(3, choice.name());
            statement.setLong(4, castAt.toEpochMilli());
            statement.executeUpdate();
        }
    }

    Optional<VoteChoice> ballotChoice(long voteId, UUID voterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT choice FROM governance_ballots
                WHERE vote_id = ? AND voter_uuid = ?
                """)) {
            statement.setLong(1, voteId);
            statement.setString(2, voterId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(VoteChoice.valueOf(result.getString(1)))
                        : Optional.empty();
            }
        }
    }

    Optional<GovernanceVote> vote(long voteId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.*,
                       d.proposer_uuid, d.proposer_name, d.duration_seconds, d.reason,
                       COALESCE(SUM(CASE WHEN b.choice = 'YES' THEN 1 ELSE 0 END), 0) AS yes_count,
                       COALESCE(SUM(CASE WHEN b.choice = 'NO' THEN 1 ELSE 0 END), 0) AS no_count,
                       COALESCE(SUM(CASE WHEN b.choice = 'ABSTAIN' THEN 1 ELSE 0 END), 0) AS abstain_count
                FROM governance_votes v
                LEFT JOIN governance_ban_vote_details d ON d.vote_id = v.id
                LEFT JOIN governance_ballots b ON b.vote_id = v.id
                WHERE v.id = ?
                GROUP BY v.id
                """)) {
            statement.setLong(1, voteId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(SqliteGovernanceRows.readVote(result)) : Optional.empty();
            }
        }
    }

    List<GovernanceVote> openVotes() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.*,
                       d.proposer_uuid, d.proposer_name, d.duration_seconds, d.reason,
                       COALESCE(SUM(CASE WHEN b.choice = 'YES' THEN 1 ELSE 0 END), 0) AS yes_count,
                       COALESCE(SUM(CASE WHEN b.choice = 'NO' THEN 1 ELSE 0 END), 0) AS no_count,
                       COALESCE(SUM(CASE WHEN b.choice = 'ABSTAIN' THEN 1 ELSE 0 END), 0) AS abstain_count
                FROM governance_votes v
                LEFT JOIN governance_ban_vote_details d ON d.vote_id = v.id
                LEFT JOIN governance_ballots b ON b.vote_id = v.id
                WHERE v.status = 'OPEN'
                GROUP BY v.id ORDER BY v.closes_at, v.id
                """)) {
            try (ResultSet result = statement.executeQuery()) {
                List<GovernanceVote> votes = new ArrayList<>();
                while (result.next()) votes.add(SqliteGovernanceRows.readVote(result));
                return List.copyOf(votes);
            }
        }
    }

    List<GovernanceVote> recentVotes(int limit) throws SQLException {
        if (limit <= 0) return List.of();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT v.*,
                       d.proposer_uuid, d.proposer_name, d.duration_seconds, d.reason,
                       COALESCE(SUM(CASE WHEN b.choice = 'YES' THEN 1 ELSE 0 END), 0) AS yes_count,
                       COALESCE(SUM(CASE WHEN b.choice = 'NO' THEN 1 ELSE 0 END), 0) AS no_count,
                       COALESCE(SUM(CASE WHEN b.choice = 'ABSTAIN' THEN 1 ELSE 0 END), 0) AS abstain_count
                FROM governance_votes v
                LEFT JOIN governance_ban_vote_details d ON d.vote_id = v.id
                LEFT JOIN governance_ballots b ON b.vote_id = v.id
                WHERE v.status <> 'OPEN'
                GROUP BY v.id
                ORDER BY v.finalized_at DESC, v.id DESC
                LIMIT ?
                """)) {
            statement.setInt(1, limit);
            try (ResultSet result = statement.executeQuery()) {
                List<GovernanceVote> votes = new ArrayList<>();
                while (result.next()) votes.add(SqliteGovernanceRows.readVote(result));
                return List.copyOf(votes);
            }
        }
    }

    Optional<GovernanceVote> openVote(VoteKind kind, UUID subjectId)
            throws SQLException {
        return openVotes().stream()
                .filter(vote -> vote.kind() == kind && vote.subjectId().equals(subjectId))
                .findFirst();
    }

    Optional<Instant> lastFailedAt(VoteKind kind, UUID subjectId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT finalized_at FROM governance_votes
                WHERE kind = ? AND subject_uuid = ? AND status = 'FAILED'
                  AND failure = 'QUORUM'
                  AND finalized_at IS NOT NULL
                ORDER BY finalized_at DESC, id DESC LIMIT 1
                """)) {
            statement.setString(1, kind.name());
            statement.setString(2, subjectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next()
                        ? Optional.of(Instant.ofEpochMilli(result.getLong(1)))
                        : Optional.empty();
            }
        }
    }

    GovernanceVote finalizeVote(
            long voteId,
            VoteStatus status,
            VoteFailure failure,
            VoteTerminationReason terminationReason,
            Instant finalizedAt
    ) throws SQLException {
        if (status == VoteStatus.OPEN) throw new IllegalArgumentException("status must be final");
        if ((status == VoteStatus.TERMINATED) != (terminationReason != null)) {
            throw new IllegalArgumentException("termination reason does not match vote status");
        }
        GovernanceVote current = vote(voteId)
                .orElseThrow(() -> new SQLException("Vote " + voteId + " does not exist"));
        if (current.status() != VoteStatus.OPEN) {
            throw new SQLException("Vote " + voteId + " is not open");
        }
        if (status == VoteStatus.PASSED || status == VoteStatus.FAILED) {
            VoteResult expected = VotingPolicy.evaluateVote(
                    current.eligibleVoters(), current.tally());
            VoteStatus expectedStatus = expected.passed()
                    ? VoteStatus.PASSED : VoteStatus.FAILED;
            VoteFailure suppliedFailure = failure == null ? VoteFailure.NONE : failure;
            if (status != expectedStatus || suppliedFailure != expected.failure()) {
                throw new SQLException("Final vote result does not match the recorded ballots");
            }
        }
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE governance_votes
                    SET status = ?, finalized_at = ?, failure = ?, termination_reason = ?
                    WHERE id = ? AND status = 'OPEN'
                    """)) {
                statement.setString(1, status.name());
                statement.setLong(2, finalizedAt.toEpochMilli());
                if (failure == null || failure == VoteFailure.NONE) {
                    statement.setNull(3, Types.VARCHAR);
                } else {
                    statement.setString(3, failure.name());
                }
                if (terminationReason == null) {
                    statement.setNull(4, Types.VARCHAR);
                } else {
                    statement.setString(4, terminationReason.storageId());
                }
                statement.setLong(5, voteId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Vote " + voteId + " is not open");
                }
            }
            if (status == VoteStatus.PASSED) {
                try (PreparedStatement action = connection.prepareStatement("""
                        INSERT INTO governance_actions(
                            action_key, action_type, source_vote_id, created_at
                        )
                        SELECT 'VOTE:' || id,
                               CASE kind
                                   WHEN 'APPOINTMENT' THEN 'APPOINT_MANAGER'
                                   WHEN 'REMOVAL' THEN 'REMOVE_MANAGER'
                                   WHEN 'BAN' THEN 'BAN_PLAYER'
                               END,
                               id, finalized_at
                        FROM governance_votes
                        WHERE id = ? AND status = 'PASSED'
                        """)) {
                    action.setLong(1, voteId);
                    if (action.executeUpdate() != 1) {
                        throw new SQLException("Could not enqueue passed vote " + voteId);
                    }
                }
            }
            connection.commit();
            return vote(voteId).orElseThrow();
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    Optional<RemovalPetition> gatheringRemovalPetition(
            UUID subjectId,
            Instant now
    ) throws SQLException {
        expireRemovalPetitions(now);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.*,
                       (SELECT COUNT(*) FROM governance_removal_sponsors s
                        WHERE s.petition_id = p.id) AS sponsor_count
                FROM governance_removal_petitions p
                WHERE p.subject_uuid = ? AND p.status = 'GATHERING' AND p.expires_at > ?
                ORDER BY p.created_at DESC, p.id DESC LIMIT 1
                """)) {
            statement.setString(1, subjectId.toString());
            statement.setLong(2, now.toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(SqliteGovernanceRows.readPetition(result)) : Optional.empty();
            }
        }
    }

    List<RemovalPetition> gatheringRemovalPetitions(Instant now)
            throws SQLException {
        expireRemovalPetitions(now);
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.*,
                       (SELECT COUNT(*) FROM governance_removal_sponsors s
                        WHERE s.petition_id = p.id) AS sponsor_count
                FROM governance_removal_petitions p
                WHERE p.status = 'GATHERING' AND p.expires_at > ?
                ORDER BY p.expires_at, p.id
                """)) {
            statement.setLong(1, now.toEpochMilli());
            try (ResultSet result = statement.executeQuery()) {
                List<RemovalPetition> petitions = new ArrayList<>();
                while (result.next()) petitions.add(SqliteGovernanceRows.readPetition(result));
                return List.copyOf(petitions);
            }
        }
    }

    private void expireRemovalPetitions(Instant now) throws SQLException {
        try (PreparedStatement expire = connection.prepareStatement("""
                UPDATE governance_removal_petitions
                SET status = 'EXPIRED'
                WHERE status = 'GATHERING' AND expires_at <= ?
                """)) {
            expire.setLong(1, now.toEpochMilli());
            expire.executeUpdate();
        }
    }

    RemovalPetition createRemovalPetition(
            UUID subjectId,
            String subjectName,
            Instant createdAt,
            int sponsorsRequired
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO governance_removal_petitions(
                    subject_uuid, subject_name, created_at, expires_at, sponsors_required
                ) VALUES (?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, subjectId.toString());
            statement.setString(2, SqliteGovernanceRows.requireText(subjectName, "subjectName"));
            statement.setLong(3, createdAt.toEpochMilli());
            statement.setLong(4, createdAt.plus(VotingPolicy.VOTE_DURATION).toEpochMilli());
            statement.setInt(5, sponsorsRequired);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("SQLite returned no petition id");
                return removalPetition(keys.getLong(1)).orElseThrow();
            }
        }
    }

    List<UUID> removalSponsors(long petitionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sponsor_uuid FROM governance_removal_sponsors
                WHERE petition_id = ? ORDER BY sponsored_at, sponsor_uuid
                """)) {
            statement.setLong(1, petitionId);
            try (ResultSet result = statement.executeQuery()) {
                List<UUID> sponsors = new ArrayList<>();
                while (result.next()) sponsors.add(UUID.fromString(result.getString(1)));
                return List.copyOf(sponsors);
            }
        }
    }

    void removeRemovalSponsor(long petitionId, UUID sponsorId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM governance_removal_sponsors
                WHERE petition_id = ? AND sponsor_uuid = ?
                """)) {
            statement.setLong(1, petitionId);
            statement.setString(2, sponsorId.toString());
            statement.executeUpdate();
        }
    }

    private Optional<RemovalPetition> removalPetition(long petitionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT p.*,
                       (SELECT COUNT(*) FROM governance_removal_sponsors s
                        WHERE s.petition_id = p.id) AS sponsor_count
                FROM governance_removal_petitions p WHERE p.id = ?
                """)) {
            statement.setLong(1, petitionId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(SqliteGovernanceRows.readPetition(result)) : Optional.empty();
            }
        }
    }

}
