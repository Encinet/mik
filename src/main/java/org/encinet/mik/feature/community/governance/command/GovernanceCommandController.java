package org.encinet.mik.feature.community.governance.command;

import org.encinet.mik.feature.community.governance.gateway.GovernanceGateway;
import org.encinet.mik.feature.player.security.PasswordAccess;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.command.ConsoleCommandSender;
import org.encinet.mik.protocol.GovernanceActionRequest.Action;
import org.encinet.mik.protocol.GovernanceCredential;
import org.encinet.mik.feature.community.governance.GovernanceTaskExecutor;
import org.encinet.mik.feature.community.governance.GovernanceText;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.shared.menu.MenuPanel;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Declares the Paper command tree and delegates each branch to a focused handler. */
public final class GovernanceCommandController implements AutoCloseable {
    private final LanguageService languages;
    private final GovernanceCommandContext context;
    private final MembershipCommandHandler membership;
    private final VotingCommandHandler voting;
    private final RemovalCommandHandler removal;
    private final GovernanceGateway governance;
    private final GovernanceText text;
    private final GovernanceWorkspace workspace;
    private final GovernancePlayerMenu players;
    private final GovernanceActivity activity;

    public GovernanceCommandController(
            JavaPlugin plugin,
            LanguageService languages,
            GovernanceGateway governance,
            GovernanceTaskExecutor executor, PasswordAccess passwords, Function<UUID, GovernanceCredential> credentials
    ) {
        this.languages = languages;
        this.governance = governance;
        this.text = new GovernanceText(languages);
        this.context = new GovernanceCommandContext(plugin, executor, text);
        this.membership = new MembershipCommandHandler(governance, context, text);
        this.voting = new VotingCommandHandler(governance, context, text, credentials);
        this.removal = new RemovalCommandHandler(governance, context, text);
        this.players = new GovernancePlayerMenu(governance, context, text, membership, passwords);
        var query = new GovernanceWorkspaceQuery(governance);
        this.workspace = new GovernanceWorkspace(plugin, context, text, governance::roster, query,
                voting, removal, passwords, this::identityPanel, players::search);
        this.activity = new GovernanceActivity(plugin, context, query, text, governance, removal, workspace::openMatters);
    }

    public void openMenu(Player player) {
        workspace.open(player);
    }

    public MenuPanel identityPanel(Player player, Runnable redraw) {
        return new GovernanceIdentityPanel(player, governance, context, text, redraw);
    }

    public MenuPanel activityPanel(Player player, Runnable redraw) { return activity.panel(player, redraw); }

    @Override public void close() {
        var cleanup = new org.encinet.mik.shared.util.ShutdownSequence();
        cleanup.attempt("governance activity",activity::close);
        cleanup.attempt("governance workspace",workspace::close);
        cleanup.attempt("governance player reader",players::close);
        cleanup.finish("governance menus");
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
                            if (player != null) players.search(player);
                            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((_, builder) -> context.suggestPlayers(builder))
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
                                        .suggests((_, builder) -> {
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
                                .suggests((_, builder) -> context.suggestPlayers(builder))
                                .executes(ctx -> removal.sponsorRemoval(
                                        ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "moderator")))))
                .then(Commands.literal("resign")
                        .executes(ctx -> removal.resign(ctx.getSource().getSender())))
                .then(maintainerCommand())
                .executes(ctx -> openOrList(ctx.getSource().getSender()))
                .build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> maintainerCommand() {
        var responsibilities = Commands.argument("responsibility", StringArgumentType.greedyString())
                .executes(ctx -> maintainer(ctx.getSource(), Action.REGISTER_MAINTAINER, StringArgumentType.getString(ctx, "uuid"),
                        StringArgumentType.getString(ctx, "name"), StringArgumentType.getString(ctx, "responsibility")));
        var set = Commands.argument("uuid", StringArgumentType.word())
                .then(Commands.argument("name", StringArgumentType.word()).then(responsibilities));
        var remove = Commands.argument("uuid", StringArgumentType.word())
                .executes(ctx -> maintainer(ctx.getSource(), Action.RETIRE_MAINTAINER, StringArgumentType.getString(ctx, "uuid"), "", ""));
        return Commands.literal("maintainer").requires(source -> source.getSender() instanceof ConsoleCommandSender)
                .then(Commands.literal("set").then(set)).then(Commands.literal("remove").then(remove));
    }

    private int maintainer(CommandSourceStack source, Action action, String uuid, String name, String responsibility) {
        if (!(source.getSender() instanceof ConsoleCommandSender console)) return 0;
        var request = GovernanceGateway.action(action, null).setLocalOperator(true)
                .setPlayerId(uuid).setPlayerName(name).setReason(responsibility);
        context.submit(console, () -> {
            governance.act(request);
            context.onMain(() -> console.sendMessage("Updated the public maintainer directory: " + uuid));
        });
        return com.mojang.brigadier.Command.SINGLE_SUCCESS;
    }

    private int openOrList(org.bukkit.command.CommandSender sender) {
        if (sender instanceof Player player) {
            workspace.open(player);
            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
        }
        return voting.listVotes(sender);
    }

}
