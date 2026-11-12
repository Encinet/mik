package org.encinet.mik.feature.community.governance.voting;

import org.encinet.mik.feature.community.governance.GovernanceException;
import org.encinet.mik.feature.community.governance.GovernanceRepositoryException;
import org.encinet.mik.feature.community.governance.delivery.DeliveryService;
import org.encinet.mik.feature.community.governance.delivery.GovernanceAction;
import org.encinet.mik.feature.community.governance.delivery.GovernanceActionType;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.membership.participation.DailyParticipation;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationPolicy;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.profile.PlayerProfile;
import org.encinet.mik.feature.community.governance.persistence.sqlite.SqliteGovernanceRepository;
import org.encinet.mik.feature.community.governance.removal.RemovalSponsorship;
import org.encinet.mik.feature.community.governance.removal.RemovalService;
import org.encinet.mik.feature.community.governance.voting.model.GovernanceVote;
import org.encinet.mik.feature.community.governance.voting.model.VoteChoice;
import org.encinet.mik.feature.community.governance.voting.model.VoteFailure;
import org.encinet.mik.feature.community.governance.voting.model.VoteKind;
import org.encinet.mik.feature.community.governance.voting.model.VoteProposal;
import org.encinet.mik.feature.community.governance.voting.model.VoteStatus;
import org.encinet.mik.feature.community.governance.voting.model.VoteTally;
import org.encinet.mik.feature.community.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.feature.community.governance.voting.model.VotingPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @ParameterizedTest
    @CsvSource({
            "2026-12-31T15:59:59Z, 6, 12",
            "2026-03-15T15:59:59Z, 12, 6",
            "2026-06-24T15:59:59Z, 6, 12",
            "2026-09-15T15:59:59Z, 12, 6"
    })
    void activeMembershipAndVoterRollChangeAtUtcPlusEightMidnight(
            String beforeMidnight, int beforeHours, int afterHours
    ) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse(beforeMidnight));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            membership.observeLogin(PLAYER, "Alex", clock.instant().minus(Duration.ofDays(100)), true);
            for (int day = 1; day <= 6; day++) {
                Instant start = clock.instant().minus(Duration.ofDays(day)).minus(Duration.ofHours(6));
                membership.recordEligibleInterval(PLAYER, start, start.plus(Duration.ofHours(1)));
            }

            var before = membership.activeMemberEligibility(PLAYER);
            assertEquals(Duration.ofHours(beforeHours), before.requiredPlaytime());
            assertEquals(beforeHours == 6, before.eligible());
            assertEquals(beforeHours == 6 ? 1 : 0, membership.activeMembers().size());

            clock.advance(Duration.ofSeconds(1));

            var after = membership.activeMemberEligibility(PLAYER);
            assertEquals(Duration.ofHours(afterHours), after.requiredPlaytime());
            assertEquals(afterHours == 6, after.eligible());
            assertEquals(afterHours == 6 ? 1 : 0, membership.activeMembers().size());
        }
    }

    @Test
    void effectiveTimeIsPersistedAcrossUtcPlusEightMidnight() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T16:01:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
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
            PlayerProfile candidate = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);

            GovernanceVote vote = voting.startVote(appointment(candidate));
            UUID lateId = UUID.fromString("29a46a2f-aa28-40a9-9fa1-f6f0ea74aa1c");
            activeMember(membership, clock, lateId, "Casey", 2);

            assertEquals(2, vote.eligibleVoters());
            assertEquals(GovernanceException.Code.NOT_VOTER,
                    assertThrows(GovernanceException.class,
                            () -> voting.castBallot(vote.id(), lateId, VoteChoice.YES)).code());
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
            voting.startVote(appointment(candidate));

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
            PlayerProfile candidate = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);
            GovernanceVote vote = voting.startVote(appointment(candidate));

            clock.advance(VotingPolicy.VOTE_DURATION);
            GovernanceException error = assertThrows(GovernanceException.class,
                    () -> voting.terminateVote(
                            vote.id(), VoteTerminationReason.CANDIDATE_WITHDREW));
            assertEquals(GovernanceException.Code.VOTE_TERMINATION_CLOSED, error.code());
            assertEquals(VoteStatus.FAILED, voting.finalizeDueVotes().getFirst().status());
        }
    }

    @Test
    void removalVoteStartsOnlyAfterRequiredDistinctSponsors() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            MembershipService membership = context.membership();
            VotingService voting = context.voting();
            PlayerProfile subject = activeMember(membership, clock, PLAYER, "Alex", 0);
            UUID secondId = UUID.fromString("870a985e-2f5f-49d3-8611-cf564db5ba20");
            activeMember(membership, clock, secondId, "Blake", 1);
            UUID thirdId = UUID.fromString("29a46a2f-aa28-40a9-9fa1-f6f0ea74aa1c");
            activeMember(membership, clock, thirdId, "Casey", 2);

            RemovalSponsorship first = context.removal().sponsorRemoval(subject, secondId);
            assertTrue(context.removal().hasSponsored(first.petition().id(), secondId));
            assertFalse(context.removal().hasSponsored(first.petition().id(), thirdId));
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
            membership.recordEligibleInterval(playerId, start, start.plus(Duration.ofHours(2)));
        }
        return membership.player(playerId).orElseThrow();
    }

    private static VoteProposal appointment(PlayerProfile candidate) {
        return new VoteProposal.Appointment(
                candidate.playerId(), candidate.playerName());
    }

    @Test
    void completedHistoryPagesAndSubjectFiltersDoNotLoseOlderRecordsOrIncludeLiveVotes() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-22T12:00:00Z"));
        try (TestContext context = context(clock)) {
            var first = activeMember(context.membership(), clock, PLAYER, "Alex", 0);
            UUID otherId = new UUID(0, 2);
            var second = activeMember(context.membership(), clock, otherId, "Blake", 1);
            for (int index = 0; index < 12; index++) {
                var subject = index % 2 == 0 ? first : second;
                var vote = context.voting().startVote(appointment(subject));
                context.voting().castBallot(vote.id(), PLAYER, VoteChoice.ABSTAIN);
                context.voting().terminateVote(vote.id(), VoteTerminationReason.CANDIDATE_WITHDREW);
                clock.advance(Duration.ofSeconds(1));
            }
            var live = context.voting().startVote(appointment(first));
            var latest = context.voting().history(null, 3, 0);
            assertEquals(12, latest.total());
            assertEquals(List.of(12L, 11L, 10L), latest.votes().stream().map(GovernanceVote::id).toList());
            var older = context.voting().history(otherId, 3, 3);
            assertEquals(6, older.total());
            assertEquals(List.of(6L, 4L, 2L), older.votes().stream().map(GovernanceVote::id).toList());
            assertTrue(older.votes().stream().allMatch(vote -> vote.subjectId().equals(otherId)));
            assertEquals(older, context.voting().history(otherId, 3, 999));
            assertTrue(context.voting().history(new UUID(0, 999), 5, 0).votes().isEmpty());
            assertFalse(latest.votes().stream().anyMatch(vote -> vote.id() == live.id()));
            assertThrows(IllegalArgumentException.class, () -> context.voting().history(null, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> context.voting().history(null, 3, 1));
        }
    }

    @Test
    void joiningAnExpiredPetitionNeverRecreatesItOrSubstitutesANewerPetition() throws Exception {
        var clock = new MutableClock(Instant.parse("2026-11-01T12:00:00Z"));
        try (var context = context(clock)) {
            var subject = activeMember(context.membership(), clock, PLAYER, "Alex", 0);
            UUID sponsor = new UUID(0, 2), other = new UUID(0, 3);
            activeMember(context.membership(), clock, sponsor, "Steve", 0);
            activeMember(context.membership(), clock, other, "Blake", 0);
            var original = context.removal().sponsorRemoval(subject, sponsor).petition();
            clock.advance(Duration.between(clock.instant(), original.expiresAt()).plusSeconds(1));
            assertEquals(GovernanceException.Code.REMOVAL_PETITION_CLOSED,
                    assertThrows(GovernanceException.class, () -> context.removal().joinPetition(subject, other, original.id())).code());
            assertTrue(context.removal().gatheringPetitions().isEmpty(), "an expired control must not create a new petition");
            var next = context.removal().sponsorRemoval(subject, sponsor).petition();
            assertTrue(next.id() != original.id());
            assertEquals(GovernanceException.Code.REMOVAL_PETITION_CLOSED,
                    assertThrows(GovernanceException.class, () -> context.removal().joinPetition(subject, other, original.id())).code());
            assertEquals(1, context.removal().gatheringPetitions().getFirst().sponsorCount());
            var started = context.removal().joinPetition(subject, other, next.id());
            assertTrue(started.startedVote() != null);
            assertEquals(next.id(), started.petition().id());
            assertTrue(context.removal().gatheringPetitions().isEmpty());
        }
    }

    private TestContext context(MutableClock clock) throws Exception {
        SqliteGovernanceRepository database = new SqliteGovernanceRepository(
                temporaryDirectory.resolve(UUID.randomUUID() + ".db").toFile());
        database.open();
        MembershipService membership = new MembershipService(
                database, clock, (_, _) -> false, (_, _) -> true);
        VotingService voting = new VotingService(database, membership, clock, new org.encinet.mik.feature.player.security.EstablishedPasswordAccounts());
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
