package org.encinet.mik.module.governance.persistence.sqlite;

import org.encinet.mik.module.governance.delivery.GovernanceAction;
import org.encinet.mik.module.governance.membership.participation.DailyParticipation;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.membership.promotion.PromotionPause;
import org.encinet.mik.module.governance.removal.RemovalPetition;
import org.encinet.mik.module.governance.removal.RemovalSponsorship;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Owns the connection lock and delegates each operation to its capability store. */
final class SqliteGovernanceStore implements AutoCloseable {

    private final File databaseFile;
    private Connection connection;
    private SqliteMembershipStore membershipStore;
    private SqliteVotingStore votingStore;
    private SqliteDeliveryStore deliveryStore;

    SqliteGovernanceStore(File databaseFile) {
        this.databaseFile = databaseFile;
    }

    synchronized void open() throws SQLException {
        if (connection != null) return;
        File parent = databaseFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create database directory " + parent);
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException error) {
            throw new SQLException("SQLite JDBC driver is unavailable", error);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
            new SqliteGovernanceSchema(connection).initialize(statement);
            membershipStore = new SqliteMembershipStore(connection);
            votingStore = new SqliteVotingStore(connection);
            deliveryStore = new SqliteDeliveryStore(connection);
        } catch (SQLException error) {
            try {
                close();
            } catch (SQLException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    synchronized void observePlayer(
            UUID playerId,
            String playerName,
            Instant firstJoinedAt,
            Instant lastLoginAt
    ) throws SQLException {
        ensureOpen();
        membershipStore.observePlayer(playerId, playerName, firstJoinedAt, lastLoginAt);
    }

    synchronized void recordMemberSince(UUID playerId, Instant memberSince)
            throws SQLException {
        ensureOpen();
        membershipStore.recordMemberSince(playerId, memberSince);
    }

    synchronized Optional<PlayerProfile> player(UUID playerId) throws SQLException {
        ensureOpen();
        return membershipStore.player(playerId);
    }

    synchronized List<PlayerProfile> players() throws SQLException {
        ensureOpen();
        return membershipStore.players();
    }

    synchronized void addParticipation(UUID playerId, LocalDate date, long seconds)
            throws SQLException {
        ensureOpen();
        membershipStore.addParticipation(playerId, date, seconds);
    }

    synchronized List<DailyParticipation> participation(UUID playerId)
            throws SQLException {
        ensureOpen();
        return membershipStore.participation(playerId);
    }

    synchronized PromotionPause createPause(
            UUID playerId,
            String reason,
            Instant startsAt,
            Instant endsAt,
            String appealPath,
            String imposedBy
    ) throws SQLException {
        ensureOpen();
        return membershipStore.createPause(
                playerId, reason, startsAt, endsAt, appealPath, imposedBy);
    }

    synchronized Optional<PromotionPause> activePause(UUID playerId, Instant now)
            throws SQLException {
        ensureOpen();
        return membershipStore.activePause(playerId, now);
    }

    synchronized boolean liftActivePause(
            UUID playerId,
            Instant now,
            String liftedBy,
            String liftReason
    ) throws SQLException {
        ensureOpen();
        return membershipStore.liftActivePause(playerId, now, liftedBy, liftReason);
    }

    synchronized GovernanceVote createVote(
            VoteProposal proposal,
            Instant opensAt,
            Collection<PlayerProfile> voters
    ) throws SQLException {
        ensureOpen();
        return votingStore.createVote(proposal, opensAt, voters);
    }

    synchronized RemovalSponsorship sponsorRemoval(
            long petitionId,
            UUID subjectId,
            String subjectName,
            UUID sponsorId,
            Instant opensAt,
            int sponsorsRequired,
            Collection<PlayerProfile> voters
    ) throws SQLException {
        ensureOpen();
        return votingStore.sponsorRemoval(
                petitionId, subjectId, subjectName, sponsorId,
                opensAt, sponsorsRequired, voters);
    }

    synchronized boolean isVoter(long voteId, UUID voterId) throws SQLException {
        ensureOpen();
        return votingStore.isVoter(voteId, voterId);
    }

    synchronized void castBallot(
            long voteId,
            UUID voterId,
            VoteChoice choice,
            Instant castAt
    ) throws SQLException {
        ensureOpen();
        votingStore.castBallot(voteId, voterId, choice, castAt);
    }

    synchronized Optional<VoteChoice> ballotChoice(long voteId, UUID voterId)
            throws SQLException {
        ensureOpen();
        return votingStore.ballotChoice(voteId, voterId);
    }

    synchronized Optional<GovernanceVote> vote(long voteId) throws SQLException {
        ensureOpen();
        return votingStore.vote(voteId);
    }

    synchronized List<GovernanceVote> openVotes() throws SQLException {
        ensureOpen();
        return votingStore.openVotes();
    }

    synchronized List<GovernanceVote> recentVotes(int limit) throws SQLException {
        ensureOpen();
        return votingStore.recentVotes(limit);
    }

    synchronized List<GovernanceVote> pendingVoteAnnouncements() throws SQLException {
        ensureOpen();
        return deliveryStore.pendingVoteAnnouncements();
    }

    synchronized void markVoteResultAnnounced(long voteId, Instant announcedAt)
            throws SQLException {
        ensureOpen();
        deliveryStore.markVoteResultAnnounced(voteId, announcedAt);
    }

    synchronized Optional<GovernanceVote> openVote(VoteKind kind, UUID subjectId)
            throws SQLException {
        ensureOpen();
        return votingStore.openVote(kind, subjectId);
    }

    synchronized Optional<Instant> lastFailedAt(VoteKind kind, UUID subjectId)
            throws SQLException {
        ensureOpen();
        return votingStore.lastFailedAt(kind, subjectId);
    }

    synchronized GovernanceVote finalizeVote(
            long voteId,
            VoteStatus status,
            VoteFailure failure,
            VoteTerminationReason terminationReason,
            Instant finalizedAt
    ) throws SQLException {
        ensureOpen();
        return votingStore.finalizeVote(
                voteId, status, failure, terminationReason, finalizedAt);
    }

    synchronized Optional<RemovalPetition> gatheringRemovalPetition(
            UUID subjectId,
            Instant now
    ) throws SQLException {
        ensureOpen();
        return votingStore.gatheringRemovalPetition(subjectId, now);
    }

    synchronized List<RemovalPetition> gatheringRemovalPetitions(Instant now)
            throws SQLException {
        ensureOpen();
        return votingStore.gatheringRemovalPetitions(now);
    }

    synchronized RemovalPetition createRemovalPetition(
            UUID subjectId,
            String subjectName,
            Instant createdAt,
            int sponsorsRequired
    ) throws SQLException {
        ensureOpen();
        return votingStore.createRemovalPetition(
                subjectId, subjectName, createdAt, sponsorsRequired);
    }

    synchronized List<UUID> removalSponsors(long petitionId) throws SQLException {
        ensureOpen();
        return votingStore.removalSponsors(petitionId);
    }

    synchronized void removeRemovalSponsor(long petitionId, UUID sponsorId)
            throws SQLException {
        ensureOpen();
        votingStore.removeRemovalSponsor(petitionId, sponsorId);
    }

    synchronized List<GovernanceAction> pendingActions() throws SQLException {
        ensureOpen();
        return deliveryStore.pendingActions();
    }

    synchronized void markActionApplied(long actionId, Instant appliedAt)
            throws SQLException {
        ensureOpen();
        deliveryStore.markActionApplied(actionId, appliedAt);
    }

    synchronized void markActionFailed(long actionId, Instant attemptedAt, String error)
            throws SQLException {
        ensureOpen();
        deliveryStore.markActionFailed(actionId, attemptedAt, error);
    }

    private void ensureOpen() throws SQLException {
        if (connection == null) throw new SQLException("Governance database is not open");
    }

    @Override
    public synchronized void close() throws SQLException {
        if (connection == null) return;
        Connection active = connection;
        connection = null;
        membershipStore = null;
        votingStore = null;
        deliveryStore = null;
        active.close();
    }
}
