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
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;

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

    private static final String MENU_TITLE = "我的家";
    private static final String DELETE_MENU_TITLE = "删除家";
    private static final int MENU_SIZE = 54;
    private static final String ACTION_CLOSE = "close";
    private static final String ACTION_BACK = "back";
    private static final String ACTION_HOME_PREFIX = "home:";
    private static final String ACTION_DELETE_PREFIX = "delete:";
    private static final int[] HOME_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33
    };
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
    private final MenuNavigation menuNavigation;
    private final NamespacedKey menuActionKey;
    private File dataFile;
    private YamlConfiguration data;

    /** uuid → (homeName → HomeEntry) */
    private final Map<UUID, Map<String, HomeEntry>> cache = new HashMap<>();

    public HomeModule(JavaPlugin plugin, MenuNavigation menuNavigation) {
        this.plugin = plugin;
        this.menuNavigation = menuNavigation;
        this.menuActionKey = new NamespacedKey(plugin, "home_menu_action");
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
                                    sendUsage(player, "/sethome <名字>", "在当前位置设置一个家");
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
                                            player.sendMessage(Component.text("你最多只能设置 " + max + " 个家", NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        setHome(player, name);
                                        player.sendMessage(homeMessage("家 ", name, " 已设置", NamedTextColor.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    "在当前位置设置一个家"
            );

            // /home <name>
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
                                                            player.sendMessage(Component.text("这个材质不能作为家的图标", NamedTextColor.RED));
                                                            return Command.SINGLE_SUCCESS;
                                                        }
                                                        setHomeIcon(player, name, material);
                                                        return Command.SINGLE_SUCCESS;
                                                    }))))
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
                                    }))
                            .build(),
                    "传送到一个家"
            );

            // /delhome <name>
            commands.register(
                    Commands.literal("delhome")
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    sendUsage(player, "/delhome <名字>", "删除一个家");
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
                                            player.sendMessage(homeMessage("家 ", name, " 不存在", NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        player.sendMessage(homeMessage("家 ", name, " 已删除", NamedTextColor.GREEN));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    "删除一个家"
            );

        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!MENU_TITLE.equals(title) && !DELETE_MENU_TITLE.equals(title)) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String action = MenuItems.readAction(item, menuActionKey);
        if (action == null) return;

        if (ACTION_CLOSE.equals(action)) {
            if (menuNavigation.returnToMainMenuIfNeeded(player, MenuNavigation.ChildMenu.HOME)) {
                return;
            }
            player.closeInventory();
            return;
        }
        if (ACTION_BACK.equals(action)) {
            openHomeMenu(player);
            return;
        }
        if (action.startsWith(ACTION_DELETE_PREFIX)) {
            String homeName = action.substring(ACTION_DELETE_PREFIX.length());
            if (deleteHome(player, homeName)) {
                player.sendMessage(homeMessage("家 ", homeName, " 已删除", NamedTextColor.GREEN));
            } else {
                player.sendMessage(homeMessage("家 ", homeName, " 不存在", NamedTextColor.RED));
            }
            openHomeMenu(player);
            return;
        }
        if (!action.startsWith(ACTION_HOME_PREFIX)) {
            return;
        }

        String homeName = action.substring(ACTION_HOME_PREFIX.length());
        if (event.getClick() == ClickType.SHIFT_RIGHT) {
            setHomeIconFromHand(player, homeName);
            openHomeMenu(player);
            return;
        }
        if (event.getClick() == ClickType.RIGHT) {
            openDeleteConfirmMenu(player, homeName);
            return;
        }
        if (event.getClick() == ClickType.SHIFT_LEFT) {
            setHome(player, homeName);
            player.sendMessage(homeMessage("家 ", homeName, " 已更新到当前位置", NamedTextColor.GREEN));
            openHomeMenu(player);
            return;
        }

        teleportHome(player, homeName);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!MENU_TITLE.equals(title) && !DELETE_MENU_TITLE.equals(title)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            String currentTitle = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
            if (!MENU_TITLE.equals(currentTitle) && !DELETE_MENU_TITLE.equals(currentTitle)) {
                menuNavigation.clearMainMenuReturn(player, MenuNavigation.ChildMenu.HOME);
            }
        });
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text("该命令只能由玩家执行", NamedTextColor.RED));
        return null;
    }

    private void sendUsage(Player player, String command, String description) {
        player.sendMessage(Component.text()
                .append(Component.text("用法 ", NamedTextColor.YELLOW))
                .append(Component.text(command, NamedTextColor.AQUA))
                .append(Component.text("  " + description, NamedTextColor.GRAY))
                .build());
    }

    private void sendHomeList(Player player) {
        List<String> homes = getHomeNames(player);
        if (homes.isEmpty()) {
            player.sendMessage(Component.text("你还没有设置家", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text()
                .append(Component.text("已有家 ", NamedTextColor.GRAY))
                .append(Component.text(String.join(", ", homes), NamedTextColor.YELLOW))
                .build());
    }

    private void openHomeMenu(Player player) {
        Inventory inventory = Bukkit.createInventory(null, MENU_SIZE, Component.text(MENU_TITLE, MenuItems.TITLE_COLOR));
        decorateMenu(inventory);

        List<String> homes = getHomeNames(player);
        homes.sort(String.CASE_INSENSITIVE_ORDER);
        inventory.setItem(4, summaryItem(player, homes.size()));

        if (homes.isEmpty()) {
            inventory.setItem(22, emptyHomesItem());
        } else {
            for (int i = 0; i < homes.size() && i < HOME_SLOTS.length; i++) {
                String homeName = homes.get(i);
                inventory.setItem(HOME_SLOTS[i], homeItem(player, homeName));
            }
        }

        inventory.setItem(45, usageBookItem());
        inventory.setItem(49, closeItem(player));
        player.openInventory(inventory);
    }

    private void openDeleteConfirmMenu(Player player, String homeName) {
        Inventory inventory = Bukkit.createInventory(null, 27, Component.text(DELETE_MENU_TITLE, NamedTextColor.RED));
        decorateMenu(inventory);
        inventory.setItem(11, confirmDeleteItem(homeName));
        inventory.setItem(13, homePreviewItem(player, homeName));
        inventory.setItem(15, backItem());
        player.openInventory(inventory);
    }

    private void decorateMenu(Inventory inventory) {
        MenuItems.border(inventory, Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack summaryItem(Player player, int homeCount) {
        int max = getMaxHomes(player);
        return MenuItems.item(Material.COMPASS, Component.text("家的位置", NamedTextColor.AQUA), List.of(
                Component.text("已设置: " + homeCount + " / " + max, NamedTextColor.GRAY),
                Component.text("左键传送，右键删除", NamedTextColor.GRAY),
                Component.text("Shift 左键更新位置，Shift 右键设置图标", NamedTextColor.GRAY)
        ));
    }

    private ItemStack homeItem(Player player, String homeName) {
        HomeEntry entry = getHomeEntry(player, homeName);
        Location location = getHome(player, homeName);
        Material displayMaterial = homeMaterial(homeName, entry, location);
        List<Component> lore = new ArrayList<>();
        if (location != null) {
            lore.add(Component.text("世界: " + readableWorldName(location.getWorld()), NamedTextColor.GRAY));
            lore.add(Component.text("坐标: " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(), NamedTextColor.GRAY));
            lore.add(Component.text("距离: " + distanceText(player, location), NamedTextColor.GRAY));
            lore.add(Component.text("图标: " + displayMaterial.name().toLowerCase(Locale.ROOT) + (entry != null && entry.icon() != null ? "" : " · 默认"), NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("这个家的位置数据有误", NamedTextColor.RED));
        }
        lore.add(Component.empty());
        lore.add(Component.text("左键传送", NamedTextColor.GREEN));
        lore.add(Component.text("右键删除", NamedTextColor.RED));
        lore.add(Component.text("Shift 左键更新位置", NamedTextColor.YELLOW));
        lore.add(Component.text("Shift 右键设置图标", NamedTextColor.YELLOW));
        return MenuItems.action(displayMaterial, Component.text(homeName, NamedTextColor.YELLOW), lore, menuActionKey, ACTION_HOME_PREFIX + homeName);
    }

    private ItemStack homePreviewItem(Player player, String homeName) {
        HomeEntry entry = getHomeEntry(player, homeName);
        Location location = getHome(player, homeName);
        List<Component> lore = new ArrayList<>();
        if (location != null) {
            lore.add(Component.text("世界: " + readableWorldName(location.getWorld()), NamedTextColor.GRAY));
            lore.add(Component.text("坐标: " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ(), NamedTextColor.GRAY));
            lore.add(Component.text("距离: " + distanceText(player, location), NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("这个家的位置数据有误", NamedTextColor.RED));
        }
        return MenuItems.item(homeMaterial(homeName, entry, location), Component.text(homeName, NamedTextColor.YELLOW), lore);
    }

    private ItemStack emptyHomesItem() {
        return MenuItems.item(Material.LIGHT_GRAY_BED, Component.text("还没有家", NamedTextColor.GRAY), List.of(
                Component.text("站在想保存的位置", NamedTextColor.GRAY),
                Component.text("输入 /sethome <名字>", NamedTextColor.YELLOW)
        ));
    }

    private ItemStack usageBookItem() {
        return MenuItems.item(Material.BOOK, Component.text("使用说明", NamedTextColor.GOLD), List.of(
                Component.text("/sethome <名字>  保存当前位置", NamedTextColor.GRAY),
                Component.text("/home <名字>  直接传送", NamedTextColor.GRAY),
                Component.text("/home icon <材质> <名字>  设置图标", NamedTextColor.GRAY),
                Component.text("/delhome <名字>  删除家", NamedTextColor.GRAY),
                Component.text("家名不能包含 < > :", NamedTextColor.GRAY)
        ));
    }

    private ItemStack confirmDeleteItem(String homeName) {
        return MenuItems.action(Material.RED_CONCRETE, Component.text("确认删除", NamedTextColor.RED), List.of(
                Component.text("将删除家 " + homeName, NamedTextColor.GRAY),
                Component.text("这个操作不能撤销", NamedTextColor.GRAY)
        ), menuActionKey, ACTION_DELETE_PREFIX + homeName);
    }

    private ItemStack backItem() {
        return MenuItems.action(Material.LIME_CONCRETE, Component.text("返回", NamedTextColor.GREEN),
                List.of(Component.text("不删除这个家", NamedTextColor.GRAY)), menuActionKey, ACTION_BACK);
    }

    private ItemStack closeItem(Player player) {
        if (menuNavigation.shouldReturnToMainMenu(player, MenuNavigation.ChildMenu.HOME)) {
            return MenuItems.action(Material.ARROW, Component.text("返回主菜单", NamedTextColor.GREEN),
                    List.of(Component.text("回到主菜单", NamedTextColor.GRAY)), menuActionKey, ACTION_CLOSE);
        }
        return MenuItems.action(Material.BARRIER, Component.text("关闭", NamedTextColor.RED),
                List.of(Component.text("返回游戏", NamedTextColor.GRAY)), menuActionKey, ACTION_CLOSE);
    }

    private Component homeMessage(String before, String homeName, String after, NamedTextColor baseColor) {
        return Component.text()
                .append(Component.text(before, baseColor))
                .append(Component.text(homeName, NamedTextColor.YELLOW))
                .append(Component.text(after, baseColor))
                .build();
    }

    private void setHome(Player player, String name) {
        Location loc = player.getLocation();
        String value = loc.getWorld().getName() + ":"
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
        if (homes == null) return List.of();
        return new ArrayList<>(homes.keySet());
    }

    private void setHomeIcon(Player player, String name, Material icon) {
        HomeEntry entry = getHomeEntry(player, name);
        if (entry == null) {
            player.sendMessage(homeMessage("家 ", name, " 不存在", NamedTextColor.RED));
            return;
        }
        cache.get(player.getUniqueId()).put(name, new HomeEntry(entry.locationRaw(), icon));
        save();
        player.sendMessage(homeMessage("家 ", name, " 的图标已更新", NamedTextColor.GREEN));
    }

    private void setHomeIconFromHand(Player player, String name) {
        Material material = parseIconMaterial(player.getInventory().getItemInMainHand().getType().name());
        if (material == null) {
            player.sendMessage(Component.text("主手物品不能作为家的图标", NamedTextColor.RED));
            return;
        }
        setHomeIcon(player, name, material);
    }

    private void teleportHome(Player player, String name) {
        Location loc = getHome(player, name);
        if (loc == null) {
            player.sendMessage(homeMessage("家 ", name, " 不存在", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.teleportAsync(loc).thenAccept(success -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage(homeMessage("已传送到家 ", name, "", NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("传送失败，请重试", NamedTextColor.RED));
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
        player.sendMessage(Component.text("家名不能包含 < > : 这些符号", NamedTextColor.RED));
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

    private String readableWorldName(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL -> world.getName();
            case NETHER -> world.getName() + " · 下界";
            case THE_END -> world.getName() + " · 末地";
            case CUSTOM -> world.getName();
        };
    }

    private String distanceText(Player player, Location location) {
        if (!player.getWorld().equals(location.getWorld())) {
            return "不同世界";
        }
        return Math.round(player.getLocation().distance(location)) + " 格";
    }

    private record HomeEntry(String locationRaw, Material icon) {
    }

    private record ParsedHomeKey(String name, Material icon) {
    }
}
