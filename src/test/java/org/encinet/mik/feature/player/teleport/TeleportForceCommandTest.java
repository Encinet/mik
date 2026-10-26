package org.encinet.mik.feature.player.teleport;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.encinet.mik.shared.role.PlayerRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TeleportForceCommandTest {
    private record Invocation(String arguments, CommandSourceStack source) {}

    private final List<Invocation> invocations = new ArrayList<>();
    private final CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

    @ParameterizedTest
    @EnumSource(PlayerRole.class)
    void brigadierRejectsNonStaffEvenWithVanillaTeleportPermission(PlayerRole role) throws Exception {
        CommandSourceStack source = source(role);
        if (role == PlayerRole.MODERATOR || role == PlayerRole.CUSTODIAN) {
            assertEquals(7, dispatcher.execute("tpforce Target Sender", source));
            assertEquals(List.of(new Invocation("Target Sender", source)), invocations);
        } else {
            assertThrows(CommandSyntaxException.class,
                    () -> dispatcher.execute("tpforce Target Sender", source));
            assertTrue(invocations.isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Target Sender", "Target ~ ~1 ~", "@a 100 64 100",
            "Target Sender facing entity Sender eyes"})
    void redirectPreservesArgumentsSourceAndResult(String arguments) throws Exception {
        CommandSourceStack source = source(PlayerRole.MODERATOR);
        assertEquals(7, dispatcher.execute("tpforce " + arguments, source));
        assertEquals(List.of(new Invocation(arguments, source)), invocations);
    }

    @Test
    void redirectUsesTheNativeCompletionProvider() throws Exception {
        var suggestions = dispatcher.getCompletionSuggestions(
                dispatcher.parse("tpforce T", source(PlayerRole.MODERATOR))).get();
        assertEquals(List.of("Target"), suggestions.getList().stream().map(s -> s.getText()).toList());
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        var result = new CommandDispatcher<CommandSourceStack>();
        // A stand-in native command records what the real Brigadier redirect delivers.
        var teleport = result.register(Commands.literal("minecraft:teleport")
                .requires(source -> source.getSender().hasPermission("minecraft.command.teleport"))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .suggests((context, builder) -> builder.suggest("Target").buildFuture())
                        .executes(context -> {
                            invocations.add(new Invocation(StringArgumentType.getString(context, "arguments"),
                                    context.getSource()));
                            return 7;
                        })));
        result.getRoot().addChild(TeleportPreferenceModule.forceCommand(teleport));
        return result;
    }

    private static CommandSourceStack source(PlayerRole role) {
        var sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                    if (method.getName().equals("hasPermission")) {
                        return role.permission().equals(args[0]) || "minecraft.command.teleport".equals(args[0]);
                    }
                    throw new AssertionError(method.getName());
                });
        return (CommandSourceStack) Proxy.newProxyInstance(CommandSourceStack.class.getClassLoader(),
                new Class<?>[]{CommandSourceStack.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getSender")) return sender;
                    if (method.getName().equals("equals")) return proxy == args[0];
                    throw new AssertionError(method.getName());
                });
    }
}
