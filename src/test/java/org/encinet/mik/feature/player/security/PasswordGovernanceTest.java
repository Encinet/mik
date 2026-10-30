package org.encinet.mik.feature.player.security;

import org.encinet.mik.feature.community.governance.GovernanceException;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.persistence.sqlite.SqliteGovernanceRepository;
import org.encinet.mik.feature.community.governance.voting.VotingService;
import org.encinet.mik.feature.community.governance.voting.model.VoteChoice;
import org.encinet.mik.feature.community.governance.voting.model.VoteProposal;
import org.encinet.mik.feature.community.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.feature.community.governance.voting.model.VoteStatus;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PasswordGovernanceTest {
    @TempDir Path directory;
    private static final String PASSWORD = "silver river lantern 8642";
    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    @Test void nominationNeedsCredentialsAndWithdrawalReleasesRetention() throws Exception {
        try (var fixture = new Fixture()) {
            assertEquals(GovernanceException.Code.PASSWORD_REQUIRED,
                    assertThrows(GovernanceException.class, () -> fixture.voting.startVote(fixture.proposal())).code());
            assertTrue(fixture.database.openVotes().isEmpty());
            fixture.setPassword();
            var vote = fixture.voting.startVote(fixture.proposal());
            assertTrue(fixture.database.requiresPassword(fixture.candidate));
            assertEquals(PasswordException.Code.REQUIRED_APPLICATION, assertThrows(PasswordException.class,
                    () -> fixture.passwords.remove(fixture.candidate, fixture.passwords.credential(fixture.candidate), PASSWORD, () -> PlayerRole.MEMBER, null)).code());
            fixture.voting.terminateVote(vote.id(), VoteTerminationReason.CANDIDATE_WITHDREW);
            assertFalse(fixture.database.requiresPassword(fixture.candidate));
            fixture.passwords.remove(fixture.candidate, fixture.passwords.credential(fixture.candidate), PASSWORD, () -> PlayerRole.MEMBER, null);
            assertFalse(fixture.passwords.hasPassword(fixture.candidate));
        }
    }

    @Test void retentionSurvivesPassedVoteUntilTheDurableAppointmentHasBeenDelivered() throws Exception {
        try (var fixture = new Fixture()) {
            fixture.setPassword();
            var vote = fixture.voting.startVote(fixture.proposal());
            fixture.voting.castBallot(vote.id(), fixture.candidate, VoteChoice.YES);
            fixture.voting.castBallot(vote.id(), fixture.other, VoteChoice.YES);
            fixture.database.finalizeVote(vote.id(), VoteStatus.PASSED,
                    org.encinet.mik.feature.community.governance.voting.model.VoteFailure.NONE, null, vote.closesAt());
            assertTrue(fixture.database.requiresPassword(fixture.candidate));
            var action = fixture.database.pendingActions().getFirst();
            fixture.database.markActionApplied(action.id(), vote.closesAt());
            assertFalse(fixture.database.requiresPassword(fixture.candidate));
            // After delivery, the actual staff role continues to prohibit removal.
            assertEquals(PasswordException.Code.KEEP_ROLE, assertThrows(PasswordException.class,
                    () -> fixture.passwords.remove(fixture.candidate, fixture.passwords.credential(fixture.candidate), PASSWORD, () -> PlayerRole.MODERATOR, null)).code());
        }
    }

    private final class Fixture implements AutoCloseable {
        final UUID candidate = UUID.randomUUID();
        final UUID other = UUID.randomUUID();
        final SqliteGovernanceRepository database = new SqliteGovernanceRepository(directory.resolve(UUID.randomUUID() + ".db").toFile());
        final PasswordService passwords = new PasswordService(directory.resolve(UUID.randomUUID() + ".db"));
        final VotingService voting;
        Fixture() throws Exception {
            database.open(); passwords.open(); passwords.retention(database::requiresPassword);
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            var membership = new MembershipService(database, clock, (id, name) -> false, (id, name) -> true);
            for (UUID id : new UUID[] {candidate, other}) {
                membership.observeLogin(id, id.equals(candidate) ? "Alex" : "Steve", NOW.minus(Duration.ofDays(100)), true);
                for (int day = 0; day < 6; day++) {
                    Instant start = NOW.minus(Duration.ofDays(day)).minus(Duration.ofHours(2));
                    membership.recordEligibleInterval(id, start, start.plus(Duration.ofHours(1)));
                }
            }
            voting = new VotingService(database, membership, clock, passwords);
        }
        VoteProposal proposal() { return new VoteProposal.Appointment(candidate, "Alex"); }
        void setPassword() { passwords.set(candidate, "Alex", passwords.credential(candidate), PASSWORD, PASSWORD, () -> PlayerRole.MEMBER, null); }
        @Override public void close() throws Exception { passwords.close(); database.close(); }
    }
}
