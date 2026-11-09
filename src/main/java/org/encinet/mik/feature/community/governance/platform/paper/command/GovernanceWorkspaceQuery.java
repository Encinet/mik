package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import org.encinet.mik.feature.community.governance.GovernanceException;
import org.encinet.mik.feature.community.governance.delivery.DeliveryService;
import org.encinet.mik.feature.community.governance.delivery.GovernanceActionType;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.removal.RemovalService;
import org.encinet.mik.feature.community.governance.roster.StaffMember;
import org.encinet.mik.feature.community.governance.voting.VotingService;
import org.encinet.mik.feature.community.governance.voting.VoteHistory;
import org.encinet.mik.feature.community.governance.voting.model.GovernanceVote;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceData.*;

/** Storage-only reads, executed on the governance executor using immutable request arguments. */
final class GovernanceWorkspaceQuery {
    private final MembershipService membership;
    private final VotingService voting;
    private final RemovalService removal;
    private final DeliveryService delivery;

    GovernanceWorkspaceQuery(MembershipService membership, VotingService voting, RemovalService removal, DeliveryService delivery) {
        this.membership = membership; this.voting = voting; this.removal = removal; this.delivery = delivery;
    }

    GovernanceWorkspaceData load(GovernanceCommandContext.KnownPlayer viewer, List<StaffMember> staff,
                                UUID subjectFilter, OptionalInt historyAnchor, UUID relatedSubject) throws GovernanceException {
        if (historyAnchor.isPresent() && historyAnchor.getAsInt() < 0) throw new IllegalArgumentException("Negative history source index");
        var open = ballots(voting.openVotes(), viewer.playerId());
        var petitions = petitions(viewer.playerId());
        int batch = GovernanceMatterWindow.HISTORY_BATCH_SIZE;
        var history = historyAnchor.isPresent() ? voting.history(subjectFilter,batch,historyAnchor.getAsInt() / batch * batch)
                : new VoteHistory(List.of(),0,0);
        var pending = new HashSet<Long>();
        boolean awaitingAppointment = false;
        for (var action : delivery.pendingActions()) {
            pending.add(action.sourceVoteId());
            if (action.type() == GovernanceActionType.APPOINT_MODERATOR
                    && action.playerId().equals(viewer.playerId())) awaitingAppointment = true;
        }
        return new GovernanceWorkspaceData(staff, open, petitions, ballots(history.votes(), viewer.playerId()), history.total(), history.offset(),
                relatedSubject == null ? List.of() : voting.history(relatedSubject, 2, 0).votes(),
                viewer.member() ? membership.activeMemberEligibility(viewer.playerId()) : null,
                membership.participation(viewer.playerId()), pending, awaitingAppointment);
    }

    GovernanceActivityData activity(GovernanceCommandContext.KnownPlayer viewer, java.time.Instant now) throws GovernanceException {
        boolean active = viewer.member() && membership.activeMemberEligibility(viewer.playerId()).eligible();
        var matters = java.util.stream.Stream.concat(
                ballots(voting.openVotes(), viewer.playerId()).stream().filter(card -> card.vote().isOpenAt(now)).map(GovernanceMatter::vote),
                petitions(viewer.playerId()).stream().filter(card -> now.isBefore(card.petition().expiresAt())).map(GovernanceMatter::petition))
                .sorted(Comparator.comparing((GovernanceMatter matter) -> !matter.available(viewer.playerId(), active) || matter.participated())
                        .thenComparing(GovernanceMatter::deadline).thenComparing(GovernanceMatter::id)).toList();
        return new GovernanceActivityData(matters, active);
    }

    private List<Petition> petitions(UUID viewer) throws GovernanceException {
        var petitions = new ArrayList<Petition>();
        for (var petition : removal.gatheringPetitions())
            petitions.add(new Petition(petition, removal.hasSponsored(petition.id(), viewer)));
        return List.copyOf(petitions);
    }

    private List<Ballot> ballots(List<GovernanceVote> votes, UUID viewer) throws GovernanceException {
        var cards = new ArrayList<Ballot>();
        for (var vote : votes) {
            boolean voter = voting.isVoter(vote.id(), viewer);
            cards.add(new Ballot(vote, voter, voter ? voting.ballotChoice(vote.id(), viewer).orElse(null) : null));
        }
        return List.copyOf(cards);
    }
}
