package org.encinet.mik.module.governance.platform.paper.command;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.module.governance.voting.VotingService;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteChoice;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteStatus;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.role.RolePermissions;

import java.util.List;
import java.util.UUID;

/** Nomination, ballot, and vote-query command handlers. */
final class VotingCommandHandler {
    private final MembershipService membership;
    private final VotingService voting;
    private final GovernanceDeliveryCoordinator coordinator;
    private final GovernanceCommandContext context;
    private final GovernanceText text;

    VotingCommandHandler(MembershipService membership, VotingService voting,
                         GovernanceDeliveryCoordinator coordinator,
                         GovernanceCommandContext context, GovernanceText text) {
        this.membership = membership;
        this.voting = voting;
        this.coordinator = coordinator;
        this.context = context;
        this.text = text;
    }

    int nominate(CommandSender sender) {
        Player player = context.requirePlayer(sender);
        if (player == null) return 0;
        if (RolePermissions.isCustodian(player)) {
            text.send(sender, Message.GOVERNANCE_CUSTODIAN_NO_NOMINATION, NamedTextColor.YELLOW);
            return 0;
        }
        if (RolePermissions.canModerate(player)) {
            text.send(sender, Message.GOVERNANCE_ALREADY_MODERATOR, NamedTextColor.YELLOW);
            return 0;
        }
        UUID playerId = player.getUniqueId();
        context.submit(sender, () -> {
            coordinator.reviewOpenVotes();
            PlayerProfile subject = membership.player(playerId)
                    .orElseThrow(() -> new GovernanceException(GovernanceException.Code.PLAYER_UNKNOWN));
            GovernanceVote started = voting.startVote(new VoteProposal.Appointment(
                    subject.playerId(), subject.playerName()));
            context.onMain(() -> text.broadcast(language -> text.t(language,
                    Message.GOVERNANCE_VOTE_STARTED, started.id(),
                    text.kind(language, started.kind()), started.subjectName(),
                    started.eligibleVoters(), started.quorumRequired(),
                    text.time(language, started.closesAt())), NamedTextColor.GOLD));
        });
        return Command.SINGLE_SUCCESS;
    }

    int withdraw(CommandSender sender) {
        Player player = context.requirePlayer(sender);
        if (player == null) return 0;
        UUID playerId = player.getUniqueId();
        context.submit(sender, () -> {
            coordinator.reviewOpenVotes();
            GovernanceVote ownVote = voting.openVotes().stream()
                    .filter(open -> open.kind() == VoteKind.APPOINTMENT
                            && open.subjectId().equals(playerId))
                    .findFirst()
                    .orElseThrow(() -> new GovernanceException(
                            GovernanceException.Code.NOMINATION_NOT_OPEN));
            voting.terminateVote(ownVote.id(), VoteTerminationReason.CANDIDATE_WITHDREW);
            context.onMain(() -> text.send(sender, Message.GOVERNANCE_NOMINATION_WITHDRAWN,
                    NamedTextColor.GREEN));
        });
        return Command.SINGLE_SUCCESS;
    }

    int vote(CommandSender sender, long voteId, String choiceText) {
        Player player = context.requirePlayer(sender);
        if (player == null) return 0;
        VoteChoice choice = VoteChoice.parse(choiceText).orElse(null);
        if (choice == null) {
            text.send(sender, Message.GOVERNANCE_INVALID_CHOICE, NamedTextColor.RED);
            return 0;
        }
        UUID playerId = player.getUniqueId();
        Language language = text.language(sender);
        context.submit(sender, () -> {
            voting.castBallot(voteId, playerId, choice);
            context.onMain(() -> text.send(sender, Message.GOVERNANCE_BALLOT_RECORDED,
                    NamedTextColor.GREEN, text.choice(language, choice)));
        });
        return Command.SINGLE_SUCCESS;
    }

    int listVotes(CommandSender sender) {
        UUID requesterId = sender instanceof Player player ? player.getUniqueId() : null;
        Language language = text.language(sender);
        context.submit(sender, () -> {
            coordinator.reviewOpenVotes();
            List<GovernanceVote> votes = voting.openVotes();
            Component message = Component.text(text.t(language,
                    Message.GOVERNANCE_VOTES_HEADER), NamedTextColor.GOLD);
            if (votes.isEmpty()) {
                message = message.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_NONE), NamedTextColor.GRAY));
            }
            for (GovernanceVote vote : votes) {
                String ownState = requesterId == null ? ""
                        : ownVoteState(language, vote, requesterId);
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
            coordinator.reviewOpenVotes();
            GovernanceVote vote = voting.vote(voteId);
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
            String details = text.proposalDetails(language, vote.proposal());
            if (details != null) {
                message = message.append(Component.newline()).append(Component.text(
                        details, NamedTextColor.WHITE));
            }
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
                        ownVoteDetail(language, vote, requesterId), NamedTextColor.AQUA));
            }
            Component result = message;
            context.onMain(() -> sender.sendMessage(result));
        });
        return Command.SINGLE_SUCCESS;
    }

    int voteHistory(CommandSender sender) {
        Language language = text.language(sender);
        context.submit(sender, () -> {
            coordinator.reviewOpenVotes();
            List<GovernanceVote> votes = voting.recentVotes(10);
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
                                text.result(language, vote),
                                text.resultProposalSuffix(language, vote.proposal())),
                        text.resultColor(vote)));
            }
            Component result = message;
            context.onMain(() -> sender.sendMessage(result));
        });
        return Command.SINGLE_SUCCESS;
    }

    private String ownVoteState(Language language, GovernanceVote vote, UUID requesterId)
            throws GovernanceException {
        if (!voting.isVoter(vote.id(), requesterId)) {
            return text.t(language, Message.GOVERNANCE_OWN_NOT_VOTER);
        }
        return text.t(language, voting.ballotChoice(vote.id(), requesterId).isPresent()
                ? Message.GOVERNANCE_OWN_VOTED : Message.GOVERNANCE_OWN_NOT_VOTED);
    }

    private String ownVoteDetail(Language language, GovernanceVote vote, UUID requesterId)
            throws GovernanceException {
        if (!voting.isVoter(vote.id(), requesterId)) {
            return text.t(language, Message.GOVERNANCE_OWN_NOT_VOTER);
        }
        VoteChoice choice = voting.ballotChoice(vote.id(), requesterId).orElse(null);
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
