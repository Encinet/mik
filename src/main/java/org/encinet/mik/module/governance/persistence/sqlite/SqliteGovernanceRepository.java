package org.encinet.mik.module.governance.persistence.sqlite;

import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.delivery.DeliveryRepository;
import org.encinet.mik.module.governance.delivery.GovernanceAction;
import org.encinet.mik.module.governance.membership.MembershipRepository;
import org.encinet.mik.module.governance.membership.participation.DailyParticipation;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.membership.promotion.PromotionPause;
import org.encinet.mik.module.governance.persistence.GovernanceDatabase;
import org.encinet.mik.module.governance.removal.RemovalPetition;
import org.encinet.mik.module.governance.removal.RemovalRepository;
import org.encinet.mik.module.governance.removal.RemovalSponsorship;
import org.encinet.mik.module.governance.voting.VotingRepository;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adapts one SQLite connection to the capability-owned repository ports. */
public final class SqliteGovernanceRepository implements GovernanceDatabase,
        MembershipRepository, VotingRepository, RemovalRepository, DeliveryRepository {
    private final SqliteGovernanceStore store;

    public SqliteGovernanceRepository(File databaseFile) {
        this.store = new SqliteGovernanceStore(databaseFile);
    }

    @Override
    public void open() throws GovernanceRepositoryException {
        execute(store::open);
    }

    @Override
    public void observePlayer(
            UUID playerId,
            String playerName,
            Instant firstJoinedAt,
            Instant lastLoginAt
    ) throws GovernanceRepositoryException {
        execute(() -> store.observePlayer(
                playerId, playerName, firstJoinedAt, lastLoginAt));
    }

    @Override
    public void recordMemberSince(UUID playerId, Instant memberSince)
            throws GovernanceRepositoryException {
        execute(() -> store.recordMemberSince(playerId, memberSince));
    }

    @Override
    public Optional<PlayerProfile> player(UUID playerId)
            throws GovernanceRepositoryException {
        return query(() -> store.player(playerId));
    }

    @Override
    public List<PlayerProfile> players() throws GovernanceRepositoryException {
        return query(store::players);
    }

    @Override
    public void addParticipation(UUID playerId, LocalDate date, long seconds)
            throws GovernanceRepositoryException {
        execute(() -> store.addParticipation(playerId, date, seconds));
    }

    @Override
    public List<DailyParticipation> participation(UUID playerId)
            throws GovernanceRepositoryException {
        return query(() -> store.participation(playerId));
    }

    @Override
    public PromotionPause createPause(
            UUID playerId,
            String reason,
            Instant startsAt,
            Instant endsAt,
            String appealPath,
            String imposedBy
    ) throws GovernanceRepositoryException {
        return query(() -> store.createPause(
                playerId, reason, startsAt, endsAt, appealPath, imposedBy));
    }

    @Override
    public Optional<PromotionPause> activePause(UUID playerId, Instant now)
            throws GovernanceRepositoryException {
        return query(() -> store.activePause(playerId, now));
    }

    @Override
    public boolean liftActivePause(
            UUID playerId,
            Instant now,
            String liftedBy,
            String liftReason
    ) throws GovernanceRepositoryException {
        return query(() -> store.liftActivePause(
                playerId, now, liftedBy, liftReason));
    }

    @Override
    public GovernanceVote createVote(
            VoteProposal proposal,
            Instant opensAt,
            Collection<PlayerProfile> voters
    ) throws GovernanceRepositoryException {
        return query(() -> store.createVote(proposal, opensAt, voters));
    }

    @Override
    public RemovalSponsorship sponsorRemoval(
            long petitionId,
            UUID subjectId,
            String subjectName,
            UUID sponsorId,
            Instant opensAt,
            int sponsorsRequired,
            Collection<PlayerProfile> voters
    ) throws GovernanceRepositoryException {
        return query(() -> store.sponsorRemoval(
                petitionId, subjectId, subjectName, sponsorId, opensAt,
                sponsorsRequired, voters));
    }

    @Override
    public boolean isVoter(long voteId, UUID voterId)
            throws GovernanceRepositoryException {
        return query(() -> store.isVoter(voteId, voterId));
    }

    @Override
    public void castBallot(long voteId, UUID voterId, VoteChoice choice, Instant castAt)
            throws GovernanceRepositoryException {
        execute(() -> store.castBallot(voteId, voterId, choice, castAt));
    }

    @Override
    public Optional<VoteChoice> ballotChoice(long voteId, UUID voterId)
            throws GovernanceRepositoryException {
        return query(() -> store.ballotChoice(voteId, voterId));
    }

    @Override
    public Optional<GovernanceVote> vote(long voteId)
            throws GovernanceRepositoryException {
        return query(() -> store.vote(voteId));
    }

    @Override
    public List<GovernanceVote> openVotes() throws GovernanceRepositoryException {
        return query(store::openVotes);
    }

    @Override
    public List<GovernanceVote> recentVotes(int limit)
            throws GovernanceRepositoryException {
        return query(() -> store.recentVotes(limit));
    }

    @Override
    public List<GovernanceVote> pendingVoteAnnouncements()
            throws GovernanceRepositoryException {
        return query(store::pendingVoteAnnouncements);
    }

    @Override
    public void markVoteResultAnnounced(long voteId, Instant announcedAt)
            throws GovernanceRepositoryException {
        execute(() -> store.markVoteResultAnnounced(voteId, announcedAt));
    }

    @Override
    public Optional<GovernanceVote> openVote(VoteKind kind, UUID subjectId)
            throws GovernanceRepositoryException {
        return query(() -> store.openVote(kind, subjectId));
    }

    @Override
    public Optional<Instant> lastFailedAt(VoteKind kind, UUID subjectId)
            throws GovernanceRepositoryException {
        return query(() -> store.lastFailedAt(kind, subjectId));
    }

    @Override
    public GovernanceVote finalizeVote(
            long voteId,
            VoteStatus status,
            VoteFailure failure,
            VoteTerminationReason terminationReason,
            Instant finalizedAt
    ) throws GovernanceRepositoryException {
        return query(() -> store.finalizeVote(
                voteId, status, failure, terminationReason, finalizedAt));
    }

    @Override
    public Optional<RemovalPetition> gatheringRemovalPetition(
            UUID subjectId,
            Instant now
    ) throws GovernanceRepositoryException {
        return query(() -> store.gatheringRemovalPetition(subjectId, now));
    }

    @Override
    public List<RemovalPetition> gatheringRemovalPetitions(Instant now)
            throws GovernanceRepositoryException {
        return query(() -> store.gatheringRemovalPetitions(now));
    }

    @Override
    public RemovalPetition createRemovalPetition(
            UUID subjectId,
            String subjectName,
            Instant createdAt,
            int sponsorsRequired
    ) throws GovernanceRepositoryException {
        return query(() -> store.createRemovalPetition(
                subjectId, subjectName, createdAt, sponsorsRequired));
    }

    @Override
    public List<UUID> removalSponsors(long petitionId)
            throws GovernanceRepositoryException {
        return query(() -> store.removalSponsors(petitionId));
    }

    @Override
    public void removeRemovalSponsor(long petitionId, UUID sponsorId)
            throws GovernanceRepositoryException {
        execute(() -> store.removeRemovalSponsor(petitionId, sponsorId));
    }

    @Override
    public List<GovernanceAction> pendingActions()
            throws GovernanceRepositoryException {
        return query(store::pendingActions);
    }

    @Override
    public void markActionApplied(long actionId, Instant appliedAt)
            throws GovernanceRepositoryException {
        execute(() -> store.markActionApplied(actionId, appliedAt));
    }

    @Override
    public void markActionFailed(long actionId, Instant attemptedAt, String error)
            throws GovernanceRepositoryException {
        execute(() -> store.markActionFailed(actionId, attemptedAt, error));
    }

    @Override
    public void close() throws GovernanceRepositoryException {
        execute(store::close);
    }

    private static void execute(SqlOperation operation)
            throws GovernanceRepositoryException {
        query(() -> {
            operation.run();
            return null;
        });
    }

    private static <T> T query(SqlQuery<T> query)
            throws GovernanceRepositoryException {
        try {
            return query.run();
        } catch (SQLException error) {
            String message = error.getMessage() == null
                    ? "SQLite governance operation failed" : error.getMessage();
            throw new GovernanceRepositoryException(message, error);
        }
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlQuery<T> {
        T run() throws SQLException;
    }
}
