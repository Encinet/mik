package org.encinet.mik.feature.community.governance.gateway;

import org.encinet.mik.feature.community.governance.membership.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.PlayerProfile;
import org.encinet.mik.feature.community.governance.membership.PromotionEligibility;
import org.encinet.mik.feature.community.governance.membership.PromotionPause;
import org.encinet.mik.feature.community.governance.removal.RemovalPetition;
import org.encinet.mik.feature.community.governance.voting.GovernanceVote;
import org.encinet.mik.feature.community.governance.voting.VoteChoice;
import org.encinet.mik.feature.community.governance.voting.VoteFailure;
import org.encinet.mik.feature.community.governance.voting.VoteProposal;
import org.encinet.mik.feature.community.governance.voting.VoteStatus;
import org.encinet.mik.feature.community.governance.voting.VoteTally;
import org.encinet.mik.feature.community.governance.voting.VoteTerminationReason;
import org.encinet.mik.protocol.GovernanceBallot;
import org.encinet.mik.protocol.GovernancePetitionView;
import org.encinet.mik.protocol.GovernanceProfile;
import org.encinet.mik.protocol.GovernancePromotionPause;
import org.encinet.mik.protocol.GovernanceVoteView;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class GovernanceViews {
    private GovernanceViews() { }

    public static PlayerProfile player(GovernanceProfile profile) {
        var player = profile.getPlayer();
        return new PlayerProfile(UUID.fromString(player.getId()), player.getName(),
                instant(profile.hasFirstLoginMs(), profile.getFirstLoginMs()),
                instant(profile.hasMemberSinceMs(), profile.getMemberSinceMs()),
                instant(profile.hasLastLoginMs(), profile.getLastLoginMs()));
    }

    public static ParticipationSummary participation(GovernanceProfile profile) {
        return new ParticipationSummary(profile.getLifetimeSeconds(), profile.getLifetimeDays(),
                profile.getRecentSeconds(), profile.getActiveDays(), profile.getRecentDays());
    }

    public static ActiveMemberEligibility active(GovernanceProfile profile) {
        return new ActiveMemberEligibility(profile.hasMemberSinceMs(), profile.getMemberAgeMet(),
                profile.getRecentPlaytimeMet(), profile.getActiveDaysMet(),
                profile.getRecentDaysMet(), profile.hasBanned() && !profile.getBanned(), profile.getActiveMember(),
                Duration.ofSeconds(profile.getRequiredRecentSeconds()));
    }

    public static PromotionEligibility promotion(GovernanceProfile profile) {
        return new PromotionEligibility(profile.getLoginAgeMet(), profile.getPromotionPlaytimeMet(),
                profile.getPromotionDaysMet(), profile.hasBanned() && !profile.getBanned(), !profile.hasPause(),
                profile.hasPause() ? pause(profile.getPause()) : null);
    }

    public static PromotionPause pause(GovernancePromotionPause pause) {
        return new PromotionPause(pause.getId(), pause.getReason(), Instant.ofEpochMilli(pause.getCreatedMs()),
                Instant.ofEpochMilli(pause.getEndsMs()), pause.getAppeal(), pause.getActor());
    }

    public static GovernanceVote vote(GovernanceVoteView view) {
        var subject = view.getSubject();
        var id = UUID.fromString(subject.getId());
        VoteProposal proposal = view.hasTenureId() ? new VoteProposal.Removal(id, subject.getName())
                : new VoteProposal.Appointment(id, subject.getName());
        var termination = view.getTermination() == GovernanceVoteView.Termination.TERMINATION_NONE ? null
                : VoteTerminationReason.valueOf(view.getTermination().name());
        var failure = view.getFailure() == GovernanceVoteView.Failure.FAILURE_NONE ? VoteFailure.NONE
                : VoteFailure.valueOf(view.getFailure().name());
        return new GovernanceVote(view.getId(), proposal, Instant.ofEpochMilli(view.getOpensMs()),
                Instant.ofEpochMilli(view.getClosesMs()), VoteStatus.valueOf(view.getStatus().name()),
                view.getEligibleVoters(), view.getQuorum(), new VoteTally(view.getYes(), view.getNo(),
                        view.getAbstain(), view.getParticipating()), instant(view.hasFinalizedMs(), view.getFinalizedMs()),
                failure, termination);
    }

    public static RemovalPetition petition(GovernancePetitionView view) {
        return new RemovalPetition(view.getId(), UUID.fromString(view.getSubject().getId()), view.getSubject().getName(),
                Instant.ofEpochMilli(view.getCreatedMs()), Instant.ofEpochMilli(view.getExpiresMs()),
                view.getSponsorsRequired(), view.getSponsorCount(), view.hasVoteId() ? view.getVoteId() : null);
    }

    public static VoteChoice choice(GovernanceBallot ballot) {
        return switch (ballot) {
            case YES -> VoteChoice.YES;
            case NO -> VoteChoice.NO;
            case ABSTAIN -> VoteChoice.ABSTAIN;
            default -> null;
        };
    }

    private static Instant instant(boolean present, long millis) {
        return present ? Instant.ofEpochMilli(millis) : null;
    }
}
