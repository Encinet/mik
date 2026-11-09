package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.encinet.mik.feature.community.governance.voting.model.VoteKind;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.ui.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceAction.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceAction.Command.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;

/** Compact personal context and an application surface that also explains missing requirements. */
final class GovernanceApplicationScenes {
    private final GovernanceWorkspaceScenes view;

    GovernanceApplicationScenes(GovernanceWorkspaceScenes view) { this.view = view; }

    UiNode personal() {
        var context = Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(4))).key("personal-context");
        var actions = Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(4))).key("personal-actions");
        if (view.data != null && !view.viewer.staff()) {
            context.child(view.info("application-summary", status()));
            boolean withdrawing = view.data.nominated(view.viewer.id()) && !view.data.awaitingAppointment();
            Message label = view.data.awaitingAppointment() ? Message.GOVERNANCE_MENU_APPLICATION
                    : withdrawing ? Message.GOVERNANCE_MENU_WITHDRAW : Message.GOVERNANCE_MENU_NOMINATE;
            actions.child(view.button("application", view.label(label, withdrawing ? YELLOW : WHITE),
                    new OpenPopup(withdrawing ? Intent.WITHDRAW : Intent.APPLICATION, view.viewer.id(), UiKey.named("application"))).build());
        }
        if (view.data != null && view.data.staff(view.viewer.id()) != null && view.data.staff(view.viewer.id()).subjectToRemoval())
            actions.child(view.button("resign", view.label(Message.GOVERNANCE_MENU_RESIGN, RED),
                    new OpenPopup(Intent.RESIGN, view.viewer.id(), UiKey.named("resign"))).build());
        return UiTemplates.actionRow(context.build(), actions.build());
    }

    UiNode content() {
        var blocks = new ArrayList<UiNode>();
        blocks.add(view.info("application-status",status()));
        var actions = Ui.layout(UiLayouts.wrap(UiLength.ui(8),UiLength.ui(8))).key("application-actions");
        if (view.data.awaitingAppointment()) return reading(blocks,actions.build());
        if (view.data.nominated(view.viewer.id())) {
            view.data.openVotes().stream().filter(card -> card.vote().subjectId().equals(view.viewer.id())
                    && card.vote().kind() == VoteKind.APPOINTMENT)
                    .findFirst().ifPresent(card -> blocks.add(view.info("application-period", view.label(Message.GOVERNANCE_VOTE_PERIOD,
                            WHITE, view.text.time(view.language, card.vote().opensAt()), view.text.time(view.language, card.vote().closesAt())))));
            return reading(blocks,actions.build());
        }
        List<UiNode> missing = new ArrayList<>();
        var requirements = GovernanceQualifications.active(view.text, view.language, view.data.active(), view.data.participation());
        for (int index = 0; index < requirements.size(); index++) {
            var requirement = requirements.get(index);
            if (!requirement.met()) missing.add(view.info("requirement:" + index, requirement.text()));
        }
        if (!view.viewer.password()) {
            missing.add(view.info("password-required", view.text.line(view.language, false, Message.PASSWORD_SET_FIRST)));
            actions.child(view.button("set-password", view.label(Message.PASSWORD_SET, AQUA), PASSWORD).build());
        }
        if (view.data.canApply(view.viewer.id()) && view.viewer.password()) {
            blocks.add(view.info("application-explanation", view.label(Message.GOVERNANCE_MENU_CONFIRM_NOMINATE)));
            actions.child(view.button("cancel",view.label(Message.GOVERNANCE_MENU_CANCEL),CANCEL_CONFIRM).build())
                    .child(view.button("apply",view.label(Message.GOVERNANCE_MENU_NOMINATE,GREEN),PERFORM).build());
        }
        blocks.addAll(missing);
        return reading(blocks,actions.build());
    }

    private UiNode reading(List<UiNode> blocks, UiNode actions) {
        return view.popupReading("application-requirements",blocks,actions);
    }

    private Component status() {
        if (view.state.busy) return view.label(Message.GOVERNANCE_MENU_LOADING, YELLOW);
        String status = view.data.awaitingAppointment() ? "pending" : view.data.nominated(view.viewer.id()) ? "open"
                : view.data.canApply(view.viewer.id()) && view.viewer.password() ? "ready" : "blocked";
        return view.label(Message.GOVERNANCE_APPLICATION_STATUS, status.equals("ready") ? GREEN : YELLOW, status);
    }
}
