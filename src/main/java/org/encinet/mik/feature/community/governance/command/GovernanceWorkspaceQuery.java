package org.encinet.mik.feature.community.governance.command;

import org.encinet.mik.feature.community.governance.GovernanceException;
import org.encinet.mik.feature.community.governance.gateway.GovernanceGateway;
import org.encinet.mik.feature.community.governance.gateway.GovernanceViews;
import org.encinet.mik.feature.community.governance.roster.StaffMember;
import org.encinet.mik.protocol.GovernancePetitionView;
import org.encinet.mik.protocol.GovernanceResult;
import org.encinet.mik.protocol.GovernanceVoteView;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Stream;

import static org.encinet.mik.feature.community.governance.command.GovernanceWorkspaceData.Ballot;
import static org.encinet.mik.feature.community.governance.command.GovernanceWorkspaceData.Petition;

final class GovernanceWorkspaceQuery {
    private final GovernanceGateway governance;

    GovernanceWorkspaceQuery(GovernanceGateway governance) { this.governance = governance; }

    GovernanceWorkspaceData load(GovernanceCommandContext.KnownPlayer viewer, List<StaffMember> staff,
                                UUID subjectFilter, OptionalInt historyAnchor, UUID relatedSubject) throws GovernanceException {
        var id = viewer.playerId();
        var matters = governance.matters(id, null);
        var profile = matters.getProfile();
        int batch = GovernanceMatterWindow.HISTORY_BATCH_SIZE;
        int offset = historyAnchor.orElse(0) / batch * batch;
        var history = historyAnchor.isPresent() ? governance.history(id, subjectFilter, batch, offset)
                : GovernanceResult.getDefaultInstance();
        var related = relatedSubject == null ? GovernanceResult.getDefaultInstance()
                : governance.history(id, relatedSubject, 2, 0);
        var pending = new HashSet<Long>();
        Stream.concat(history.getVotesList().stream(), related.getVotesList().stream())
                .filter(GovernanceVoteView::getGrantPending).map(GovernanceVoteView::getId).forEach(pending::add);
        return new GovernanceWorkspaceData(staff, matters.getVotesList().stream().map(GovernanceWorkspaceQuery::ballot).toList(),
                matters.getPetitionsList().stream().map(GovernanceWorkspaceQuery::petition).toList(),
                history.getVotesList().stream().map(GovernanceWorkspaceQuery::ballot).toList(), history.getTotal(), offset,
                related.getVotesList().stream().map(GovernanceViews::vote).toList(),
                profile.hasMemberSinceMs() ? GovernanceViews.active(profile) : null,
                GovernanceViews.participation(profile), pending, profile.getAppointmentPending());
    }

    GovernanceActivityData activity(GovernanceCommandContext.KnownPlayer viewer, Instant now) throws GovernanceException {
        var result = governance.matters(viewer.playerId(), null);
        boolean active = result.getProfile().getActiveMember();
        var matters = Stream.concat(
                result.getVotesList().stream().map(GovernanceWorkspaceQuery::ballot)
                        .filter(card -> card.vote().isOpenAt(now)).map(GovernanceMatter::vote),
                result.getPetitionsList().stream().map(GovernanceWorkspaceQuery::petition)
                        .filter(card -> now.isBefore(card.petition().expiresAt())).map(GovernanceMatter::petition))
                .sorted(Comparator.comparing((GovernanceMatter matter) -> !matter.available(viewer.playerId(), active) || matter.participated())
                        .thenComparing(GovernanceMatter::deadline).thenComparing(GovernanceMatter::id)).toList();
        return new GovernanceActivityData(matters, active);
    }

    private static Ballot ballot(GovernanceVoteView view) {
        return new Ballot(GovernanceViews.vote(view), view.getEligibleToVote(), GovernanceViews.choice(view.getOwnBallot()));
    }

    private static Petition petition(GovernancePetitionView view) {
        return new Petition(GovernanceViews.petition(view), view.getOwnSponsorship());
    }
}
