package org.encinet.mik.module;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages player homes: /sethome, /home, /delhome
 * Data is persisted to homes.yml under the plugin data folder.
 * Storage layout: <uuid>.<homeName>.{world, x, y, z, yaw, pitch}
 */
public class HomeModule {

    private final JavaPlugin plugin;
    private File dataFile;
    private YamlConfiguration data;

    public HomeModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        dataFile = new File(plugin.getDataFolder(), "homes.yml");
        if (!dataFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create homes.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
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
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .executes(ctx -> {
                                        CommandSourceStack source = ctx.getSource();
                                        if (!(source.getSender() instanceof Player player)) {
                                            source.getSender().sendMessage(Component.text("只有玩家可以使用此命令。", NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        String name = StringArgumentType.getString(ctx, "name");
                                        List<String> existing = getHomeNames(player);
                                        int maxHomes = getMaxHomes(player);
                                        if (!existing.contains(name) && existing.size() >= maxHomes) {
                                            player.sendMessage(Component.text("你最多只能设置 " + maxHomes + " 个家。", NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        setHome(player, name);
                                        player.sendMessage(Component.empty()
                                                .append(Component.text("家 ", NamedTextColor.GREEN))
                                                .append(Component.text(name, NamedTextColor.YELLOW))
                                                .append(Component.text(" 已设置。", NamedTextColor.GREEN)));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    "在当前位置设置一个家"
            );

            // /home <name>
            commands.register(
                    Commands.literal("home")
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        if (ctx.getSource().getSender() instanceof Player player) {
                                            getHomeNames(player).forEach(builder::suggest);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        CommandSourceStack source = ctx.getSource();
                                        if (!(source.getSender() instanceof Player player)) {
                                            source.getSender().sendMessage(Component.text("只有玩家可以使用此命令。", NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        String name = StringArgumentType.getString(ctx, "name");
                                        Location loc = getHome(player, name);
                                        if (loc == null) {
                                            player.sendMessage(Component.empty()
                                                    .append(Component.text("家 ", NamedTextColor.RED))
                                                    .append(Component.text(name, NamedTextColor.YELLOW))
                                                    .append(Component.text(" 不存在。", NamedTextColor.RED)));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        player.teleportAsync(loc).thenAccept(success -> {
                                            if (success) {
                                                player.sendMessage(Component.empty()
                                                        .append(Component.text("已传送到家 ", NamedTextColor.GREEN))
                                                        .append(Component.text(name, NamedTextColor.YELLOW))
                                                        .append(Component.text("。", NamedTextColor.GREEN)));
                                            } else {
                                                player.sendMessage(Component.text("传送失败，请重试。", NamedTextColor.RED));
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
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        if (ctx.getSource().getSender() instanceof Player player) {
                                            getHomeNames(player).forEach(builder::suggest);
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> {
                                        CommandSourceStack source = ctx.getSource();
                                        if (!(source.getSender() instanceof Player player)) {
                                            source.getSender().sendMessage(Component.text("只有玩家可以使用此命令。", NamedTextColor.RED));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        String name = StringArgumentType.getString(ctx, "name");
                                        if (!deleteHome(player, name)) {
                                            player.sendMessage(Component.empty()
                                                    .append(Component.text("家 ", NamedTextColor.RED))
                                                    .append(Component.text(name, NamedTextColor.YELLOW))
                                                    .append(Component.text(" 不存在。", NamedTextColor.RED)));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                        player.sendMessage(Component.empty()
                                                .append(Component.text("家 ", NamedTextColor.GREEN))
                                                .append(Component.text(name, NamedTextColor.YELLOW))
                                                .append(Component.text(" 已删除。", NamedTextColor.GREEN)));
                                        return Command.SINGLE_SUCCESS;
                                    }))
                            .build(),
                    "删除一个家"
            );
        });
    }

    // Storage helpers

    /** YAML path prefix for a specific home: "<uuid>.<name>" */
    private String key(Player player, String name) {
        return player.getUniqueId() + "." + name;
    }

    private void setHome(Player player, String name) {
        Location loc = player.getLocation();
        String base = key(player, name);
        data.set(base + ".world", loc.getWorld().getName());
        data.set(base + ".x", loc.getX());
        data.set(base + ".y", loc.getY());
        data.set(base + ".z", loc.getZ());
        data.set(base + ".yaw", (double) loc.getYaw());
        data.set(base + ".pitch", (double) loc.getPitch());
        save();
    }

    private Location getHome(Player player, String name) {
        String base = key(player, name);
        if (!data.contains(base)) return null;
        String worldName = data.getString(base + ".world");
        if (worldName == null) return null;
        var world = plugin.getServer().getWorld(worldName);
        if (world == null) return null;
        return new Location(
                world,
                data.getDouble(base + ".x"),
                data.getDouble(base + ".y"),
                data.getDouble(base + ".z"),
                (float) data.getDouble(base + ".yaw"),
                (float) data.getDouble(base + ".pitch")
        );
    }

    /** @return false if the home did not exist */
    private boolean deleteHome(Player player, String name) {
        String base = key(player, name);
        if (!data.contains(base)) return false;
        data.set(base, null);
        save();
        return true;
    }

    private List<String> getHomeNames(Player player) {
        var section = data.getConfigurationSection(player.getUniqueId().toString());
        if (section == null) return List.of();
        return new ArrayList<>(section.getKeys(false));
    }

    private void save() {
        // Snapshot the current state on the calling (main) thread
        YamlConfiguration snapshot = new YamlConfiguration();
        for (String key : data.getKeys(true)) {
            snapshot.set(key, data.get(key));
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                snapshot.save(dataFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save homes.yml: " + e.getMessage());
            }
        });
    }
}