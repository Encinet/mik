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
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.communication.MentionModule;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuDialogs;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainMenuModule implements Listener {

    private static final DateTimeFormatter FIRST_JOINED_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Shanghai"));
    private static final int MENU_SIZE = 54;
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_OPEN_MENTION = "open:mention";
    private static final String ACTION_OPEN_TELEPORT = "open:teleport";
    private static final String ACTION_OPEN_PVP = "open:pvp";
    private static final String ACTION_OPEN_LANGUAGE = "open:language";
    private static final String ACTION_OPEN_WEBSITE = "open:url:website";
    private static final String ACTION_OPEN_MAP = "open:url:map";
    private static final String ACTION_OPEN_WIKI = "open:url:wiki";
    private static final String ACTION_COMMAND_PREFIX = "command:";
    private static final String URL_WEBSITE = "https://mcmik.top";
    private static final String URL_MAP = "https://mcmik.top/map";
    private static final String URL_WIKI = "https://mcmik.top/wiki";

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final MentionModule mentionModule;
    private final TeleportPreferenceModule teleportPreferenceModule;
    private final PvpModule pvpModule;
    private final MenuNavigation menuNavigation;
    private final LanguageService languageService;
    private final ClientVersionReminderModule clientVersionReminderModule;
    private final NamespacedKey actionKey;

    public MainMenuModule(JavaPlugin plugin, AfkService afkService, MentionModule mentionModule,
                          TeleportPreferenceModule teleportPreferenceModule, PvpModule pvpModule, MenuNavigation menuNavigation,
                          LanguageService languageService, ClientVersionReminderModule clientVersionReminderModule) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.mentionModule = mentionModule;
        this.teleportPreferenceModule = teleportPreferenceModule;
        this.pvpModule = pvpModule;
        this.menuNavigation = menuNavigation;
        this.languageService = languageService;
        this.clientVersionReminderModule = clientVersionReminderModule;
        this.actionKey = new NamespacedKey(plugin, "main_menu_action");
    }

    public void enable() {
        menuNavigation.setMainMenuOpener(this::openMenu);
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
                    languageService.t(Language.DEFAULT, Message.MAIN_MENU_TITLE)
            );
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!isMainMenuTitle(title)) return;

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
            case ACTION_OPEN_PVP -> {
                if (event.isRightClick()) {
                    pvpModule.openMenu(player);
                } else {
                    pvpModule.togglePvp(player);
                    openMenu(player);
                }
                return;
            }
            case ACTION_OPEN_LANGUAGE -> {
                languageService.openMenu(player);
                return;
            }
            case ACTION_OPEN_WEBSITE -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player,
                        languageService.t(player, Message.MAIN_WEBSITE), URL_WEBSITE, languageService));
                return;
            }
            case ACTION_OPEN_MAP -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player,
                        languageService.t(player, Message.MAIN_MAP), URL_MAP, languageService));
                return;
            }
            case ACTION_OPEN_WIKI -> {
                player.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> MenuDialogs.openUrlConfirm(player,
                        languageService.t(player, Message.MAIN_WIKI), URL_WIKI, languageService));
                return;
            }
        }
        if (action.startsWith(ACTION_COMMAND_PREFIX)) {
            String command = action.substring(ACTION_COMMAND_PREFIX.length());
            markMainMenuChild(player, command);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
        }

    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    public void openMenu(Player player) {
        MenuBuilder.create(MENU_SIZE, Component.text(languageService.t(player, Message.MAIN_MENU_TITLE), MenuItems.TITLE_COLOR))
                .item(22, playerSummaryItem(player))
                .item(0, urlItem(player, Material.COMPASS, Message.MAIN_WEBSITE, URL_WEBSITE, ACTION_OPEN_WEBSITE))
                .item(1, urlItem(player, Material.FILLED_MAP, Message.MAIN_MAP, URL_MAP, ACTION_OPEN_MAP))
                .item(2, urlItem(player, Material.BOOK, Message.MAIN_WIKI, URL_WIKI, ACTION_OPEN_WIKI))
                .item(6, languageMenuItem(player))
                .item(7, mentionMenuItem(player))
                .item(8, teleportMenuItem(player))
                .item(45, afkStatusItem(player))
                .item(46, commandItem(player, Material.NAME_TAG, Message.MAIN_NAME_TAG, "/nametag", List.of(Message.MAIN_NAME_TAG_LORE)))
                .item(47, commandItem(player, Material.RED_BED, Message.MAIN_HOME, "/home", List.of(Message.MAIN_HOME_LORE)))
                .item(48, commandItem(player, Material.PAPER, Message.MAIN_ANNOUNCEMENTS, "/announcements", List.of(Message.MAIN_ANNOUNCEMENTS_LORE)))
                .item(49, musicMenuItem(player))
                .item(50, pvpMenuItem(player))
                .item(53, closeItem(player))
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
            inventory.setItem(50, pvpMenuItem(player));
        }
    }

    private boolean isMainMenuOpen(Player player) {
        String title = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
        return isMainMenuTitle(title);
    }

    private ItemStack playerSummaryItem(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        Location location = player.getLocation();
        meta.displayName(Component.text(player.getName(), NamedTextColor.GOLD));
        RoleDisplay role = roleDisplay(player);
        meta.lore(List.of(
                statLine(player, Message.STAT_ROLE, languageService.t(player, role.label()), role.color()),
                clientVersionLine(player),
                statLine(player, Message.STAT_PLAY_TIME, formatPlayTime(player), NamedTextColor.GREEN),
                statLine(player, Message.STAT_PING, player.getPing() + "ms", NamedTextColor.GREEN),
                statLine(player, Message.STAT_FIRST_JOINED, formatFirstJoined(player), NamedTextColor.YELLOW),
                statLine(player, Message.STAT_WORLD, player.getWorld().getName(), NamedTextColor.AQUA),
                statLine(player, Message.STAT_LOCATION, location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(), NamedTextColor.YELLOW)
        ));
        MenuItems.style(meta);
        item.setItemMeta(meta);
        return item;
    }

    private RoleDisplay roleDisplay(Player player) {
        if (player.hasPermission("group." + Mik.GROUP_MANAGER)) {
            return new RoleDisplay(Message.ROLE_MANAGER, NamedTextColor.RED);
        }
        if (player.hasPermission("group." + Mik.GROUP_HELPER)) {
            return new RoleDisplay(Message.ROLE_HELPER, NamedTextColor.LIGHT_PURPLE);
        }
        if (player.hasPermission("group." + Mik.GROUP_MEMBER)) {
            return new RoleDisplay(Message.ROLE_MEMBER, NamedTextColor.GOLD);
        }
        return new RoleDisplay(Message.ROLE_NEW_PLAYER, NamedTextColor.GRAY);
    }

    private ItemStack mentionMenuItem(Player player) {
        return MenuItems.action(Material.BELL, Component.text(languageService.t(player, Message.MENTION_MENU_TITLE), NamedTextColor.AQUA), List.of(
                Component.text(mentionModule.summary(player), NamedTextColor.GRAY),
                Component.empty(),
                Component.text(languageService.t(player, Message.CLICK_SET), NamedTextColor.YELLOW)
        ), actionKey, ACTION_OPEN_MENTION);
    }

    private ItemStack teleportMenuItem(Player player) {
        return MenuItems.action(Material.SHIELD, Component.text(languageService.t(player, Message.TELEPORT_MENU_TITLE), NamedTextColor.AQUA), List.of(
                Component.text(teleportPreferenceModule.summary(player), NamedTextColor.GRAY),
                Component.empty(),
                Component.text(languageService.t(player, Message.CLICK_SET), NamedTextColor.YELLOW)
        ), actionKey, ACTION_OPEN_TELEPORT);
    }

    private ItemStack pvpMenuItem(Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(player, Message.MAIN_PVP_LORE), NamedTextColor.GRAY));
        lore.add(pvpModule.stateLine(player, Message.PVP_STATE_LABEL, pvpModule.isEnabled(player)));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.MAIN_PVP_LEFT_CLICK), NamedTextColor.YELLOW));
        lore.add(Component.text(languageService.t(player, Message.MAIN_PVP_RIGHT_CLICK), NamedTextColor.YELLOW));
        return MenuItems.action(Material.IRON_SWORD,
                Component.text(languageService.t(player, Message.MAIN_PVP), pvpModule.isEnabled(player) ? NamedTextColor.GREEN : NamedTextColor.AQUA),
                lore, actionKey, ACTION_OPEN_PVP);
    }

    private ItemStack languageMenuItem(Player player) {
        return MenuItems.action(Material.WRITABLE_BOOK,
                Component.text(languageService.t(player, Message.MAIN_LANGUAGE), NamedTextColor.AQUA),
                List.of(
                        Component.text(languageService.t(player, Message.MAIN_LANGUAGE_LORE, languageService.languageLabel(player)), NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text(languageService.t(player, Message.CLICK_SET), NamedTextColor.YELLOW)
                ), actionKey, ACTION_OPEN_LANGUAGE);
    }

    private ItemStack urlItem(Player player, Material material, Message title, String url, String action) {
        return MenuItems.action(material, Component.text(languageService.t(player, title), NamedTextColor.AQUA), List.of(
                Component.text(url, NamedTextColor.GRAY),
                Component.empty(),
                Component.text(languageService.t(player, Message.CLICK_OPEN), NamedTextColor.YELLOW)
        ), actionKey, action);
    }

    private ItemStack commandItem(Player player, Material material, Message title, String command, List<Message> lines) {
        List<Component> lore = new ArrayList<>();
        for (Message line : lines) {
            lore.add(Component.text(languageService.t(player, line), NamedTextColor.GRAY));
        }
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.CLICK_OPEN), NamedTextColor.YELLOW));
        return MenuItems.action(material, Component.text(languageService.t(player, title), NamedTextColor.AQUA), lore, actionKey, ACTION_COMMAND_PREFIX + command.substring(1));
    }

    private ItemStack musicMenuItem(Player player) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(player, Message.MAIN_MUSIC_LORE), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.CLICK_OPEN), NamedTextColor.YELLOW));
        return MenuItems.actionWithoutDiscTrack(Material.MUSIC_DISC_13,
                Component.text(languageService.t(player, Message.MAIN_MUSIC), NamedTextColor.AQUA),
                lore, actionKey, ACTION_COMMAND_PREFIX + "music");
    }

    private ItemStack closeItem(Player player) {
        return MenuItems.action(Material.BARRIER, Component.text(languageService.t(player, Message.CLOSE), NamedTextColor.RED),
                List.of(Component.text(languageService.t(player, Message.RETURN_TO_GAME), NamedTextColor.GRAY)), actionKey, ACTION_CLOSE);
    }

    private ItemStack afkStatusItem(Player player) {
        return commandItem(player, Material.CLOCK, Message.MAIN_AFK_STATUS, "/afk", List.of(
                afkService.isAfk(player.getUniqueId()) ? Message.MAIN_AFK_CURRENT_AFK : Message.MAIN_AFK_CURRENT_ONLINE,
                Message.MAIN_AFK_LORE
        ));
    }

    private Component statLine(Player player, Message label, String value, NamedTextColor valueColor) {
        return Component.text()
                .append(Component.text(languageService.t(player, label) + ": ", NamedTextColor.GRAY))
                .append(Component.text(value, valueColor))
                .build();
    }

    private Component statLine(Player player, Message label, Component value) {
        return Component.text()
                .append(Component.text(languageService.t(player, label) + ": ", NamedTextColor.GRAY))
                .append(value)
                .build();
    }

    private Component clientVersionLine(Player player) {
        String versionName = clientVersionReminderModule != null
                ? clientVersionReminderModule.clientVersionName(player)
                : languageService.t(player, Message.UNKNOWN);
        boolean outdated = clientVersionReminderModule != null && clientVersionReminderModule.isOutdated(player);
        Component value = Component.text(versionName, outdated ? NamedTextColor.YELLOW : NamedTextColor.GREEN);
        if (outdated) {
            value = Component.text()
                    .append(Component.text("⚠ ", NamedTextColor.GOLD))
                    .append(value)
                    .append(Component.text(" · " + languageService.t(player, Message.CLIENT_VERSION_OUTDATED_HINT,
                            clientVersionReminderModule.minimumVersionName()), NamedTextColor.GRAY))
                    .build();
        }
        return statLine(player, Message.STAT_CLIENT_VERSION, value);
    }

    private String formatPlayTime(Player player) {
        Language language = languageService.language(player);
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        Duration duration = Duration.ofSeconds(ticks / 20L);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) {
            return languageService.t(language, Message.TIME_HOURS_MINUTES, hours, minutes);
        }
        return languageService.t(language, Message.TIME_MINUTES, minutes);
    }

    private String formatFirstJoined(Player player) {
        long firstPlayed = player.getFirstPlayed();
        if (firstPlayed <= 0) {
            return languageService.t(player, Message.UNKNOWN);
        }
        return FIRST_JOINED_FORMAT.format(Instant.ofEpochMilli(firstPlayed));
    }

    private record RoleDisplay(Message label, NamedTextColor color) {
    }

    private boolean isMainMenuTitle(String title) {
        for (Language language : Language.values()) {
            if (languageService.t(language, Message.MAIN_MENU_TITLE).equals(title)) {
                return true;
            }
        }
        return false;
    }
}
