package org.encinet.mik.module.player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuContext;
import org.encinet.mik.module.menu.FloatingMenuFeedbackKind;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenuPage;
import org.encinet.mik.module.menu.FloatingMenuScreen;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages player homes: /sethome, /home, /delhome
 * <p>
 * Storage format  →  homes.yml
 *   <uuid>:
 *     <homeName>: "world:x,y,z,yaw,pitch"
 *     <homeName><material>: "world:x,y,z,yaw,pitch"
 *     我的家:     "world:128.5,64.0,-200.3,90.0,0.0"
 *     矿洞<DIAMOND_PICKAXE>: "world:128.5,64.0,-200.3,90.0,0.0"
 * <p>
 * Runtime reads   →  100% in-memory HashMap, 零 YAML 查询
 */
public class HomeModule implements Listener {

    private static final int MAX_MENU_HOMES = 4;
    private static final Material[] DEFAULT_HOME_ICONS = {
            Material.RED_BED,
            Material.BLUE_BED,
            Material.GREEN_BED,
            Material.YELLOW_BED,
            Material.CHEST,
            Material.BARREL,
            Material.LANTERN,
            Material.COMPASS,
            Material.MAP,
            Material.BOOKSHELF,
            Material.OAK_DOOR,
            Material.BRICKS,
            Material.GRASS_BLOCK,
            Material.CHERRY_SAPLING,
            Material.AMETHYST_BLOCK,
            Material.COPPER_BLOCK,
            Material.DIAMOND_PICKAXE,
            Material.CRAFTING_TABLE,
            Material.FURNACE,
            Material.CAMPFIRE
    };

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final FloatingMenuScreen<HomeMenuState> homeScreen;
    private File dataFile;
    private YamlConfiguration data;

    /** uuid → (homeName → HomeEntry) */
    private final Map<UUID, Map<String, HomeEntry>> cache = new HashMap<>();

