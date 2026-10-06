package org.encinet.mik.module.governance.platform.paper.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.governance.GovernanceException;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.membership.activity.ActiveMemberEligibility;
import org.encinet.mik.module.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.module.governance.membership.profile.PlayerProfile;
import org.encinet.mik.module.governance.membership.promotion.PromotionEligibility;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.FloatingMenuAppearance;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuFraming;
import org.encinet.mik.module.menu.FloatingMenuHandle;
import org.encinet.mik.module.menu.FloatingMenuTextWidth;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.MenuDialogs;
import org.encinet.mik.module.role.RolePermissions;

import java.util.UUID;

/** Staff player lookup and promotion-pause controls inside the governance menu. */
final class GovernancePlayerMenu {
    private final JavaPlugin plugin;
    private final MembershipService membership;
    private final LuckPermsGovernanceRoles roles;
    private final GovernanceCommandContext context;
    private final GovernanceText text;
    private final MembershipCommandHandler commands;

    GovernancePlayerMenu(JavaPlugin plugin, MembershipService membership,
                         LuckPermsGovernanceRoles roles,
                         GovernanceCommandContext context, GovernanceText text,
                         MembershipCommandHandler commands) {
        this.plugin = plugin;
        this.membership = membership;
        this.roles = roles;
        this.context = context;
        this.text = text;
        this.commands = commands;
    }

