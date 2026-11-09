package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.voting.model.*;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/** Compact activity and its attached participation surface; all reads and writes belong to the owner. */
final class GovernanceActivityScenes {
    interface Actions {
        void open(GovernanceMatter matter, UiKey source, boolean confirm);
        void cast(GovernanceMatter matter, VoteChoice choice);
        void join(GovernanceMatter matter);
        void all();
        void reload();
    }

    private final GovernanceText text;
    private final Language language;
    private final UUID viewer;
    private final GovernanceActivityState state;
    private final Instant now;
    private final boolean busy;
    private final Component failure;
    private final Actions actions;

    GovernanceActivityScenes(GovernanceText text, Language language, UUID viewer, GovernanceActivityState state,
                             Instant now, boolean busy, Component failure, Actions actions) {
        this.text = text; this.language = language; this.viewer = viewer; this.state = state;
        this.now = now; this.busy = busy; this.failure = failure; this.actions = actions;
    }

    UiNode content() {
        var body = Ui.column().key("governance-activity").gap(UiLength.ui(8));
        if (state.single()) {
            var matter = state.displayed().getFirst();
            body.child(UiKey.named("activity-header"), header(matter, false), LinearSlot.stretch());
            body.child(controls(matter), LinearSlot.stretch());
        } else if (!state.displayed().isEmpty()) {
            UiNode all = null;
            if (state.displayed().size() > 2) {
                var button = Ui.button(label(Message.GOVERNANCE_ACTIVITY_ALL, AQUA, state.displayed().size()))
                        .key("activity-all").primary((_,_) -> actions.all());
                if (busy) button.disabled(label(Message.GOVERNANCE_MENU_LOADING, YELLOW));
                all = button.build();
            }
            body.child(UiKey.named("activity-heading-bar"), UiTemplates.actionRow(Ui.heading(label(Message.GOVERNANCE_MENU_MATTERS, GOLD)).key("activity-heading").build(), all), LinearSlot.stretch());
            for (var matter : state.displayed().stream().limit(2).toList()) body.child(UiKey.named("row:" + matter.id()), header(matter, true), LinearSlot.stretch());
        }
        if (failure != null) body.child(UiKey.named("activity-feedback"), UiTemplates.actionRow(Ui.text(failure).key("activity-error").alignment(FloatingMenuDecoration.Alignment.LEFT).build(),
                Ui.button(UiIcons.REFRESH, label(Message.GOVERNANCE_MENU_REFRESH, WHITE)).key("activity-retry")
                        .primary((_,_) -> actions.reload()).build()), LinearSlot.stretch());
        return body.build();
    }

