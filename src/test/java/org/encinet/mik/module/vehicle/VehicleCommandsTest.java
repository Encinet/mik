package org.encinet.mik.module.vehicle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.resolvers.FinePositionResolver;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VehicleCommandsTest {
    private static final UUID VEHICLE = UUID.fromString("93d258b9-f44f-4d74-9d8d-b37c8d4e1b4c");
    private final List<Component> messages = new ArrayList<>();
    private final CommandSourceStack operator = source(true, true);
    private final CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

    @Test void registrationUsesPaperLifecycleAndNoWholeCommandStringDispatcher() throws Exception {
        String module = Files.readString(Path.of("src/main/java/org/encinet/mik/module/vehicle/VehicleModule.java"));
        String commands = Files.readString(Path.of("src/main/java/org/encinet/mik/module/vehicle/VehicleCommands.java"));
        assertTrue(module.contains("new VehicleCommands(this, language).register(manager)"));
        assertFalse(module.contains("private void execute(Player player, String input)"));
        assertFalse(module.contains("StringArgumentType.greedyString()"));
        assertTrue(commands.contains("LifecycleEvents.COMMANDS"));
        assertTrue(commands.contains("event.registrar().register("));
        assertTrue(commands.contains("ArgumentTypes.uuid()"));
        assertTrue(commands.contains("ArgumentTypes.finePosition()"));
        assertTrue(commands.contains("ArgumentTypes.blockState()"));
    }

    @Test void allPublicOperationsAreLiteralChildrenRatherThanOneArgsNode() {
        var root = dispatcher.getRoot().getChild("vehicle");
        assertEquals(Set.of("help", "list", "ride", "passenger", "inspect", "remove", "leave", "engine", "mode", "gear", "throttle",
                "hud", "dashboard", "fuel", "repair", "access", "origin", "select", "capture", "spawn", "reload", "model"),
                root.getChildren().stream().map(CommandNode::getName).collect(java.util.stream.Collectors.toSet()));
        assertTrue(root.getChildren().stream().allMatch(child -> child instanceof LiteralCommandNode<?>));
        assertNull(root.getChild("args"));
    }

    @Test void administrativeBranchesAreUnavailableToDriversAndDoNotRequireUseForAdministrators() {
        var root = dispatcher.getRoot().getChild("vehicle");
        var driver = source(true, false);
        var admin = source(false, true);
        var denied = source(false, false);
        assertTrue(root.canUse(driver));
        assertTrue(root.canUse(admin));
        assertFalse(root.canUse(denied));
        for (String name : List.of("origin", "select", "capture", "spawn", "reload", "model")) {
            assertFalse(root.getChild(name).canUse(driver), name);
            assertTrue(root.getChild(name).canUse(admin), name);
        }
        for (String name : List.of("ride", "gear", "dashboard", "fuel", "remove")) {
            assertTrue(root.getChild(name).canUse(driver), name);
            assertFalse(root.getChild(name).canUse(admin), name);
        }
        for (String input : List.of("vehicle model", "vehicle spawn sedan", "vehicle origin", "vehicle reload"))
            assertFalse(complete(dispatcher.parse(input, driver)), input);
    }

    @Test void drivingAndAdministrationCommandsParseAtTheirOwnLeaves() {
        for (String input : List.of("vehicle", "vehicle help", "vehicle list", "vehicle ride", "vehicle ride " + VEHICLE,
                "vehicle passenger " + VEHICLE, "vehicle inspect " + VEHICLE, "vehicle remove " + VEHICLE, "vehicle leave",
                "vehicle engine", "vehicle mode", "vehicle mode automatic", "vehicle mode manual", "vehicle mode assisted", "vehicle mode auto",
                "vehicle gear P", "vehicle gear R", "vehicle gear N", "vehicle gear D", "vehicle gear up", "vehicle gear down", "vehicle gear 3", "vehicle gear -1",
                "vehicle throttle up", "vehicle throttle down", "vehicle hud", "vehicle hud on", "vehicle hud off",
                "vehicle dashboard", "vehicle dashboard drive", "vehicle dashboard gearbox", "vehicle dashboard details", "vehicle dashboard off",
                "vehicle fuel", "vehicle repair", "vehicle access public", "vehicle access private", "vehicle origin",
                "vehicle select clear", "vehicle select radius 8.5", "vehicle select " + VEHICLE,
                "vehicle capture sedan car", "vehicle capture yacht boat", "vehicle capture glider plane",
                "vehicle spawn sedan", "vehicle spawn sedan 1 64 -3", "vehicle reload")) assertParses(input);
    }

    @Test void everyModelOperationHasAnExplicitValidatedPath() {
        for (String suffix : List.of("", "menu", "help", "list", "info", "info sedan", "create sedan car", "edit sedan", "clone sedan sedan_sport",
                "delete sedan", "save", "undo", "cancel", "preview", "preview off", "set kind boat", "set mass 1500", "set center-of-mass 0 1 0",
                "set inertia 100 100 100", "set engine.ratios 3.8,2.4,1.6", "part add selected", "part add item",
                "part add block minecraft:stone", "part add text Passenger information", "part remove 0", "part item 0", "part block 0 minecraft:oak_log[axis=y]",
                "part text 0 Some text with spaces", "part position 0 1 2 3", "part rotate 0 0 90 0", "part scale 0 1 2 3", "part animation 0 FRONT_WHEEL",
                "part option 0 billboard CENTER", "part option 0 item-transform FIXED", "part option 0 alignment LEFT",
                "part option 0 block-light -1", "part option 0 sky-light 15", "part option 0 opacity 255", "part option 0 line-width 2048",
                "part option 0 background -16777216", "part option 0 shadow true", "part option 0 see-through false",
                "seat add 0 1 0 driver", "seat add 1 1 0 passenger", "seat remove 0", "seat driver 0", "seat position 0 0 1 0", "seat exit 0 2 0 0",
                "collider auto", "collider remove 0", "collider add 0 0 0 1 1 1", "collider set 0 0 0 0 1 1 1",
                "support remove 0", "support add 1 0 1", "support set 0 1 0 1")) assertParses("vehicle model" + (suffix.isEmpty() ? "" : " " + suffix));
    }

    @Test void worldEditingShortcutsAndRelativeMovesHaveExplicitTypedPaths() {
        for (String suffix : List.of("anchor", "pick", "here", "set center-of-mass here", "part position 0 here", "part move 0 0.1 0 -0.25",
                "seat add here driver", "seat add here passenger", "seat position 0 here", "seat exit 0 here", "seat move 0 0 0.1 0",
                "collider corner1", "collider corner2", "collider position 0 here", "collider position 0 1 2 3", "collider move 0 0 0 0.1",
                "support add here", "support set 0 here", "support move 0 -0.1 0 0")) assertParses("vehicle model " + suffix);
        for (String suffix : List.of("seat add here", "seat add here pilot", "seat exit here", "part position -1 here",
                "part move 0 here", "collider corner1 extra", "collider position here", "set inertia here", "support set here", "here extra"))
            assertFalse(complete(dispatcher.parse("vehicle model " + suffix, operator)), suffix);
    }

    @Test void surfaceSamplingGridAndCheckpointToolsHaveExplicitValidatedPaths() {
        for (String suffix : List.of("look", "apply", "check", "redo", "snap", "snap on", "snap off", "set center-of-mass look",
                "part position 0 look", "seat position 0 look", "seat exit 0 look", "seat add look driver", "seat add look passenger",
                "support add look", "support set 0 look", "collider position 0 look", "collider corner1 look", "collider corner2 look", "collider cancel"))
            assertParses("vehicle model " + suffix);
        for (String suffix : List.of("look extra", "apply extra", "snap true", "snap on extra", "seat add look", "part move 0 look",
                "collider corner1 look extra", "collider cancel look", "set inertia look"))
            assertFalse(complete(dispatcher.parse("vehicle model " + suffix, operator)), suffix);
    }

    @Test void componentCopiesAndFullColliderDimensionsUseTypedValidatedCommands() {
        for (String section : List.of("part", "seat", "collider", "support")) {
            assertParses("vehicle model " + section + " copy 0");
            for (String suffix : List.of("copy", "copy -1", "copy 0 extra"))
                assertFalse(complete(dispatcher.parse("vehicle model " + section + " " + suffix, operator)), section + " " + suffix);
        }
        assertParses("vehicle model collider size 0 2 1 4");
        for (String suffix : List.of("size 0 0 1 1", "size 0 1 -1 1", "size 0 1 1", "size 0 1 1 1 extra"))
            assertFalse(complete(dispatcher.parse("vehicle model collider " + suffix, operator)), suffix);
    }

    @Test void groupSelectionImportAndAppendHaveTypedPermissionGatedBranches() {
        for (String input : List.of("vehicle select group", "vehicle select group " + VEHICLE, "vehicle select info",
                "vehicle model import sedan car", "vehicle model import yacht boat " + VEHICLE,
                "vehicle model import glider plane " + VEHICLE, "vehicle model part add group", "vehicle model part add group " + VEHICLE)) {
            assertParses(input);
            assertFalse(complete(dispatcher.parse(input, source(true, false))), input);
        }
        for (String input : List.of("vehicle select group broken", "vehicle select group " + VEHICLE + " extra", "vehicle model import",
                "vehicle model import sedan", "vehicle model import sedan submarine", "vehicle model import sedan car broken",
                "vehicle model part add group broken")) assertFalse(complete(dispatcher.parse(input, operator)), input);
        var parsed = dispatcher.parse("vehicle model import sedan car " + VEHICLE, operator).getContext().build("vehicle model import sedan car " + VEHICLE);
        assertEquals(VEHICLE, parsed.getArgument("group", UUID.class));
        assertTrue(suggestionsUnchecked("vehicle select ").containsAll(Set.of("clear", "radius", "group", "info")));
    }

    @Test void unknownIncompleteAndExtraArgumentsCannotFallBackToHelpOrMutateState() {
        for (String input : List.of("vehicle bogus", "vehicle leave extra", "vehicle engine on", "vehicle mode bogus", "vehicle gear", "vehicle gear banana",
                "vehicle select", "vehicle select radius", "vehicle select not-a-uuid", "vehicle capture sedan", "vehicle capture sedan submarine",
                "vehicle spawn", "vehicle spawn sedan 1 2", "vehicle spawn sedan 1 2 3 4", "vehicle dashboard bogus",
                "vehicle model create sedan", "vehicle model part mystery 0", "vehicle model set unknown 1", "vehicle model seat add 0 0 0",
                "vehicle model seat add 0 0 0 pilot", "vehicle model save extra", "vehicle model preview on", "vehicle model part add item extra",
                "vehicle model part option 0 shadow yes", "vehicle model part option 0 billboard bogus"))
            assertFalse(complete(dispatcher.parse(input, operator)), input);
    }

    @Test void numericBoundsAreValidatedBeforeTheExecutor() {
        for (String input : List.of("vehicle gear 13", "vehicle gear -2", "vehicle select radius 0", "vehicle select radius 17",
                "vehicle model set mass 0", "vehicle model set mass -10", "vehicle model set mass NaN",
                "vehicle model part remove -1", "vehicle model part scale 0 1 0 1", "vehicle model part option 0 block-light 16",
                "vehicle model part option 0 sky-light -2", "vehicle model part option 0 opacity 256", "vehicle model part option 0 opacity -129",
                "vehicle model part option 0 line-width 0", "vehicle model part option 0 line-width 2049", "vehicle model collider add 0 0 0 1 0 1"))
            assertFalse(complete(dispatcher.parse(input, operator)), input);
        assertParses("vehicle select radius 16");
        assertParses("vehicle gear 12");
    }

    @Test void numericAndIdentifierArgumentsReachContextsAsTypedValues() {
        var gear = dispatcher.parse("vehicle gear 4", operator).getContext().build("vehicle gear 4");
        assertEquals(4, gear.getArgument("gear", Integer.class));
        var ride = dispatcher.parse("vehicle ride " + VEHICLE, operator).getContext().build("vehicle ride " + VEHICLE);
        assertEquals(VEHICLE, ride.getArgument("vehicle", UUID.class));
        var mass = dispatcher.parse("vehicle model set mass 1500", operator).getContext().build("vehicle model set mass 1500");
        assertEquals(1500.0, mass.getArgument("value", Double.class));
        var shadow = dispatcher.parse("vehicle model part option 0 shadow false", operator).getContext().build("vehicle model part option 0 shadow false");
        assertFalse(shadow.getArgument("value", Boolean.class));
    }

    @Test void greedyArgumentsOnlyRepresentTextOrTheVariableLengthRatioList() {
        Set<String> paths = new HashSet<>();
        greedyPaths(dispatcher.getRoot(), "", paths);
        assertEquals(Set.of("vehicle model set engine.ratios ratios", "vehicle model part add text text", "vehicle model part text index text"), paths);
    }

    @Test void tabCompletionUsesTheCurrentLayerInsteadOfWholeCommandSuggestions() throws Exception {
        assertEquals(Set.of("P", "R", "N", "D", "up", "down"), suggestions("vehicle gear "));
        assertEquals(Set.of("automatic", "manual", "assisted", "auto"), suggestions("vehicle mode "));
        assertEquals(Set.of("drive", "gearbox", "details", "off"), suggestions("vehicle dashboard "));
        assertEquals(Set.of("add", "remove", "item", "block", "text", "position", "rotate", "scale", "animation", "option", "move", "copy"), suggestions("vehicle model part "));
        assertTrue(suggestions("vehicle model part position 0 ").contains("here"));
        assertTrue(suggestions("vehicle model seat add ").contains("here"));
        assertTrue(suggestions("vehicle model set ").containsAll(VehicleModelDraft.SCALARS));
        assertTrue(suggestions("vehicle model set ").containsAll(VehicleModelDraft.VECTORS));
    }

    @Test void dynamicSuggestionsDoNotAccessBackendDataAfterPermissionIsRevoked() throws Exception {
        var player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, values) -> {
            if (method.getName().equals("hasPermission")) return false;
            throw new UnsupportedOperationException(method.getName());
        });
        var source = source(player);
        var context = new CommandContextBuilder<>(dispatcher, source, dispatcher.getRoot(), 0).build("");
        var root = dispatcher.getRoot().getChild("vehicle");
        for (var node : List.of(root.getChild("spawn").getChild("model"), root.getChild("ride").getChild("vehicle"), root.getChild("select").getChild("display"))) {
            var argument = (ArgumentCommandNode<CommandSourceStack, ?>) node;
            assertTrue(argument.getCustomSuggestions().getSuggestions(context, new SuggestionsBuilder("", 0)).get().isEmpty());
        }
    }

    @Test void consoleReceivesPlayerOnlyFeedbackAndAFailureResultAcrossCommandGroups() throws Exception {
        for (String input : List.of("vehicle", "vehicle list", "vehicle gear D", "vehicle select clear", "vehicle model", "vehicle model save", "vehicle reload"))
            assertEquals(0, dispatcher.execute(input, operator), input);
        assertEquals(7, messages.size());
        assertTrue(messages.stream().allMatch(message -> Component.text(Message.PLAYER_ONLY.name()).equals(message)));
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        var nativeTypes = new VehicleCommands.NativeArguments(VehicleCommandsTest::uuid, VehicleCommandsTest::position, VehicleCommandsTest::block);
        var language = new LanguageService(null) {
            @Override public Component text(Language locale, Message message, TextColor color, Object... values) { return Component.text(message.name()); }
        };
        var result = new CommandDispatcher<CommandSourceStack>();
        result.getRoot().addChild(new VehicleCommands(null, language, nativeTypes).build());
        return result;
    }

    private CommandSourceStack source(boolean use, boolean admin) {
        var sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class}, (proxy, method, values) -> switch (method.getName()) {
            case "hasPermission" -> values[0].equals(VehicleCommands.USE) ? use : values[0].equals(VehicleCommands.ADMIN) && admin;
            case "sendMessage" -> { messages.add((Component) values[0]); yield null; }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        return source(sender);
    }

    private CommandSourceStack source(CommandSender sender) {
        return (CommandSourceStack) Proxy.newProxyInstance(CommandSourceStack.class.getClassLoader(), new Class<?>[]{CommandSourceStack.class}, (proxy, method, values) -> switch (method.getName()) {
            case "getSender" -> sender;
            case "getExecutor" -> null;
            case "getLocation" -> new Location(null, 0, 0, 0);
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private void assertParses(String input) { assertTrue(complete(dispatcher.parse(input, operator)), input); }
    private static boolean complete(ParseResults<CommandSourceStack> result) {
        return !result.getReader().canRead() && result.getExceptions().isEmpty() && result.getContext().getCommand() != null;
    }
    private Set<String> suggestions(String input) throws Exception {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, operator)).get().getList().stream()
                .map(com.mojang.brigadier.suggestion.Suggestion::getText).collect(java.util.stream.Collectors.toSet());
    }
    private Set<String> suggestionsUnchecked(String input) {
        try { return suggestions(input); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
    private static void greedyPaths(CommandNode<CommandSourceStack> node, String prefix, Set<String> result) {
        String path = prefix.isEmpty() ? node.getName() : prefix + " " + node.getName();
        if (node instanceof ArgumentCommandNode<?, ?> argument && argument.getType() instanceof StringArgumentType string
                && string.getType() == StringArgumentType.StringType.GREEDY_PHRASE) result.add(path);
        for (var child : node.getChildren()) greedyPaths(child, path, result);
    }
    private static UUID uuid(StringReader reader) throws CommandSyntaxException {
        try { return UUID.fromString(token(reader)); }
        catch (IllegalArgumentException exception) { throw new SimpleCommandExceptionType(new LiteralMessage("Invalid UUID")).createWithContext(reader); }
    }
    private static FinePositionResolver position(StringReader reader) throws CommandSyntaxException {
        double coordinateX = reader.readDouble();
        reader.skipWhitespace();
        double coordinateY = reader.readDouble();
        reader.skipWhitespace();
        double coordinateZ = reader.readDouble();
        return source -> Position.fine(coordinateX, coordinateY, coordinateZ);
    }
    private static BlockState block(StringReader reader) {
        token(reader);
        return (BlockState) Proxy.newProxyInstance(BlockState.class.getClassLoader(), new Class<?>[]{BlockState.class},
                (proxy, method, values) -> { throw new UnsupportedOperationException(method.getName()); });
    }
    private static String token(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }
}
