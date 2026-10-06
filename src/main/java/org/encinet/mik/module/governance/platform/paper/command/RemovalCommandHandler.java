package org.encinet.mik.module.governance.platform.paper.command;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.module.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.module.governance.removal.RemovalPetition;
import org.encinet.mik.module.governance.removal.RemovalService;
import org.encinet.mik.module.governance.removal.RemovalSponsorship;
import org.encinet.mik.module.governance.voting.VotingService;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.role.RolePermissions;

import java.util.List;
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
    private final GovernanceTaskExecutor executor;
    private final GovernanceCommandContext context;
    private final GovernanceText text;

    RemovalCommandHandler(JavaPlugin plugin, MembershipService membership,
                          VotingService voting, RemovalService removal,
                          LuckPermsGovernanceRoles roleManager,
                          GovernanceDeliveryCoordinator coordinator,
                          GovernanceTaskExecutor executor,
                          GovernanceCommandContext context, GovernanceText text) {
        this.plugin = plugin;
        this.membership = membership;
        this.voting = voting;
        this.removal = removal;
        this.roleManager = roleManager;
        this.coordinator = coordinator;
        this.executor = executor;
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
        Player player = context.requirePlayer(sender);
        if (player == null) return 0;
        if (RolePermissions.isCustodian(player)) {
            text.send(sender, Message.GOVERNANCE_CUSTODIAN_NO_RESIGN, NamedTextColor.RED);
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
                return;
            }
            if (!moderator) {
                text.send(sender, Message.GOVERNANCE_NOT_MODERATOR, NamedTextColor.RED);
                return;
            }
            removeModeratorRole(playerId, error -> {
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not resign moderator " + playerName, error);
                    text.send(sender, Message.GOVERNANCE_RESIGN_FAILED, NamedTextColor.RED);
                    return;
                }
                text.broadcast(Message.GOVERNANCE_RESIGNED, NamedTextColor.YELLOW, playerName);
                executor.submit(() -> {
                    voting.terminateOpenVote(VoteKind.REMOVAL, playerId,
                            VoteTerminationReason.MODERATOR_RESIGNED);
                    coordinator.reviewOpenVotes();
                });
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
        if (sponsor == null) return 0;
        GovernanceCommandContext.KnownPlayer known = context.snapshot(target);
        UUID sponsorId = sponsor.getUniqueId();
        roleManager.isModerator(known.playerId()).whenComplete((moderator, error) -> {
            Throwable cause = GovernanceCommandContext.unwrap(error);
            if (cause != null) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not inspect moderator role", cause);
                context.onMain(() -> text.send(sender,
                        Message.GOVERNANCE_ROLE_LOOKUP_ERROR, NamedTextColor.RED));
                context.onMain(completion);
                return;
            }
            if (!moderator) {
                context.onMain(() -> text.send(sender,
                        Message.GOVERNANCE_TARGET_NOT_MODERATOR,
                        NamedTextColor.RED, known.playerName()));
                context.onMain(completion);
                return;
            }
            context.submit(sender, () -> {
                try {
                    coordinator.reviewOpenVotes();
                    context.ensureKnown(known);
                    PlayerProfile subject = membership.player(known.playerId()).orElseThrow();
                    RemovalSponsorship result = removal.sponsorRemoval(subject, sponsorId);
                    if (result.startedVote() == null) {
                        if (result.newlyAdded()) {
                            context.onMain(() -> text.broadcast(language -> text.t(language,
                                    Message.GOVERNANCE_PETITION_STARTED,
                                    result.petition().subjectName(),
                                    result.petition().sponsorCount(),
                                    result.petition().sponsorsRequired(),
                                    text.time(language, result.petition().expiresAt())),
                                    NamedTextColor.YELLOW));
                        } else {
                            context.onMain(() -> text.send(sender,
                                    Message.GOVERNANCE_PETITION_ALREADY_SPONSORED,
                                    NamedTextColor.YELLOW,
                                    result.petition().sponsorCount(),
                                    result.petition().sponsorsRequired()));
                        }
                        return;
                    }
                    GovernanceVote vote = result.startedVote();
                    context.onMain(() -> text.broadcast(language -> text.t(language,
                            Message.GOVERNANCE_VOTE_STARTED, vote.id(),
                            text.kind(language, vote.kind()), vote.subjectName(),
                            vote.eligibleVoters(), vote.quorumRequired(),
                            text.time(language, vote.closesAt())), NamedTextColor.GOLD));
                } finally {
                    context.onMain(completion);
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    private void removeModeratorRole(UUID playerId, Consumer<Throwable> completion) {
        roleManager.removeModerator(playerId).whenComplete((ignored, error) -> context.onMain(
                () -> completion.accept(GovernanceCommandContext.unwrap(error))));
    }
}
