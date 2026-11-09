package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import org.encinet.mik.feature.community.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.removal.RemovalPetition;
import org.encinet.mik.feature.community.governance.roster.StaffMember;
import org.encinet.mik.feature.community.governance.voting.model.*;

/** Requester-specific read model. It contains no other voter's or sponsor's identity. */
record GovernanceWorkspaceData(List<StaffMember> staff, List<Ballot> openVotes, List<Petition> petitions,
                              List<Ballot> history, int historyTotal, int historyOffset,
                              List<GovernanceVote> related, ActiveMemberEligibility active, ParticipationSummary participation,
                              Set<Long> pendingVotes, boolean awaitingAppointment) {
    GovernanceWorkspaceData {
        staff = List.copyOf(staff); openVotes = List.copyOf(openVotes); petitions = List.copyOf(petitions);
        history = List.copyOf(history); related = List.copyOf(related); pendingVotes = Set.copyOf(pendingVotes);
        Objects.requireNonNull(participation, "participation");
    }
    record Ballot(GovernanceVote vote, boolean voter, VoteChoice choice) { }
    record Petition(RemovalPetition petition, boolean sponsored) { }
    List<GovernanceMatter> ongoing(UUID subject) {
        return java.util.stream.Stream.concat(openVotes.stream().map(GovernanceMatter::vote), petitions.stream().map(GovernanceMatter::petition))
                .filter(matter -> subject == null || subject.equals(matter.subject()))
                .sorted(Comparator.comparing(GovernanceMatter::deadline).thenComparing(GovernanceMatter::id)).toList();
    }
    boolean activeMember() { return active != null && active.eligible(); }
    boolean nominated(UUID id) { return openVotes.stream().anyMatch(card -> card.vote().kind() == VoteKind.APPOINTMENT && card.vote().subjectId().equals(id)); }
    boolean canApply(UUID id) { return activeMember() && !nominated(id) && !awaitingAppointment; }
    StaffMember staff(UUID id) { return staff.stream().filter(person -> person.id().equals(id)).findFirst().orElse(null); }
    boolean removing(UUID id) { return openVotes.stream().anyMatch(card -> card.vote().kind() == VoteKind.REMOVAL && card.vote().subjectId().equals(id)); }
    Petition petition(UUID id) { return petitions.stream().filter(card -> card.petition().subjectId().equals(id)).findFirst().orElse(null); }
}
