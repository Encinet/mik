package org.encinet.mik.feature.player.teleport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.ShadowBrigNode;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.role.RolePermissions;

import java.util.concurrent.CompletableFuture;

/** Keeps native teleport parsing behind an ordinary, help-safe Paper command. */
final class ForceTeleportCommand {
    private ForceTeleportCommand() {}

    static LiteralCommandNode<CommandSourceStack> create(CommandNode<CommandSourceStack> teleport,
                                                        LanguageService languageService) {
        CommandNode<CommandSourceStack> nativeTeleport = nativeTarget(teleport);
        return Commands.literal("tpforce")
                .requires(source -> RolePermissions.canModerate(source.getSender()))
                .executes(context -> execute(nativeTeleport, context, languageService))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .suggests((context, builder) -> invocation(nativeTeleport, context)
                                .suggestions(builder.getInput().length()))
                        .executes(context -> execute(nativeTeleport, context, languageService)))
                .build();
    }

    private static int execute(CommandNode<CommandSourceStack> teleport,
                               CommandContext<CommandSourceStack> context,
                               LanguageService languageService) throws CommandSyntaxException {
        var nodes = context.getNodes();
        if (nodes.size() > 1 && !TeleportCommandSafety.allow(
                nodes.getLast().getRange().get(context.getInput()), context.getSource().getSender(), languageService)) {
            return 0;
        }
        return invocation(teleport, context).execute();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static CommandNode<CommandSourceStack> nativeTarget(CommandNode<CommandSourceStack> node) {
        // Paper's API proxy forbids child traversal. Use its native handle only in
        // our private dispatcher, never as a redirect in the registered API tree.
        while (true) {
            if (node instanceof ShadowBrigNode shadow) node = (CommandNode) shadow.getHandle();
            else if (node.getRedirect() != null) node = node.getRedirect();
            else return node;
        }
    }

    private static Invocation invocation(CommandNode<CommandSourceStack> teleport,
                                         CommandContext<CommandSourceStack> context) {
        var command = context.getNodes().getFirst();
        var alias = Commands.literal(command.getNode().getName());
        if (teleport.getCommand() != null) alias.executes(teleport.getCommand());
        // Preserve the native arguments and their suggestion providers, while the
        // registered tpforce root supplies its own staff permission requirement.
        for (var child : teleport.getChildren()) alias.then(child);
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        dispatcher.register(alias);
        var reader = new StringReader(context.getInput());
        // Keep original offsets for namespaced labels and /execute ... run tpforce.
        reader.setCursor(command.getRange().getStart());
        return new Invocation(dispatcher, dispatcher.parse(reader, context.getSource()));
    }

    private record Invocation(CommandDispatcher<CommandSourceStack> dispatcher,
                              ParseResults<CommandSourceStack> parsed) {
        int execute() throws CommandSyntaxException {
            return dispatcher.execute(parsed);
        }

        CompletableFuture<Suggestions> suggestions(int cursor) {
            return dispatcher.getCompletionSuggestions(parsed, cursor);
        }
    }
}
