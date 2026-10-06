package org.encinet.mik.module.governance.platform.paper.membership;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.tenure.ModeratorPresence;
import org.encinet.mik.module.governance.membership.tenure.ModeratorTenurePolicy;
import org.encinet.mik.module.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.module.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.module.governance.voting.VotingService;
import org.encinet.mik.module.governance.voting.model.VoteKind;
import org.encinet.mik.module.governance.voting.model.VoteTerminationReason;
import org.encinet.mik.module.i18n.Message;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.logging.Level;

/** Performs the daily moderator inactivity review; custodians are excluded. */
public final class ModeratorTenureCoordinator {
    private final JavaPlugin plugin;
    private final LuckPermsGovernanceRoles roleManager;
    private final VotingService voting;
    private final GovernanceDeliveryCoordinator delivery;
    private final GovernanceTaskExecutor executor;
    private final GovernanceText text;

    private BukkitTask reviewTask;

    public ModeratorTenureCoordinator(
            JavaPlugin plugin,
            LuckPermsGovernanceRoles roleManager,
            VotingService voting,
            GovernanceDeliveryCoordinator delivery,
            GovernanceTaskExecutor executor,
            GovernanceText text
    ) {
        this.plugin = plugin;
        this.roleManager = roleManager;
        this.voting = voting;
        this.delivery = delivery;
        this.executor = executor;
        this.text = text;
    }

    public void start() {
        ZonedDateTime now = ZonedDateTime.now(MembershipService.GOVERNANCE_ZONE);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1)
                .atStartOfDay(MembershipService.GOVERNANCE_ZONE);
        long delayTicks = Math.max(1L,
                Duration.between(now, nextMidnight).toMillis() / 50L);
        reviewTask = Bukkit.getScheduler().runTaskTimer(
                plugin, this::review,
                delayTicks, 20L * 60L * 60L * 24L);
    }

    public void stop() {
        if (reviewTask != null) reviewTask.cancel();
        reviewTask = null;
    }

    private void review() {
        roleManager.moderatorIds().whenComplete((moderatorIds, error) -> onMain(() -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not build daily LuckPerms moderator list", error);
                return;
            }
            Instant now = Instant.now();
            List<ModeratorPresence> moderators = moderatorIds.stream().map(playerId -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerId);
                String name = player.getName() == null
                        ? playerId.toString() : player.getName();
                long lastPlayed = player.getLastPlayed();
                return new ModeratorPresence(playerId, name,
                        lastPlayed > 0 ? Instant.ofEpochMilli(lastPlayed) : Instant.EPOCH);
            }).toList();
            for (ModeratorPresence moderator : ModeratorTenurePolicy.automaticRemovals(
                    moderators, now)) {
                removeInactiveModerator(moderator);
            }
        }));
    }

    private void removeInactiveModerator(ModeratorPresence moderator) {
        roleManager.removeModerator(moderator.playerId())
                .whenComplete((ignored, error) -> onMain(() -> {
                    if (error != null) {
                        plugin.getLogger().log(Level.SEVERE,
                                "Could not auto-remove inactive moderator "
                                        + moderator.playerName(), error);
                        return;
                    }
                    text.broadcast(Message.GOVERNANCE_INACTIVE_REMOVAL,
                            NamedTextColor.YELLOW, moderator.playerName());
                    plugin.getLogger().info(
                            "Automatically removed inactive LuckPerms moderator "
                                    + moderator.playerName());
                    executor.submit(() -> {
                        voting.terminateOpenVote(VoteKind.REMOVAL, moderator.playerId(),
                                VoteTerminationReason.MODERATOR_AUTO_REMOVED);
                        delivery.reviewOpenVotes();
                    });
                }));
    }

    private void onMain(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }
}
