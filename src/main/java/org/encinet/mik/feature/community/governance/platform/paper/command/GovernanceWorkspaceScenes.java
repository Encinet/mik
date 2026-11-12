package org.encinet.mik.feature.community.governance.platform.paper.command;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.roster.StaffMember;
import org.encinet.mik.shared.i18n.*;
import org.encinet.mik.shared.menu.*;
import org.encinet.mik.shared.menu.ui.*;
import org.encinet.mik.shared.role.PlayerRole;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceState.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceAction.*;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernanceWorkspaceAction.Command.*;
import static net.kyori.adventure.text.format.NamedTextColor.*;

/** Declarative public directory, contextual companion and compact workspace navigation. */
final class GovernanceWorkspaceScenes {
    private record StaffRow(StaffMember person, boolean online) { }
    record Viewer(UUID id, PlayerRole role, boolean password) {
        boolean staff() { return role == PlayerRole.MODERATOR || role == PlayerRole.CUSTODIAN; }
    }
    final GovernanceText text;
    final Language language;
    final Viewer viewer;
    final GovernanceWorkspaceState state;
    final GovernanceWorkspaceData data;
    private final Consumer<GovernanceWorkspaceAction> actions;
    private final Predicate<UUID> online;

    GovernanceWorkspaceScenes(GovernanceText text, Language language, Viewer viewer, GovernanceWorkspaceState state,
                              GovernanceWorkspaceData data, Consumer<GovernanceWorkspaceAction> actions, Predicate<UUID> online) {
        this.text = text; this.language = language; this.viewer = viewer; this.state = state;
        this.data = data; this.actions = actions; this.online = online;
    }

    MenuScene render(UiNode search, boolean failed) {
        return new GovernanceWorkspaceScenes(text,language,viewer,state.renderCopy(),data,actions,online).compose(search,failed);
    }

