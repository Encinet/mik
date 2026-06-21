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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
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
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList("w", "tell", "msg", "tp", "teleport"));
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
        String commandLower = message.toLowerCase();

        // 全员禁止 /kill @e
        if (message.equals("/kill @e")) {
            event.setCancelled(true);
            player.sendMessage(mm(player, Message.RESTRICTION_NO_KILL_E_MM));
            plugin.getLogger().warning("Blocked /kill @e from " + player.getName());
            return;
        }

        // Helper 权限放行
        if (player.hasPermission("group." + Mik.GROUP_HELPER)) {
            return;
        }

        String[] parts = message.substring(1).split("\\s+", 2);
        String commandName = parts[0].toLowerCase();

        // 阻止目标选择器
        if (SELECTOR_PATTERN.matcher(commandLower).find()) {
            event.setCancelled(true);
            player.sendMessage(mm(player, Message.RESTRICTION_NO_SELECTOR_MM));
            plugin.getLogger().info("Blocked selector command from " + player.getName() + ": " + message);
            return;
        }

        if (ALLOWED_COMMANDS.contains(commandName)) {
            return;
        }

        // 检查命令中出现的所有 UUID
        Matcher uuidMatcher = UUID_PATTERN.matcher(message);
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

            // 其他 UUID 一律拒绝
            event.setCancelled(true);
            player.sendMessage(mm(player, Message.RESTRICTION_FOREIGN_UUID_MM));
            plugin.getLogger().info("Blocked foreign UUID command from " + player.getName() + ": " + message);
            return;
        }

        // 检查是否包含其他在线玩家的名字
        if (containsOtherPlayerName(message, player)) {
            event.setCancelled(true);
            player.sendMessage(mm(player, Message.RESTRICTION_OTHER_PLAYER_NAME_MM));
            plugin.getLogger().info("Blocked player name command from " + player.getName() + ": " + message);
        }
    }

    private Component mm(Player player, Message message) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message));
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