    private UiNode header(GovernanceMatter matter, boolean summary) {
        var title = Ui.button(proposal(matter)).key(source(matter)).alignment(FloatingMenuDecoration.Alignment.LEFT)
                .primary((_,_) -> actions.open(matter, source(matter), false));
        if (summary) title.summaryLines(1);
        if (!state.current(matter, now)) title.disabled(label(Message.GOVERNANCE_ACTIVITY_CLOSED, YELLOW));
        else if (busy) title.disabled(label(Message.GOVERNANCE_MENU_LOADING, YELLOW));
        var metadata = Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(4))).key("metadata")
                .child(Ui.text(remaining(matter)).key(state.current(matter, now) ? "remaining" : "closed").build());
        if (summary && state.current(matter, now)) metadata.child(Ui.text(status(matter)).key("participation").build());
        return UiTemplates.actionRow(title.build(), metadata.build());
    }

    UiNode details(GovernanceMatter matter, boolean confirming, UiReadingPosition position,
                   Consumer<UiReadingPosition> read, Runnable cancel) {
        var body = Ui.column().key("matter-details").gap(UiLength.ui(8));
        UiNode detail;
        if (matter.ballot() != null) {
            var voting = !state.current(matter, now) ? GovernanceVoteDetails.VotingState.CLOSED
                    : busy ? GovernanceVoteDetails.VotingState.BUSY : GovernanceVoteDetails.VotingState.READY;
            detail = new GovernanceVoteDetails(text, language).content(matter.ballot(), false, voting,position,read,
                    choice -> actions.cast(matter, choice));
        } else detail = petitionDetails(matter, confirming, cancel);
        body.child(detail, LinearSlot.stretch());
        if (failure != null) body.child(Ui.text(failure).key("error").alignment(FloatingMenuDecoration.Alignment.LEFT).build());
        return UiTemplates.focus(body.build(),detail.key());
    }

    private UiNode petitionDetails(GovernanceMatter matter, boolean confirming, Runnable cancel) {
        var body = Ui.column().key("petition-details").gap(UiLength.ui(8))
                .child(Ui.text(remaining(matter)).key(state.current(matter, now) ? "remaining" : "closed").build());
        if (confirming && state.current(matter, now)) {
            body.child(Ui.text(label(Message.GOVERNANCE_ACTIVITY_CONFIRM_PETITION, WHITE)).key("confirmation")
                    .alignment(FloatingMenuDecoration.Alignment.LEFT).build());
            var confirm = Ui.button(action("confirm")).key("confirm-petition").primary((_,_) -> actions.join(matter));
            var cancelButton = Ui.button(label(Message.GOVERNANCE_MENU_CANCEL, WHITE)).key("cancel-petition").primary((_,_) -> cancel.run());
            if (busy) {
                confirm.disabled(label(Message.GOVERNANCE_MENU_LOADING, YELLOW));
                cancelButton.disabled(label(Message.GOVERNANCE_MENU_LOADING, YELLOW));
            }
            body.child(Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(8))).key("confirmation-actions")
                    .child(cancelButton.build())
                    .child(confirm.build()).build());
        } else {
            if (state.current(matter, now)) body.child(Ui.text(label(Message.GOVERNANCE_ACTIVITY_NEEDED, WHITE,
                    Math.max(0, matter.petition().petition().sponsorsRequired() - matter.petition().petition().sponsorCount()))).key("needed").build());
            body.child(petitionControls(matter, true), LinearSlot.stretch());
        }
        return body.build();
    }

    private UiNode controls(GovernanceMatter matter) {
        if (!state.current(matter, now)) return Ui.column().build();
        if (matter.ballot() == null) return petitionControls(matter, false);
        var ballot = matter.ballot();
        if (!ballot.voter()) return Ui.text(label(Message.GOVERNANCE_ACTIVITY_STATUS, WHITE, "view")).key("view-only").build();
        if (ballot.vote().kind() == VoteKind.BAN) {
            var read = Ui.button(action("vote")).key("read-vote").primary((_,_) -> actions.open(matter, source(matter), false));
            if (busy) read.disabled(label(Message.GOVERNANCE_MENU_LOADING, YELLOW));
            return read.build();
        }
        return GovernanceParticipation.choices(text, language, ballot.choice(), busy ? label(Message.GOVERNANCE_MENU_LOADING, YELLOW) : null,
                choice -> actions.cast(matter, choice));
    }

    private UiNode petitionControls(GovernanceMatter matter, boolean detail) {
        if (!state.current(matter, now)) return Ui.column().build();
        if (matter.petition().sponsored()) return Ui.text(label(Message.GOVERNANCE_MENU_ALREADY_SPONSORED, GREEN)).key("joined").build();
        if (!matter.available(viewer, state.data().activeMember()))
            return Ui.text(label(detail ? Message.GOVERNANCE_ERROR_REMOVAL_SPONSOR_INELIGIBLE : Message.GOVERNANCE_ACTIVITY_STATUS,
                    WHITE, detail ? new Object[0] : new Object[]{"view"})).key("view-only").build();
        var key = UiKey.named("activity-join:" + matter.id());
        var join = Ui.button(label(Message.GOVERNANCE_MENU_JOIN_PETITION, WHITE)).key(key)
                .primary((_,_) -> actions.open(matter, source(matter), true));
        if (busy) join.disabled(label(Message.GOVERNANCE_MENU_LOADING, YELLOW));
        return join.build();
    }

    Component proposal(GovernanceMatter matter) {
        String kind = matter.ballot() == null ? "petition" : matter.ballot().vote().kind().name().toLowerCase(java.util.Locale.ROOT);
        return label(Message.GOVERNANCE_ACTIVITY_PROPOSAL, WHITE, kind, matter.name());
    }

    private Component remaining(GovernanceMatter matter) {
        if (!state.current(matter, now)) return label(Message.GOVERNANCE_ACTIVITY_CLOSED, YELLOW);
        long seconds = Math.max(1, Duration.between(now, matter.deadline()).toSeconds());
        long unit = seconds >= 86_400 ? 86_400 : seconds >= 3_600 ? 3_600 : 60;
        return label(Message.GOVERNANCE_ACTIVITY_REMAINING, YELLOW, Math.ceilDiv(seconds, unit), unit == 86_400 ? "days" : unit == 3_600 ? "hours" : "minutes");
    }

    private Component status(GovernanceMatter matter) {
        String status = !state.current(matter, now) ? "closed" : matter.participated()
                ? matter.ballot() == null ? "joined" : "voted" : matter.available(viewer, state.data().activeMember())
                ? matter.ballot() == null ? "join" : "available" : "view";
        return label(Message.GOVERNANCE_ACTIVITY_STATUS, status.equals("voted") || status.equals("joined") ? GREEN : WHITE, status);
    }

    private Component action(String action) { return label(Message.GOVERNANCE_ACTIVITY_ACTION, WHITE, action); }
    private Component label(Message message, net.kyori.adventure.text.format.NamedTextColor color, Object... args) {
        return Component.text(text.t(language, message, args), color);
    }
    static UiKey source(GovernanceMatter matter) { return UiKey.named("activity:" + matter.id()); }
}
