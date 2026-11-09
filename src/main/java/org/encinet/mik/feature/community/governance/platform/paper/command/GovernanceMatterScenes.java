package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceAction.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceAction.Command.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/** Matters share one list and detail surface; live tallies never expose ballot choices. */
final class GovernanceMatterScenes {
    private final GovernanceWorkspaceScenes view;
    GovernanceMatterScenes(GovernanceWorkspaceScenes view) { this.view = view; }

    UiNode summary() {
        var content = Ui.column().key("summary").gap(UiLength.ui(8))
                .sizing(UiSizing.width(UiLength.ZERO, UiLength.ui(280)))
                .child(view.info("heading", view.label(Message.GOVERNANCE_MENU_ONGOING, GOLD), UiTheme.HEADING));
        var matters = view.data.ongoing(null);
        for (var matter : matters.stream().limit(3).toList())
            content.child(view.button("summary:" + matter.id(), title(matter), new SelectMatter(matter.id()))
                    .singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build(), LinearSlot.stretch());
        content.child(view.button("all-matters", view.label(Message.GOVERNANCE_MENU_MATTERS, AQUA), new Navigate(Section.MATTERS)).build());
        return content.build();
    }

    UiNode body() {
        var state = view.state;
        var window = GovernanceMatterWindow.of(state,view.data);
        var selected = window.selected(state.selectedMatter);
        var content = Ui.layout(UiLayouts.contentColumn(UiKey.named(selected == null ? "empty" : "split"),UiLength.ui(10))).key("matters");
        var filters = Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(8))).key("filters")
                .child(view.button("ongoing", view.label(Message.GOVERNANCE_MENU_ONGOING), new FilterMatters(Filter.ONGOING)).selected(state.filter == Filter.ONGOING).build())
                .child(view.button("completed", view.label(Message.GOVERNANCE_MENU_COMPLETED), new FilterMatters(Filter.COMPLETED)).selected(state.filter == Filter.COMPLETED).build());
        if (state.subjectFilter != null) filters.child(view.button("clear-subject", view.label(Message.GOVERNANCE_MENU_SUBJECT_FILTER, AQUA, state.subjectName), CLEAR_FILTER).build());
        content.child(filters.build());
        if (selected == null) content.child(view.info("empty", view.label(Message.GOVERNANCE_NONE, GRAY)));
        else {
            long revision = state.detailRevision;
            var titles = window.items().stream().map(this::title).toList();
            var list = Ui.windowedList(window.items().size(),Ui.button(Component.text("M")).singleLine().build(),index -> {
                var matter = window.items().get(index);
                return view.button(matter.id(),Component.text(matter.name(),WHITE),new SelectMatter(matter.id())).singleLine()
                        .tooltip(titles.get(index)).selected(matter.id().equals(selected.id())).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
            }).key("matter-list").sizing(new UiSizing(UiSize.ZERO,new UiSize(260,180))).containing(window.localAnchor())
                    .pager(page -> view.pager(window.navigation(page),index -> new ReadMatterWindow(revision,view.data,window.sourceIndex(index,page.pageSize()))));
            var directory = Ui.column().key("directory").child(list.build()).build();
            content.child(UiTemplates.split(directory, details(selected)), LinearSlot.stretch());
        }
        return UiTemplates.focus(content.build(),UiKey.named(selected == null ? "empty" : "split"));
    }

    private Component title(GovernanceMatter matter) {
        Component subject = Component.text(matter.name(), WHITE);
        return Component.text(matter.ballot() == null ? view.text.t(view.language, Message.GOVERNANCE_MENU_PETITIONS)
                        : view.text.kind(view.language, matter.ballot().vote().kind()), GRAY)
                .append(Component.text(" · ", GRAY)).append(subject)
                .append(Component.newline()).append(matter.ballot() == null
                        ? Component.text(matter.petition().petition().sponsorCount() + " / " + matter.petition().petition().sponsorsRequired(), YELLOW)
                        : result(matter.ballot().vote()));
    }

    private UiNode details(GovernanceMatter matter) {
        if (matter.ballot() == null) return petitionDetails(matter);
        var card = matter.ballot();
        long revision = view.state.detailRevision;
        return new GovernanceVoteDetails(view.text, view.language).content(card, view.data.pendingVotes().contains(card.vote().id()),
                view.state.busy ? GovernanceVoteDetails.VotingState.BUSY : GovernanceVoteDetails.VotingState.READY,
                view.state.matterPosition(matter.id()),position -> view.dispatch(new ReadMatter(revision,matter.id(),position)),
                choice -> view.dispatch(new Cast(card.vote().id(), choice)));
    }

    private UiNode petitionDetails(GovernanceMatter matter) {
        var card = matter.petition();
        var petition = card.petition();
        String key = "join:" + petition.id();
        var join = view.button(key, view.label(card.sponsored() ? Message.GOVERNANCE_MENU_ALREADY_SPONSORED : Message.GOVERNANCE_MENU_JOIN_PETITION, YELLOW),
                new OpenPopup(Intent.SPONSOR, petition.subjectId(), UiKey.named(key)));
        if (card.sponsored()) join.disabled(view.label(Message.GOVERNANCE_MENU_ALREADY_SPONSORED, GRAY));
        else if (!view.data.activeMember() || petition.subjectId().equals(view.viewer.id()))
            join.disabled(view.label(Message.GOVERNANCE_ERROR_REMOVAL_SPONSOR_INELIGIBLE, RED));
        long revision = view.state.detailRevision;
        var reading = Ui.document().key("reading").readingBody()
                .header(Ui.heading(view.label(Message.GOVERNANCE_MENU_PETITIONS,GOLD)).key("title").singleLine().build())
                .blocks(List.of(view.info("details",view.label(Message.GOVERNANCE_PETITION_ROW,WHITE,petition.subjectName(),
                        petition.sponsorCount(),petition.sponsorsRequired(),view.text.time(view.language,petition.expiresAt())))))
                .position(view.state.matterPosition(matter.id()),position -> view.dispatch(new ReadMatter(revision,matter.id(),position))).build();
        var scoped = UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(reading.key(),UiLength.ui(8))).key(matter.id()).child(reading).child(join.build()).build(),reading.key());
        return UiTemplates.focus(Ui.column().key("petition-detail").child(scoped).build(),scoped.key());
    }

    Component result(GovernanceVote vote) {
        if (view.data.pendingVotes().contains(vote.id())) return view.label(Message.GOVERNANCE_MENU_PENDING_APPLICATION, YELLOW);
        return view.label(switch (vote.status()) {
            case OPEN -> Message.GOVERNANCE_RESULT_OPEN;
            case PASSED -> Message.GOVERNANCE_RESULT_PASSED;
            case FAILED -> Message.GOVERNANCE_RESULT_FAILED;
            case TERMINATED -> Message.GOVERNANCE_RESULT_TERMINATED;
        }, view.text.resultColor(vote), vote.status() == VoteStatus.FAILED ? view.text.failure(view.language, vote.failure())
                : vote.status() == VoteStatus.TERMINATED ? view.text.termination(view.language, vote.terminationReason()) : "");
    }
}
