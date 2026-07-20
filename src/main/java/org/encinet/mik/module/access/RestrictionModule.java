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
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.util.Locale;
import java.util.UUID;
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
        if (player.hasPermission("group." + Mik.GROUP_HELPER)) {
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
        if (player.hasPermission("group." + Mik.GROUP_HELPER)) {
            return RestrictionViolation.NONE;
        }

        CommandPolicy policy = policyFor(command.name());
        if (containsRestrictedSelector(command, policy)) {
            return RestrictionViolation.SELECTOR;
        }
        if (!policy.checkIdentities()) {
            return RestrictionViolation.NONE;
        }
        if (containsForeignUuid(command.arguments(), player)) {
            return RestrictionViolation.FOREIGN_UUID;
        }
        if (containsOtherPlayerName(command.arguments(), player)) {
            return RestrictionViolation.OTHER_PLAYER;
        }
        return RestrictionViolation.NONE;
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

    private Component mm(Player player, Message message) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message));
    }

    static boolean containsRestrictedSelector(String rawCommand) {
        ParsedCommand command = parseCommand(rawCommand);
        return command != null && containsRestrictedSelector(command, policyFor(command.name()));
    }

    static boolean checksIdentities(String rawCommand) {
        ParsedCommand command = parseCommand(rawCommand);
        return command != null && policyFor(command.name()).checkIdentities();
    }

    private static boolean containsRestrictedSelector(ParsedCommand command, CommandPolicy policy) {
        String arguments = policy.selectorScope().select(command.arguments());
        return SELECTOR_PATTERN.matcher(arguments.toLowerCase(Locale.ROOT)).find();
    }

    private static CommandPolicy policyFor(String commandName) {
        return switch (commandName) {
            case "w", "tell", "msg", "whisper" -> CommandPolicy.DIRECT_MESSAGE;
            case "r", "reply" -> CommandPolicy.REPLY;
            case "tp", "teleport" -> CommandPolicy.TELEPORT;
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

    private record CommandPolicy(SelectorScope selectorScope, boolean checkIdentities) {
        private static final CommandPolicy DEFAULT =
                new CommandPolicy(SelectorScope.ALL_ARGUMENTS, true);
        private static final CommandPolicy DIRECT_MESSAGE =
                new CommandPolicy(SelectorScope.FIRST_ARGUMENT, false);
        private static final CommandPolicy REPLY =
                new CommandPolicy(SelectorScope.NO_ARGUMENTS, false);
        private static final CommandPolicy TELEPORT =
                new CommandPolicy(SelectorScope.ALL_ARGUMENTS, false);
    }

    private enum RestrictionViolation {
        NONE(null, ""),
        KILL_ALL_ENTITIES(Message.RESTRICTION_NO_KILL_E_MM, "kill-all command"),
        SELECTOR(Message.RESTRICTION_NO_SELECTOR_MM, "selector command"),
        FOREIGN_UUID(Message.RESTRICTION_FOREIGN_UUID_MM, "foreign UUID command"),
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
