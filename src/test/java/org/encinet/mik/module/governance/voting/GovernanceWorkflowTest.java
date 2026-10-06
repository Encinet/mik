package org.encinet.mik.module.governance.voting;

import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.GovernanceRepositoryException;
import org.encinet.mik.module.governance.delivery.DeliveryService;
import org.encinet.mik.module.governance.delivery.GovernanceAction;
import org.encinet.mik.module.governance.delivery.GovernanceActionType;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.participation.DailyParticipation;
import org.encinet.mik.module.governance.membership.participation.ParticipationPolicy;
import org.encinet.mik.module.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.persistence.sqlite.SqliteGovernanceRepository;
import org.encinet.mik.module.governance.removal.RemovalSponsorship;
import org.encinet.mik.module.governance.removal.RemovalService;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteFailure;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTally;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.module.governance.voting.model.VotingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernanceWorkflowTest {

    private static final UUID PLAYER = UUID.fromString("8a22c064-504e-431f-ae7d-81e1ee429866");

    @TempDir
    Path temporaryDirectory;

    @Test
    void effectiveTimeIsPersistedAcrossUtcPlusEightMidnight() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T16:01:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            membership.observeLogin(PLAYER, "Alex", clock.instant().minus(Duration.ofDays(5)), false);
            membership.recordEligibleInterval(PLAYER,
                    Instant.parse("2026-08-22T15:59:30Z"),
                    Instant.parse("2026-08-22T16:00:30Z"));

            ParticipationSummary summary = membership.participation(PLAYER);
            assertEquals(60, summary.lifetimeSeconds());
            assertEquals(2, summary.lifetimePlayDays());
        }
    }

    @Test
    void participationDayNeedsThirtyMinutesButAllEffectiveSecondsCount() {
        List<DailyParticipation> days = List.of(
                new DailyParticipation(LocalDate.parse("2026-08-22"), 1_800),
                new DailyParticipation(LocalDate.parse("2026-08-21"), 1_799),
                new DailyParticipation(LocalDate.parse("2026-07-01"), 7_200),
                new DailyParticipation(LocalDate.parse("2026-06-01"), 8_000));

        ParticipationSummary summary = ParticipationPolicy.summarize(
                days, LocalDate.parse("2026-08-22"));

        assertEquals(18_799, summary.lifetimeSeconds());
        assertEquals(4, summary.lifetimePlayDays());
        assertEquals(10_799, summary.secondsLast60Days());
        assertEquals(2, summary.participationDaysLast60());
        assertEquals(1, summary.participationDaysLast30());
    }

    @Test
    void expiredPauseStopsBlockingWithoutBeingDeleted() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            membership.observeLogin(PLAYER, "Alex", clock.instant().minus(Duration.ofDays(4)), false);
            membership.recordEligibleInterval(PLAYER,
                    clock.instant().minus(Duration.ofHours(9)), clock.instant());
            // Add a second natural play day.
            membership.recordEligibleInterval(PLAYER,
                    clock.instant().minus(Duration.ofDays(1)),
                    clock.instant().minus(Duration.ofDays(1)).plusSeconds(1));
            membership.pausePromotion(PLAYER, "written reason", clock.instant().plusSeconds(60),
                    "appeal@example.invalid", "Custodian");

            assertFalse(membership.promotionEligibility(PLAYER).eligible());
            clock.advance(Duration.ofSeconds(61));
            assertTrue(membership.promotionEligibility(PLAYER).eligible());
        }
    }

    @Test
    void observingAnExistingMemberAgainDoesNotRewriteMemberSince() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        Instant firstJoined = clock.instant().minus(Duration.ofDays(100));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            membership.observeLogin(PLAYER, "Alex", firstJoined, true);
            clock.advance(Duration.ofDays(1));
            membership.observeLogin(PLAYER, "Alex", firstJoined, true);

            assertEquals(firstJoined, membership.player(PLAYER).orElseThrow().memberSince());
        }
    }

    @Test
    void memberCannotReceiveNewcomerPromotionPause() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            membership.observeLogin(PLAYER, "Alex", clock.instant().minus(Duration.ofDays(100)), true);

            GovernanceException error = assertThrows(GovernanceException.class,
                    () -> membership.pausePromotion(PLAYER, "reason",
                            clock.instant().plus(Duration.ofDays(1)), "appeal", "custodian-uuid"));
            assertEquals(GovernanceException.Code.PAUSE_ALREADY_MEMBER, error.code());
        }
    }

    @Test
    void frozenVoterRollDoesNotChangeAfterVoteStarts() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            PlayerProfile candidate = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);

            GovernanceVote vote = voting.startVote(appointment(candidate));
            UUID lateId = UUID.fromString("29a46a2f-aa28-40a9-9fa1-f6f0ea74aa1c");
            activeMember(membership, clock, lateId, "Casey", 2);

            assertEquals(2, vote.eligibleVoters());
            try {
                voting.castBallot(vote.id(), lateId, VoteChoice.YES);
            } catch (GovernanceException expected) {
                assertTrue(expected.getMessage().contains("冻结选民名单"));
            }
            assertEquals(0, voting.openVotes().getFirst().tally().participating());
        }
    }

    @Test
    void finalizationUsesAnonymousAggregateOnly() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            PlayerProfile candidate = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);
            GovernanceVote vote = voting.startVote(appointment(candidate));
            voting.castBallot(vote.id(), PLAYER, VoteChoice.YES);
            voting.castBallot(vote.id(), secondId, VoteChoice.YES);

            clock.advance(VotingPolicy.VOTE_DURATION);
            GovernanceVote finalized = voting.finalizeDueVotes().getFirst();

            assertEquals(VoteStatus.PASSED, finalized.status());
            assertEquals(new VoteTally(2, 0, 0), finalized.tally());
            GovernanceAction action = delivery.pendingActions().getFirst();
            assertEquals(GovernanceActionType.APPOINT_MODERATOR, action.type());
            assertEquals(finalized.id(), action.sourceVoteId());
            assertEquals(VoteChoice.YES,
                    voting.ballotChoice(finalized.id(), PLAYER).orElseThrow());
            assertTrue(voting.isVoter(finalized.id(), PLAYER));
        }
    }

    @Test
    void withdrawalReasonAndResultRemainQueryable() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            PlayerProfile candidate = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);
            GovernanceVote vote = voting.startVote(appointment(candidate));
            voting.castBallot(vote.id(), PLAYER, VoteChoice.ABSTAIN);

            GovernanceVote terminated = voting.terminateVote(
                    vote.id(), VoteTerminationReason.CANDIDATE_WITHDREW);

            assertEquals(VoteStatus.TERMINATED, terminated.status());
            assertEquals(VoteTerminationReason.CANDIDATE_WITHDREW,
                    terminated.terminationReason());
            assertEquals(terminated.id(), voting.recentVotes(10).getFirst().id());
            assertEquals(terminated.id(), delivery.pendingVoteAnnouncements().getFirst().id());
        }
    }

    @Test
    void quorumFailureEnforcesExactlyThreeDayRetryCooldown() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            PlayerProfile candidate = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);
            GovernanceVote vote = voting.startVote(appointment(candidate));

            clock.advance(VotingPolicy.VOTE_DURATION);
            GovernanceVote failed = voting.finalizeDueVotes().getFirst();
            assertEquals(VoteFailure.QUORUM, failed.failure());
            assertTrue(delivery.pendingActions().isEmpty());
            assertEquals(failed.id(), delivery.pendingVoteAnnouncements().getFirst().id());
            assertThrows(GovernanceException.class,
                    () -> voting.startVote(appointment(candidate)));

            clock.advance(VotingPolicy.FAILED_VOTE_COOLDOWN);
            assertEquals(VoteStatus.OPEN,
                    voting.startVote(appointment(candidate)).status());
        }
    }

    @Test
    void candidateCannotWithdrawAfterVotingDeadline() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            PlayerProfile candidate = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);
            GovernanceVote vote = voting.startVote(appointment(candidate));

            clock.advance(VotingPolicy.VOTE_DURATION);
            GovernanceException error = assertThrows(GovernanceException.class,
                    () -> voting.terminateVote(
                            vote.id(), VoteTerminationReason.CANDIDATE_WITHDREW));
            assertTrue(error.getMessage().contains("截止时间"));
            assertEquals(VoteStatus.FAILED, voting.finalizeDueVotes().getFirst().status());
        }
    }

    @Test
    void removalVoteStartsOnlyAfterRequiredDistinctSponsors() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            PlayerProfile subject = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);
            UUID thirdId = UUID.fromString("29a46a2f-aa28-40a9-9fa1-f6f0ea74aa1c");
            activeMember(membership, clock, thirdId, "Casey", 2);

            RemovalSponsorship first = context.removal().sponsorRemoval(subject, secondId);
            RemovalSponsorship duplicate = context.removal().sponsorRemoval(subject, secondId);
            RemovalSponsorship second = context.removal().sponsorRemoval(subject, thirdId);

            assertEquals(1, first.petition().sponsorCount());
            assertEquals(1, duplicate.petition().sponsorCount());
            assertTrue(first.newlyAdded());
            assertFalse(duplicate.newlyAdded());
            assertTrue(second.newlyAdded());
            assertTrue(first.startedVote() == null);
            assertEquals(VoteKind.REMOVAL, second.startedVote().kind());
            assertEquals(3, second.startedVote().eligibleVoters());
            assertEquals(second.startedVote().id(), second.petition().voteId());
            assertTrue(voting.isVoter(second.startedVote().id(), subject.playerId()));
        }
    }

    @Test
    void banProposalUsesTheSameElectorateBallotsAndThresholds() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            DeliveryService delivery = context.delivery();
            PlayerProfile proposer = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);
            UUID targetId = UUID.fromString("29a46a2f-aa28-40a9-9fa1-f6f0ea74aa1c");
            membership.observeLogin(targetId, "Casey",
                    clock.instant().minus(Duration.ofDays(2)), false);
            VoteProposal.Ban proposal = new VoteProposal.Ban(
                    targetId, "Casey", proposer.playerId(), proposer.playerName(),
                    Duration.ofDays(30), "反复破坏公共设施");

            GovernanceVote vote = voting.startVote(proposal);
            voting.castBallot(vote.id(), proposer.playerId(), VoteChoice.YES);
            voting.castBallot(vote.id(), secondId, VoteChoice.YES);
            clock.advance(VotingPolicy.VOTE_DURATION);
            GovernanceVote finalized = voting.finalizeDueVotes().getFirst();

            assertEquals(VoteKind.BAN, finalized.kind());
            assertEquals(proposal, finalized.proposal());
            assertEquals(VoteStatus.PASSED, finalized.status());
            GovernanceAction action = delivery.pendingActions().getFirst();
            assertEquals(GovernanceActionType.BAN_PLAYER, action.type());
            assertEquals(proposal, action.proposal());
        }
    }

    private PlayerProfile activeMember(
            MembershipService membership,
            MutableClock clock,
            UUID playerId,
            String name,
            int dayOffset
    ) throws Exception {
        Instant firstJoined = clock.instant().minus(Duration.ofDays(100));
        membership.observeLogin(playerId, name, firstJoined, true);
        for (int day = 0; day < 6; day++) {
            Instant start = clock.instant().minus(Duration.ofDays(day + dayOffset))
                    .minus(Duration.ofHours(2));
            membership.recordEligibleInterval(playerId, start, start.plus(Duration.ofHours(1)));
        }
        return membership.player(playerId).orElseThrow();
    }

    private static VoteProposal appointment(PlayerProfile candidate) {
        return new VoteProposal.Appointment(
                candidate.playerId(), candidate.playerName());
    }

    private TestContext context(MutableClock clock) throws Exception {
        SqliteGovernanceRepository database = new SqliteGovernanceRepository(
                temporaryDirectory.resolve(UUID.randomUUID() + ".db").toFile());
        database.open();
        MembershipService membership = new MembershipService(
                database, clock, (uuid, name) -> false, (uuid, name) -> true);
        VotingService voting = new VotingService(database, membership, clock);
        RemovalService removal = new RemovalService(database, database, membership, clock);
        DeliveryService delivery = new DeliveryService(database);
        return new TestContext(database, membership, voting, removal, delivery);
    }

    private record TestContext(
            SqliteGovernanceRepository database,
            MembershipService membership,
            VotingService voting,
            RemovalService removal,
            DeliveryService delivery
    ) implements AutoCloseable {
        @Override
        public void close() throws GovernanceRepositoryException {
            database.close();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
