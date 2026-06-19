package org.encinet.mik.module.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class SimpleFeaturesModule {

    private static final String SPAWN_WORLD = "world";
    private static final double SPAWN_X = 33.5;
    private static final double SPAWN_Y = 63.5;
    private static final double SPAWN_Z = 82.5;
    private static final float SPAWN_YAW = 90.0f;
    private static final float SPAWN_PITCH = 0.0f;

    public SimpleFeaturesModule() {
    }

    /**
     * Register commands
     *
     * @param lifecycleManager the lifecycle event manager
     */
    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // Register /spawn command
            commands.register(Commands.literal("spawn")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            player.teleport(new Location(
                                    Bukkit.getWorld(SPAWN_WORLD),
                                    SPAWN_X, SPAWN_Y, SPAWN_Z,
                                    SPAWN_YAW, SPAWN_PITCH
                            ));
                            return Command.SINGLE_SUCCESS;
                        } else {
                            sender.sendMessage(playerOnlyMessage());
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), "返回主城", List.of("lobby"));

            // Register /tpany command
            commands.register(
                    Commands.literal("tpany")
                            .requires(source -> {
                                var sender = source.getSender();
                                return sender instanceof Player && sender.hasPermission("group.helper");
                            })
                            .executes(ctx -> {
                                sendTpanyUsage(ctx.getSource().getSender());
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(
                                    Commands.argument("player", StringArgumentType.word())
                                            .executes(ctx -> {
                                                Player self = (Player) ctx.getSource().getSender();
                                                String targetName = StringArgumentType.getString(ctx, "player");

                                                Player online = Bukkit.getPlayerExact(targetName);
                                                if (online != null) {
                                                    self.teleportAsync(online.getLocation());
                                                    self.sendMessage(Component.text()
                                                            .append(Component.text("已传送到 ", NamedTextColor.GREEN))
                                                            .append(Component.text(targetName, NamedTextColor.YELLOW))
                                                            .append(Component.text(" 当前位置", NamedTextColor.GREEN))
                                                            .build());
                                                    return 1;
                                                }

                                                OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(targetName);
                                                Location loc = target != null ? target.getLocation() : null;
                                                if (loc == null) {
                                                    self.sendMessage(Component.text()
                                                            .append(Component.text("找不到 ", NamedTextColor.RED))
                                                            .append(Component.text(targetName, NamedTextColor.YELLOW))
                                                            .append(Component.text(" 的位置", NamedTextColor.RED))
                                                            .build());
                                                    return 0;
                                                }

                                                self.teleportAsync(loc);
                                                self.sendMessage(Component.text()
                                                        .append(Component.text("已传送到 ", NamedTextColor.GREEN))
                                                        .append(Component.text(targetName, NamedTextColor.YELLOW))
                                                        .append(Component.text(" 的下线位置 ", NamedTextColor.GREEN))
                                                        .append(Component.text(loc.getWorld().getName() + " ", NamedTextColor.GRAY))
                                                        .append(Component.text(String.format("(%.1f, %.1f, %.1f)",
                                                                loc.getX(), loc.getY(), loc.getZ()), NamedTextColor.GRAY))
                                                        .build());
                                                return 1;
                                            })
                            )
                            .build(),
                    "传送到玩家位置（在线/离线均可）"
            );

            // Register /trash command
            commands.register(Commands.literal("trash")
                    .executes(ctx -> {
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            // 创建一个临时的 27 格箱子界面
                            Component title = Component.text("垃圾桶 ", NamedTextColor.RED, TextDecoration.BOLD)
                                    .append(Component.text("(关闭后物品将被清空)", NamedTextColor.GRAY));
                            Inventory trash = Bukkit.createInventory(null, 27, title);
                            player.openInventory(trash);
                            return Command.SINGLE_SUCCESS;
                        }
                        ctx.getSource().getSender().sendMessage(
                                playerOnlyMessage()
                        );
                        return 0;
                    }).build(), "打开垃圾桶", List.of("trashcan", "garbage"));

            // Register /removeitems command
            commands.register(Commands.literal("removeitems")
                    .requires(source -> source.getSender().hasPermission("mik.command.removeitems"))
                    // 带半径参数的分支
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 300))
                            .executes(ctx -> {
                                int radius = IntegerArgumentType.getInteger(ctx, "radius");
                                return removeItems(ctx.getSource(), radius);
                            }))
                    // 无参数分支，使用默认半径50
                    .executes(ctx -> removeItems(ctx.getSource(), 50)).build(), "清除掉落物", List.of("rmitems"));
        });
    }

    private int removeItems(CommandSourceStack source, int radius) {
        CommandSender sender = source.getSender();

        // 获取执行位置（支持 execute at/positioned）
        Location location;
        World world;

        if (source.getExecutor() != null) {
            // 有实体执行者（玩家或其他实体）
            location = source.getExecutor().getLocation();
        } else {
            source.getLocation();// 通过 execute positioned 等指定了位置
            location = source.getLocation();
        }
        world = location.getWorld();

        if (world == null) {
            sender.sendMessage(Component.text("无法确定世界", NamedTextColor.RED));
            return 0;
        }

        int count = 0;
        // 获取指定位置周围指定半径内的所有实体
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Item) {
                entity.remove();
                count++;
            }
        }

        // 格式化位置信息
        String posInfo = String.format("%.1f, %.1f, %.1f",
                location.getX(), location.getY(), location.getZ());

        sender.sendMessage(Component.text()
                .append(Component.text("已在 ", NamedTextColor.GREEN))
                .append(Component.text(world.getName(), NamedTextColor.YELLOW))
                .append(Component.text(" 世界 ", NamedTextColor.GREEN))
                .append(Component.text("[" + posInfo + "]", NamedTextColor.YELLOW))
                .append(Component.text(" 清除 ", NamedTextColor.GREEN))
                .append(Component.text(Integer.toString(count), NamedTextColor.YELLOW))
                .append(Component.text(" 个掉落物品 ", NamedTextColor.GREEN))
                .append(Component.text("半径 " + radius + " 格", NamedTextColor.GRAY))
                .build());

        return Command.SINGLE_SUCCESS;
    }

    private void sendTpanyUsage(CommandSender sender) {
        sender.sendMessage(Component.text()
                .append(Component.text("用法 ", NamedTextColor.YELLOW))
                .append(Component.text("/tpany <玩家名>", NamedTextColor.AQUA))
                .append(Component.text("  传送到玩家位置，支持离线缓存", NamedTextColor.GRAY))
                .build());
    }

    private Component playerOnlyMessage() {
        return Component.text("该命令只能由玩家执行", NamedTextColor.RED);
    }

}
