package org.encinet.mik.feature.community.governance.command;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.governance.GovernanceText;
import org.encinet.mik.feature.community.governance.removal.RemovalPetition;
import org.encinet.mik.feature.community.governance.removal.RemovalSponsorship;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import org.encinet.mik.feature.community.governance.gateway.GovernanceGateway;
import org.encinet.mik.feature.community.governance.gateway.GovernanceViews;
import org.encinet.mik.protocol.GovernanceActionRequest.Action;

/** Removal petitions and moderator resignation. */
final class RemovalCommandHandler {
    private final GovernanceGateway governance;
    private final GovernanceCommandContext context;
    private final GovernanceText text;

    RemovalCommandHandler(GovernanceGateway governance, GovernanceCommandContext context, GovernanceText text) {
        this.governance = governance;
        this.context = context;
        this.text = text;
    }

    int listPetitions(CommandSender sender) {
        Language language = text.language(sender);
        context.submit(sender, () -> {
            List<RemovalPetition> petitions = governance.matters(null, null).getPetitionsList().stream()
                    .map(GovernanceViews::petition).toList();
            Component message = Component.text(text.t(language,
                    Message.GOVERNANCE_PETITIONS_HEADER), NamedTextColor.GOLD);
            if (petitions.isEmpty()) {
                message = message.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_NONE), NamedTextColor.GRAY));
            }
            for (RemovalPetition petition : petitions) {
                message = message.append(Component.newline()).append(Component.text(
                        text.t(language, Message.GOVERNANCE_PETITION_ROW,
                                petition.subjectName(), petition.sponsorCount(),
                                petition.sponsorsRequired(),
                                text.time(language, petition.expiresAt())), NamedTextColor.WHITE));
            }
            Component result = message;
            context.onMain(() -> sender.sendMessage(result));
        });
        return Command.SINGLE_SUCCESS;
    }

    int resign(CommandSender sender) {
        return resign(sender, () -> {});
    }

    int resign(CommandSender sender, Runnable completed) {
        Player player = context.requirePlayer(sender);
        if (player == null) { completed.run(); return 0; }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        context.submit(sender, () -> {
            governance.act(GovernanceGateway.action(Action.RESIGN, playerId));
            context.onMain(() -> {
                text.broadcast(Message.GOVERNANCE_RESIGNED, NamedTextColor.YELLOW, playerName);
                completed.run();
            });
        }, completed);
        return Command.SINGLE_SUCCESS;
    }

    int sponsorRemoval(CommandSender sender, String targetName) {
        return sponsorRemoval(sender, targetName, () -> { });
    }

    int sponsorRemoval(CommandSender sender, String targetName, Runnable completion) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            text.send(sender, Message.GOVERNANCE_PLAYER_NEVER_JOINED,
                    NamedTextColor.RED, targetName);
            completion.run();
            return 0;
        }
        return sponsorRemoval(sender, target, completion);
    }

    int sponsorRemoval(CommandSender sender, UUID targetId, Runnable completion) {
        return sponsorRemoval(sender, Bukkit.getOfflinePlayer(targetId), completion);
    }

    private int sponsorRemoval(CommandSender sender, OfflinePlayer target, Runnable completion) {
        Player sponsor = context.requirePlayer(sender);
        if (sponsor == null) { completion.run(); return 0; }
        sponsor(context.snapshot(target), sponsor.getUniqueId(), null).whenComplete((result, error) -> context.onMain(() -> {
            if (error != null) context.reportFailure(sender, error);
            else if (!result.newlyAdded() && result.startedVote() == null)
                text.send(sender, Message.GOVERNANCE_PETITION_ALREADY_SPONSORED, NamedTextColor.YELLOW,
                        result.petition().sponsorCount(), result.petition().sponsorsRequired());
            completion.run();
        }));
        return Command.SINGLE_SUCCESS;
    }

    CompletableFuture<RemovalSponsorship> joinPetition(Player sponsor, RemovalPetition petition) {
        return sponsor(context.snapshot(Bukkit.getOfflinePlayer(petition.subjectId())), sponsor.getUniqueId(), petition.id());
    }

    private CompletableFuture<RemovalSponsorship> sponsor(
            GovernanceCommandContext.KnownPlayer known, UUID sponsorId, Long expectedPetition) {
        return context.query(() -> {
            var request = GovernanceGateway.action(Action.SPONSOR, sponsorId).setPlayerId(known.playerId().toString());
            if (expectedPetition != null) request.setItemId(expectedPetition);
            var result = governance.act(request);
            var petition = result.getPetitions(0);
            return new RemovalSponsorship(GovernanceViews.petition(petition),
                    result.getVotesCount() == 0 ? null : GovernanceViews.vote(result.getVotes(0)), petition.getNewlySponsored());
        }).thenApply(result -> {
            context.onMain(() -> announce(result));
            return result;
        });
    }

    private void announce(RemovalSponsorship result) {
        if (result.startedVote() != null) {
            var vote = result.startedVote();
            text.broadcast(language -> text.t(language, Message.GOVERNANCE_VOTE_STARTED, vote.id(),
                    text.kind(language, vote.kind()), vote.subjectName(), vote.eligibleVoters(),
                    vote.quorumRequired(), text.time(language, vote.closesAt())), NamedTextColor.GOLD);
        } else if (result.newlyAdded()) {
            text.broadcast(language -> text.t(language, Message.GOVERNANCE_PETITION_STARTED,
                    result.petition().subjectName(), result.petition().sponsorCount(),
                    result.petition().sponsorsRequired(), text.time(language, result.petition().expiresAt())), NamedTextColor.YELLOW);
        }
    }
}
