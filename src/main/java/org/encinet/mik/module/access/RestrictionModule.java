package org.encinet.mik.module.access;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentIteratorType;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.role.RolePermissions;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.safety.EntitySizePolicy;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Module for restricting commands with entity selectors and player names
 */
public class RestrictionModule implements Listener {

    private static final Pattern SELECTOR_PATTERN = Pattern.compile("@[earpn](?:\\[|\\s|$)");
    // 匹配命令中的 UUID 格式字符串
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern UUID_INT_ARRAY_PATTERN = Pattern.compile(
            "\\[\\s*[Ii]\\s*;\\s*([+-]?\\d+)\\s*,\\s*([+-]?\\d+)\\s*,\\s*"
                    + "([+-]?\\d+)\\s*,\\s*([+-]?\\d+)\\s*\\]"
    );
    private static final Pattern EXECUTE_ENTITY_COMMAND_PATTERN = Pattern.compile(
            "\\brun\\s+(?:minecraft:)?(summon|give)\\s+", Pattern.CASE_INSENSITIVE);
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final LanguageService languageService;

    public RestrictionModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("RestrictionModule enabled");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (RolePermissions.canModerate(player)) {
            return;
        }
        Component message = event.message();
        if (containsNewline(message)) {
            event.setCancelled(true);
            player.sendMessage(mm(player, Message.RESTRICTION_NO_NEWLINE_MM));
        }
        if (containsIllegalRunCommand(message)) {
            event.setCancelled(true);
            player.sendMessage(mm(player, Message.RESTRICTION_NO_RUN_COMMAND_MM));
        }
    }

    public static boolean containsNewline(Component component) {
        StringBuilder sb = new StringBuilder();
        ComponentFlattener.basic().flatten(component, sb::append);
        return sb.indexOf("\n") >= 0;
    }

    private boolean containsIllegalRunCommand(Component component) {
        for (Component child : component.iterable(ComponentIteratorType.DEPTH_FIRST)) {
            ClickEvent clickEvent = child.clickEvent();

            if (clickEvent == null
                    || clickEvent.action() != ClickEvent.Action.RUN_COMMAND) {
                continue;
            }

            boolean illegal = switch (clickEvent.payload()) {
                case ClickEvent.Payload.Text text -> {
                    String cmd = text.value()
                            .trim()
                            .toLowerCase();

                    yield !(cmd.startsWith("/tp")
                            || cmd.startsWith("/seed"));
                }
                default -> true;
            };

            if (illegal) {
                return true;
            }
        }

        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();
        ParsedCommand command = parseCommand(message);
        if (command == null) return;

        RestrictionViolation violation = evaluateCommand(player, command);
        if (violation == RestrictionViolation.NONE) return;

        event.setCancelled(true);
        player.sendMessage(mm(player, violation.message()));
        if (violation == RestrictionViolation.KILL_ALL_ENTITIES) {
            plugin.getLogger().warning("Blocked /kill @e from " + player.getName());
        } else {
            plugin.getLogger().info("Blocked " + violation.logLabel() + " from "
                    + player.getName() + ": " + message);
        }
    }

    private RestrictionViolation evaluateCommand(Player player, ParsedCommand command) {
        if (command.name().equals("kill") && command.arguments().equalsIgnoreCase("@e")) {
            return RestrictionViolation.KILL_ALL_ENTITIES;
        }
        if (containsOversizedEntityData(command)) {
            return RestrictionViolation.OVERSIZED_ENTITY;
        }
        if (RolePermissions.canModerate(player)) {
            return RestrictionViolation.NONE;
        }

        CommandPolicy policy = policyFor(command.name());
        if (containsRestrictedSelector(command, policy)) {
            return RestrictionViolation.SELECTOR;
        }
        if (policy.checkUuids() && containsForeignUuid(command.arguments(), player)) {
            return RestrictionViolation.FOREIGN_UUID;
        }
        // /summon deliberately permits arbitrary UUIDs in entity data, but an online
        // player's UUID must never be usable as a projectile or mob owner.
        if (containsForeignPlayerUuid(command.arguments(), player.getUniqueId(),
                uuid -> Bukkit.getPlayer(uuid) != null)) {
            return RestrictionViolation.FOREIGN_UUID;
        }
        if (policy.checkPlayerNames() && containsOtherPlayerName(command.arguments(), player)) {
            return RestrictionViolation.OTHER_PLAYER;
        }
        return RestrictionViolation.NONE;
    }

    static boolean containsOversizedEntityData(String rawCommand) {
        ParsedCommand command = parseCommand(rawCommand);
        return command != null && containsOversizedEntityData(command);
    }

    private static boolean containsOversizedEntityData(ParsedCommand command) {
        return switch (command.name()) {
            case "summon" -> EntitySizePolicy.hasOversizedSummonData(command.arguments());
            case "give" -> EntitySizePolicy.hasOversizedGiveData(command.arguments());
            case "execute" -> containsOversizedExecutedCommand(command.arguments());
            default -> false;
        };
    }

    private static boolean containsOversizedExecutedCommand(String arguments) {
        Matcher matcher = EXECUTE_ENTITY_COMMAND_PATTERN.matcher(arguments);
        while (matcher.find()) {
            String nestedArguments = arguments.substring(matcher.end());
            boolean oversized = matcher.group(1).equalsIgnoreCase("summon")
                    ? EntitySizePolicy.hasOversizedSummonData(nestedArguments)
                    : EntitySizePolicy.hasOversizedGiveData(nestedArguments);
            if (oversized) {
                return true;
            }
        }
        return false;
    }

    private boolean containsForeignUuid(String arguments, Player player) {
        Matcher uuidMatcher = UUID_PATTERN.matcher(arguments);
        while (uuidMatcher.find()) {
            String uuidStr = uuidMatcher.group();
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            // 是否是玩家自己
            if (uuid.equals(player.getUniqueId())) {
                continue;
            }

            // 是否是该玩家驯服的生物
            if (isOwnedTamedMob(uuid, player)) {
                continue;
            }
            return true;
        }
        return false;
    }

    static boolean containsForeignPlayerUuid(String arguments, UUID senderId,
                                             Predicate<UUID> isOnlinePlayer) {
        Matcher stringMatcher = UUID_PATTERN.matcher(arguments);
        while (stringMatcher.find()) {
            UUID uuid = UUID.fromString(stringMatcher.group());
            if (!uuid.equals(senderId) && isOnlinePlayer.test(uuid)) {
                return true;
            }
        }

        // Minecraft stores entity Owner UUIDs as four signed integers in SNBT.
        Matcher arrayMatcher = UUID_INT_ARRAY_PATTERN.matcher(arguments);
        while (arrayMatcher.find()) {
            try {
                long most = ((long) Integer.parseInt(arrayMatcher.group(1)) << 32)
                        | (Integer.parseInt(arrayMatcher.group(2)) & 0xffffffffL);
                long least = ((long) Integer.parseInt(arrayMatcher.group(3)) << 32)
                        | (Integer.parseInt(arrayMatcher.group(4)) & 0xffffffffL);
                UUID uuid = new UUID(most, least);
                if (!uuid.equals(senderId) && isOnlinePlayer.test(uuid)) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // An invalid integer array cannot be decoded as a Minecraft UUID.
            }
        }
        return false;
    }

    private Component mm(Player player, Message message) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message));
    }

    static boolean containsRestrictedSelector(String rawCommand) {
        ParsedCommand command = parseCommand(rawCommand);
        return command != null && containsRestrictedSelector(command, policyFor(command.name()));
    }

    static boolean checksUuids(String rawCommand) {
        ParsedCommand command = parseCommand(rawCommand);
        return command != null && policyFor(command.name()).checkUuids();
    }

    static boolean checksPlayerNames(String rawCommand) {
        ParsedCommand command = parseCommand(rawCommand);
        return command != null && policyFor(command.name()).checkPlayerNames();
    }

    private static boolean containsRestrictedSelector(ParsedCommand command, CommandPolicy policy) {
        String arguments = policy.selectorScope().select(command.arguments());
        return SELECTOR_PATTERN.matcher(arguments.toLowerCase(Locale.ROOT)).find();
    }

    private static CommandPolicy policyFor(String commandName) {
        return switch (commandName) {
            case "global", "public" -> CommandPolicy.CHAT_MESSAGE;
            case "w", "tell", "msg", "whisper" -> CommandPolicy.DIRECT_MESSAGE;
            case "r", "reply" -> CommandPolicy.REPLY;
            case "tp", "teleport", "give" -> CommandPolicy.PLAYER_TARGET;
            case "summon" -> CommandPolicy.SUMMON;
            case "mikrepeat" -> CommandPolicy.UUID_EXEMPT;
            default -> CommandPolicy.DEFAULT;
        };
    }

    private static String firstArgument(String arguments) {
        int separator = firstWhitespace(arguments);
        return separator < 0 ? arguments : arguments.substring(0, separator);
    }

    private static ParsedCommand parseCommand(String rawCommand) {
        if (rawCommand == null) return null;

        String command = rawCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1).stripLeading();
        }
        if (command.isEmpty()) return null;

        int separator = firstWhitespace(command);
        String name = separator < 0 ? command : command.substring(0, separator);
        String arguments = separator < 0 ? "" : command.substring(separator + 1).stripLeading();
        int namespaceSeparator = name.lastIndexOf(':');
        if (namespaceSeparator >= 0) {
            name = name.substring(namespaceSeparator + 1);
        }
        if (name.isEmpty()) return null;

        return new ParsedCommand(name.toLowerCase(Locale.ROOT), arguments);
    }

    private static int firstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private record ParsedCommand(String name, String arguments) {
    }

    private enum SelectorScope {
        ALL_ARGUMENTS,
        FIRST_ARGUMENT,
        NO_ARGUMENTS;

        private String select(String arguments) {
            return switch (this) {
                case ALL_ARGUMENTS -> arguments;
                case FIRST_ARGUMENT -> firstArgument(arguments);
                case NO_ARGUMENTS -> "";
            };
        }
    }

    private record CommandPolicy(
            SelectorScope selectorScope,
            boolean checkUuids,
            boolean checkPlayerNames
    ) {
        private static final CommandPolicy DEFAULT =
                new CommandPolicy(SelectorScope.ALL_ARGUMENTS, true, true);
        private static final CommandPolicy DIRECT_MESSAGE =
                new CommandPolicy(SelectorScope.FIRST_ARGUMENT, true, false);
        private static final CommandPolicy REPLY =
                new CommandPolicy(SelectorScope.NO_ARGUMENTS, true, false);
        private static final CommandPolicy CHAT_MESSAGE =
                new CommandPolicy(SelectorScope.NO_ARGUMENTS, true, false);
        private static final CommandPolicy PLAYER_TARGET =
                new CommandPolicy(SelectorScope.ALL_ARGUMENTS, true, false);
        private static final CommandPolicy SUMMON =
                new CommandPolicy(SelectorScope.ALL_ARGUMENTS, false, false);
        private static final CommandPolicy UUID_EXEMPT =
                new CommandPolicy(SelectorScope.ALL_ARGUMENTS, false, true);
    }

    private enum RestrictionViolation {
        NONE(null, ""),
        KILL_ALL_ENTITIES(Message.RESTRICTION_NO_KILL_E_MM, "kill-all command"),
        SELECTOR(Message.RESTRICTION_NO_SELECTOR_MM, "selector command"),
        FOREIGN_UUID(Message.RESTRICTION_FOREIGN_UUID_MM, "foreign UUID command"),
        OVERSIZED_ENTITY(Message.RESTRICTION_ENTITY_TOO_LARGE_MM, "oversized entity command"),
        OTHER_PLAYER(Message.RESTRICTION_OTHER_PLAYER_NAME_MM, "player name command");

        private final Message message;
        private final String logLabel;

        RestrictionViolation(Message message, String logLabel) {
            this.message = message;
            this.logLabel = logLabel;
        }

        private Message message() {
            return message;
        }

        private String logLabel() {
            return logLabel;
        }
    }

    /**
     * 判断某个 UUID 对应的实体是否是 player 驯服的生物
     */
    private boolean isOwnedTamedMob(UUID uuid, Player player) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity instanceof Tameable tameable) {
                return tameable.isTamed()
                        && tameable.getOwner() != null
                        && tameable.getOwner().getUniqueId().equals(player.getUniqueId());
            }
        }
        return false;
    }

    /**
     * 检查命令中是否含有其他在线玩家的名字
     */
    private boolean containsOtherPlayerName(String command, Player sender) {
        String commandLower = command.toLowerCase();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.getUniqueId().equals(sender.getUniqueId())) continue;

            String playerName = onlinePlayer.getName().toLowerCase();
            String namePattern = "\\b" + Pattern.quote(playerName) + "\\b";
            if (commandLower.matches(".*(" + namePattern + ").*")) {
                return true;
            }
        }
        return false;
    }
}
