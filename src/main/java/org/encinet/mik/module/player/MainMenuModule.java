package org.encinet.mik.module.player;

import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.communication.MentionModule;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuDialogs;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainMenuModule implements Listener {

    private static final String MENU_TITLE = "主菜单";
    private static final int MENU_SIZE = 54;
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_OPEN_MENTION = "open:mention";
    private static final String ACTION_OPEN_TELEPORT = "open:teleport";
    private static final String ACTION_OPEN_WEBSITE = "open:url:website";
    private static final String ACTION_OPEN_MAP = "open:url:map";
    private static final String ACTION_OPEN_WIKI = "open:url:wiki";
    private static final String ACTION_COMMAND_PREFIX = "command:";
    private static final String URL_WEBSITE = "https://mik.noctiro.moe";
    private static final String URL_MAP = "http://203.135.99.76:34567";
    private static final String URL_WIKI = "https://mik.noctiro.moe/wiki";

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final MentionModule mentionModule;
    private final TeleportPreferenceModule teleportPreferenceModule;
    private final MenuNavigation menuNavigation;
    private final NamespacedKey actionKey;

    public MainMenuModule(JavaPlugin plugin, AfkService afkService, MentionModule mentionModule, TeleportPreferenceModule teleportPreferenceModule, MenuNavigation menuNavigation) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.mentionModule = mentionModule;
        this.teleportPreferenceModule = teleportPreferenceModule;
        this.menuNavigation = menuNavigation;
        this.actionKey = new NamespacedKey(plugin, "main_menu_action");
        this.menuNavigation.setMainMenuOpener(this::openMenu);
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenMainMenus, 100L, 100L);
        plugin.getLogger().info("Main menu module enabled");
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(
                    Commands.literal("menu")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    openMenu(player);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .build(),
                    "打开主菜单"
            );
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        MenuView view = MenuView.fromTitle(title);
        if (view == null) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String action = MenuItems.readAction(item, actionKey);
        if (action == null) return;

        switch (action) {
            case ACTION_CLOSE -> {
                player.closeInventory();
                return;
            }
            case ACTION_OPEN_MENTION -> {
                mentionModule.openMenu(player);
                return;
            }
            case ACTION_OPEN_TELEPORT -> {
                teleportPreferenceModule.openMenu(player);
                return;
            }
            case ACTION_OPEN_WEBSITE -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player, "官网", URL_WEBSITE));
                return;
            }
            case ACTION_OPEN_MAP -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player, "网页地图", URL_MAP));
                return;
            }
            case ACTION_OPEN_WIKI -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player, "Wiki", URL_WIKI));
                return;
            }
        }
        if (action.startsWith(ACTION_COMMAND_PREFIX)) {
            String command = action.substring(ACTION_COMMAND_PREFIX.length());
            markMainMenuChild(player, command);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
            return;
        }

    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
        return null;
    }

    public void openMenu(Player player) {
        MenuBuilder.create(MENU_SIZE, Component.text(MENU_TITLE, MenuItems.TITLE_COLOR))
                .item(22, playerSummaryItem(player))
                .item(0, urlItem(Material.COMPASS, "官网", URL_WEBSITE, ACTION_OPEN_WEBSITE))
                .item(1, urlItem(Material.FILLED_MAP, "网页地图", URL_MAP, ACTION_OPEN_MAP))
                .item(2, urlItem(Material.BOOK, "Wiki", URL_WIKI, ACTION_OPEN_WIKI))
                .item(7, mentionMenuItem(player.getUniqueId()))
                .item(8, teleportMenuItem(player.getUniqueId()))
                .item(45, afkStatusItem(player))
                .item(46, commandItem(Material.NAME_TAG, "名称标签", "/nametag", List.of("调整自己的前缀和后缀")))
                .item(47, commandItem(Material.RED_BED, "我的家", "/home", List.of("打开家菜单")))
                .item(48, commandItem(Material.PAPER, "公告", "/announcements", List.of("查看服务器公告")))
                .item(49, commandItem(Material.MUSIC_DISC_13, "音乐", "/music", List.of("打开音乐菜单")))
                .item(53, closeItem())
                .open(player);
    }

    private void markMainMenuChild(Player player, String command) {
        String rootCommand = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        switch (rootCommand) {
            case "home" -> menuNavigation.markMainMenuChild(player, MenuNavigation.ChildMenu.HOME);
            case "announcements", "announcement" ->
                    menuNavigation.markMainMenuChild(player, MenuNavigation.ChildMenu.ANNOUNCEMENTS);
            default -> {
            }
        }
    }

    private void refreshOpenMainMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isMainMenuOpen(player)) {
                continue;
            }
            Inventory inventory = player.getOpenInventory().getTopInventory();
            inventory.setItem(22, playerSummaryItem(player));
            inventory.setItem(45, afkStatusItem(player));
        }
    }

    private boolean isMainMenuOpen(Player player) {
        String title = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
        return MENU_TITLE.equals(title);
    }

    private ItemStack playerSummaryItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        Location location = player.getLocation();
        meta.displayName(Component.text(player.getName(), NamedTextColor.GOLD));
        meta.lore(List.of(
                statLine("在线时长", formatPlayTime(player), NamedTextColor.GREEN),
                statLine("世界", player.getWorld().getName(), NamedTextColor.AQUA),
                statLine("坐标", location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(), NamedTextColor.YELLOW),
                statLine("生命", (int) Math.ceil(player.getHealth()) + " / 20", NamedTextColor.RED),
                statLine("饱食", player.getFoodLevel() + " / 20", NamedTextColor.GOLD),
                statLine("Ping", player.getPing() + "ms", NamedTextColor.GREEN),
                statLine("模式", player.getGameMode().name().toLowerCase(Locale.ROOT), NamedTextColor.AQUA)
        ));
        MenuItems.style(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack mentionMenuItem(UUID playerId) {
        return MenuItems.action(Material.BELL, Component.text("提及提醒", NamedTextColor.AQUA), List.of(
                Component.text(mentionModule.summary(playerId), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("点击设置", NamedTextColor.YELLOW)
        ), actionKey, ACTION_OPEN_MENTION);
    }

    private ItemStack teleportMenuItem(UUID playerId) {
        return MenuItems.action(Material.SHIELD, Component.text("被传送设置", NamedTextColor.AQUA), List.of(
                Component.text(teleportPreferenceModule.summary(playerId), NamedTextColor.GRAY),
                Component.empty(),
                Component.text("点击设置", NamedTextColor.YELLOW)
        ), actionKey, ACTION_OPEN_TELEPORT);
    }

    private ItemStack urlItem(Material material, String title, String url, String action) {
        return MenuItems.action(material, Component.text(title, NamedTextColor.AQUA), List.of(
                Component.text(url, NamedTextColor.GRAY),
                Component.empty(),
                Component.text("点击打开", NamedTextColor.YELLOW)
        ), actionKey, action);
    }

    private ItemStack commandItem(Material material, String title, String command, List<String> lines) {
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(Component.text(line, NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Component.text("点击打开", NamedTextColor.YELLOW));
        return MenuItems.action(material, Component.text(title, NamedTextColor.AQUA), lore, actionKey, ACTION_COMMAND_PREFIX + command.substring(1));
    }

    private ItemStack closeItem() {
        return MenuItems.action(Material.BARRIER, Component.text("关闭", NamedTextColor.RED),
                List.of(Component.text("返回游戏", NamedTextColor.GRAY)), actionKey, ACTION_CLOSE);
    }

    private ItemStack afkStatusItem(Player player) {
        return commandItem(Material.CLOCK, "挂机状态", "/afk", List.of(
                afkService.isAfk(player.getUniqueId()) ? "当前正在挂机" : "当前在线",
                "切换自己的挂机状态"
        ));
    }

    private Component statLine(String label, String value, NamedTextColor valueColor) {
        return Component.text()
                .append(Component.text(label + ": ", NamedTextColor.GRAY))
                .append(Component.text(value, valueColor))
                .build();
    }

    private String formatPlayTime(Player player) {
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        Duration duration = Duration.ofSeconds(ticks / 20L);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return hours + "小时 " + minutes + "分钟";
        }
        return minutes + "分钟";
    }

    private enum MenuView {
        MAIN(MENU_TITLE);

        private final String title;

        MenuView(String title) {
            this.title = title;
        }

        static MenuView fromTitle(String title) {
            for (MenuView view : values()) {
                if (view.title.equals(title)) {
                    return view;
                }
            }
            return null;
        }
    }
}
