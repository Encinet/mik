package org.encinet.mik.feature.community.governance.command;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.governance.GovernanceText;
import org.encinet.mik.feature.community.governance.voting.GovernanceVote;
import org.encinet.mik.feature.community.governance.voting.VoteChoice;
import org.encinet.mik.feature.community.governance.voting.VoteStatus;
import org.encinet.mik.feature.community.governance.gateway.GovernanceGateway;
import org.encinet.mik.feature.community.governance.gateway.GovernanceViews;
import org.encinet.mik.protocol.GovernanceActionRequest.Action;
import org.encinet.mik.protocol.GovernanceBallot;
import org.encinet.mik.protocol.GovernanceCredential;
import org.encinet.mik.protocol.GovernanceVoteView;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Nomination, ballot, and vote-query command handlers. */
final class VotingCommandHandler {
    private final GovernanceGateway governance;
    private final GovernanceCommandContext context;
    private final GovernanceText text;
    private final Function<UUID, GovernanceCredential> credentials;

    VotingCommandHandler(GovernanceGateway governance, GovernanceCommandContext context, GovernanceText text,
                         Function<UUID, GovernanceCredential> credentials) {
        this.governance = governance;
        this.context = context;
        this.text = text;
        this.credentials = credentials;
    }

    int nominate(CommandSender sender) {
        return nominate(sender, () -> {});
    }

    int nominate(CommandSender sender, Runnable completed) {
        Player player = context.requirePlayer(sender);
        if (player == null) { completed.run(); return 0; }
        if (RolePermissions.isCustodian(player)) {
            text.send(sender, Message.GOVERNANCE_CUSTODIAN_NO_NOMINATION, NamedTextColor.YELLOW);
            completed.run();
            return 0;
        }
        UUID playerId = player.getUniqueId();
        context.submit(sender, () -> {
            GovernanceVote started = GovernanceViews.vote(governance.act(
                    GovernanceGateway.action(Action.APPLY, playerId).setCredential(credentials.apply(playerId))).getVotes(0));
            context.onMain(() -> text.broadcast(language -> text.t(language,
                    Message.GOVERNANCE_VOTE_STARTED, started.id(),
                    text.kind(language, started.kind()), started.subjectName(),
                    started.eligibleVoters(), started.quorumRequired(),
                    text.time(language, started.closesAt())), NamedTextColor.GOLD));
            context.onMain(completed);
        }, completed);
        return Command.SINGLE_SUCCESS;
    }

    int withdraw(CommandSender sender) {
        return withdraw(sender, () -> {});
    }

    int withdraw(CommandSender sender, Runnable completed) {
        Player player = context.requirePlayer(sender);
        if (player == null) { completed.run(); return 0; }
        UUID playerId = player.getUniqueId();
        context.submit(sender, () -> {
            governance.act(GovernanceGateway.action(Action.WITHDRAW, playerId));
            context.onMain(() -> text.send(sender, Message.GOVERNANCE_NOMINATION_WITHDRAWN,
                    NamedTextColor.GREEN));
            context.onMain(completed);
        }, completed);
        return Command.SINGLE_SUCCESS;
    }

    int vote(CommandSender sender, long voteId, String choiceText) {
        return vote(sender, voteId, choiceText, () -> {});
    }

    int vote(CommandSender sender, long voteId, String choiceText, Runnable completed) {
        Player player = context.requirePlayer(sender);
        if (player == null) { completed.run(); return 0; }
        VoteChoice choice = VoteChoice.parse(choiceText).orElse(null);
        if (choice == null) {
            text.send(sender, Message.GOVERNANCE_INVALID_CHOICE, NamedTextColor.RED);
            completed.run();
            return 0;
        }
        UUID playerId = player.getUniqueId();
        Language language = text.language(sender);
        context.submit(sender, () -> {
            governance.act(GovernanceGateway.action(Action.VOTE, playerId).setItemId(voteId)
                    .setBallot(GovernanceBallot.valueOf(choice.name())));
            context.onMain(() -> text.send(sender, Message.GOVERNANCE_BALLOT_RECORDED,
                    NamedTextColor.GREEN, text.choice(language, choice)));
            context.onMain(completed);
        }, completed);
        return Command.SINGLE_SUCCESS;
    }

