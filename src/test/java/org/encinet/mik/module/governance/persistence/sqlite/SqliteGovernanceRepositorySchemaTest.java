package org.encinet.mik.module.governance.persistence.sqlite;

import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.delivery.GovernanceAction;
import org.encinet.mik.module.governance.delivery.GovernanceActionType;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.removal.RemovalPetition;
import org.encinet.mik.module.governance.removal.RemovalSponsorship;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTally;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteGovernanceRepositorySchemaTest {

    private static final UUID ALEX =
            UUID.fromString("8a22c064-504e-431f-ae7d-81e1ee429866");
    private static final UUID BLAKE =
            UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
    private static final UUID CASEY =
            UUID.fromString("29a46a2f-aa28-40a9-9fa1-f6f0ea74aa1c");
    private static final Instant OPENED_AT = Instant.parse("2026-08-20T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void foreignKeysAndDailyTimeBoundsAreEnforced() throws Exception {
        try (SqliteGovernanceRepository repository = repository("constraints.db")) {
            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.addParticipation(
                    ALEX, LocalDate.parse("2026-08-20"), 1));

            observe(repository, ALEX, "Alex");
            repository.addParticipation(ALEX, LocalDate.parse("2026-08-20"), 86_400);
            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.addParticipation(
                    ALEX, LocalDate.parse("2026-08-20"), 1));
            assertEquals(86_400,
                    repository.participation(ALEX).getFirst().eligibleSeconds());
        }
    }

    @Test
    void onlyOneOpenVotePerKindAndSubjectCanExist() throws Exception {
        try (SqliteGovernanceRepository repository = repository("open-vote.db")) {
            List<PlayerProfile> voters = voters(repository);
            repository.createVote(appointment(), OPENED_AT, voters);

            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.createVote(
                    appointment(), OPENED_AT, voters));
        }
    }

    @Test
    void ballotsCannotChangeAfterVoteFinalization() throws Exception {
        try (SqliteGovernanceRepository repository = repository("closed-ballot.db")) {
            GovernanceVote vote = repository.createVote(
                    appointment(), OPENED_AT, voters(repository));
            repository.castBallot(vote.id(), ALEX, VoteChoice.YES,
                    OPENED_AT.plusSeconds(1));
            repository.castBallot(vote.id(), BLAKE, VoteChoice.YES,
                    OPENED_AT.plusSeconds(1));
            repository.finalizeVote(vote.id(), VoteStatus.PASSED,
                    VoteFailure.NONE, null, vote.closesAt());

            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.castBallot(
                    vote.id(), ALEX, VoteChoice.NO, vote.closesAt().minusSeconds(1)));
        }
    }

    @Test
    void ballotReplacementTimeCannotMoveBackwards() throws Exception {
        try (SqliteGovernanceRepository repository = repository("ballot-time.db")) {
            GovernanceVote vote = repository.createVote(
                    appointment(), OPENED_AT, voters(repository));
            repository.castBallot(vote.id(), ALEX, VoteChoice.YES,
                    OPENED_AT.plusSeconds(2));

            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.castBallot(
                    vote.id(), ALEX, VoteChoice.NO, OPENED_AT.plusSeconds(1)));
            assertEquals(1, repository.vote(vote.id()).orElseThrow().tally().yes());
        }
    }

    @Test
    void openVoteFrozenRollAndBallotSurviveRestart() throws Exception {
        Path database = temporaryDirectory.resolve("open-vote-restart.db");
        long voteId;
        try (SqliteGovernanceRepository repository = open(database)) {
            GovernanceVote vote = repository.createVote(
                    appointment(), OPENED_AT, voters(repository));
            voteId = vote.id();
            repository.castBallot(vote.id(), ALEX, VoteChoice.ABSTAIN,
                    OPENED_AT.plusSeconds(1));
        }

        try (SqliteGovernanceRepository repository = open(database)) {
            GovernanceVote recovered = repository.vote(voteId).orElseThrow();
            assertEquals(VoteStatus.OPEN, recovered.status());
            assertEquals(new VoteTally(0, 0, 1), recovered.tally());
            assertTrue(repository.isVoter(voteId, BLAKE));
            assertEquals(VoteChoice.ABSTAIN,
                    repository.ballotChoice(voteId, ALEX).orElseThrow());
        }
    }

    @Test
    void passedVoteAndActionCommitTogetherAndSurviveRestart() throws Exception {
        Path database = temporaryDirectory.resolve("durable-action.db");
        long actionId;
        try (SqliteGovernanceRepository repository = open(database)) {
            GovernanceVote vote = repository.createVote(
                    appointment(), OPENED_AT, voters(repository));
            repository.castBallot(vote.id(), ALEX, VoteChoice.YES,
                    OPENED_AT.plusSeconds(1));
            repository.castBallot(vote.id(), BLAKE, VoteChoice.YES,
                    OPENED_AT.plusSeconds(1));
            repository.finalizeVote(vote.id(), VoteStatus.PASSED,
                    VoteFailure.NONE, null, vote.closesAt());

            GovernanceAction action = repository.pendingActions().getFirst();
            actionId = action.id();
            assertEquals("VOTE:" + vote.id(), action.actionKey());
            assertEquals(GovernanceActionType.APPOINT_MODERATOR, action.type());
            assertEquals(ALEX, action.playerId());
            assertEquals(vote.id(), action.sourceVoteId());
            assertEquals(vote.id(),
                    repository.pendingVoteAnnouncements().getFirst().id());
        }

        try (SqliteGovernanceRepository repository = open(database)) {
            GovernanceAction recovered = repository.pendingActions().getFirst();
            assertEquals(actionId, recovered.id());
            Instant firstAttempt = recovered.createdAt().plusSeconds(10);
            repository.markActionFailed(actionId, firstAttempt,
                    "LuckPerms temporarily unavailable");
            GovernanceAction failed = repository.pendingActions().getFirst();
            assertEquals(1, failed.attemptCount());
            assertTrue(failed.lastError().contains("temporarily unavailable"));

            repository.markActionApplied(actionId, firstAttempt.plusSeconds(10));
            assertTrue(repository.pendingActions().isEmpty());

            GovernanceVote announcement = repository.pendingVoteAnnouncements().getFirst();
            Instant announcedAt = announcement.finalizedAt().plusSeconds(30);
            repository.markVoteResultAnnounced(announcement.id(), announcedAt);
            assertTrue(repository.pendingVoteAnnouncements().isEmpty());
            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.markVoteResultAnnounced(
                    announcement.id(), announcedAt.plusSeconds(1)));
        }
    }

    @Test
    void banProposalAndActionUseTheSharedDurableVotePipeline() throws Exception {
        Path database = temporaryDirectory.resolve("durable-ban-action.db");
        long voteId;
        VoteProposal.Ban proposal = new VoteProposal.Ban(
                CASEY, "Casey", ALEX, "Alex", Duration.ofDays(30),
                "反复破坏公共设施");
        try (SqliteGovernanceRepository repository = open(database)) {
            List<PlayerProfile> voters = voters(repository);
            observe(repository, CASEY, "Casey");
            GovernanceVote vote = repository.createVote(proposal, OPENED_AT, voters);
            voteId = vote.id();
            repository.castBallot(vote.id(), ALEX, VoteChoice.YES,
                    OPENED_AT.plusSeconds(1));
            repository.castBallot(vote.id(), BLAKE, VoteChoice.YES,
                    OPENED_AT.plusSeconds(1));
            repository.finalizeVote(vote.id(), VoteStatus.PASSED,
                    VoteFailure.NONE, null, vote.closesAt());

            GovernanceAction action = repository.pendingActions().getFirst();
            assertEquals(GovernanceActionType.BAN_PLAYER, action.type());
            assertEquals(proposal, action.proposal());
            assertEquals(CASEY, action.playerId());
        }

        try (SqliteGovernanceRepository repository = open(database)) {
            assertEquals(proposal, repository.vote(voteId).orElseThrow().proposal());
            assertEquals(proposal, repository.pendingActions().getFirst().proposal());
        }
    }

    @Test
    void invalidFinalizationRollsBackVoteAndActionTogether() throws Exception {
        try (SqliteGovernanceRepository repository = repository("finalization-rollback.db")) {
            GovernanceVote vote = repository.createVote(
                    appointment(), OPENED_AT, voters(repository));

            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.finalizeVote(
                    vote.id(), VoteStatus.PASSED, VoteFailure.QUORUM,
                    null, vote.closesAt()));
            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.finalizeVote(
                    vote.id(), VoteStatus.FAILED, VoteFailure.MINIMUM_YES,
                    null, vote.closesAt()));
            assertEquals(VoteStatus.OPEN, repository.vote(vote.id()).orElseThrow().status());
            assertTrue(repository.pendingActions().isEmpty());
            assertTrue(repository.pendingVoteAnnouncements().isEmpty());
        }
    }

    @Test
    void finalRemovalSponsorAndVoteCreationRollbackTogether() throws Exception {
        try (SqliteGovernanceRepository repository = repository("removal-atomicity.db")) {
            observe(repository, ALEX, "Alex");
            observe(repository, BLAKE, "Blake");
            observe(repository, CASEY, "Casey");
            PlayerProfile alex = repository.player(ALEX).orElseThrow();
            PlayerProfile blake = repository.player(BLAKE).orElseThrow();
            PlayerProfile casey = repository.player(CASEY).orElseThrow();
            RemovalPetition petition = repository.createRemovalPetition(
                    ALEX, "Alex", OPENED_AT, 2);

            RemovalSponsorship first = repository.sponsorRemoval(
                    petition.id(), ALEX, "Alex", BLAKE, OPENED_AT.plusSeconds(1), 2,
                    List.of(alex, blake, casey));
            assertTrue(first.newlyAdded());
            assertTrue(first.startedVote() == null);

            assertThrows(GovernanceRepositoryException.class,
                    () -> repository.sponsorRemoval(
                    petition.id(), ALEX, "Alex", CASEY, OPENED_AT.plusSeconds(2), 2,
                    List.of(alex, alex)));
            RemovalPetition recovered = repository.gatheringRemovalPetition(
                    ALEX, OPENED_AT.plusSeconds(3)).orElseThrow();
            assertEquals(1, recovered.sponsorCount());
            assertEquals(List.of(BLAKE), repository.removalSponsors(petition.id()));
            assertTrue(repository.openVotes().isEmpty());
        }
    }

    @Test
    void expiredRemovalPetitionDisappearsAtItsDeadline() throws Exception {
        try (SqliteGovernanceRepository repository = repository("petition-expiry.db")) {
            observe(repository, ALEX, "Alex");
            RemovalPetition petition = repository.createRemovalPetition(
                    ALEX, "Alex", OPENED_AT, 2);

            assertEquals(1, repository.gatheringRemovalPetitions(
                    petition.expiresAt().minusMillis(1)).size());
            assertTrue(repository.gatheringRemovalPetitions(petition.expiresAt()).isEmpty());
            assertTrue(repository.gatheringRemovalPetition(
                    ALEX, petition.expiresAt()).isEmpty());
        }
    }

    @Test
    void unversionedExistingGovernanceTablesAreNotSilentlyUpgraded() throws Exception {
        Path database = temporaryDirectory.resolve("unversioned.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE governance_partial(id INTEGER PRIMARY KEY)");
        }

        SqliteGovernanceRepository repository = new SqliteGovernanceRepository(database.toFile());
        GovernanceRepositoryException error = assertThrows(
                GovernanceRepositoryException.class, repository::open);
        assertTrue(error.getMessage().contains("automatic schema upgrades"));
    }

    private SqliteGovernanceRepository repository(String fileName)
            throws GovernanceRepositoryException {
        return open(temporaryDirectory.resolve(fileName));
    }

    private static SqliteGovernanceRepository open(Path database)
            throws GovernanceRepositoryException {
        SqliteGovernanceRepository repository = new SqliteGovernanceRepository(database.toFile());
        repository.open();
        return repository;
    }

    private static List<PlayerProfile> voters(SqliteGovernanceRepository repository)
            throws GovernanceRepositoryException {
        observe(repository, ALEX, "Alex");
        observe(repository, BLAKE, "Blake");
        return List.of(repository.player(ALEX).orElseThrow(),
                repository.player(BLAKE).orElseThrow());
    }

    private static VoteProposal appointment() {
        return new VoteProposal.Appointment(ALEX, "Alex");
    }

    private static void observe(SqliteGovernanceRepository repository, UUID playerId, String name)
            throws GovernanceRepositoryException {
        repository.observePlayer(playerId, name,
                OPENED_AT.minusSeconds(10_000), OPENED_AT.minusSeconds(1));
    }
}