    void search(Player staff) {
        if (!staff(staff)) return;
        Language language = text.language(staff);
        MenuDialogs.openTextInput(plugin, staff,
                label(language, Message.GOVERNANCE_MENU_PLAYERS, NamedTextColor.GOLD),
                label(language, Message.GOVERNANCE_MENU_PLAYER_NAME, NamedTextColor.WHITE),
                "", 36, false,
                label(language, Message.GOVERNANCE_MENU_CONFIRM, NamedTextColor.GREEN),
                label(language, Message.GOVERNANCE_MENU_CANCEL, NamedTextColor.GRAY),
                (viewer, name) -> {
                    if (name.isBlank() || !staff(viewer)) return;
                    OfflinePlayer target = Bukkit.getOfflinePlayer(name);
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        text.send(viewer, Message.GOVERNANCE_PLAYER_NEVER_JOINED,
                                NamedTextColor.RED, name);
                        return;
                    }
                    openPlayer(viewer, target.getUniqueId());
                });
    }

    private void openPlayer(Player staff, UUID targetId) {
        if (!staff.isOnline() || !staff(staff)) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        GovernanceCommandContext.KnownPlayer known = context.snapshot(target);
        Language language = text.language(staff);
        FloatingMenuHandle handle = FloatingMenus.present(staff, loading(staff));
        roles.isMember(targetId).whenComplete((member, error) -> context.onMain(() -> {
            if (!staff.isOnline() || !staff(staff)) return;
            if (error != null) {
                text.send(staff, Message.GOVERNANCE_ROLE_LOOKUP_ERROR, NamedTextColor.RED);
                FloatingMenus.current(staff).filter(current -> current.id().equals(handle.id()))
                        .ifPresent(current -> current.update(failure(staff, targetId,
                                Message.GOVERNANCE_ROLE_LOOKUP_ERROR)));
                return;
            }
            context.submit(staff, () -> {
                context.ensureKnown(known.withMember(member));
                PlayerProfile profile = membership.player(targetId).orElseThrow(
                        () -> new GovernanceException(GovernanceException.Code.PLAYER_UNKNOWN));
                ParticipationSummary participation = membership.participation(targetId);
                PromotionEligibility promotion = member
                        ? null : membership.promotionEligibility(targetId);
                ActiveMemberEligibility active = member
                        ? membership.activeMemberEligibility(targetId) : null;
                Component report = commands.report(language, profile,
                        participation, promotion, active, true);
                context.onMain(() -> {
                    if (!staff.isOnline() || !staff(staff)) return;
                    FloatingMenus.current(staff)
                            .filter(current -> current.id().equals(handle.id()))
                            .ifPresent(current -> current.update(
                                    screen(staff, known.playerId(),
                                            report, promotion)));
                });
            }, () -> FloatingMenus.current(staff)
                    .filter(current -> current.id().equals(handle.id()))
                    .ifPresent(current -> current.update(failure(staff, targetId,
                            Message.GOVERNANCE_OPERATION_ERROR))));
        }));
    }

    private FloatingMenuDefinition screen(Player staff, UUID targetId,
                                          Component report, PromotionEligibility promotion) {
        Language language = text.language(staff);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("governance-player")
                .appearance(FloatingMenuAppearance.CONSOLE)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(GovernanceArcLayouts.player());
        menu.information("report", report).region("report")
                .textWidth(FloatingMenuTextWidth.EXPANDED).keepAccessible();
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_PLAYERS,
                NamedTextColor.AQUA)).region("heading").keepAccessible();
        if (promotion != null) {
            if (promotion.activePause() == null) {
                menu.item("pause", Material.CLOCK,
                                label(language, Message.GOVERNANCE_MENU_PAUSE, NamedTextColor.YELLOW))
                        .region("actions")
                        .primary((viewer, handle) -> pauseInput(viewer, targetId));
            } else {
                menu.item("resume", Material.LIME_DYE,
                                label(language, Message.GOVERNANCE_MENU_RESUME, NamedTextColor.GREEN))
                        .region("actions")
                        .primary((viewer, handle) -> resumeInput(viewer, targetId));
            }
        }
        menu.item("find", Material.SPYGLASS,
                        label(language, Message.GOVERNANCE_MENU_FIND_PLAYER, NamedTextColor.AQUA))
                .region("actions").primary((viewer, handle) -> search(viewer));
        menu.item("refresh", Material.CLOCK,
                        label(language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.GRAY))
                .region("navigation").keepAccessible().primary((viewer, handle) -> openPlayer(viewer, targetId));
        menu.back(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private void pauseInput(Player staff, UUID targetId) {
        if (!staff(staff)) return;
        Language language = text.language(staff);
        MenuDialogs.openThreeTextInputs(plugin, staff,
                label(language, Message.GOVERNANCE_MENU_PAUSE, NamedTextColor.GOLD),
                label(language, Message.GOVERNANCE_MENU_PAUSE_DURATION, NamedTextColor.WHITE), 12,
                label(language, Message.GOVERNANCE_MENU_PAUSE_APPEAL, NamedTextColor.WHITE), 120,
                label(language, Message.GOVERNANCE_MENU_PAUSE_REASON, NamedTextColor.WHITE), 160,
                label(language, Message.GOVERNANCE_MENU_CONFIRM, NamedTextColor.GREEN),
                label(language, Message.GOVERNANCE_MENU_CANCEL, NamedTextColor.GRAY),
                (viewer, values) -> {
                    if (!staff(viewer)) return;
                    commands.setPause(viewer, targetId, values.first(), values.second(),
                            values.third(), () -> openPlayer(viewer, targetId));
                });
    }

    private void resumeInput(Player staff, UUID targetId) {
        if (!staff(staff)) return;
        Language language = text.language(staff);
        MenuDialogs.openTextInput(plugin, staff,
                label(language, Message.GOVERNANCE_MENU_RESUME, NamedTextColor.GOLD),
                label(language, Message.GOVERNANCE_MENU_RESUME_REASON, NamedTextColor.WHITE),
                "", 160, false,
                label(language, Message.GOVERNANCE_MENU_CONFIRM, NamedTextColor.GREEN),
                label(language, Message.GOVERNANCE_MENU_CANCEL, NamedTextColor.GRAY),
                (viewer, reason) -> {
                    if (!staff(viewer)) return;
                    commands.clearPause(viewer, targetId, reason,
                            () -> openPlayer(viewer, targetId));
                });
    }

    private FloatingMenuDefinition loading(Player player) {
        Language language = text.language(player);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("governance-player")
                .appearance(FloatingMenuAppearance.CONSOLE)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(GovernanceArcLayouts.status());
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_PLAYERS,
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("status", label(language, Message.GOVERNANCE_MENU_LOADING,
                NamedTextColor.GRAY)).region("status").keepAccessible();
        menu.back(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private FloatingMenuDefinition failure(Player player, UUID targetId, Message reason) {
        Language language = text.language(player);
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("governance-player")
                .appearance(FloatingMenuAppearance.CONSOLE)
                .stableAnchor()
                .frontArc()
                .framing(FloatingMenuFraming.WIDE_ARC)
                .layout(GovernanceArcLayouts.status());
        menu.information("heading", label(language, Message.GOVERNANCE_MENU_PLAYERS,
                NamedTextColor.GOLD)).region("heading").keepAccessible();
        menu.information("error", label(language, reason, NamedTextColor.RED))
                .region("status").keepAccessible();
        menu.item("refresh", Material.CLOCK,
                        label(language, Message.GOVERNANCE_MENU_REFRESH, NamedTextColor.AQUA))
                .region("navigation").keepAccessible().primary((viewer, handle) -> openPlayer(viewer, targetId));
        menu.back(label(language, Message.GOVERNANCE_MENU_BACK, NamedTextColor.GREEN))
                .region("navigation").keepAccessible();
        return menu.build();
    }

    private boolean staff(Player player) {
        if (RolePermissions.canModerate(player)) return true;
        text.send(player, Message.GOVERNANCE_MENU_STAFF_ONLY, NamedTextColor.RED);
        return false;
    }

    private Component label(Language language, Message message, NamedTextColor color) {
        return Component.text(text.t(language, message), color);
    }
}
