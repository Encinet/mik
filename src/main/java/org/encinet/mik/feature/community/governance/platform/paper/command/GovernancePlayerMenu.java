package org.encinet.mik.feature.community.governance.platform.paper.command;

import org.encinet.mik.feature.player.security.PasswordAccess;

import org.encinet.mik.shared.menu.MenuInputForm;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.feature.community.governance.GovernanceException;
import org.encinet.mik.feature.community.governance.membership.MembershipService;
import org.encinet.mik.feature.community.governance.membership.participation.ParticipationSummary;
import org.encinet.mik.feature.community.governance.membership.profile.PlayerProfile;
import org.encinet.mik.feature.community.governance.platform.paper.GovernanceText;
import org.encinet.mik.feature.community.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.menu.MenuScene;
import org.encinet.mik.shared.menu.FloatingMenuScreen;
import org.encinet.mik.shared.menu.FloatingMenuContext;
import org.encinet.mik.shared.menu.FloatingMenuState;
import static org.encinet.mik.feature.community.governance.platform.paper.command.GovernancePlayerState.*;
import org.encinet.mik.shared.menu.FloatingMenus;
import org.encinet.mik.shared.menu.MenuPlayerSearch;
import org.encinet.mik.shared.menu.ui.UiLength;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.UUID;

/** Staff player lookup and promotion-pause controls inside the governance menu. */
final class GovernancePlayerMenu {
    private final JavaPlugin plugin;
    private final MembershipService membership;
    private final LuckPermsGovernanceRoles roles;
    private final GovernanceCommandContext context;
    private final GovernanceText text;
    private final MembershipCommandHandler commands;
    private final PasswordAccess passwords;
    private final GovernancePlayerScenes scenes;
    private final FloatingMenuScreen<GovernancePlayerState> screen;
    private volatile boolean closed;

    GovernancePlayerMenu(JavaPlugin plugin, MembershipService membership,
                         LuckPermsGovernanceRoles roles,
                         GovernanceCommandContext context, GovernanceText text,
                         MembershipCommandHandler commands, PasswordAccess passwords) {
        this.plugin = plugin;
        this.membership = membership;
        this.roles = roles;
        this.context = context;
        this.text = text;
        this.commands = commands;
        this.passwords = passwords;
        scenes = new GovernancePlayerScenes(text);
        screen = new FloatingMenuScreen<>("governance-player",this::render);
    }

    void search(Player staff) {
        if (!staff(staff)) return;
        Language language = text.language(staff);
        FloatingMenus.search(staff, MenuPlayerSearch.builder(label(language, Message.GOVERNANCE_MENU_PLAYERS, NamedTextColor.GOLD),
                label(language, Message.GOVERNANCE_MENU_PLAYER_NAME, NamedTextColor.AQUA))
                .flowResults(3, UiLength.ui(480))
                .find(query -> {
                    if (closed || !RolePermissions.canModerate(staff)) return java.util.concurrent.CompletableFuture.completedFuture(java.util.List.of());
                    var cached = MenuPlayerSearch.cached(staff,query);
                    return context.query(() -> MenuPlayerSearch.matching(query, java.util.stream.Stream.concat(cached.stream(),
                            membership.players().stream().map(profile -> new MenuPlayerSearch.Candidate(profile.playerId(),profile.playerName()))).toList()));
                }).selected((viewer, selected) -> {
                    if (!staff(viewer)) return;
                    OfflinePlayer target = Bukkit.getOfflinePlayer(selected.id());
                    if (!target.hasPlayedBefore() && !target.isOnline()) {
                        text.send(viewer, Message.GOVERNANCE_PLAYER_NEVER_JOINED,
                                NamedTextColor.RED, selected.name());
                        return;
                    }
                    openPlayer(viewer, target.getUniqueId());
                }).build());
    }

