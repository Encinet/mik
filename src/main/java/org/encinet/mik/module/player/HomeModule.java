package org.encinet.mik.module.player;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages player homes: /sethome, /home, /delhome
 * <p>
 * Storage format  →  homes.yml
 *   <uuid>:
 *     <homeName>: "world:x,y,z,yaw,pitch"
 *     我的家:     "world:128.5,64.0,-200.3,90.0,0.0"
 * <p>
 * Runtime reads   →  100% in-memory HashMap, 零 YAML 查询
 */
public class HomeModule {

    private final JavaPlugin plugin;
    private File dataFile;
    private YamlConfiguration data;

    /** uuid → (homeName → "world:x,y,z,yaw,pitch") */
    private final Map<UUID, Map<String, String>> cache = new HashMap<>();

    public HomeModule(JavaPlugin plugin) {
        this.plugin = plugin;
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
            Map<String, String> homes = new HashMap<>();
            for (String homeName : section.getKeys(false)) {
                String val = section.getString(homeName);
                if (val != null) homes.put(homeName, val);
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
                                    sendUsage(player, "/home <名字>", "传送到一个家");
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
                                        Location loc = getHome(player, name);
                                        if (loc == null) {
                                            player.sendMessage(homeMessage("家 ", name, " 不存在", NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        player.teleportAsync(loc).thenAccept(success -> {
                                            if (success) {
                                                player.sendMessage(homeMessage("已传送到家 ", name, "", NamedTextColor.GREEN));
                                            } else {
                                                player.sendMessage(Component.text("传送失败，请重试", NamedTextColor.RED));
                                            }
                                        });
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
        cache.computeIfAbsent(player.getUniqueId(), _ -> new HashMap<>()).put(name, value);
        save();
    }

    private Location getHome(Player player, String name) {
        Map<String, String> homes = cache.get(player.getUniqueId());
        if (homes == null) return null;
        String raw = homes.get(name);
        if (raw == null) return null;
        return parseLocation(raw, player.getName(), name);
    }

    private boolean deleteHome(Player player, String name) {
        Map<String, String> homes = cache.get(player.getUniqueId());
        if (homes == null || !homes.containsKey(name)) return false;
        homes.remove(name);
        save();
        return true;
    }

    private List<String> getHomeNames(Player player) {
        Map<String, String> homes = cache.get(player.getUniqueId());
        if (homes == null) return List.of();
        return new ArrayList<>(homes.keySet());
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
     * 用 createSection 而非点路径拼接，家名中的中文/点号/特殊字符均安全。
     */
    private void save() {
        Map<UUID, Map<String, String>> snapshot = new HashMap<>();
        cache.forEach((uuid, homes) -> snapshot.put(uuid, new HashMap<>(homes)));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            YamlConfiguration yml = new YamlConfiguration();
            snapshot.forEach((uuid, homes) -> {
                var section = yml.createSection(uuid.toString());
                homes.forEach(section::set);
            });
            try {
                yml.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save homes.yml: " + e.getMessage());
            }
        });
    }
}