    private MenuScene compose(UiNode search,boolean failed) {
        var body = Ui.layout(UiLayouts.contentColumn(UiKey.named(data == null ? "status" : state.section == Section.TEAM ? "directory" : "matters"),UiLength.ui(12))).key("page")
                .child(info("heading", label(Message.GOVERNANCE_MENU_TITLE, GOLD), UiTheme.TITLE))
                .child(new GovernanceApplicationScenes(this).personal(), LinearSlot.stretch())
                .child(Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(8))).key("tabs")
                        .child(button("team-tab", label(Message.GOVERNANCE_MENU_TEAM), new Navigate(Section.TEAM)).selected(state.section == Section.TEAM).build())
                        .child(button("matters-tab", label(Message.GOVERNANCE_MENU_MATTERS), new Navigate(Section.MATTERS)).selected(state.section == Section.MATTERS).build()).build());
        if (data == null) body.child(info("status", label(failed ? Message.GOVERNANCE_OPERATION_ERROR : Message.GOVERNANCE_MENU_LOADING)).withSummaryLines(1));
        else {
            if (failed) body.child(info("error", label(Message.GOVERNANCE_OPERATION_ERROR, RED)));
            body.child(switch (state.section) {
                case TEAM -> team(search);
                case MATTERS -> new GovernanceMatterScenes(this).body();
            }, LinearSlot.stretch());
        }
        UiNode page = UiTemplates.focus(body.build(),UiKey.named(data == null ? "status" : state.section == Section.TEAM ? "directory" : "matters"));
        var overview = Ui.layout(UiLayouts.firstFit(UiLayouts.row(UiLength.ui(14)), UiLayouts.column(UiLength.ui(12))))
                .key("overview").child(UiKey.named("main"), UiTemplates.window("main", page), LinearSlot.fit(LinearSlot.Alignment.START));
        if (data != null && state.section == Section.TEAM && (!data.openVotes().isEmpty() || !data.petitions().isEmpty())) {
            UiNode summary = UiTemplates.sidebar("ongoing", new GovernanceMatterScenes(this).summary());
            overview.child(UiKey.named("ongoing"), summary, LinearSlot.fit(LinearSlot.Alignment.START));
        }
        UiNode main = UiTemplates.focus(overview.build(), UiKey.named("main"));
        var tools = Ui.layout(UiLayouts.wrap(UiLength.ui(8), UiLength.ui(8))).key("tools");
        tools.child(button("identity", avatar(viewer.id()).append(Component.space())
                .append(label(viewer.role().label(), AQUA)), IDENTITY)
                .tooltip(label(Message.GOVERNANCE_MENU_IDENTITY_HINT)).build());
        if (viewer.staff()) tools.child(button("players", label(Message.GOVERNANCE_MENU_PLAYERS), PLAYERS).build());
        var refresh = Ui.button(UiIcons.REFRESH, label(Message.GOVERNANCE_MENU_REFRESH)).key("refresh").primary((_,_) -> actions.accept(REFRESH));
        if (state.busy) refresh.disabled(label(Message.GOVERNANCE_MENU_LOADING, YELLOW));
        tools.child(refresh.build());
        tools.child(Ui.button(UiIcons.CROSS, label(Message.CLOSE)).key("close").primary((_,_) -> actions.accept(CLOSE)).build());
        var scene = MenuScene.screen("governance-workspace").stableAnchor().appearance(FloatingMenuAppearance.DEFAULT)
                .content(UiTemplates.sheet(main, UiTemplates.toolbar("actions", tools.build())));
        if (data != null && state.popup != null) {
            var popup = MenuPopover.attached(state.popup.source(), popupTitle(), popup(), label(Message.CLOSE), (_,_) -> actions.accept(DISMISS_POPUP));
            scene.popover(state.popup.intent() == Intent.DETAILS ? popup : popup.exclusive());
        }
        return scene.build();
    }

    private UiNode team(UiNode search) {
        var matching = data.staff().stream().filter(person -> state.matches(person.name())).toList();
        var content = Ui.layout(UiLayouts.contentColumn(UiKey.named(matching.isEmpty() ? "staff-empty" : "staff-table"),UiLength.ui(8))).key("directory");
        if (search != null) content.child(search, LinearSlot.fit());
        if (matching.isEmpty()) content.child(info("staff-empty", label(Message.GOVERNANCE_MENU_TEAM_EMPTY, GRAY)));
        else {
            var rows = matching.stream().map(person -> new StaffRow(person,online.test(person.id()))).toList();
            var template = new StaffRow(new StaffMember(new UUID(0,0),"Long_Player_Name",PlayerRole.CUSTODIAN,false),false);
            var table = new UiTable<StaffRow>().key(UiKey.named("staff-table"))
                    .column(new UiTable.Column<>(UiKey.named("person"), singleLine("person-header",label(Message.GOVERNANCE_MENU_PLAYER_NAME, WHITE)),
                            UiTrack.content(), row -> {
                        var person = row.person();
                        UiKey key = UiKey.named("staff:" + person.id());
                        return button(key.value(), avatar(person.id()).append(Component.space()).append(Component.text(person.name())),
                                new SelectStaff(person.id(), key)).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT)
                                .selected(state.popup != null && person.id().equals(state.popup.subject())).build();
                    }))
                    .column(new UiTable.Column<>(UiKey.named("role"),singleLine("role-header",label(Message.GOVERNANCE_MENU_IDENTITY,WHITE)),
                            UiTrack.content(),row -> singleLine("role",label(row.person().role().label(),AQUA))))
                    .column(new UiTable.Column<>(UiKey.named("presence"),singleLine("presence-header",Component.empty()),
                            UiTrack.content(),row -> singleLine("presence",presence(row.online()))))
                    .windowed(rows.size(),template,rows::get,row -> UiKey.item(row.person().id().toString()))
                    .page(state.staffPage,index -> actions.accept(new StaffPage(index)));
            content.child(table, LinearSlot.stretch());
        }
        return UiTemplates.focus(content.build(),UiKey.named(matching.isEmpty() ? "staff-empty" : "staff-table"));
    }

    private UiElement singleLine(String key, Component label) {
        return Ui.text(label).key(key).singleLine().alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }

    private Component popupTitle() {
        StaffMember member = data.staff(state.popup.subject());
        return state.popup.intent() == Intent.DETAILS && member != null
                ? avatar(member.id()).append(Component.space()).append(Component.text(member.name(), WHITE))
                : label(confirmationLabel(state.popup.intent()));
    }

    private Message confirmationLabel(Intent intent) {
        return switch (intent) {
            case APPLICATION -> Message.GOVERNANCE_MENU_NOMINATE;
            case WITHDRAW -> Message.GOVERNANCE_MENU_WITHDRAW;
            case SPONSOR -> Message.GOVERNANCE_MENU_JOIN_PETITION;
            case RESIGN -> Message.GOVERNANCE_MENU_RESIGN;
            case DETAILS -> Message.GOVERNANCE_MENU_IDENTITY;
        };
    }

    private UiNode popup() {
        var popup = state.popup;
        if (popup.intent() == Intent.APPLICATION) return new GovernanceApplicationScenes(this).content();
        if (popup.intent() != Intent.DETAILS) {
            if (popup.intent() == Intent.WITHDRAW && !data.nominated(viewer.id()))
                return info("application-closed", label(Message.GOVERNANCE_ACTIVITY_CLOSED, YELLOW));
            Message question = switch (popup.intent()) {
                case WITHDRAW -> Message.GOVERNANCE_MENU_CONFIRM_WITHDRAW;
                case SPONSOR -> Message.GOVERNANCE_MENU_CONFIRM_SPONSOR;
                case RESIGN -> Message.GOVERNANCE_MENU_CONFIRM_RESIGN;
                case APPLICATION, DETAILS -> throw new IllegalStateException();
            };
            var blocks = new ArrayList<UiNode>();
            String name = data.staff(popup.subject()) == null ? null : data.staff(popup.subject()).name();
            if (name == null && data.petition(popup.subject()) != null) name = data.petition(popup.subject()).petition().subjectName();
            if (name != null) blocks.add(info("subject",avatar(popup.subject()).append(Component.space()).append(Component.text(name)),UiTheme.HEADING));
            blocks.add(info("question",label(question)));
            var decisions = Ui.row().key("decision").gap(UiLength.ui(8))
                    .child(button("cancel", label(Message.CANCEL), CANCEL_CONFIRM).build())
                    .child(button("confirm", label(confirmationLabel(popup.intent()), popup.intent() == Intent.SPONSOR ? YELLOW : RED), PERFORM).build()).build();
            return popupReading("confirmation",blocks,decisions);
        }
        StaffMember member = data.staff(popup.subject());
        if (member == null) return info("missing", label(Message.GOVERNANCE_MENU_TEAM_EMPTY));
        var blocks = new ArrayList<UiNode>();
        blocks.add(info("role",label(member.role().label(),AQUA).append(Component.text(" · ",GRAY)).append(presence(member.id()))));
        blocks.add(info("responsibility",label(member.role() == PlayerRole.CUSTODIAN
                ? Message.GOVERNANCE_MENU_CUSTODIAN_DUTIES : Message.GOVERNANCE_MENU_MODERATOR_DUTIES)));
        var petition = data.petition(member.id());
        if (petition != null) blocks.add(info("petition-status", label(Message.GOVERNANCE_MENU_PETITION_ROW, YELLOW,
                member.name(), petition.petition().sponsorCount(), petition.petition().sponsorsRequired())));
        if (!data.related().isEmpty()) {
            blocks.add(info("recent-heading", label(Message.GOVERNANCE_MENU_RELATED, GOLD)));
            for (var vote : data.related()) blocks.add(info("record:" + vote.id(),
                    Component.text(text.kind(language, vote.kind()) + " · " + text.time(language, vote.finalizedAt()), GRAY)
                            .append(Component.newline()).append(new GovernanceMatterScenes(this).result(vote))));
        }
        var actions = Ui.column().key("person-actions").gap(UiLength.ui(8))
                .child(button("related",label(Message.GOVERNANCE_MENU_RELATED_MATTERS,AQUA),new Related(member.id(),member.name())).build());
        if (member.subjectToRemoval() && !member.id().equals(viewer.id())) {
            var sponsor = button("sponsor", label(petition != null && petition.sponsored() ? Message.GOVERNANCE_MENU_ALREADY_SPONSORED
                    : Message.GOVERNANCE_MENU_JOIN_PETITION, YELLOW), new OpenPopup(Intent.SPONSOR, member.id(), popup.source()));
            if (data.removing(member.id())) sponsor.disabled(label(Message.GOVERNANCE_ERROR_REMOVAL_VOTE_OPEN, YELLOW));
            else if (petition != null && petition.sponsored()) sponsor.disabled(label(Message.GOVERNANCE_MENU_ALREADY_SPONSORED, GRAY));
            else if (!data.activeMember()) sponsor.disabled(label(Message.GOVERNANCE_ERROR_REMOVAL_SPONSOR_INELIGIBLE, RED));
            actions.child(sponsor.build());
        }
        return popupReading("person-detail",blocks,actions.build());
    }

    UiNode popupReading(String key, List<UiNode> blocks, UiNode actions) {
        var source = Objects.requireNonNull(state.popup);
        long revision = state.detailRevision;
        var document = Ui.document().key("reading").readingBody().blocks(blocks)
                .position(state.popupPosition(),position -> dispatch(new ReadPopup(revision,source,position))).build();
        var scoped = UiTemplates.focus(Ui.layout(UiLayouts.contentColumn(document.key(),UiLength.ui(8))).key("popup:" + source.subject() + ":" + source.intent())
                .child(document).child(actions).build(),document.key());
        return UiTemplates.focus(Ui.column().key(key).child(scoped).build(),scoped.key());
    }

    void dispatch(GovernanceWorkspaceAction action) { actions.accept(action); }

    Ui.ElementBuilder button(String key, Component label, GovernanceWorkspaceAction action) {
        var button = Ui.button(label).key(key).primary((_,_) -> actions.accept(action));
        if (state.busy) button.disabled(label(Message.GOVERNANCE_MENU_LOADING, GRAY));
        return button;
    }
    UiElement info(String key, Component label) { return info(key, label, UiStyle.PLAIN); }
    UiElement info(String key, Component label, UiStyle style) {
        return Ui.text(label).key(key).style(style).alignment(FloatingMenuDecoration.Alignment.LEFT).build();
    }
    Component label(Message message) { return label(message, WHITE); }
    Component label(Message message, NamedTextColor color, Object... args) { return Component.text(text.t(language, message, args), color); }
    private Component presence(UUID id) {
        return presence(online.test(id));
    }
    private Component presence(boolean visible) {
        return label(visible ? Message.GOVERNANCE_MENU_ONLINE : Message.GOVERNANCE_MENU_OFFLINE, visible ? GREEN : GRAY);
    }
    Component avatar(UUID id) { return Component.object(ObjectContents.playerHead(id)); }

    UiNode pager(FloatingMenuPage page, java.util.function.IntFunction<GovernanceWorkspaceAction> action) {
        return UiTemplates.pager(page, index -> actions.accept(action.apply(index)));
    }
}
