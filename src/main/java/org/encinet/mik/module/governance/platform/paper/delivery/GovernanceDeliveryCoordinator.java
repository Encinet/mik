package org.encinet.mik.module.governance.platform.paper.delivery;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.ban.BanManager;
import org.encinet.mik.module.ban.BanRecord;
import org.encinet.mik.module.ban.BanServiceException;
import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.delivery.DeliveryService;
import org.encinet.mik.module.governance.delivery.GovernanceAction;
import org.encinet.mik.module.governance.delivery.GovernanceActionType;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.module.governance.voting.VotingService;
import org.encinet.mik.module.governance.voting.model.GovernanceVote;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteProposal;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.module.i18n.Message;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Finalizes votes and drains durable announcements and approved side effects. */
public final class GovernanceDeliveryCoordinator {
    private final JavaPlugin plugin;
    private final MembershipService membership;
    private final VotingService voting;
    private final DeliveryService delivery;
    private final LuckPermsGovernanceRoles roleManager;
    private final BanManager banManager;
    private final GovernanceTaskExecutor executor;
    private final GovernanceText text;
    private final Set<Long> actionsInFlight = ConcurrentHashMap.newKeySet();
    private final Set<Long> announcementsInFlight = ConcurrentHashMap.newKeySet();

    public GovernanceDeliveryCoordinator(JavaPlugin plugin, MembershipService membership,
                                         VotingService voting, DeliveryService delivery,
                                         LuckPermsGovernanceRoles roleManager,
                                         BanManager banManager, GovernanceTaskExecutor executor,
                                         GovernanceText text) {
        this.plugin = plugin;
        this.membership = membership;
        this.voting = voting;
        this.delivery = delivery;
        this.roleManager = roleManager;
        this.banManager = banManager;
        this.executor = executor;
        this.text = text;
    }

    public void reviewOpenVotes() {
        try {
            voting.finalizeDueVotes();
            for (GovernanceVote vote : voting.openVotes()) {
                if (vote.kind() == VoteKind.APPOINTMENT
                        && !membership.activeMemberEligibility(vote.subjectId()).eligible()) {
                    voting.terminateVote(vote.id(),
                            VoteTerminationReason.CANDIDATE_INELIGIBLE);
                }
            }
        } catch (GovernanceException error) {
            plugin.getLogger().log(Level.SEVERE, "Could not review governance votes", error);
        }
        dispatchAnnouncements();
        dispatchActions();
    }

    private void dispatchAnnouncements() {
        final List<GovernanceVote> pending;
        try {
            pending = delivery.pendingVoteAnnouncements();
        } catch (GovernanceException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not dispatch durable vote result announcements", error);
            return;
        }
        for (GovernanceVote vote : pending) {
            if (!announcementsInFlight.add(vote.id())) continue;
            onMain(() -> {
                try {
                    text.broadcast(language -> text.voteResult(language, vote),
                            text.resultColor(vote));
                } catch (RuntimeException error) {
                    announcementsInFlight.remove(vote.id());
                    plugin.getLogger().log(Level.SEVERE,
                            "Could not broadcast result for vote #" + vote.id(), error);
                    return;
                }
                Instant now = Instant.now();
                Instant announcedAt = now.isBefore(vote.finalizedAt())
                        ? vote.finalizedAt() : now;
                executor.submit(() -> {
                    try {
                        delivery.markVoteResultAnnounced(vote.id(), announcedAt);
                    } finally {
                        announcementsInFlight.remove(vote.id());
                    }
                });
            });
        }
    }

    private void dispatchActions() {
        final List<GovernanceAction> pending;
        try {
            pending = delivery.pendingActions();
        } catch (GovernanceException error) {
            plugin.getLogger().log(Level.SEVERE,
                    "Could not dispatch durable governance actions", error);
            return;
        }
        for (GovernanceAction action : pending) {
            if (!actionsInFlight.add(action.id())) continue;
            if (action.type() == GovernanceActionType.BAN_PLAYER) {
                applyBan(action);
            } else {
                applyRoleChange(action);
            }
        }
    }

    private void applyRoleChange(GovernanceAction action) {
        var operation = action.type() == GovernanceActionType.APPOINT_MODERATOR
                ? roleManager.appointModerator(action.playerId())
                : roleManager.removeModerator(action.playerId());
        operation.whenComplete((ignored, error) -> complete(action, unwrap(error)));
    }

    private void applyBan(GovernanceAction action) {
        VoteProposal.Ban proposal = (VoteProposal.Ban) action.proposal();
        onMain(() -> {
            Throwable failure = null;
            try {
                if (banManager.active(action.playerId(), action.playerName()).isEmpty()) {
                    Instant expiresAt = Instant.now().plus(proposal.duration());
                    banManager.ban(action.playerId(), action.playerName(), proposal.reason(),
                            "Governance vote #" + action.sourceVoteId(), expiresAt,
                            BanRecord.Origin.GOVERNANCE);
                }
                Player online = Bukkit.getPlayer(action.playerId());
                if (online != null) {
                    var language = text.language(online);
                    online.kick(Component.text(text.t(language,
                                    Message.GOVERNANCE_LEGACY_BAN_KICK,
                                    text.duration(language, proposal.duration()),
                                    proposal.reason()), NamedTextColor.RED),
                            PlayerKickEvent.Cause.BANNED);
                }
            } catch (BanServiceException | RuntimeException error) {
                failure = error;
            }
            complete(action, failure);
        });
    }

    private void complete(GovernanceAction action, Throwable error) {
        Instant now = Instant.now();
        Instant attemptedAt = now.isBefore(action.createdAt()) ? action.createdAt() : now;
        executor.submit(() -> {
            try {
                if (error == null) {
                    delivery.markActionApplied(action.id(), attemptedAt);
                    plugin.getLogger().info("Applied durable " + action.type()
                            + " action from vote #" + action.sourceVoteId()
                            + " for " + action.playerName());
                } else {
                    delivery.markActionFailed(action.id(), attemptedAt, actionError(error));
                    plugin.getLogger().log(Level.SEVERE,
                            "Durable action from vote #" + action.sourceVoteId()
                                    + " could not be applied; it will be retried",
                            error);
                }
            } finally {
                actionsInFlight.remove(action.id());
            }
        });
    }

    private void onMain(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private static String actionError(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getName()
                + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completion && completion.getCause() != null) {
            return completion.getCause();
        }
        return error;
    }
}