    public HomeModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.homeScreen = new FloatingMenuScreen<>("homes",
                ignored -> new HomeMenuState(0, null), this::buildHomeMenu);
    }

    public void enable() {
        dataFile = new File(plugin.getDataFolder(), "homes.yml");
        if (!dataFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().severe("Failed to create plugin data folder.");
                }
                if (!dataFile.createNewFile()) {
                    plugin.getLogger().warning("homes.yml already exists but was not visible during setup.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create homes.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadCache();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** 启动时把 YAML 全量读入 cache，之后不再直接操作 data */
    private void loadCache() {
        for (String uuidStr : data.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            var section = data.getConfigurationSection(uuidStr);
            if (section == null) continue;
            Map<String, HomeEntry> homes = new HashMap<>();
            for (String storedName : section.getKeys(false)) {
                String val = section.getString(storedName);
                if (val != null) {
                    ParsedHomeKey parsed = parseHomeKey(storedName);
                    homes.put(parsed.name(), new HomeEntry(val, parsed.icon()));
                }
            }
            cache.put(uuid, homes);
        }
        plugin.getLogger().info("Homes loaded: " + cache.values().stream().mapToInt(Map::size).sum() + " entries.");
    }

    private int getMaxHomes(Player player) {
        return player.hasPermission("group.member") ? 20 : 2;
    }

    public void registerCommands(LifecycleEventManager<Plugin> manager) {
        manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            // /sethome <name>
            commands.register(
                    Commands.literal("sethome")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    sendUsage(player, "/sethome <name>", Message.HOME_SET_USAGE_DESC);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player == null) {
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        String name = StringArgumentType.getString(ctx, "name");
                                        if (!isValidHomeName(name)) {
                                            sendInvalidHomeName(player);
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        List<String> existing = getHomeNames(player);
                                        int max = getMaxHomes(player);
                                        if (!existing.contains(name) && existing.size() >= max) {
                                            player.sendMessage(languageService.text(player,
                                                    Message.HOME_MAX_REACHED, NamedTextColor.RED, max));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        setHome(player, name);
                                        player.sendMessage(homeMessage(player, Message.HOME_SET_RICH,
                                                name, NamedTextColor.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.HOME_SET_COMMAND_DESCRIPTION)
            );

            // /home, /home tp <name>, /home icon <material> <name>
            commands.register(
                    Commands.literal("home")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    openHomeMenu(player);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.literal("gui")
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player != null) {
                                            openHomeMenu(player);
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .then(Commands.literal("icon")
                                    .then(Commands.argument("material", StringArgumentType.word())
                                            .suggests((ctx, builder) -> {
                                                Arrays.stream(Material.values())
                                                        .filter(Material::isItem)
                                                        .map(material -> material.name().toLowerCase(Locale.ROOT))
                                                        .forEach(builder::suggest);
                                                return builder.buildFuture();
                                            })
                                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                                    .suggests((ctx, builder) -> {
                                                        if (ctx.getSource().getSender() instanceof Player player) {
                                                            getHomeNames(player).forEach(builder::suggest);
                                                        }
                                                        return builder.buildFuture();
                                                    })
                                                    .executes(ctx -> {
                                                        Player player = requirePlayer(ctx.getSource().getSender());
                                                        if (player == null) {
                                                            return Command.SINGLE_SUCCESS;
                                                        }
                                                        String name = StringArgumentType.getString(ctx, "name");
                                                        Material material = parseIconMaterial(StringArgumentType.getString(ctx, "material"));
                                                        if (material == null) {
                                                            player.sendMessage(languageService.text(player,
                                                                    Message.HOME_INVALID_ICON, NamedTextColor.RED));
                                                            return Command.SINGLE_SUCCESS;
                                                        }
                                                        setHomeIcon(player, name, material);
                                                        return Command.SINGLE_SUCCESS;
                                                    }))))
                            .then(Commands.literal("tp")
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player != null) {
                                            sendUsage(player, "/home tp <name>",
                                                    Message.HOME_TELEPORT_COMMAND_DESCRIPTION);
                                            sendHomeList(player);
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    })
                                    .then(Commands.argument("name", StringArgumentType.greedyString())
                                            .suggests((ctx, builder) -> {
                                                if (ctx.getSource().getSender() instanceof Player player) {
                                                    getHomeNames(player).forEach(builder::suggest);
                                                }
                                                return builder.buildFuture();
                                            })
                                            .executes(ctx -> {
                                                Player player = requirePlayer(ctx.getSource().getSender());
                                                if (player == null) {
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                                String name = StringArgumentType.getString(ctx, "name");
                                                teleportHome(player, name);
                                                return Command.SINGLE_SUCCESS;
                                            })))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.HOME_MENU_TITLE)
            );

            // /delhome <name>
            commands.register(
                    Commands.literal("delhome")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    sendUsage(player, "/delhome <name>", Message.HOME_DELETE_USAGE_DESC);
                                    sendHomeList(player);
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("name", StringArgumentType.greedyString())
                                    .suggests((ctx, builder) -> {
                                        if (ctx.getSource().getSender() instanceof Player player) {
                                            getHomeNames(player).forEach(builder::suggest);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player == null) {
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        String name = StringArgumentType.getString(ctx, "name");
                                        if (!isValidHomeName(name)) {
                                            sendInvalidHomeName(player);
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        if (!deleteHome(player, name)) {
                                            player.sendMessage(homeMessage(player, Message.HOME_NOT_FOUND_RICH,
                                                    name, NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        player.sendMessage(homeMessage(player, Message.HOME_DELETED_RICH,
                                                name, NamedTextColor.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    languageService.t(Language.DEFAULT, Message.HOME_DELETE_COMMAND_DESCRIPTION)
            );

        });
    }


    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    private void sendUsage(Player player, String command, Message description) {
        player.sendMessage(Component.text()
                .append(Component.text(languageService.t(player, Message.USAGE), NamedTextColor.YELLOW))
                .append(Component.space())
                .append(Component.text(command, NamedTextColor.AQUA))
                .append(Component.text("  " + languageService.t(player, description), NamedTextColor.GRAY))
                .build());
    }


    private void sendHomeList(Player player) {
        List<String> homes = getHomeNames(player);
        if (homes.isEmpty()) {
            player.sendMessage(languageService.text(player, Message.HOME_NO_HOMES, NamedTextColor.GRAY));
            return;
        }
        String joinedHomes = String.join(", ", homes);
        player.sendMessage(languageService.rich(player, Message.HOME_LIST_RICH, NamedTextColor.GRAY,
                RichArg.component("homes", Component.text(joinedHomes, NamedTextColor.YELLOW), joinedHomes)));
    }

    private void openHomeMenu(Player player) {
        homeScreen.open(player, new HomeMenuState(0, null));
    }

    private FloatingMenuDefinition buildHomeMenu(FloatingMenuContext<HomeMenuState> context) {
        Player player = context.player();
        List<String> homes = getHomeNames(player);
        homes.sort(String.CASE_INSENSITIVE_ORDER);
        FloatingMenuPage page = new FloatingMenuPage(context.state().pageIndex(),
                homes.size(), MAX_MENU_HOMES);
        List<String> visibleHomes = page.slice(homes);
        String activeHome = visibleHomes.contains(context.state().focusedHome())
                ? context.state().focusedHome()
                : visibleHomes.isEmpty() ? null : visibleHomes.getFirst();
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("homes")
                .layout(FloatingMenuLayouts.horizontalPanels(0.52,
                        FloatingMenuLayouts.panel("browser",
                                FloatingMenuLayouts.verticalRegions(0.24,
                                        FloatingMenuLayouts.region("header",
                                                FloatingMenuLayouts.adaptiveRow(0.0)),
                                        FloatingMenuLayouts.region("homes",
                                                FloatingMenuLayouts.adaptiveCurvedGrid(
                                                        2, 0.30, 0.20, 0.20)),
                                        FloatingMenuLayouts.region("empty",
                                                FloatingMenuLayouts.adaptiveColumn(0.12)),
                                        FloatingMenuLayouts.region("pagination",
                                                FloatingMenuLayouts.adaptiveArc(0.18, 0.10)),
                                        FloatingMenuLayouts.region("global-actions",
                                                FloatingMenuLayouts.adaptiveArc(0.24, 0.12))),
                                "header", "homes", "empty", "pagination", "global-actions"),
                        FloatingMenuLayouts.panel("inspector",
                                FloatingMenuLayouts.verticalRegions(0.24,
                                        FloatingMenuLayouts.region("detail",
                                                FloatingMenuLayouts.adaptiveColumn(0.0)),
                                        FloatingMenuLayouts.region("actions",
                                                FloatingMenuLayouts.adaptiveCurvedGrid(
                                                        2, 0.24, 0.18, 0.14))),
                                "detail", "actions")));
        menu.information("summary", summaryLabel(player, homes.size()))
                .region("header");

        if (homes.isEmpty()) {
            menu.information("empty", Component.text(languageService.t(player,
                                    Message.HOME_EMPTY_TITLE), NamedTextColor.GRAY)
                            .append(Component.newline())
                            .append(Component.text(languageService.t(player,
                                    Message.HOME_EMPTY_DESCRIPTION), NamedTextColor.DARK_GRAY))
                            .append(Component.newline())
                            .append(Component.text(languageService.t(player,
                                    Message.HOME_USAGE_SETHOME), NamedTextColor.YELLOW)))
                    .region("empty");
        } else {
            for (int offset = 0; offset < visibleHomes.size(); offset++) {
                String homeName = visibleHomes.get(offset);
                HomePresentation presentation = homePresentation(player, homeName, false);
                menu.item("home:" + (page.fromIndex() + offset),
                                presentation.material(), presentation.label())
                        .region("homes")
                        .selected(homeName.equals(activeHome))
                        .focus((p, handle, focused) -> {
                            if (focused && !homeName.equals(activeHome)) {
                                context.setState(new HomeMenuState(page.index(), homeName));
                            }
                        })
                        .primary((p, handle) -> teleportHome(p, homeName))
                        .secondary((p, handle) -> {
                            if (p.getInventory().getItemInMainHand().getType() != Material.AIR) {
                                setHomeIconFromHand(p, homeName);
                                context.redraw();
                            } else openDeleteConfirmMenu(context, homeName);
                        })
                        .hotkey((p, handle) -> openUpdateConfirmMenu(
                                context, homeName, p.getLocation()));
            }

            HomePresentation detail = homePresentation(player, activeHome, true);
            menu.item("active-home", detail.material(), detail.label())
                    .region("detail")
                    .passive();
            menu.item("action:teleport", Material.ENDER_PEARL,
                            Component.text(languageService.t(player,
                                    Message.HOME_ACTION_TELEPORT), NamedTextColor.GREEN))
                    .region("actions")
                    .primary((p, handle) -> teleportHome(p, activeHome));
            menu.item("action:update", Material.RECOVERY_COMPASS,
                            Component.text(languageService.t(player,
                                    Message.HOME_ACTION_UPDATE), NamedTextColor.YELLOW))
                    .region("actions")
                    .hotkey((p, handle) -> openUpdateConfirmMenu(
                            context, activeHome, p.getLocation()));
            menu.item("action:icon", Material.ITEM_FRAME,
                            Component.text(languageService.t(player,
                                    Message.HOME_ACTION_ICON), NamedTextColor.AQUA))
                    .region("actions")
                    .secondary((p, handle) -> {
                        setHomeIconFromHand(p, activeHome);
                        context.redraw();
                    });
            menu.item("action:delete", Material.RED_CONCRETE,
                            Component.text(languageService.t(player,
                                    Message.HOME_ACTION_DELETE), NamedTextColor.RED))
                    .region("actions")
                    .secondary((p, handle) -> openDeleteConfirmMenu(context, activeHome));
        }
        menu.pagination("pagination", page, index ->
                context.setState(new HomeMenuState(index, null)));
        menu.item("help", Material.BOOK,
                        Component.text(languageService.t(player, Message.HOME_USAGE_BOOK),
                                NamedTextColor.GOLD))
                .region("global-actions")
                .primary((p, handle) -> context.openChild(buildHomeHelpMenu(p)));
        menu.dismiss(
                        Component.text(languageService.t(player,
                                        context.canGoBack() ? Message.BACK_TO_MAIN : Message.CLOSE),
                                context.canGoBack() ? NamedTextColor.GREEN : NamedTextColor.RED))
                .region("global-actions");
        return menu.build();
    }

    private void openDeleteConfirmMenu(FloatingMenuContext<HomeMenuState> context,
                                       String homeName) {
        Player player = context.player();
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("home-delete")
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.information("warning"),
                        FloatingMenuLayouts.navigation("actions")));
        HomePresentation preview = homePresentation(player, homeName, true);
        menu.item("preview", preview.material(), preview.label())
                .region("warning").passive();
        menu.information("warning", confirmDeleteLabel(player, homeName))
                .region("warning");
        menu.item("confirm", Material.RED_CONCRETE,
                        Component.text(languageService.t(player,
                                Message.HOME_CONFIRM_DELETE), NamedTextColor.RED))
                .region("actions")
                .primary((p, handle) -> {
                    if (deleteHome(p, homeName)) p.sendMessage(homeMessage(p, Message.HOME_DELETED_RICH, homeName, NamedTextColor.GREEN));
                    else p.sendMessage(homeMessage(p, Message.HOME_NOT_FOUND_RICH, homeName, NamedTextColor.RED));
                    homeScreen.update(p, state -> new HomeMenuState(state.pageIndex(), null));
                    handle.back();
                });
        menu.back(
                        Component.text(languageService.t(player, Message.HOME_BACK),
                                NamedTextColor.GREEN))
                .region("actions");
        context.openChild(menu.build());
    }

    private void openUpdateConfirmMenu(FloatingMenuContext<HomeMenuState> context,
                                       String homeName, Location proposedLocation) {
        Player player = context.player();
        Location target = proposedLocation.clone();
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("home-update")
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.information("target"),
                        FloatingMenuLayouts.navigation("actions")));
        menu.information("target", confirmUpdateLabel(player, homeName, target))
                .region("target");
        menu.item("confirm", Material.LIME_CONCRETE,
                        Component.text(languageService.t(player,
                                Message.HOME_CONFIRM_UPDATE), NamedTextColor.GREEN))
                .region("actions")
                .primary((p, handle) -> {
                    if (getHomeEntry(p, homeName) == null) {
                        handle.feedback(homeMessage(p, Message.HOME_NOT_FOUND_RICH,
                                homeName, NamedTextColor.RED), FloatingMenuFeedbackKind.ERROR);
                    } else {
                        setHome(p, homeName, target);
                        handle.feedback(homeMessage(p, Message.HOME_UPDATED_RICH,
                                homeName, NamedTextColor.GREEN),
                                FloatingMenuFeedbackKind.SUCCESS);
                        homeScreen.update(p, state ->
                                new HomeMenuState(state.pageIndex(), homeName));
                    }
                    handle.back();
                });
        menu.back(
                        Component.text(languageService.t(player, Message.HOME_BACK),
                                NamedTextColor.GREEN))
                .region("actions");
        context.openChild(menu.build());
    }

    private FloatingMenuDefinition buildHomeHelpMenu(Player player) {
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen("home-help")
                .layout(FloatingMenuLayouts.menu(
                        FloatingMenuLayouts.heading("header"),
                        FloatingMenuLayouts.information("commands"),
                        FloatingMenuLayouts.navigation("navigation")));
        menu.information("help-heading",
                        Component.text(languageService.t(player, Message.HOME_USAGE_BOOK),
                                NamedTextColor.GOLD))
                .region("header");
        Message[] usage = {Message.HOME_USAGE_SETHOME, Message.HOME_USAGE_HOME,
                Message.HOME_USAGE_ICON, Message.HOME_USAGE_DELHOME,
                Message.HOME_USAGE_NAME_RULE};
        for (int index = 0; index < usage.length; index++) {
            menu.information("usage:" + index,
                            Component.text(languageService.t(player, usage[index]),
                                    index == usage.length - 1
                                            ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY))
                    .region("commands");
        }
        menu.back(
                        Component.text(languageService.t(player, Message.HOME_BACK),
                                NamedTextColor.GREEN))
                .region("navigation");
        return menu.build();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        homeScreen.forget(event.getPlayer());
    }

    private Component summaryLabel(Player player, int homeCount) {
        int max = getMaxHomes(player);
        return Component.text(languageService.t(player, Message.HOME_MENU_TITLE),
                        NamedTextColor.DARK_PURPLE)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                        Message.HOME_SUMMARY_COUNT, homeCount, max), NamedTextColor.GRAY));
    }

    private HomePresentation homePresentation(Player player, String homeName, boolean detailed) {
        HomeEntry entry = getHomeEntry(player, homeName);
        Location location = getHome(player, homeName);
        Material displayMaterial = homeMaterial(homeName, entry, location);
        Component label = Component.text(homeName, NamedTextColor.YELLOW);
        if (location != null) {
            if (detailed) {
                label = label.append(Component.newline())
                        .append(Component.text(languageService.t(player, Message.HOME_WORLD,
                                readableWorldName(player, location.getWorld())), NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.text(languageService.t(player, Message.HOME_LOCATION,
                                location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                                NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.text(languageService.t(player, Message.HOME_DISTANCE,
                                distanceText(player, location)), NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.text(languageService.t(player, Message.HOME_ICON,
                                displayMaterial.name().toLowerCase(Locale.ROOT),
                                entry != null && entry.icon() != null ? ""
                                        : "  " + languageService.t(player,
                                                Message.HOME_ICON_DEFAULT_NOTE)),
                                NamedTextColor.GRAY));
            } else {
                label = label.append(Component.newline())
                        .append(Component.text(languageService.t(player, Message.HOME_WORLD,
                                readableWorldName(player, location.getWorld())), NamedTextColor.GRAY))
                        .append(Component.newline())
                        .append(Component.text(languageService.t(player, Message.HOME_DISTANCE,
                                distanceText(player, location)), NamedTextColor.DARK_GRAY));
            }
        } else {
            label = label.append(Component.newline())
                    .append(Component.text(languageService.t(player,
                            Message.HOME_LOCATION_INVALID), NamedTextColor.RED));
        }
        return new HomePresentation(displayMaterial, label);
    }

    private Component confirmDeleteLabel(Player player, String homeName) {
        return Component.text(languageService.t(player, Message.HOME_CONFIRM_DELETE), NamedTextColor.RED)
                .append(Component.newline())
                .append(homeMessage(player, Message.HOME_CONFIRM_DELETE_DETAIL_RICH,
                        homeName, NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                        Message.HOME_CONFIRM_DELETE_WARNING), NamedTextColor.GRAY));
    }

    private Component confirmUpdateLabel(Player player, String homeName, Location target) {
        return homeMessage(player, Message.HOME_CONFIRM_UPDATE_DETAIL_RICH,
                        homeName, NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text(languageService.t(player, Message.HOME_WORLD,
                        readableWorldName(player, target.getWorld())), NamedTextColor.GRAY))
                .append(Component.newline())
                .append(Component.text(languageService.t(player, Message.HOME_LOCATION,
                        target.getBlockX(), target.getBlockY(), target.getBlockZ()),
                        NamedTextColor.YELLOW));
    }

    private Component homeMessage(Player player, Message message, String homeName, NamedTextColor baseColor) {
        return languageService.rich(player, message, baseColor,
                RichArg.component("home", Component.text(homeName, NamedTextColor.YELLOW), homeName));
    }

    private void setHome(Player player, String name) {
        setHome(player, name, player.getLocation());
    }

    private void setHome(Player player, String name, Location location) {
        Location loc = location.clone();
        World world = Objects.requireNonNull(loc.getWorld(), "Home location needs a world");
        String value = world.getName() + ":"
                + loc.getX() + "," + loc.getY() + "," + loc.getZ() + ","
                + loc.getYaw() + "," + loc.getPitch();
        HomeEntry existing = getHomeEntry(player, name);
        Material icon = existing != null ? existing.icon() : null;
        cache.computeIfAbsent(player.getUniqueId(), _ -> new HashMap<>()).put(name, new HomeEntry(value, icon));
        save();
    }

    private Location getHome(Player player, String name) {
        HomeEntry entry = getHomeEntry(player, name);
        if (entry == null) return null;
        return parseLocation(entry.locationRaw(), player.getName(), name);
    }

    private HomeEntry getHomeEntry(Player player, String name) {
        Map<String, HomeEntry> homes = cache.get(player.getUniqueId());
        if (homes == null) return null;
        return homes.get(name);
    }

    private boolean deleteHome(Player player, String name) {
        Map<String, HomeEntry> homes = cache.get(player.getUniqueId());
        if (homes == null || !homes.containsKey(name)) return false;
        homes.remove(name);
        save();
        return true;
    }

    private List<String> getHomeNames(Player player) {
        Map<String, HomeEntry> homes = cache.get(player.getUniqueId());
        if (homes == null) return new ArrayList<>();
        return new ArrayList<>(homes.keySet());
    }

    private void setHomeIcon(Player player, String name, Material icon) {
        HomeEntry entry = getHomeEntry(player, name);
        if (entry == null) {
            player.sendMessage(homeMessage(player, Message.HOME_NOT_FOUND_RICH, name, NamedTextColor.RED));
            return;
        }
        cache.get(player.getUniqueId()).put(name, new HomeEntry(entry.locationRaw(), icon));
        save();
        player.sendMessage(homeMessage(player, Message.HOME_ICON_UPDATED_RICH, name, NamedTextColor.GREEN));
    }

    private void setHomeIconFromHand(Player player, String name) {
        Material material = parseIconMaterial(player.getInventory().getItemInMainHand().getType().name());
        if (material == null) {
            player.sendMessage(languageService.text(player, Message.HOME_ICON_HAND_INVALID, NamedTextColor.RED));
            return;
        }
        setHomeIcon(player, name, material);
    }

    private void teleportHome(Player player, String name) {
        Location loc = getHome(player, name);
        if (loc == null) {
            player.sendMessage(homeMessage(player, Message.HOME_NOT_FOUND_RICH, name, NamedTextColor.RED));
            return;
        }
        FloatingMenus.current(player).ifPresent(handle -> handle.close());
        player.teleportAsync(loc).thenAccept(success -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(homeMessage(player, Message.HOME_TELEPORTED_RICH, name, NamedTextColor.GREEN));
                } else {
                    player.sendMessage(languageService.text(player, Message.HOME_TELEPORT_FAILED, NamedTextColor.RED));
                }
            });
        });
    }

    /** "world:x,y,z,yaw,pitch"  →  Location，格式有误返回 null */
    private Location parseLocation(String raw, String playerName, String homeName) {
        int colon = raw.indexOf(':');
        if (colon < 1) return malformed(playerName, homeName);
        World world = plugin.getServer().getWorld(raw.substring(0, colon));
        if (world == null) return malformed(playerName, homeName);
        String[] c = raw.substring(colon + 1).split(",", 5);
        if (c.length != 5) return malformed(playerName, homeName);
        try {
            return new Location(world,
                    Double.parseDouble(c[0]),
                    Double.parseDouble(c[1]),
                    Double.parseDouble(c[2]),
                    Float.parseFloat(c[3]),
                    Float.parseFloat(c[4]));
        } catch (NumberFormatException e) {
            return malformed(playerName, homeName);
        }
    }

    private Location malformed(String player, String home) {
        plugin.getLogger().warning("Malformed home entry: player=" + player + " home=" + home);
        return null;
    }

    /**
     * 主线程做快照，异步写盘。
     * 用 createSection 而非点路径拼接；家名支持中文/点号，< > : 作为格式保留符号。
     */
    private void save() {
        Map<UUID, Map<String, HomeEntry>> snapshot = new HashMap<>();
        cache.forEach((uuid, homes) -> snapshot.put(uuid, new HashMap<>(homes)));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration yml = new YamlConfiguration();
            snapshot.forEach((uuid, homes) -> {
                var section = yml.createSection(uuid.toString());
                homes.forEach((name, entry) -> section.set(formatHomeKey(name, entry), entry.locationRaw()));
            });
            try {
                yml.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save homes.yml: " + e.getMessage());
            }
        });
    }

    private boolean isValidHomeName(String name) {
        return !name.isBlank()
                && name.indexOf('<') < 0
                && name.indexOf('>') < 0
                && name.indexOf(':') < 0;
    }

    private void sendInvalidHomeName(Player player) {
        player.sendMessage(languageService.text(player, Message.HOME_INVALID_NAME, NamedTextColor.RED));
    }

    private ParsedHomeKey parseHomeKey(String storedName) {
        int left = storedName.lastIndexOf('<');
        int right = storedName.endsWith(">") ? storedName.length() - 1 : -1;
        if (left > 0 && right > left) {
            String name = storedName.substring(0, left);
            Material icon = parseIconMaterial(storedName.substring(left + 1, right));
            if (icon != null) {
                return new ParsedHomeKey(name, icon);
            }
        }
        return new ParsedHomeKey(storedName, null);
    }

    private String formatHomeKey(String name, HomeEntry entry) {
        if (entry.icon() == null) {
            return name;
        }
        return name + "<" + entry.icon().name() + ">";
    }

    private Material parseIconMaterial(String input) {
        Material material = Material.matchMaterial(input.toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem() || material == Material.AIR) {
            return null;
        }
        return material;
    }

    private Material homeMaterial(String homeName, HomeEntry entry, Location location) {
        if (entry != null && entry.icon() != null && entry.icon().isItem() && entry.icon() != Material.AIR) {
            return entry.icon();
        }
        if (location == null) {
            return Material.BARRIER;
        }
        return DEFAULT_HOME_ICONS[Math.floorMod(homeName.hashCode(), DEFAULT_HOME_ICONS.length)];
    }

    private String readableWorldName(Player player, World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> world.getName();
            case NETHER -> world.getName() + "  " + languageService.t(player, Message.HOME_WORLD_NETHER);
            case THE_END -> world.getName() + "  " + languageService.t(player, Message.HOME_WORLD_END);
            case CUSTOM -> world.getName();
        };
    }

    private String distanceText(Player player, Location location) {
        if (!player.getWorld().equals(location.getWorld())) {
            return languageService.t(player, Message.HOME_DISTANCE_OTHER_WORLD);
        }
        return languageService.t(player, Message.HOME_DISTANCE_BLOCKS,
                Math.round(player.getLocation().distance(location)));
    }

    private record HomeEntry(String locationRaw, Material icon) {
    }

    private record HomeMenuState(int pageIndex, String focusedHome) {
        private HomeMenuState {
            if (pageIndex < 0) throw new IllegalArgumentException("Page index must not be negative");
        }
    }

    private record HomePresentation(Material material, Component label) {
    }

    private record ParsedHomeKey(String name, Material icon) {
    }
}
