package org.encinet.mik.module.vehicle;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

final class VehicleCommands {
    static final String USE = "mik.vehicle.use";
    static final String ADMIN = "mik.vehicle.admin";
    record NativeArguments(ArgumentType<UUID> uuid, ArgumentType<FinePositionResolver> position, ArgumentType<BlockState> blockState) {
        static NativeArguments paper() { return new NativeArguments(ArgumentTypes.uuid(), ArgumentTypes.finePosition(), ArgumentTypes.blockState()); }
    }
    @FunctionalInterface interface Action {
        void run(Player player, CommandContext<CommandSourceStack> context) throws IOException, CommandSyntaxException;
    }
    private final VehicleModule module;
    private final LanguageService language;
    private final NativeArguments nativeArguments;

    VehicleCommands(VehicleModule module, LanguageService language) { this(module, language, null); }
    VehicleCommands(VehicleModule module, LanguageService language, NativeArguments nativeArguments) {
        this.module = module;
        this.language = language;
        this.nativeArguments = nativeArguments;
    }

    void register(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                build(), language.t(Language.DEFAULT, Message.VEHICLE_COMMAND_DESCRIPTION)));
    }

    LiteralCommandNode<CommandSourceStack> build() {
        NativeArguments arguments = nativeArguments == null ? NativeArguments.paper() : nativeArguments;
        Command<CommandSourceStack> help = command(null, (player, context) -> {
            if (player.hasPermission(USE)) player.sendMessage(language.text(player, Message.VEHICLE_HELP, NamedTextColor.AQUA));
            if (player.hasPermission(ADMIN)) player.sendMessage(language.text(player, Message.VEHICLE_ADMIN_HELP, NamedTextColor.AQUA));
        });
        var root = Commands.literal("vehicle").requires(source -> source.getSender().hasPermission(USE) || source.getSender().hasPermission(ADMIN))
                .executes(help).then(Commands.literal("help").executes(help))
                .then(user("list").executes(command(USE, (player, context) -> module.listVehicles(player))))
                .then(target("ride", arguments, (player, id) -> module.enter(player, module.target(player, id), true)))
                .then(target("passenger", arguments, (player, id) -> module.enter(player, module.target(player, id), false)))
                .then(target("inspect", arguments, (player, id) -> module.inspect(player, id)))
                .then(user("remove").executes(command(USE, (player, context) -> module.remove(player, module.target(player, null))))
                        .then(Commands.argument("vehicle", arguments.uuid()).suggests(vehicleSuggestions())
                                .executes(command(USE, (player, context) -> module.remove(player, module.target(player, context.getArgument("vehicle", UUID.class)))))))
                .then(user("leave").executes(command(USE, (player, context) -> player.leaveVehicle())))
                .then(user("engine").executes(control("engine")))
                .then(modes()).then(gears()).then(throttle())
                .then(user("hud").executes(command(USE, (player, context) -> module.toggleHud(player, null)))
                        .then(Commands.literal("on").executes(command(USE, (player, context) -> module.toggleHud(player, true))))
                        .then(Commands.literal("off").executes(command(USE, (player, context) -> module.toggleHud(player, false)))))
                .then(dashboard())
                .then(user("fuel").executes(command(USE, (player, context) -> module.service(player, true))))
                .then(user("repair").executes(command(USE, (player, context) -> module.service(player, false))))
                .then(user("access")
                        .then(Commands.literal("public").executes(command(USE, (player, context) -> module.setAccess(player, true))))
                        .then(Commands.literal("private").executes(command(USE, (player, context) -> module.setAccess(player, false)))))
                .then(admin("origin").executes(command(ADMIN, (player, context) -> module.setOrigin(player))))
                .then(selection(arguments)).then(capture())
                .then(admin("spawn").then(modelArgument("model")
                        .executes(command(ADMIN, (player, context) -> module.spawn(player, StringArgumentType.getString(context, "model"), null)))
                        .then(Commands.argument("position", arguments.position()).executes(command(ADMIN, (player, context) ->
                                module.spawn(player, StringArgumentType.getString(context, "model"),
                                        context.getArgument("position", FinePositionResolver.class).resolve(context.getSource()).toLocation(player.getWorld())))))))
                .then(admin("reload").executes(command(ADMIN, (player, context) -> module.reload(player))))
                .then(new VehicleModelCommands(this, arguments).build());
        return root.build();
    }

    private LiteralArgumentBuilder<CommandSourceStack> target(String name, NativeArguments arguments, BiConsumer<Player, UUID> action) {
        return user(name).executes(command(USE, (player, context) -> action.accept(player, null)))
                .then(Commands.argument("vehicle", arguments.uuid()).suggests(vehicleSuggestions())
                        .executes(command(USE, (player, context) -> action.accept(player, context.getArgument("vehicle", UUID.class)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> modes() {
        var node = user("mode").executes(control("mode"));
        for (String mode : List.of("automatic", "manual", "assisted", "auto")) node.then(Commands.literal(mode).executes(control("mode " + mode)));
        return node;
    }

    private LiteralArgumentBuilder<CommandSourceStack> gears() {
        var node = user("gear");
        for (String gear : List.of("P", "R", "N", "D", "up", "down")) node.then(Commands.literal(gear).executes(control("gear " + gear)));
        node.then(Commands.argument("gear", IntegerArgumentType.integer(-1, 12))
                .executes(command(USE, (player, context) -> module.success(player,
                        module.drivingControl(player, null, "gear " + IntegerArgumentType.getInteger(context, "gear"))))));
        return node;
    }

    private LiteralArgumentBuilder<CommandSourceStack> throttle() {
        return user("throttle").then(Commands.literal("up").executes(control("throttle up")))
                .then(Commands.literal("down").executes(control("throttle down")));
    }

    private LiteralArgumentBuilder<CommandSourceStack> dashboard() {
        var node = user("dashboard").executes(command(USE, (player, context) -> module.openDashboard(player, VehicleDashboardMenu.Page.DRIVE)));
        for (var page : VehicleDashboardMenu.Page.values()) node.then(Commands.literal(page.name().toLowerCase(Locale.ROOT))
                .executes(command(USE, (player, context) -> module.openDashboard(player, page))));
        return node.then(Commands.literal("off").executes(command(USE, (player, context) -> module.closeDashboard(player))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> selection(NativeArguments arguments) {
        return admin("select").then(Commands.literal("clear").executes(command(ADMIN, (player, context) -> module.clearSelection(player))))
                .then(Commands.literal("info").executes(command(ADMIN, (player, context) -> module.selectionInfo(player))))
                .then(Commands.literal("group").executes(command(ADMIN, (player, context) -> module.selectGroup(player, null)))
                        .then(groupArgument().executes(command(ADMIN, (player, context) -> module.selectGroup(player, context.getArgument("group", UUID.class))))))
                .then(Commands.literal("radius").then(Commands.argument("radius", DoubleArgumentType.doubleArg(Double.MIN_VALUE, 16))
                        .executes(command(ADMIN, (player, context) -> module.selectRadius(player, DoubleArgumentType.getDouble(context, "radius"))))))
                .then(Commands.argument("display", arguments.uuid()).suggests(suggestions(ADMIN, player -> module.selectableDisplayIds(player).stream().map(UUID::toString).toList()))
                        .executes(command(ADMIN, (player, context) -> module.selectEntity(player, context.getArgument("display", UUID.class)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> capture() {
        var model = Commands.argument("id", StringArgumentType.word());
        for (var kind : VehicleDefinition.Kind.values()) model.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT))
                .executes(command(ADMIN, (player, context) -> module.capture(player, StringArgumentType.getString(context, "id"), kind))));
        return admin("capture").then(model);
    }

    private Command<CommandSourceStack> control(String control) {
        return command(USE, (player, context) -> module.success(player, module.drivingControl(player, null, control)));
    }

    static LiteralArgumentBuilder<CommandSourceStack> user(String name) {
        return Commands.literal(name).requires(source -> source.getSender().hasPermission(USE));
    }
    static LiteralArgumentBuilder<CommandSourceStack> admin(String name) {
        return Commands.literal(name).requires(source -> source.getSender().hasPermission(ADMIN));
    }

    RequiredArgumentBuilder<CommandSourceStack, String> modelArgument(String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests(suggestions(ADMIN, player -> module.models().keySet().stream().sorted().toList()));
    }

    RequiredArgumentBuilder<CommandSourceStack, UUID> groupArgument() {
        var arguments = nativeArguments == null ? NativeArguments.paper() : nativeArguments;
        return Commands.argument("group", arguments.uuid()).suggests(suggestions(ADMIN, player -> module.selectableDisplayIds(player).stream().map(UUID::toString).toList()));
    }
    private SuggestionProvider<CommandSourceStack> vehicleSuggestions() {
        return suggestions(USE, player -> module.accessibleVehicleIds(player).stream().map(UUID::toString).toList());
    }
    private SuggestionProvider<CommandSourceStack> suggestions(String permission, Function<Player, List<String>> values) {
        return (context, builder) -> {
            if (context.getSource().getSender() instanceof Player player && player.hasPermission(permission)) {
                String remaining = builder.getRemainingLowerCase();
                for (String value : values.apply(player)) if (value.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(value);
            }
            return builder.buildFuture();
        };
    }

    Command<CommandSourceStack> modelCommand(Function<CommandContext<CommandSourceStack>, String[]> arguments) {
        return command(ADMIN, (player, context) -> module.modelCommand(player, arguments.apply(context)));
    }

    Command<CommandSourceStack> command(String permission, Action action) {
        return context -> {
            if (!(context.getSource().getSender() instanceof Player player)) {
                context.getSource().getSender().sendMessage(language.text(Language.DEFAULT, Message.PLAYER_ONLY, NamedTextColor.RED));
                return 0;
            }
            try {
                module.requireCommandAccess(player, permission == null ? player.hasPermission(USE) ? USE : ADMIN : permission);
                action.run(player, context);
                return Command.SINGLE_SUCCESS;
            } catch (IllegalArgumentException | IllegalStateException exception) {
                player.sendMessage(language.text(player, Message.VEHICLE_ERROR, NamedTextColor.RED,
                        exception instanceof VehicleImportException imported ? imported.describe(language, player) : exception.getMessage()));
            } catch (IOException exception) {
                module.storageFailure(player, exception);
            }
            return 0;
        };
    }
}