    private void openPlayer(Player staff, UUID targetId) {
        if (!staff.isOnline() || !staff(staff)) return;
        var request = GovernancePlayerState.request(screen.state(staff).orElse(null),targetId);
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetId);
        GovernanceCommandContext.KnownPlayer known = context.snapshot(target);
        screen.open(staff,request);
        roles.isMember(targetId).whenComplete((member,error) -> {
            if (closed) return;
            context.onMain(() -> {
                if (!current(staff,request)) return;
                if (error != null) {
                    screen.update(staff,state -> GovernancePlayerState.fail(state,request,Message.GOVERNANCE_ROLE_LOOKUP_ERROR));
                    return;
                }
                context.query(() -> {
                    if (closed) throw new java.util.concurrent.CancellationException("Player reader closed");
                    context.ensureKnown(known.withMember(member));
                    PlayerProfile profile = membership.player(targetId).orElseThrow(
                            () -> new GovernanceException(GovernanceException.Code.PLAYER_UNKNOWN));
                    ParticipationSummary participation = membership.participation(targetId);
                    return member ? GovernancePlayerReport.Data.active(profile,participation,membership.activeMemberEligibility(targetId))
                            : GovernancePlayerReport.Data.promoting(profile,participation,membership.promotionEligibility(targetId));
                }).whenComplete((data,failure) -> {
                    if (closed) return;
                    context.onMain(() -> {
                        if (!current(staff,request)) return;
                        if (failure != null) {
                            plugin.getLogger().log(java.util.logging.Level.WARNING,"Could not load governance player " + targetId,GovernanceCommandContext.unwrap(failure));
                            screen.update(staff,state -> GovernancePlayerState.fail(state,request,Message.GOVERNANCE_OPERATION_ERROR));
                        } else screen.update(staff,state -> GovernancePlayerState.complete(state,request,data));
                    });
                });
            });
        });
    }

    private boolean current(Player player,GovernancePlayerState expected) {
        return !closed && player.isOnline() && RolePermissions.canModerate(player) && screen.flow(player)
                .filter(flow -> flow.state() == expected && flow.handle().state() != FloatingMenuState.CLOSING
                        && flow.handle().state() != FloatingMenuState.CLOSED).isPresent();
    }

    private MenuScene render(FloatingMenuContext<GovernancePlayerState> view) {
        var state = view.state(); var player = view.player();
        Language language = text.language(player);
        MenuScene scene = scenes.render(language,state,position -> {
            if (state instanceof Loaded source && current(player,source))
                screen.update(player,value -> GovernancePlayerState.read(value,source,position));
        },(viewer,command) -> act(viewer,state,command));
        return scene.toBuilder().refreshEvery(20,(viewer,handle) -> {
            if (closed || !RolePermissions.canModerate(viewer)) handle.close();
            else if (current(viewer,state) && text.language(viewer) != language) view.redraw();
        }).build();
    }

    void close() { closed = true; screen.forgetAll(); }

    private void act(Player viewer,GovernancePlayerState source,GovernancePlayerScenes.Command command) {
        if (!current(viewer,source)) return;
        switch (command) {
            case REFRESH -> openPlayer(viewer,source.target());
            case PASSWORD -> {
                if (source instanceof Loaded) passwords.manage(viewer,source.target(),() -> {
                    if (current(viewer,source)) openPlayer(viewer,source.target());
                });
            }
            case PAUSE -> { if (source instanceof Loaded loaded) pauseInput(viewer,loaded); }
            case RESUME -> { if (source instanceof Loaded loaded) resumeInput(viewer,loaded); }
        }
    }

    private void pauseInput(Player staff,Loaded source) {
        UUID targetId = source.target();
        if (!staff(staff)) return;
        Language language = text.language(staff);
        FloatingMenus.input(staff, MenuInputForm.builder(label(language, Message.GOVERNANCE_MENU_PAUSE, NamedTextColor.GOLD))
                .field(MenuInputForm.Field.text("duration", label(language, Message.GOVERNANCE_MENU_PAUSE_DURATION, NamedTextColor.WHITE), "", 12))
                .field(MenuInputForm.Field.text("appeal", label(language, Message.GOVERNANCE_MENU_PAUSE_APPEAL, NamedTextColor.WHITE), "", 120))
                .field(MenuInputForm.Field.text("reason", label(language, Message.GOVERNANCE_MENU_PAUSE_REASON, NamedTextColor.WHITE), "", 160))
                .validate((viewer,values) -> {
                    Message failure = !current(viewer,source) ? Message.GOVERNANCE_OPERATION_ERROR
                            : GovernanceDurationParser.parse(values.text("duration").strip()).isEmpty() ? Message.GOVERNANCE_INVALID_DURATION
                            : values.text("appeal").isBlank() || values.text("reason").isBlank() ? Message.GOVERNANCE_PAUSE_FIELDS_REQUIRED : null;
                    return failure == null ? null : label(text.language(viewer),failure,NamedTextColor.RED);
                })
                .cancel(label(language, Message.GOVERNANCE_MENU_CANCEL, NamedTextColor.WHITE))
                .submit(label(language, Message.GOVERNANCE_MENU_CONFIRM, NamedTextColor.GREEN), (viewer, values) -> {
                    if (!current(viewer,source) || !staff(viewer)) return;
                    commands.setPause(viewer, targetId, values.text("duration").strip(), values.text("appeal").strip(),
                            values.text("reason").strip(), () -> { if (current(viewer,source)) openPlayer(viewer,targetId); });
                }).build());
    }

    private void resumeInput(Player staff,Loaded source) {
        UUID targetId = source.target();
        if (!staff(staff)) return;
        Language language = text.language(staff);
        FloatingMenus.input(staff, MenuInputForm.builder(label(language, Message.GOVERNANCE_MENU_RESUME, NamedTextColor.GOLD))
                .field(MenuInputForm.Field.text("reason", label(language, Message.GOVERNANCE_MENU_RESUME_REASON, NamedTextColor.WHITE), "", 160))
                .validate((viewer,values) -> {
                    Message failure = !current(viewer,source) ? Message.GOVERNANCE_OPERATION_ERROR
                            : values.text("reason").isBlank() ? Message.GOVERNANCE_RESUME_REASON_REQUIRED : null;
                    return failure == null ? null : label(text.language(viewer),failure,NamedTextColor.RED);
                })
                .cancel(label(language, Message.GOVERNANCE_MENU_CANCEL, NamedTextColor.WHITE))
                .submit(label(language, Message.GOVERNANCE_MENU_CONFIRM, NamedTextColor.GREEN), (viewer, inputValues) -> {
                    String reason = inputValues.text("reason").strip();
                    if (!current(viewer,source) || !staff(viewer)) return;
                    commands.clearPause(viewer, targetId, reason,
                            () -> { if (current(viewer,source)) openPlayer(viewer,targetId); });
                }).build());
    }

    private boolean staff(Player player) {
        if (closed) return false;
        if (RolePermissions.canModerate(player)) return true;
        text.send(player, Message.GOVERNANCE_MENU_STAFF_ONLY, NamedTextColor.RED);
        return false;
    }

    private Component label(Language language, Message message, NamedTextColor color) {
        return Component.text(text.t(language, message), color);
    }
}
