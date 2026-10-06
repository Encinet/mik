package org.encinet.mik.module.vehicle;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class VehicleModelCommands {
    private final VehicleCommands commands;
    private final VehicleCommands.NativeArguments arguments;

    VehicleModelCommands(VehicleCommands commands, VehicleCommands.NativeArguments arguments) {
        this.commands = commands;
        this.arguments = arguments;
    }

    LiteralArgumentBuilder<CommandSourceStack> build() {
        var root = VehicleCommands.admin("model").executes(fixed("menu"));
        for (String operation : List.of("menu", "help", "list", "save", "apply", "check", "undo", "redo", "cancel", "anchor", "pick", "here", "look"))
            root.then(Commands.literal(operation).executes(fixed(operation)));
        root.then(Commands.literal("snap").executes(fixed("snap"))
                .then(Commands.literal("on").executes(fixed("snap", "on")))
                .then(Commands.literal("off").executes(fixed("snap", "off"))));
        root.then(Commands.literal("info").executes(fixed("info"))
                .then(commands.modelArgument("model").executes(values(new String[]{"info"}, "model"))));
        var id = Commands.argument("id", StringArgumentType.word());
        for (var kind : VehicleDefinition.Kind.values()) id.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT))
                .executes(commands.modelCommand(context -> new String[]{"create", value(context, "id"), kind.name()})));
        root.then(Commands.literal("create").then(id));
        var imported = Commands.argument("id", StringArgumentType.word());
        for (var kind : VehicleDefinition.Kind.values()) imported.then(Commands.literal(kind.name().toLowerCase(Locale.ROOT))
                .executes(commands.modelCommand(context -> new String[]{"import", value(context, "id"), kind.name()}))
                .then(commands.groupArgument().executes(commands.modelCommand(context -> new String[]{"import", value(context, "id"), kind.name(), value(context, "group")}))));
        root.then(Commands.literal("import").then(imported));
        for (String operation : List.of("edit", "delete")) root.then(Commands.literal(operation)
                .then(commands.modelArgument("model").executes(values(new String[]{operation}, "model"))));
        root.then(Commands.literal("clone").then(commands.modelArgument("model")
                .then(Commands.argument("id", StringArgumentType.word()).executes(values(new String[]{"clone"}, "model", "id")))));
        root.then(Commands.literal("preview").executes(fixed("preview"))
                .then(Commands.literal("off").executes(fixed("preview", "off"))));
        return root.then(parameters()).then(parts()).then(seats()).then(colliders()).then(supports());
    }

    private LiteralArgumentBuilder<CommandSourceStack> parameters() {
        var node = Commands.literal("set");
        for (String field : VehicleModelDraft.SCALARS.stream().sorted().toList()) node.then(Commands.literal(field)
                .then(Commands.argument("value", DoubleArgumentType.doubleArg(Double.MIN_VALUE)).executes(values(new String[]{"set", field}, "value"))));
        for (String field : VehicleModelDraft.VECTORS.stream().sorted().toList()) {
            var vector = Commands.literal(field).then(vector(-Double.MAX_VALUE, values(new String[]{"set", field}, "x", "y", "z")));
            if (field.equals("center-of-mass")) for (String target : List.of("here", "look"))
                vector.then(Commands.literal(target).executes(fixed("set", field, target)));
            node.then(vector);
        }
        var kind = Commands.literal("kind");
        for (var choice : VehicleDefinition.Kind.values()) kind.then(Commands.literal(choice.name().toLowerCase(Locale.ROOT)).executes(fixed("set", "kind", choice.name())));
        return node.then(kind).then(Commands.literal("engine.ratios")
                .then(Commands.argument("ratios", StringArgumentType.greedyString()).executes(values(new String[]{"set", "engine.ratios"}, "ratios"))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> parts() {
        var add = Commands.literal("add")
                .then(Commands.literal("group").executes(fixed("part", "add", "group"))
                        .then(commands.groupArgument().executes(values(new String[]{"part", "add", "group"}, "group"))))
                .then(Commands.literal("selected").executes(fixed("part", "add", "selected")))
                .then(Commands.literal("item").executes(fixed("part", "add", "item")))
                .then(Commands.literal("block").then(Commands.argument("block", arguments.blockState())
                        .executes(commands.modelCommand(context -> new String[]{"part", "add", "block", block(context)}))))
                .then(Commands.literal("text").then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(values(new String[]{"part", "add", "text"}, "text"))));
        var node = Commands.literal("part").then(add).then(remove("part")).then(copy("part"))
                .then(Commands.literal("item").then(index().executes(values(new String[]{"part", "item"}, "index"))))
                .then(Commands.literal("block").then(index().then(Commands.argument("block", arguments.blockState())
                        .executes(commands.modelCommand(context -> new String[]{"part", "block", value(context, "index"), block(context)})))))
                .then(Commands.literal("text").then(index().then(Commands.argument("text", StringArgumentType.greedyString())
                        .executes(values(new String[]{"part", "text"}, "index", "text")))));
        for (String operation : List.of("position", "rotate", "scale", "move")) {
            var index = index().then(vector(operation.equals("scale") ? Double.MIN_VALUE : -Double.MAX_VALUE,
                    values(new String[]{"part", operation}, "index", "x", "y", "z")));
            if (operation.equals("position")) for (String target : List.of("here", "look"))
                index.then(Commands.literal(target).executes(sample("part", operation, target)));
            node.then(Commands.literal(operation).then(index));
        }
        var animation = index();
        for (var choice : VehicleDefinition.Animation.values()) animation.then(Commands.literal(choice.name())
                .executes(commands.modelCommand(context -> new String[]{"part", "animation", value(context, "index"), choice.name()})));
        return node.then(Commands.literal("animation").then(animation)).then(options());
    }

    private LiteralArgumentBuilder<CommandSourceStack> options() {
        var index = index();
        index.then(enumOption("billboard", Display.Billboard.values()));
        index.then(enumOption("item-transform", ItemDisplay.ItemDisplayTransform.values()));
        index.then(enumOption("alignment", TextDisplay.TextAlignment.values()));
        for (String property : List.of("block-light", "sky-light")) index.then(integerOption(property, -1, 15));
        index.then(integerOption("opacity", -128, 255)).then(integerOption("line-width", 1, 2048));
        index.then(Commands.literal("background").then(Commands.argument("value", StringArgumentType.word())
                .executes(option("background", "value"))));
        for (String property : List.of("shadow", "see-through")) index.then(Commands.literal(property)
                .then(Commands.argument("value", BoolArgumentType.bool()).executes(option(property, "value"))));
        return Commands.literal("option").then(index);
    }

    private LiteralArgumentBuilder<CommandSourceStack> integerOption(String property, int minimum, int maximum) {
        return Commands.literal(property).then(Commands.argument("value", IntegerArgumentType.integer(minimum, maximum))
                .executes(option(property, "value")));
    }

    private LiteralArgumentBuilder<CommandSourceStack> enumOption(String property, Enum<?>[] choices) {
        var node = Commands.literal(property);
        for (var choice : choices) node.then(Commands.literal(choice.name())
                .executes(commands.modelCommand(context -> new String[]{"part", "option", value(context, "index"), property, choice.name()})));
        return node;
    }

    private Command<CommandSourceStack> option(String property, String argument) {
        return commands.modelCommand(context -> new String[]{"part", "option", value(context, "index"), property, value(context, argument)});
    }

    private LiteralArgumentBuilder<CommandSourceStack> seats() {
        var role = Commands.argument("z", DoubleArgumentType.doubleArg());
        for (String choice : List.of("driver", "passenger")) role.then(Commands.literal(choice)
                .executes(commands.modelCommand(context -> new String[]{"seat", "add", value(context, "x"), value(context, "y"), value(context, "z"), choice})));
        var node = Commands.literal("seat").then(remove("seat")).then(copy("seat"))
                .then(Commands.literal("driver").then(index().executes(values(new String[]{"seat", "driver"}, "index"))))
                .then(Commands.literal("add").then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg()).then(role)))
                        .then(Commands.literal("here")
                                .then(Commands.literal("driver").executes(fixed("seat", "add", "here", "driver")))
                                .then(Commands.literal("passenger").executes(fixed("seat", "add", "here", "passenger"))))
                        .then(Commands.literal("look")
                                .then(Commands.literal("driver").executes(fixed("seat", "add", "look", "driver")))
                                .then(Commands.literal("passenger").executes(fixed("seat", "add", "look", "passenger")))));
        for (String operation : List.of("position", "exit", "move")) {
            var index = index().then(vector(-Double.MAX_VALUE, values(new String[]{"seat", operation}, "index", "x", "y", "z")));
            if (!operation.equals("move")) for (String target : List.of("here", "look"))
                index.then(Commands.literal(target).executes(sample("seat", operation, target)));
            node.then(Commands.literal(operation).then(index));
        }
        return node;
    }

    private LiteralArgumentBuilder<CommandSourceStack> colliders() {
        return Commands.literal("collider").then(remove("collider")).then(copy("collider"))
                .then(Commands.literal("size").then(index().then(vector(Double.MIN_VALUE, values(new String[]{"collider", "size"}, "index", "x", "y", "z")))))
                .then(Commands.literal("auto").executes(fixed("collider", "auto")))
                .then(Commands.literal("corner1").executes(fixed("collider", "corner1"))
                        .then(Commands.literal("look").executes(fixed("collider", "corner1", "look"))))
                .then(Commands.literal("corner2").executes(fixed("collider", "corner2"))
                        .then(Commands.literal("look").executes(fixed("collider", "corner2", "look"))))
                .then(Commands.literal("cancel").executes(fixed("collider", "cancel")))
                .then(Commands.literal("position").then(index().then(Commands.literal("here").executes(here("collider", "position")))
                        .then(Commands.literal("look").executes(sample("collider", "position", "look")))
                        .then(vector(-Double.MAX_VALUE, values(new String[]{"collider", "position"}, "index", "x", "y", "z")))))
                .then(Commands.literal("move").then(index().then(vector(-Double.MAX_VALUE, values(new String[]{"collider", "move"}, "index", "x", "y", "z")))))
                .then(Commands.literal("add").then(colliderDimensions(values(new String[]{"collider", "add"}, "x", "y", "z", "halfX", "halfY", "halfZ"))))
                .then(Commands.literal("set").then(index().then(colliderDimensions(values(new String[]{"collider", "set"}, "index", "x", "y", "z", "halfX", "halfY", "halfZ")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> supports() {
        return Commands.literal("support").then(remove("support")).then(copy("support"))
                .then(Commands.literal("add").then(vector(-Double.MAX_VALUE, values(new String[]{"support", "add"}, "x", "y", "z")))
                        .then(Commands.literal("here").executes(fixed("support", "add", "here")))
                        .then(Commands.literal("look").executes(fixed("support", "add", "look"))))
                .then(Commands.literal("set").then(index().then(vector(-Double.MAX_VALUE, values(new String[]{"support", "set"}, "index", "x", "y", "z")))
                        .then(Commands.literal("here").executes(here("support", "set")))
                        .then(Commands.literal("look").executes(sample("support", "set", "look")))))
                .then(Commands.literal("move").then(index().then(vector(-Double.MAX_VALUE, values(new String[]{"support", "move"}, "index", "x", "y", "z")))));
    }

    private Command<CommandSourceStack> here(String section, String operation) {
        return sample(section, operation, "here");
    }

    private Command<CommandSourceStack> sample(String section, String operation, String target) {
        return commands.modelCommand(context -> new String[]{section, operation, value(context, "index"), target});
    }

    private LiteralArgumentBuilder<CommandSourceStack> remove(String section) {
        return Commands.literal("remove").then(index().executes(values(new String[]{section, "remove"}, "index")));
    }

    private LiteralArgumentBuilder<CommandSourceStack> copy(String section) {
        return Commands.literal("copy").then(index().executes(values(new String[]{section, "copy"}, "index")));
    }

    private RequiredArgumentBuilder<CommandSourceStack, Integer> index() { return Commands.argument("index", IntegerArgumentType.integer(0)); }

    private RequiredArgumentBuilder<CommandSourceStack, Double> vector(double minimum, Command<CommandSourceStack> action) {
        return Commands.argument("x", DoubleArgumentType.doubleArg(minimum))
                .then(Commands.argument("y", DoubleArgumentType.doubleArg(minimum))
                        .then(Commands.argument("z", DoubleArgumentType.doubleArg(minimum)).executes(action)));
    }

    private RequiredArgumentBuilder<CommandSourceStack, Double> colliderDimensions(Command<CommandSourceStack> action) {
        return Commands.argument("x", DoubleArgumentType.doubleArg()).then(Commands.argument("y", DoubleArgumentType.doubleArg())
                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("halfX", DoubleArgumentType.doubleArg(Double.MIN_VALUE))
                                .then(Commands.argument("halfY", DoubleArgumentType.doubleArg(Double.MIN_VALUE))
                                        .then(Commands.argument("halfZ", DoubleArgumentType.doubleArg(Double.MIN_VALUE)).executes(action))))));
    }

    private Command<CommandSourceStack> fixed(String... prefix) { return commands.modelCommand(context -> prefix); }
    private Command<CommandSourceStack> values(String[] prefix, String... names) {
        return commands.modelCommand(context -> {
            String[] result = Arrays.copyOf(prefix, prefix.length + names.length);
            for (int index = 0; index < names.length; index++) result[prefix.length + index] = value(context, names[index]);
            return result;
        });
    }
    private static String value(CommandContext<CommandSourceStack> context, String name) { return String.valueOf(context.getArgument(name, Object.class)); }
    private static String block(CommandContext<CommandSourceStack> context) { return context.getArgument("block", BlockState.class).getBlockData().getAsString(); }
}