    int listVotes(CommandSender sender) {
        UUID requesterId = sender instanceof Player player ? player.getUniqueId() : null;
        Language language = text.language(sender);
        context.submit(sender, () -> {
            List<GovernanceVoteView> votes = governance.matters(requesterId, null).getVotesList();
            Component message = Component.text(text.t(language,
                    Message.GOVERNANCE_VOTES_HEADER), NamedTextColor.GOLD);
            if (votes.isEmpty()) {
                message = message.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_NONE), NamedTextColor.GRAY));
            }
            for (var view : votes) {
                var vote = GovernanceViews.vote(view);
                String ownState = requesterId == null ? ""
                        : ownVoteState(language, view);
                message = message.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_VOTE_LIST_ROW,
                                vote.id(), text.kind(language, vote.kind()), vote.subjectName(),
                                text.time(language, vote.closesAt()), vote.eligibleVoters(),
                                vote.tally().participating(), vote.quorumRequired(), ownState),
                        NamedTextColor.WHITE));
            }
            Component result = message;
            context.onMain(() -> sender.sendMessage(result));
        });
        return Command.SINGLE_SUCCESS;
    }

    int showVote(CommandSender sender, long voteId) {
        UUID requesterId = sender instanceof Player player ? player.getUniqueId() : null;
        Language language = text.language(sender);
        context.submit(sender, () -> {
            var view = governance.vote(voteId, requesterId);
            GovernanceVote vote = GovernanceViews.vote(view);
            Component message = Component.text(text.t(language,
                            Message.GOVERNANCE_VOTE_HEADER, vote.id()), NamedTextColor.GOLD)
                    .append(Component.newline()).append(Component.text(
                            text.t(language, Message.GOVERNANCE_VOTE_SUBJECT,
                                    text.kind(language, vote.kind()), vote.subjectName()),
                            NamedTextColor.WHITE))
                    .append(Component.newline()).append(Component.text(
                            text.t(language, Message.GOVERNANCE_VOTE_ELECTORATE,
                                    vote.eligibleVoters(), vote.quorumRequired()),
                            NamedTextColor.WHITE))
                    .append(Component.newline()).append(Component.text(
                            text.t(language, Message.GOVERNANCE_VOTE_PERIOD,
                                    text.time(language, vote.opensAt()),
                                    text.time(language, vote.closesAt())), NamedTextColor.GRAY));
            if (vote.status() == VoteStatus.OPEN) {
                message = message.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_VOTE_IN_PROGRESS,
                                vote.tally().participating()), NamedTextColor.YELLOW));
            } else {
                message = message.append(Component.newline()).append(Component.text(
                                text.result(language, vote), text.resultColor(vote)))
                        .append(Component.newline()).append(Component.text(
                                text.t(language, Message.GOVERNANCE_VOTE_FINALIZED,
                                        text.time(language, vote.finalizedAt())), NamedTextColor.GRAY));
            }
            if (requesterId != null) {
                message = message.append(Component.newline()).append(Component.text(
                        ownVoteDetail(language, vote, view), NamedTextColor.AQUA));
            }
            Component result = message;
            context.onMain(() -> sender.sendMessage(result));
        });
        return Command.SINGLE_SUCCESS;
    }

    int voteHistory(CommandSender sender) {
        Language language = text.language(sender);
        context.submit(sender, () -> {
            var votes = governance.history(null, null, 10, 0).getVotesList().stream().map(GovernanceViews::vote).toList();
            Component message = Component.text(text.t(language,
                    Message.GOVERNANCE_HISTORY_HEADER), NamedTextColor.GOLD);
            if (votes.isEmpty()) {
                message = message.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_NONE), NamedTextColor.GRAY));
            }
            for (GovernanceVote vote : votes) {
                message = message.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_HISTORY_ROW,
                                vote.id(), text.kind(language, vote.kind()), vote.subjectName(),
                                text.result(language, vote)),
                        text.resultColor(vote)));
            }
            Component result = message;
            context.onMain(() -> sender.sendMessage(result));
        });
        return Command.SINGLE_SUCCESS;
    }

    private String ownVoteState(Language language, GovernanceVoteView view) {
        if (!view.getEligibleToVote()) {
            return text.t(language, Message.GOVERNANCE_OWN_NOT_VOTER);
        }
        return text.t(language, GovernanceViews.choice(view.getOwnBallot()) != null
                ? Message.GOVERNANCE_OWN_VOTED : Message.GOVERNANCE_OWN_NOT_VOTED);
    }

    private String ownVoteDetail(Language language, GovernanceVote vote, GovernanceVoteView view) {
        if (!view.getEligibleToVote()) {
            return text.t(language, Message.GOVERNANCE_OWN_NOT_VOTER);
        }
        VoteChoice choice = GovernanceViews.choice(view.getOwnBallot());
        if (choice == null) {
            return text.t(language, vote.status() == VoteStatus.OPEN
                    ? Message.GOVERNANCE_OWN_NOT_VOTED_HINT
                    : Message.GOVERNANCE_OWN_NOT_VOTED, vote.id());
        }
        return text.t(language, vote.status() == VoteStatus.OPEN
                ? Message.GOVERNANCE_OWN_CHOICE_OPEN
                : Message.GOVERNANCE_OWN_CHOICE_CLOSED, text.choice(language, choice));
    }
}
