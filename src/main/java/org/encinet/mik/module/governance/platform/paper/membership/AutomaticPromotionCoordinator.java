package org.encinet.mik.module.governance.platform.paper.membership;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.module.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Evaluates eligible newcomers and applies the member role through LuckPerms. */
public final class AutomaticPromotionCoordinator {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languages;
    private final MembershipService membership;
    private final MembershipRoleSnapshot roles;
    private final LuckPermsGovernanceRoles roleManager;
    private final GovernanceTaskExecutor executor;
    private final Set<UUID> inFlight = new HashSet<>();

    public AutomaticPromotionCoordinator(
            JavaPlugin plugin,
            LanguageService languages,
            MembershipService membership,
            MembershipRoleSnapshot roles,
            LuckPermsGovernanceRoles roleManager,
            GovernanceTaskExecutor executor
    ) {
        this.plugin = plugin;
        this.languages = languages;
        this.membership = membership;
        this.roles = roles;
        this.roleManager = roleManager;
        this.executor = executor;
    }

    /** Captures Bukkit state on the main thread before eligibility is checked off-thread. */
    public List<Candidate> captureCandidates() {
        List<Candidate> candidates = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            if (!roles.hasMemberRole(player) && !inFlight.contains(playerId)) {
                candidates.add(new Candidate(playerId, player.getName()));
            }
        }
        return List.copyOf(candidates);
    }

    /** Called on the governance executor after the latest play intervals are persisted. */
    public void review(List<Candidate> candidates) {
        for (Candidate candidate : candidates) {
            try {
                if (membership.promotionEligibility(candidate.playerId()).eligible()) {
                    requestPromotion(candidate);
                }
            } catch (GovernanceException error) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not check promotion for " + candidate.playerName(), error);
            }
        }
    }

    private void requestPromotion(Candidate candidate) {
        onMain(() -> {
            Player player = Bukkit.getPlayer(candidate.playerId());
            if (player == null || !player.isOnline() || roles.hasMemberRole(player)
                    || !inFlight.add(candidate.playerId())) {
                return;
            }
            roleManager.promoteMember(candidate.playerId())
                    .whenComplete((ignored, error) -> onMain(() -> {
                        inFlight.remove(candidate.playerId());
                        if (error != null) {
                            plugin.getLogger().log(Level.SEVERE,
                                    "Could not promote " + candidate.playerName()
                                            + " through LuckPerms", error);
                            return;
                        }
                        roles.rememberPromoted(candidate.playerId());
                        executor.submit(() -> membership.recordPromotion(
                                candidate.playerId(), Instant.now()));
                        Player online = Bukkit.getPlayer(candidate.playerId());
                        if (online != null && online.isOnline()) {
                            online.sendMessage(MINI_MESSAGE.deserialize(
                                    languages.t(online, Message.AUTOPROMOTE_SUCCESS_MM)));
                            online.playSound(online,
                                    Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
                        }
                        plugin.getLogger().info("Promoted " + candidate.playerName()
                                + " to the LuckPerms member group");
                    }));
        });
    }

    private void onMain(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    public record Candidate(UUID playerId, String playerName) {
    }
}
