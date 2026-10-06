package org.encinet.mik.module.governance.platform.paper.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.module.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.module.governance.membership.promotion.PromotionEligibility;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.module.governance.removal.RemovalPetition;
import org.encinet.mik.module.governance.removal.RemovalService;
import org.encinet.mik.module.governance.voting.VotingService;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuHandle;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.MenuDialogs;
import org.encinet.mik.module.role.RolePermissions;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Player-facing spatial surface over the same governance services used by commands. */
final class GovernanceMenuController {
    private static final int DASHBOARD_VOTES_PER_PAGE = 3;
    private static final int DASHBOARD_RECENT_RESULTS = 2;
    private static final int VOTES_PER_PAGE = 5;
    private static final int PETITIONS_PER_PAGE = 6;

    private final JavaPlugin plugin;
    private final MembershipService membership;
    private final VotingService voting;
    private final RemovalService removal;
    private final GovernanceDeliveryCoordinator coordinator;
    private final GovernanceCommandContext context;
    private final GovernanceText text;
    private final VotingCommandHandler votingCommands;
    private final RemovalCommandHandler removalCommands;
    private final GovernancePlayerMenu players;

    GovernanceMenuController(JavaPlugin plugin, MembershipService membership,
                             VotingService voting, RemovalService removal,
                             GovernanceDeliveryCoordinator coordinator,
                             GovernanceCommandContext context, GovernanceText text,
                             VotingCommandHandler votingCommands,
                             RemovalCommandHandler removalCommands,
                             GovernancePlayerMenu players) {
        this.plugin = plugin;
        this.membership = membership;
        this.voting = voting;
        this.removal = removal;
        this.coordinator = coordinator;
        this.context = context;
        this.text = text;
        this.votingCommands = votingCommands;
        this.removalCommands = removalCommands;
        this.players = players;
    }

    void openMenu(Player player) {
        FloatingMenuHandle handle = FloatingMenus.present(player, loading(player, "governance-main"));
        loadSelf(player, handle.id());
    }

    void openPlayerSearch(Player player) {
        players.search(player);
    }

    private void loadSelf(Player player, UUID handleId) {
        loadSelf(player, handleId, 0, 0);
    }

    private void loadSelf(Player player, UUID handleId, int requestedPage, long selectedId) {
        GovernanceCommandContext.KnownPlayer known = context.snapshot(player);
        context.submit(player, () -> {
            coordinator.reviewOpenVotes();
            context.ensureKnown(known);
            ParticipationSummary participation = membership.participation(known.playerId());
            PromotionEligibility promotion = known.member() ? null
                    : membership.promotionEligibility(known.playerId());
            ActiveMemberEligibility active = known.member()
                    ? membership.activeMemberEligibility(known.playerId()) : null;
            List<GovernanceVote> openVotes = voting.openVotes();
            boolean nominated = openVotes.stream().anyMatch(vote ->
                    vote.kind() == VoteKind.APPOINTMENT
                            && vote.subjectId().equals(known.playerId()));
            List<VoteCard> cards = new java.util.ArrayList<>(openVotes.size());
            for (GovernanceVote vote : openVotes) {
                boolean voter = voting.isVoter(vote.id(), known.playerId());
                VoteChoice choice = voter
                        ? voting.ballotChoice(vote.id(), known.playerId()).orElse(null) : null;
                cards.add(new VoteCard(vote, voter, choice));
            }
            SelfView view = new SelfView(participation, promotion, active, nominated,
                    List.copyOf(cards), voting.recentVotes(DASHBOARD_RECENT_RESULTS));
            context.onMain(() -> updateIfCurrent(player, handleId,
                    selfScreen(player, view, requestedPage, selectedId)));
        }, () -> updateIfCurrent(player, handleId, failure(player,
                "governance-main", Message.GOVERNANCE_OPERATION_ERROR, this::openMenu)));
    }

