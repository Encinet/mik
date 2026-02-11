package org.encinet.mik.module;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.musicdisc.*;

import java.util.List;
import java.util.Set;

/**
 * Module for music disc management
 */
public class MusicDiscModule implements Listener {

    private final JavaPlugin plugin;
    private final MusicFileLoader musicFileLoader;
    private final MusicDiscCreator discCreator;
    private final MusicDiscPlayer discPlayer;
    private final MusicInventoryGUI inventoryGUI;
    private final JukeboxPlaylistManager playlistManager;
    private final JukeboxControlGUI jukeboxControlGUI;
    private final JukeboxAutoPlayManager autoPlayManager;
    private final MusicDiscEventHandler eventHandler;

    public MusicDiscModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.musicFileLoader = new MusicFileLoader(plugin);
        this.discCreator = new MusicDiscCreator();
        this.discPlayer = new MusicDiscPlayer(musicFileLoader, discCreator);
        this.inventoryGUI = new MusicInventoryGUI(musicFileLoader, discCreator);
        this.playlistManager = new JukeboxPlaylistManager(musicFileLoader);
        this.jukeboxControlGUI = new JukeboxControlGUI(playlistManager, discCreator);
        this.autoPlayManager = new JukeboxAutoPlayManager(plugin, playlistManager, discCreator, discPlayer);
        this.eventHandler = new MusicDiscEventHandler(musicFileLoader, discCreator, discPlayer,
                inventoryGUI, playlistManager, jukeboxControlGUI, autoPlayManager);

        Bukkit.getPluginManager().registerEvents(eventHandler, plugin);
    }

    /**
     * Enable music chest locations
     */
    public void enableMusicChests() {
        World mainWorld = Bukkit.getWorld("world");
        if (mainWorld == null) {
            plugin.getLogger().severe("World 'world' not found!");
            return;
        }

        Location musicChest1 = new Location(mainWorld, 49, 55, 120).getBlock().getLocation();
        Location musicChest2 = new Location(mainWorld, 49, 55, 121).getBlock().getLocation();

        Set<Location> musicChestLocations = Set.of(musicChest1, musicChest2);
        eventHandler.setMusicChestLocations(musicChestLocations);

        plugin.getLogger().info("MusicChestModule enabled with " + musicChestLocations.size() + " chests");
    }

    /**
     * Register commands
     */
    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            commands.register(Commands.literal("music")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            inventoryGUI.openMusicInventory(player);
                            return Command.SINGLE_SUCCESS;
                        } else {
                            sender.sendMessage("你不是玩家");
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.literal("reload")
                            .requires(source -> source.getSender().hasPermission("group.manager"))
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                musicFileLoader.loadMusicFiles();
                                discPlayer.clearCache();
                                sender.sendMessage(Component.text("已重新加载歌曲列表"));
                                return Command.SINGLE_SUCCESS;
                            })
                    )
                    .then(Commands.literal("search")
                            .then(Commands.argument("keyword", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        CommandSender sender = ctx.getSource().getSender();
                                        Entity executor = ctx.getSource().getExecutor();
                                        if (executor instanceof Player player) {
                                            String keyword = ctx.getArgument("keyword", String.class);
                                            inventoryGUI.openMusicInventory(player, keyword);
                                            return Command.SINGLE_SUCCESS;
                                        } else {
                                            sender.sendMessage("你不是玩家");
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                    )
                    .then(Commands.literal("page")
                            .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                    .executes(ctx -> {
                                        CommandSender sender = ctx.getSource().getSender();
                                        Entity executor = ctx.getSource().getExecutor();
                                        if (executor instanceof Player player) {
                                            int pageNumber = ctx.getArgument("number", Integer.class);
                                            String keyword = inventoryGUI.getPlayerSearchKeyword(player.getUniqueId());

                                            List<MusicFileLoader.MusicFile> filteredFiles =
                                                    inventoryGUI.getFilteredFiles(keyword);

                                            int totalPages = (int) Math.ceil((double) filteredFiles.size() / 45);
                                            if (totalPages == 0) totalPages = 1;

                                            if (pageNumber > totalPages) {
                                                sender.sendMessage(Component.text("页码超出范围，最大页码为 " + totalPages)
                                                        .color(NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            inventoryGUI.openMusicInventory(player, keyword, pageNumber - 1);
                                            return Command.SINGLE_SUCCESS;
                                        } else {
                                            sender.sendMessage("你不是玩家");
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    })
                            )
                    )
                    .then(Commands.literal("random")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                Entity executor = ctx.getSource().getExecutor();
                                if (executor instanceof Player player) {
                                    eventHandler.giveRandomDisc(player);
                                    return Command.SINGLE_SUCCESS;
                                } else {
                                    sender.sendMessage("你不是玩家");
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                    )
                    .then(Commands.literal("randomplay")
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                Entity executor = ctx.getSource().getExecutor();
                                if (executor instanceof Player player) {
                                    eventHandler.playRandomDisc(player);
                                    return Command.SINGLE_SUCCESS;
                                } else {
                                    sender.sendMessage("你不是玩家");
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                    )
                    .build(), "Plasmo Voice唱片");
        });
    }

    /**
     * Load music files from directory
     */
    public void loadMusicFiles() {
        musicFileLoader.loadMusicFiles();
    }
}
