package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceData.Ballot;
import org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceData.Petition;

/** One requester-specific matter; the same identity is used by the directory and embedded activity. */
record GovernanceMatter(String id, UUID subject, String name, Ballot ballot, Petition petition) {
    GovernanceMatter {
        Objects.requireNonNull(id); Objects.requireNonNull(subject); Objects.requireNonNull(name);
        if ((ballot == null) == (petition == null)) throw new IllegalArgumentException("Exactly one matter kind is required");
    }
    static GovernanceMatter vote(Ballot card) {
        return new GovernanceMatter("vote:" + card.vote().id(), card.vote().subjectId(), card.vote().subjectName(), card, null);
    }
    static GovernanceMatter petition(Petition card) {
        return new GovernanceMatter("petition:" + card.petition().id(), card.petition().subjectId(), card.petition().subjectName(), null, card);
    }
    Instant deadline() { return ballot != null ? ballot.vote().closesAt() : petition.petition().expiresAt(); }
    boolean participated() { return ballot != null ? ballot.choice() != null : petition.sponsored(); }
    boolean available(UUID viewer, boolean activeMember) {
        return ballot != null ? ballot.voter() : activeMember && !subject.equals(viewer) && !petition.sponsored();
    }
}