    private FloatingMenuDefinition selfScreen(Player player, SelfView view,
                                              int requestedPage, long selectedId) {
        Language language = text.language(player);
        FloatingMenuPage page = new FloatingMenuPage(requestedPage,
                view.openVotes().size(), DASHBOARD_VOTES_PER_PAGE);
        List<VoteCard> visibleVotes = page.slice(view.openVotes());
        VoteCard selected = visibleVotes.stream()
                .filter(card -> card.vote().id() == selectedId)
                .findFirst().orElse(visibleVotes.isEmpty() ? null : visibleVotes.getFirst());
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("governance-main")
                .appearance(FloatingMenuAppearance.CONSOLE)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(GovernanceArcLayouts.dashboard());
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_TITLE,
                        NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("status-heading", label(language, Message.GOVERNANCE_MENU_STATUS,
                NamedTextColor.AQUA)).region("status-heading");
        menu.information("status", selfStatus(language, player, view))
                .region("status").textWidth(FloatingMenuTextWidth.EXPANDED);
        menu.information("results-heading", label(language, Message.GOVERNANCE_MENU_RECENT_RESULTS,
                NamedTextColor.GOLD)).region("results-heading");
        if (view.recentResults().isEmpty()) {
            menu.information("results-empty", label(language, Message.GOVERNANCE_NONE,
                    NamedTextColor.GRAY)).region("results");
        }
        for (GovernanceVote vote : view.recentResults()) {
            Component row = Component.text("#" + vote.id() + " · "
                            + text.kind(language, vote.kind()) + " · " + vote.subjectName(),
                            NamedTextColor.WHITE)
                    .append(Component.newline())
                    .append(Component.text(text.result(language, vote), text.resultColor(vote)));
            menu.item("result:" + vote.id(), voteMaterial(vote.kind()), row)
                    .region("results").textWidth(FloatingMenuTextWidth.EXPANDED)
                    .primary((p, handle) -> openHistory(p, 0, vote.id()));
        }
        menu.navigation("history", label(language, Message.GOVERNANCE_MENU_HISTORY,
                        NamedTextColor.YELLOW))
                .region("results-navigation")
                .primary((p, handle) -> openHistory(p, 0, 0));
        menu.information("votes-heading", label(language, Message.GOVERNANCE_MENU_VOTES,
                NamedTextColor.GOLD)).region("votes-heading");
        if (view.openVotes().isEmpty()) {
            menu.information("votes-empty", label(language, Message.GOVERNANCE_NONE,
                    NamedTextColor.GRAY)).region("open-votes");
        }
        for (VoteCard card : visibleVotes) {
            GovernanceVote vote = card.vote();
            Component row = Component.text("#" + vote.id() + " · "
                            + text.kind(language, vote.kind()) + " · " + vote.subjectName(),
                            NamedTextColor.WHITE)
                    .append(Component.newline())
                    .append(Component.text(ownChoice(language, card), NamedTextColor.AQUA));
            menu.item("open-vote:" + vote.id(), voteMaterial(vote.kind()), row)
                    .region("open-votes").textWidth(FloatingMenuTextWidth.WIDE)
                    .selected(selected != null && selected.vote().id() == vote.id())
                    .primary((p, handle) -> handle.update(
                            selfScreen(p, view, page.index(), vote.id())));
        }
        if (page.count() > 1) {
            menu.pagination("vote-pagination", page, index ->
                    FloatingMenus.current(player).ifPresent(handle ->
                            loadSelf(player, handle.id(), index, 0)));
        }
        if (selected != null) {
            menu.information("selected-vote", voteDetail(language, selected))
                    .region("vote-detail").textWidth(FloatingMenuTextWidth.EXPANDED)
                    .keepAccessible();
            addBallotChoices(menu, language, selected, (p, handle) ->
                    loadSelf(p, handle.id(), page.index(), selected.vote().id()));
        }
        menu.information("actions-heading", label(language, Message.GOVERNANCE_MENU_ACTIONS,
                        NamedTextColor.AQUA)).region("actions-heading");
        if (view.nominated()) {
            menu.item("nomination", Material.BARRIER,
                            label(language, Message.GOVERNANCE_MENU_WITHDRAW, NamedTextColor.YELLOW))
                    .region("actions")
                    .primary((p, handle) -> confirm(p, Message.GOVERNANCE_MENU_WITHDRAW,
                            Message.GOVERNANCE_MENU_CONFIRM_WITHDRAW, responder -> {
                                votingCommands.withdraw(responder);
                                loadSelf(responder, handle.id());
                            }));
        } else if (!RolePermissions.canModerate(player)) {
            var nominate = menu.item("nomination", Material.EMERALD,
                            label(language, Message.GOVERNANCE_MENU_NOMINATE, NamedTextColor.GREEN))
                    .region("actions")
                    .primary((p, handle) -> confirm(p, Message.GOVERNANCE_MENU_NOMINATE,
                            Message.GOVERNANCE_MENU_CONFIRM_NOMINATE, responder -> {
                                votingCommands.nominate(responder);
                                loadSelf(responder, handle.id());
                            }));
            if (view.active() == null || !view.active().eligible()) {
                nominate.disabled(label(language, Message.GOVERNANCE_ERROR_CANDIDATE_INELIGIBLE,
                        NamedTextColor.RED));
            }
        }
        menu.item("petitions", Material.PAPER,
                        label(language, Message.GOVERNANCE_MENU_PETITIONS, NamedTextColor.AQUA))
                .region("actions").primary((p, handle) -> openPetitions(p, 0));
        if (RolePermissions.canModerate(player)) {
            menu.item("players", Material.PLAYER_HEAD,
                            label(language, Message.GOVERNANCE_MENU_PLAYERS, NamedTextColor.AQUA))
                    .region("actions").primary((p, handle) -> players.search(p));
        }
        if (RolePermissions.canModerate(player) && !RolePermissions.isCustodian(player)) {
            menu.item("resign", Material.IRON_DOOR,
                            label(language, Message.GOVERNANCE_MENU_RESIGN, NamedTextColor.RED))
                    .region("actions")
                    .primary((p, handle) -> confirm(p, Message.GOVERNANCE_MENU_RESIGN,
                            Message.GOVERNANCE_MENU_CONFIRM_RESIGN, responder -> {
                                handle.close();
                                removalCommands.resign(responder);
                            }));
        }
        menu.item("refresh", Material.CLOCK,
                        label(language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.GRAY))
                .region("navigation").keepAccessible().primary((p, handle) -> loadSelf(p,
                        handle.id(), page.index(), selected == null ? 0 : selected.vote().id()));
        menu.dismiss(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private Component selfStatus(Language language, Player player, SelfView view) {
        Message role = RolePermissions.isCustodian(player) ? Message.ROLE_CUSTODIAN
                : RolePermissions.canModerate(player) ? Message.ROLE_MODERATOR
                : RolePermissions.isMember(player) ? Message.ROLE_MEMBER
                : Message.ROLE_NEW_PLAYER;
        TextComponent.Builder status = Component.text()
                .append(label(language, Message.GOVERNANCE_MENU_STATUS, NamedTextColor.AQUA))
                .append(Component.text(" · " + text.t(language, role), NamedTextColor.WHITE))
                .append(Component.newline());
        if (view.promotion() != null) {
            PromotionEligibility check = view.promotion();
            status.append(label(language, Message.GOVERNANCE_PROMOTION_HEADER, NamedTextColor.GOLD))
                    .append(Component.newline())
                    .append(text.line(language, check.accountAgeMet(),
                            Message.GOVERNANCE_PROMOTION_ACCOUNT_AGE))
                    .append(Component.newline())
                    .append(text.line(language, check.playtimeMet(),
                            Message.GOVERNANCE_PROMOTION_PLAYTIME,
                            text.hours(language, view.participation().lifetimeSeconds())))
                    .append(Component.newline())
                    .append(text.line(language, check.playDaysMet(),
                            Message.GOVERNANCE_PROMOTION_PLAY_DAYS,
                            view.participation().lifetimePlayDays()))
                    .append(Component.newline())
                    .append(text.line(language, check.noActiveBan(), Message.GOVERNANCE_NO_ACTIVE_BAN))
                    .append(Component.newline())
                    .append(text.line(language, check.noActivePause(), Message.GOVERNANCE_NO_ACTIVE_PAUSE));
            if (check.activePause() != null) {
                status.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_PAUSE_DETAILS,
                                check.activePause().reason(),
                                text.time(language, check.activePause().endsAt()),
                                check.activePause().appealPath()), NamedTextColor.YELLOW));
            }
        } else {
            ActiveMemberEligibility check = view.active();
            status.append(label(language, Message.GOVERNANCE_ACTIVE_HEADER, NamedTextColor.GOLD))
                    .append(Component.newline())
                    .append(text.line(language, check.currentMember(), Message.GOVERNANCE_ACTIVE_MEMBER))
                    .append(Component.newline())
                    .append(text.line(language, check.memberAgeMet(), Message.GOVERNANCE_ACTIVE_MEMBER_AGE))
                    .append(Component.newline())
                    .append(text.line(language, check.playtimeMet(), Message.GOVERNANCE_ACTIVE_PLAYTIME,
                            text.hours(language, view.participation().secondsLast60Days())))
                    .append(Component.newline())
                    .append(text.line(language, check.participationDaysMet(),
                            Message.GOVERNANCE_ACTIVE_DAYS_60,
                            view.participation().participationDaysLast60()))
                    .append(Component.newline())
                    .append(text.line(language, check.recentDaysMet(),
                            Message.GOVERNANCE_ACTIVE_DAYS_30,
                            view.participation().participationDaysLast30()))
                    .append(Component.newline())
                    .append(text.line(language, check.noActiveBan(), Message.GOVERNANCE_NO_ACTIVE_BAN));
        }
        if (view.nominated()) {
            status.append(Component.newline())
                    .append(label(language, Message.GOVERNANCE_MENU_NOMINATION_OPEN,
                            NamedTextColor.YELLOW));
        }
        return status.build();
    }

    private void openHistory(Player player, int requestedPage, long selectedId) {
        FloatingMenuHandle handle = FloatingMenus.present(player,
                loading(player, "governance-history"));
        loadHistory(player, handle.id(), requestedPage, selectedId);
    }

    private void loadHistory(Player player, UUID handleId,
                             int requestedPage, long selectedId) {
        UUID playerId = player.getUniqueId();
        context.submit(player, () -> {
            coordinator.reviewOpenVotes();
            List<GovernanceVote> votes = voting.recentVotes(10);
            List<VoteCard> cards = new java.util.ArrayList<>();
            for (GovernanceVote vote : votes) {
                boolean voter = voting.isVoter(vote.id(), playerId);
                VoteChoice choice = voter
                        ? voting.ballotChoice(vote.id(), playerId).orElse(null) : null;
                cards.add(new VoteCard(vote, voter, choice));
            }
            context.onMain(() -> updateIfCurrent(player, handleId,
                    historyScreen(player, cards, requestedPage, selectedId)));
        }, () -> updateIfCurrent(player, handleId, failure(player,
                "governance-history", Message.GOVERNANCE_OPERATION_ERROR,
                p -> openHistory(p, requestedPage, selectedId))));
    }

    private FloatingMenuDefinition historyScreen(Player player, List<VoteCard> cards,
                                                  int requestedPage, long selectedId) {
        Language language = text.language(player);
        FloatingMenuPage page = new FloatingMenuPage(requestedPage, cards.size(), VOTES_PER_PAGE);
        VoteCard selected = cards.stream().filter(card -> card.vote().id() == selectedId)
                .findFirst().orElse(cards.isEmpty() ? null : cards.get(page.fromIndex()));
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("governance-history")
                .appearance(FloatingMenuAppearance.ARCHIVE)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(GovernanceArcLayouts.history());
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_HISTORY,
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        if (cards.isEmpty()) {
            menu.information("empty", label(language, Message.GOVERNANCE_NONE,
                    NamedTextColor.GRAY)).region("votes");
        }
        for (VoteCard card : page.slice(cards)) {
            GovernanceVote vote = card.vote();
            Component row = Component.text("#" + vote.id() + " · "
                            + text.kind(language, vote.kind()) + " · "
                            + vote.subjectName(), NamedTextColor.WHITE)
                    .append(Component.newline()).append(Component.text(
                            text.result(language, vote), NamedTextColor.GRAY));
            menu.item("vote:" + vote.id(), voteMaterial(vote.kind()), row)
                    .region("votes").textWidth(FloatingMenuTextWidth.WIDE)
                    .selected(selected != null && selected.vote().id() == vote.id())
                    .primary((p, handle) -> handle.update(historyScreen(
                            p, cards, page.index(), vote.id())));
        }
        if (page.count() > 1) {
            menu.pagination("pagination", page, index ->
                    FloatingMenus.current(player).ifPresent(handle ->
                            handle.update(historyScreen(player, cards, index, 0))));
        }
        if (selected != null) {
            menu.information("selected", voteDetail(language, selected)).region("detail")
                    .textWidth(FloatingMenuTextWidth.EXPANDED).keepAccessible();
        }
        menu.item("refresh", Material.CLOCK,
                        label(language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.GRAY))
                .region("navigation").keepAccessible().primary((p, handle) ->
                        loadHistory(p, handle.id(), page.index(),
                                selected == null ? 0 : selected.vote().id()));
        menu.back(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private Component voteDetail(Language language, VoteCard selected) {
        GovernanceVote vote = selected.vote();
        TextComponent.Builder detail = Component.text()
                .append(Component.text(text.t(language, Message.GOVERNANCE_VOTE_HEADER,
                        vote.id()), NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text(text.t(language, Message.GOVERNANCE_VOTE_SUBJECT,
                        text.kind(language, vote.kind()), vote.subjectName()),
                        NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(text.t(language, Message.GOVERNANCE_VOTE_ELECTORATE,
                        vote.eligibleVoters(), vote.quorumRequired()), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(text.t(language, Message.GOVERNANCE_VOTE_PERIOD,
                        text.time(language, vote.opensAt()),
                        text.time(language, vote.closesAt())), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(vote.status() == VoteStatus.OPEN
                        ? text.t(language, Message.GOVERNANCE_VOTE_IN_PROGRESS,
                                vote.tally().participating())
                        : text.result(language, vote), NamedTextColor.YELLOW))
                .append(Component.newline())
                .append(Component.text(ownChoice(language, selected), NamedTextColor.AQUA));
        String proposal = text.proposalDetails(language, vote.proposal());
        if (proposal != null) detail.append(Component.newline())
                .append(Component.text(proposal, NamedTextColor.GRAY));
        return detail.build();
    }

    private void addBallotChoices(FloatingMenuDefinition.Builder menu, Language language,
                                  VoteCard selected,
                                  BiConsumer<Player, FloatingMenuHandle> afterVote) {
        GovernanceVote vote = selected.vote();
        if (vote.status() != VoteStatus.OPEN || !selected.voter()) return;
        for (VoteChoice choice : VoteChoice.values()) {
            Message choiceLabel = switch (choice) {
                case YES -> Message.GOVERNANCE_CHOICE_YES;
                case NO -> Message.GOVERNANCE_CHOICE_NO;
                case ABSTAIN -> Message.GOVERNANCE_CHOICE_ABSTAIN;
            };
            Material icon = switch (choice) {
                case YES -> Material.LIME_DYE;
                case NO -> Material.RED_DYE;
                case ABSTAIN -> Material.GRAY_DYE;
            };
            menu.choice("choice:" + choice, selected.choice() == choice, icon,
                            label(language, choiceLabel, NamedTextColor.WHITE))
                    .region("ballot").keepAccessible()
                    .primary((p, handle) -> {
                        votingCommands.vote(p, vote.id(), choice.name());
                        afterVote.accept(p, handle);
                    });
        }
    }

    private void openPetitions(Player player, int requestedPage) {
        FloatingMenuHandle handle = FloatingMenus.present(
                player, loading(player, "governance-petitions"));
        loadPetitions(player, handle.id(), requestedPage);
    }

    private void loadPetitions(Player player, UUID handleId, int requestedPage) {
        GovernanceCommandContext.KnownPlayer known = context.snapshot(player);
        context.submit(player, () -> {
            context.ensureKnown(known);
            boolean eligible = known.member()
                    && membership.activeMemberEligibility(known.playerId()).eligible();
            List<RemovalPetition> petitions = removal.gatheringPetitions();
            context.onMain(() -> updateIfCurrent(player, handleId,
                    petitionScreen(player, petitions, eligible, requestedPage)));
        }, () -> updateIfCurrent(player, handleId, failure(player,
                "governance-petitions", Message.GOVERNANCE_OPERATION_ERROR,
                p -> openPetitions(p, requestedPage))));
    }

    private FloatingMenuDefinition petitionScreen(Player player,
                                                   List<RemovalPetition> petitions,
                                                   boolean eligible, int requestedPage) {
        Language language = text.language(player);
        FloatingMenuPage page = new FloatingMenuPage(requestedPage,
                petitions.size(), PETITIONS_PER_PAGE);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("governance-petitions")
                .appearance(FloatingMenuAppearance.ARCHIVE)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(GovernanceArcLayouts.petitions());
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_PETITIONS,
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        if (petitions.isEmpty()) {
            menu.information("empty", label(language, Message.GOVERNANCE_NONE,
                    NamedTextColor.GRAY)).region("petitions-left");
        }
        List<RemovalPetition> visiblePetitions = page.slice(petitions);
        for (int index = 0; index < visiblePetitions.size(); index++) {
            RemovalPetition petition = visiblePetitions.get(index);
            var entry = menu.item("petition:" + petition.id(), Material.PAPER,
                            Component.text(text.t(language, Message.GOVERNANCE_MENU_PETITION_ROW,
                                    petition.subjectName(), petition.sponsorCount(),
                                    petition.sponsorsRequired()), NamedTextColor.WHITE))
                    .region(index < PETITIONS_PER_PAGE / 2 ? "petitions-left" : "petitions-right")
                    .primary((p, handle) -> confirm(p, Message.GOVERNANCE_MENU_PETITIONS,
                            Message.GOVERNANCE_MENU_CONFIRM_SPONSOR, responder -> {
                                removalCommands.sponsorRemoval(responder, petition.subjectId(),
                                        () -> loadPetitions(responder, handle.id(), page.index()));
                            }));
            if (!eligible || petition.subjectId().equals(player.getUniqueId())) {
                entry.disabled(label(language, Message.GOVERNANCE_ERROR_REMOVAL_SPONSOR_INELIGIBLE,
                        NamedTextColor.RED));
            }
        }
        if (page.count() > 1) {
            menu.pagination("pagination", page, index ->
                    FloatingMenus.current(player).ifPresent(handle ->
                            handle.update(petitionScreen(player, petitions, eligible, index))));
        }
        var newPetition = menu.item("new", Material.WRITABLE_BOOK,
                        label(language, Message.GOVERNANCE_MENU_NEW_PETITION, NamedTextColor.AQUA))
                .region("navigation").keepAccessible()
                .primary((p, handle) -> MenuDialogs.openTextInput(plugin, p,
                        label(language, Message.GOVERNANCE_MENU_NEW_PETITION, NamedTextColor.GOLD),
                        label(language, Message.GOVERNANCE_MENU_TARGET, NamedTextColor.WHITE),
                        "", 36, false,
                        label(language, Message.GOVERNANCE_MENU_CONFIRM, NamedTextColor.GREEN),
                        label(language, Message.GOVERNANCE_MENU_CANCEL, NamedTextColor.GRAY),
                        (responder, name) -> {
                            if (name.isBlank()) return;
                            removalCommands.sponsorRemoval(responder, name,
                                    () -> loadPetitions(responder, handle.id(), page.index()));
                        }));
        if (!eligible) newPetition.disabled(label(language,
                Message.GOVERNANCE_ERROR_REMOVAL_SPONSOR_INELIGIBLE, NamedTextColor.RED));
        menu.item("refresh", Material.CLOCK,
                        label(language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.GRAY))
                .region("navigation").keepAccessible().primary((p, handle) ->
                        loadPetitions(p, handle.id(), page.index()));
        menu.back(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private FloatingMenuDefinition loading(Player player, String screenId) {
        Language language = text.language(player);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(screenId)
                .appearance(FloatingMenuAppearance.CONSOLE)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(GovernanceArcLayouts.status());
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_TITLE,
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("loading", label(language, Message.GOVERNANCE_MENU_LOADING,
                NamedTextColor.GRAY)).region("status").keepAccessible();
        menu.dismiss(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private FloatingMenuDefinition failure(Player player, String screenId,
                                           Message reason,
                                           java.util.function.Consumer<Player> retry) {
        Language language = text.language(player);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(screenId)
                .appearance(FloatingMenuAppearance.CONSOLE)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(GovernanceArcLayouts.status());
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_TITLE,
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("error", label(language, reason, NamedTextColor.RED))
                .region("status").keepAccessible();
        menu.item("refresh", Material.CLOCK,
                        label(language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.AQUA))
                .region("navigation").keepAccessible().primary((p, handle) -> retry.accept(p));
        menu.dismiss(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private void updateIfCurrent(Player player, UUID handleId, FloatingMenuDefinition screen) {
        if (!player.isOnline()) return;
        FloatingMenus.current(player).filter(handle -> handle.id().equals(handleId))
                .ifPresent(handle -> handle.update(screen));
    }

    private void confirm(Player player, Message title, Message question,
                         java.util.function.Consumer<Player> action) {
        Language language = text.language(player);
        MenuDialogs.openConfirm(plugin, player,
                label(language, title, NamedTextColor.GOLD),
                label(language, question, NamedTextColor.WHITE),
                label(language, Message.GOVERNANCE_MENU_CONFIRM, NamedTextColor.GREEN),
                label(language, Message.GOVERNANCE_MENU_CANCEL, NamedTextColor.GRAY), action);
    }

    private Component label(Language language, Message message, NamedTextColor color) {
        return Component.text(text.t(language, message), color);
    }

    private String ownChoice(Language language, VoteCard card) {
        if (!card.voter()) return text.t(language, Message.GOVERNANCE_OWN_NOT_VOTER);
        if (card.choice() == null) return text.t(language, Message.GOVERNANCE_OWN_NOT_VOTED);
        return text.t(language, card.vote().status() == VoteStatus.OPEN
                        ? Message.GOVERNANCE_OWN_CHOICE_OPEN : Message.GOVERNANCE_OWN_CHOICE_CLOSED,
                text.choice(language, card.choice()));
    }

    private static Material voteMaterial(VoteKind kind) {
        return switch (kind) {
            case APPOINTMENT -> Material.EMERALD;
            case REMOVAL -> Material.IRON_AXE;
            case BAN -> Material.BARRIER;
        };
    }

    private record SelfView(ParticipationSummary participation,
                            PromotionEligibility promotion, ActiveMemberEligibility active,
                            boolean nominated, List<VoteCard> openVotes,
                            List<GovernanceVote> recentResults) { }

    private record VoteCard(GovernanceVote vote, boolean voter, VoteChoice choice) { }
}
