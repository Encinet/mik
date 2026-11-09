package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.function.Consumer;
import java.util.ArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceData.Ballot;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.FloatingMenuDecoration;
import org.encinet.mik.shared.menu.ui.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/** Complete vote presentation shared by the matters workspace and embedded activity companions. */
final class GovernanceVoteDetails {
    /** A host may retain an open snapshot after its source has expired or disappeared. */
    enum VotingState { READY, BUSY, CLOSED }

    private final GovernanceText text;
    private final Language language;

    GovernanceVoteDetails(GovernanceText text, Language language) {
        this.text = text;
        this.language = language;
    }

    UiNode content(Ballot card, boolean pendingDelivery, VotingState voting, UiReadingPosition position,
                   Consumer<UiReadingPosition> read, Consumer<VoteChoice> cast) {
        var vote = card.vote();
        boolean acceptingVotes = vote.status() == VoteStatus.OPEN && voting != VotingState.CLOSED;
        var heading = Ui.heading(Component.text("#" + vote.id() + " · " + text.kind(language, vote.kind())
                + " · " + vote.subjectName(), GOLD)).key("vote-title").summaryLines(1)
                .alignment(FloatingMenuDecoration.Alignment.LEFT).build();
        var detail = new ArrayList<UiNode>();
        detail.add(info("electorate", label(Message.GOVERNANCE_VOTE_ELECTORATE, WHITE, vote.eligibleVoters(), vote.quorumRequired())));
        detail.add(info("period", label(Message.GOVERNANCE_VOTE_PERIOD, WHITE,
                text.time(language, vote.opensAt()), text.time(language, vote.closesAt()))));
        if (vote.status() == VoteStatus.OPEN) {
            detail.add(acceptingVotes
                    ? info("progress", label(Message.GOVERNANCE_VOTE_IN_PROGRESS, YELLOW, vote.tally().participating()))
                    : info("closed", label(Message.GOVERNANCE_ACTIVITY_CLOSED, YELLOW)));
        } else {
            detail.add(info("result", Component.text(text.result(language, vote), text.resultColor(vote))));
            detail.add(info("finalized", label(Message.GOVERNANCE_VOTE_FINALIZED, WHITE, text.time(language, vote.finalizedAt()))));
        }
        if (pendingDelivery) detail.add(info("delivery", label(Message.GOVERNANCE_MENU_PENDING_APPLICATION, YELLOW)));
        Message own = !card.voter() ? Message.GOVERNANCE_OWN_NOT_VOTER : card.choice() == null ? Message.GOVERNANCE_OWN_NOT_VOTED
                : acceptingVotes ? Message.GOVERNANCE_OWN_CHOICE_OPEN : Message.GOVERNANCE_OWN_CHOICE_CLOSED;
        detail.add(info("own-ballot", card.choice() == null ? label(own, AQUA)
                : label(own, AQUA, text.choice(language, card.choice()))));
        String proposal = text.proposalDetails(language, vote.proposal());
        if (proposal != null) detail.add(info("proposal", Component.text(proposal, WHITE)));
        var reading = Ui.document().key(UiKey.named("vote:" + vote.id())).readingBody().header(heading).blocks(detail).position(position,read)
                .pager((page,select) -> {
                    var navigation = UiTemplates.pager(page,select);
                    if (!acceptingVotes || !card.voter() || page.hasNext()) return navigation;
                    return Ui.column().key("vote-actions").gap(UiLength.ui(8)).child(navigation)
                            .child(GovernanceParticipation.choices(text,language,card.choice(),
                                    voting == VotingState.BUSY ? label(Message.GOVERNANCE_MENU_LOADING,YELLOW) : null,cast)).build();
                }).build();
        return UiTemplates.focus(Ui.column().key("matter-detail").child(reading).build(),reading.key());
    }

    private UiElement info(String key, Component label) {
        return Ui.text(label).key(key).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }

    private Component label(Message message, NamedTextColor color, Object... args) {
        return Component.text(text.t(language, message, args), color);
    }
}
