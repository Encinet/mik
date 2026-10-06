package org.encinet.mik.module.governance.platform.paper.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.encinet.mik.module.governance.membership.MembershipService;
import org.encinet.mik.module.governance.platform.paper.GovernanceTaskExecutor;
import org.encinet.mik.module.governance.platform.paper.GovernanceText;
import org.encinet.mik.module.governance.platform.paper.delivery.GovernanceDeliveryCoordinator;
import org.encinet.mik.module.governance.platform.paper.role.LuckPermsGovernanceRoles;
import org.encinet.mik.module.governance.removal.RemovalService;
import org.encinet.mik.module.governance.voting.VotingService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.role.RolePermissions;

import java.util.List;

/** Declares the Paper command tree and delegates each branch to a focused handler. */
public final class GovernanceCommandController {
    private final LanguageService languages;
    private final GovernanceCommandContext context;
    private final MembershipCommandHandler membership;
    private final VotingCommandHandler voting;
    private final RemovalCommandHandler removal;
    private final GovernanceMenuController menus;

    public GovernanceCommandController(
            JavaPlugin plugin,
            LanguageService languages,
            MembershipService membershipService,
            VotingService votingService,
            RemovalService removalService,
            LuckPermsGovernanceRoles roleManager,
            GovernanceDeliveryCoordinator coordinator,
            GovernanceTaskExecutor executor
    ) {
        this.languages = languages;
        GovernanceText text = new GovernanceText(languages);
        this.context = new GovernanceCommandContext(plugin, membershipService, executor, text);
        this.membership = new MembershipCommandHandler(
                membershipService, roleManager, context, text);
        this.voting = new VotingCommandHandler(
                membershipService, votingService, coordinator, context, text);
        this.removal = new RemovalCommandHandler(
                plugin, membershipService, votingService, removalService, roleManager,
                coordinator, executor, context, text);
        GovernancePlayerMenu players = new GovernancePlayerMenu(
                plugin, membershipService, roleManager, context, text, membership);
        this.menus = new GovernanceMenuController(plugin, membershipService, votingService,
                removalService, coordinator, context, text, voting, removal, players);
    }

    public void openMenu(Player player) {
        menus.openMenu(player);
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(governanceCommand(),
                        languages.t(Language.DEFAULT, Message.GOVERNANCE_COMMAND_DESCRIPTION),
                        List.of("gov")));
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack>
    governanceCommand() {
        return Commands.literal("governance")
                .then(Commands.literal("menu")
                        .executes(ctx -> openOrList(ctx.getSource().getSender())))
                .then(Commands.literal("status")
                        .executes(ctx -> membership.status(ctx.getSource().getSender())))
                .then(Commands.literal("player")
                        .requires(source -> RolePermissions.canModerate(source.getSender()))
                        .executes(ctx -> {
                            Player player = context.requirePlayer(ctx.getSource().getSender());
                            if (player != null) menus.openPlayerSearch(player);
                            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((ctx, builder) -> context.suggestPlayers(builder))
                                .executes(ctx -> membership.inspectPlayer(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player")))
                                .then(Commands.literal("pause")
                                        .then(Commands.argument("duration", StringArgumentType.word())
                                                .then(Commands.argument("appeal", StringArgumentType.string())
                                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                                .executes(ctx -> membership.setPause(
                                                                        ctx.getSource().getSender(),
                                                                        StringArgumentType.getString(ctx, "player"),
                                                                        StringArgumentType.getString(ctx, "duration"),
                                                                        StringArgumentType.getString(ctx, "appeal"),
                                                                        StringArgumentType.getString(ctx, "reason")))))))
                                .then(Commands.literal("resume")
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> membership.clearPause(
                                                        ctx.getSource().getSender(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "reason")))))))
                .then(Commands.literal("nominate")
                        .executes(ctx -> voting.nominate(ctx.getSource().getSender())))
                .then(Commands.literal("withdraw")
                        .executes(ctx -> voting.withdraw(ctx.getSource().getSender())))
                .then(Commands.literal("vote")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .then(Commands.argument("choice", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("yes").suggest("no").suggest("abstain");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> voting.vote(
                                                ctx.getSource().getSender(),
                                                LongArgumentType.getLong(ctx, "id"),
                                                StringArgumentType.getString(ctx, "choice"))))))
                .then(Commands.literal("list")
                        .executes(ctx -> voting.listVotes(ctx.getSource().getSender())))
                .then(Commands.literal("show")
                        .then(Commands.argument("id", LongArgumentType.longArg(1))
                                .executes(ctx -> voting.showVote(
                                        ctx.getSource().getSender(),
                                        LongArgumentType.getLong(ctx, "id")))))
                .then(Commands.literal("history")
                        .executes(ctx -> voting.voteHistory(ctx.getSource().getSender())))
                .then(Commands.literal("petitions")
                        .executes(ctx -> removal.listPetitions(ctx.getSource().getSender())))
                .then(Commands.literal("remove")
                        .then(Commands.argument("moderator", StringArgumentType.word())
                                .suggests((ctx, builder) -> context.suggestPlayers(builder))
                                .executes(ctx -> removal.sponsorRemoval(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "moderator")))))
                .then(Commands.literal("resign")
                        .requires(source -> RolePermissions.canModerate(source.getSender())
                                && !RolePermissions.isCustodian(source.getSender()))
                        .executes(ctx -> removal.resign(ctx.getSource().getSender())))
                .executes(ctx -> openOrList(ctx.getSource().getSender()))
                .build();
    }

    private int openOrList(org.bukkit.command.CommandSender sender) {
        if (sender instanceof Player player) {
            menus.openMenu(player);
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        return voting.listVotes(sender);
    }

}
