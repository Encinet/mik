package org.encinet.mik.feature.community.governance.platform.paper.command;

import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.profile.PlayerProfile;
import org.encinet.mik.feature.community.governance.membership.promotion.PromotionPause;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Self-service eligibility and moderator-facing player management. */
final class MembershipCommandHandler {
    private final MembershipService membership;
    private final LuckPermsGovernanceRoles roles;
    private final GovernanceCommandContext context;
    private final GovernanceText text;

    MembershipCommandHandler(
            MembershipService membership,
            LuckPermsGovernanceRoles roles,
            GovernanceCommandContext context,
            GovernanceText text
    ) {
        this.membership = membership;
        this.roles = roles;
        this.context = context;
        this.text = text;
    }

    int status(CommandSender sender) {
        Player player = context.requirePlayer(sender);
        if (player == null) return 0;
        return inspect(sender, context.snapshot(player), false);
    }

    int inspectPlayer(CommandSender sender, String targetName) {
        if (!requireStaff(sender)) return 0;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            text.send(sender, Message.GOVERNANCE_PLAYER_NEVER_JOINED,
                    NamedTextColor.RED, targetName);
            return 0;
        }
        GovernanceCommandContext.KnownPlayer known = context.snapshot(target);
        roles.isMember(known.playerId()).whenComplete((member, error) ->
                context.onMain(() -> {
                    if (error != null) {
                        text.send(sender, Message.GOVERNANCE_ROLE_LOOKUP_ERROR,
                                NamedTextColor.RED);
                    } else {
                        inspect(sender, known.withMember(member), true);
                    }
                }));
        return Command.SINGLE_SUCCESS;
    }

    private int inspect(CommandSender sender, GovernanceCommandContext.KnownPlayer known,
                        boolean managementView) {
        Language language = text.language(sender);
        context.submit(sender, () -> {
            context.ensureKnown(known);
            PlayerProfile player = membership.player(known.playerId()).orElseThrow();
            ParticipationSummary participation = membership.participation(known.playerId());
            var snapshot = known.member() ? GovernancePlayerReport.Data.active(player,participation,membership.activeMemberEligibility(known.playerId()))
                    : GovernancePlayerReport.Data.promoting(player,participation,membership.promotionEligibility(known.playerId()));
            Component report = new GovernancePlayerReport(text).render(language,snapshot,managementView);
            context.onMain(() -> sender.sendMessage(report));
        });
        return Command.SINGLE_SUCCESS;
    }

    int setPause(CommandSender sender, String targetName, String durationText,
                 String appealPath, String reason) {
        return setPause(sender, targetName, durationText, appealPath, reason, () -> { });
    }

    int setPause(CommandSender sender, String targetName, String durationText,
                 String appealPath, String reason, Runnable completion) {
        return setPause(sender, Bukkit.getOfflinePlayer(targetName), targetName,
                durationText, appealPath, reason, completion);
    }

    int setPause(CommandSender sender, UUID targetId, String durationText,
                 String appealPath, String reason, Runnable completion) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        return setPause(sender, target, target.getName() == null
                        ? targetId.toString() : target.getName(),
                durationText, appealPath, reason, completion);
    }

    private int setPause(CommandSender sender, OfflinePlayer target, String targetName,
                         String durationText, String appealPath, String reason,
                         Runnable completion) {
        if (!requireStaff(sender)) return 0;
        Duration duration = GovernanceDurationParser.parse(durationText).orElse(null);
        if (duration == null) {
            text.send(sender, Message.GOVERNANCE_INVALID_DURATION, NamedTextColor.RED);
            completion.run();
            return 0;
        }
        if (appealPath == null || appealPath.isBlank() || reason == null || reason.isBlank()) {
            text.send(sender, Message.GOVERNANCE_PAUSE_FIELDS_REQUIRED, NamedTextColor.RED);
            completion.run();
            return 0;
        }
        Instant endsAt;
        try {
            endsAt = Instant.now().plus(duration);
        } catch (DateTimeException | ArithmeticException error) {
            text.send(sender, Message.GOVERNANCE_INVALID_DURATION, NamedTextColor.RED);
            completion.run();
            return 0;
        }
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            text.send(sender, Message.GOVERNANCE_PLAYER_NEVER_JOINED,
                    NamedTextColor.RED, targetName);
            completion.run();
            return 0;
        }
        Language language = text.language(sender);
        GovernanceCommandContext.KnownPlayer known = context.snapshot(target);
        String actor = actorId(sender);
        roles.isMember(known.playerId()).whenComplete((member, error) ->
                context.onMain(() -> {
                    if (error != null) {
                        text.send(sender, Message.GOVERNANCE_ROLE_LOOKUP_ERROR,
                                NamedTextColor.RED);
                        completion.run();
                        return;
                    }
                    if (member) {
                        text.send(sender, Message.GOVERNANCE_ERROR_PAUSE_ALREADY_MEMBER,
                                NamedTextColor.RED);
                        completion.run();
                        return;
                    }
                    context.submit(sender, () -> {
                        try {
                            context.ensureKnown(known);
                            PromotionPause pause = membership.pausePromotion(
                                    known.playerId(), reason.strip(), endsAt, appealPath.strip(), actor);
                            context.onMain(() -> {
                                text.send(sender, Message.GOVERNANCE_PAUSE_SET, NamedTextColor.GREEN,
                                        known.playerName(), text.time(language, pause.endsAt()));
                                Player affected = Bukkit.getPlayer(known.playerId());
                                if (affected != null && affected != sender) {
                                    text.send(affected, Message.GOVERNANCE_PAUSE_NOTICE,
                                            NamedTextColor.YELLOW, pause.reason(),
                                            text.time(text.language(affected), pause.endsAt()),
                                            pause.appealPath());
                                }
                            });
                        } finally {
                            context.onMain(completion);
                        }
                    });
                }));
        return Command.SINGLE_SUCCESS;
    }

    int clearPause(CommandSender sender, String targetName, String reason) {
        return clearPause(sender, targetName, reason, () -> { });
    }

    int clearPause(CommandSender sender, String targetName, String reason,
                   Runnable completion) {
        return clearPause(sender, Bukkit.getOfflinePlayer(targetName), targetName,
                reason, completion);
    }

    int clearPause(CommandSender sender, UUID targetId, String reason,
                   Runnable completion) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        return clearPause(sender, target, target.getName() == null
                ? targetId.toString() : target.getName(), reason, completion);
    }

    private int clearPause(CommandSender sender, OfflinePlayer target,
                           String targetName, String reason, Runnable completion) {
        if (!requireStaff(sender)) return 0;
        if (reason == null || reason.isBlank()) {
            text.send(sender, Message.GOVERNANCE_RESUME_REASON_REQUIRED, NamedTextColor.RED);
            completion.run();
            return 0;
        }
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            text.send(sender, Message.GOVERNANCE_PLAYER_NEVER_JOINED,
                    NamedTextColor.RED, targetName);
            completion.run();
            return 0;
        }
        GovernanceCommandContext.KnownPlayer known = context.snapshot(target);
        String actor = actorId(sender);
        context.submit(sender, () -> {
            try {
                boolean lifted = membership.liftPromotionPause(
                        known.playerId(), actor, reason.strip());
                context.onMain(() -> {
                    text.send(sender, lifted ? Message.GOVERNANCE_PAUSE_CLEARED
                                    : Message.GOVERNANCE_PAUSE_NOT_ACTIVE,
                            lifted ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                            known.playerName());
                    Player affected = Bukkit.getPlayer(known.playerId());
                    if (lifted && affected != null && affected != sender) {
                        text.send(affected, Message.GOVERNANCE_RESUME_NOTICE,
                                NamedTextColor.GREEN, reason.strip());
                    }
                });
            } finally {
                context.onMain(completion);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private boolean requireStaff(CommandSender sender) {
        if (RolePermissions.canModerate(sender)) return true;
        text.send(sender, Message.GOVERNANCE_MENU_STAFF_ONLY, NamedTextColor.RED);
        return false;
    }

    private static String actorId(CommandSender sender) {
        return sender instanceof Player player
                ? player.getUniqueId().toString() : "console";
    }
}
