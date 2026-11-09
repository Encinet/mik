package org.encinet.mik.feature.community.governance.platform.paper.command;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.membership.profile.PlayerProfile;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.feature.community.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.feature.community.governance.removal.RemovalPetition;
import org.encinet.mik.feature.community.governance.removal.RemovalService;
import org.encinet.mik.feature.community.governance.removal.RemovalSponsorship;
import org.encinet.mik.feature.community.governance.voting.VotingService;
import org.encinet.mik.feature.community.governance.voting.model.VoteKind;
import org.encinet.mik.feature.community.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.encinet.mik.feature.community.governance.GovernanceException;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/** Removal petitions and moderator resignation. */
final class RemovalCommandHandler {
    private final JavaPlugin plugin;
    private final MembershipService membership;
    private final VotingService voting;
    private final RemovalService removal;
    private final LuckPermsGovernanceRoles roleManager;
    private final GovernanceDeliveryCoordinator coordinator;
    private final GovernanceCommandContext context;
    private final GovernanceText text;

    RemovalCommandHandler(JavaPlugin plugin, MembershipService membership,
                          VotingService voting, RemovalService removal,
                          LuckPermsGovernanceRoles roleManager,
                          GovernanceDeliveryCoordinator coordinator,
                          GovernanceCommandContext context, GovernanceText text) {
        this.plugin = plugin;
        this.membership = membership;
        this.voting = voting;
        this.removal = removal;
        this.roleManager = roleManager;
        this.coordinator = coordinator;
        this.context = context;
        this.text = text;
    }

    int listPetitions(CommandSender sender) {
        Language language = text.language(sender);
        context.submit(sender, () -> {
            List<RemovalPetition> petitions = removal.gatheringPetitions();
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
        if (RolePermissions.isCustodian(player)) {
            text.send(sender, Message.GOVERNANCE_CUSTODIAN_NO_RESIGN, NamedTextColor.RED);
            completed.run();
            return 0;
        }
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        roleManager.isModerator(playerId).whenComplete((moderator, lookupError) ->
                context.onMain(() -> {
            if (lookupError != null) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not inspect moderator role for resignation", lookupError);
                text.send(sender, Message.GOVERNANCE_ROLE_LOOKUP_ERROR, NamedTextColor.RED);
                completed.run();
                return;
            }
            if (!moderator) {
                text.send(sender, Message.GOVERNANCE_NOT_MODERATOR, NamedTextColor.RED);
                completed.run();
                return;
            }
            removeModeratorRole(playerId, error -> {
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not resign moderator " + playerName, error);
                    text.send(sender, Message.GOVERNANCE_RESIGN_FAILED, NamedTextColor.RED);
                    completed.run();
                    return;
                }
                text.broadcast(Message.GOVERNANCE_RESIGNED, NamedTextColor.YELLOW, playerName);
                context.submit(sender, () -> {
                    voting.terminateOpenVote(VoteKind.REMOVAL, playerId,
                            VoteTerminationReason.MODERATOR_RESIGNED);
                    coordinator.reviewOpenVotes();
                    context.onMain(completed);
                }, completed);
            });
        }));
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
        return roleManager.isModerator(known.playerId()).thenCompose(moderator -> {
            if (!moderator) return CompletableFuture.failedFuture(
                    new GovernanceException(
                            GovernanceException.Code.REMOVAL_TARGET_NOT_MODERATOR));
            return context.query(() -> {
                coordinator.reviewOpenVotes();
                context.ensureKnown(known);
                PlayerProfile subject = membership.player(known.playerId()).orElseThrow();
                return expectedPetition == null ? removal.sponsorRemoval(subject, sponsorId)
                        : removal.joinPetition(subject, sponsorId, expectedPetition);
            });
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

    private void removeModeratorRole(UUID playerId, Consumer<Throwable> completion) {
        roleManager.removeModerator(playerId).whenComplete((_, error) -> context.onMain(
                () -> completion.accept(GovernanceCommandContext.unwrap(error))));
    }
}
