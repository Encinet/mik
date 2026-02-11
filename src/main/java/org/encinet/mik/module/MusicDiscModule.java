package org.encinet.mik.module;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Module for music disc management
 */
public class MusicDiscModule implements Listener {

    private static final String MUSIC_FOLDER = "plugins/pv-addon-discs/local";
    private static final int ITEMS_PER_PAGE = 45; // 5 rows for items
    private static final int GUI_SIZE = 54; // 6 rows total
    private static final Random RANDOM = new Random();
    private static final Material[] DISC_TYPES = {
            Material.MUSIC_DISC_5,
            Material.MUSIC_DISC_11,
            Material.MUSIC_DISC_13,
            Material.MUSIC_DISC_BLOCKS,
            Material.MUSIC_DISC_CAT,
            Material.MUSIC_DISC_CHIRP,
            Material.MUSIC_DISC_CREATOR,
            Material.MUSIC_DISC_CREATOR_MUSIC_BOX,
            Material.MUSIC_DISC_FAR,
            Material.MUSIC_DISC_LAVA_CHICKEN,
            Material.MUSIC_DISC_MALL,
            Material.MUSIC_DISC_MELLOHI,
            Material.MUSIC_DISC_OTHERSIDE,
            Material.MUSIC_DISC_PIGSTEP,
            Material.MUSIC_DISC_PRECIPICE,
            Material.MUSIC_DISC_RELIC,
            Material.MUSIC_DISC_STAL,
            Material.MUSIC_DISC_STRAD,
            Material.MUSIC_DISC_TEARS,
            Material.MUSIC_DISC_WAIT,
            Material.MUSIC_DISC_WARD,
    };

    private final JavaPlugin plugin;
    private final List<MusicFile> musicFiles = new CopyOnWriteArrayList<>();
    private final Map<UUID, Integer> playerPages = new HashMap<>();
    private final Map<UUID, String> playerSearchKeywords = new HashMap<>();

    public MusicDiscModule(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Register commands
     */
    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();

            // Register /music command
            commands.register(Commands.literal("music")
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        Entity executor = ctx.getSource().getExecutor();
                        if (executor instanceof Player player) {
                            openMusicInventory(player);
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
                                loadMusicFiles();
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
                                            openMusicInventory(player, keyword);
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
                                            String keyword = playerSearchKeywords.get(player.getUniqueId());

                                            // Calculate total pages
                                            List<MusicFile> filteredFiles = musicFiles;
                                            if (keyword != null && !keyword.isEmpty()) {
                                                String lowerKeyword = keyword.toLowerCase();
                                                filteredFiles = musicFiles.stream()
                                                        .filter(music -> music.displayName().toLowerCase().contains(lowerKeyword)
                                                                || music.fileName().toLowerCase().contains(lowerKeyword))
                                                        .toList();
                                            }

                                            int totalPages = (int) Math.ceil((double) filteredFiles.size() / ITEMS_PER_PAGE);
                                            if (totalPages == 0) totalPages = 1;

                                            // Validate page number
                                            if (pageNumber > totalPages) {
                                                sender.sendMessage(Component.text("页码超出范围，最大页码为 " + totalPages)
                                                        .color(NamedTextColor.RED));
                                                return Command.SINGLE_SUCCESS;
                                            }

                                            openMusicInventory(player, keyword, pageNumber - 1);
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
                                    giveRandomDisc(player);
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
                                    playRandomDisc(player);
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
        List<MusicFile> newMusicFiles = new ArrayList<>();

        Path musicPath = Paths.get(MUSIC_FOLDER);
        if (!Files.exists(musicPath)) {
            plugin.getLogger().warning("Music folder not found: " + MUSIC_FOLDER);
            return;
        }

        try (Stream<Path> paths = Files.list(musicPath)) {
            paths.filter(path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".mp3")
                        || name.endsWith(".m4a")
                        || name.endsWith(".wav")
                        || name.endsWith(".flac")
                        || name.endsWith(".ogg")
                        || name.endsWith(".aac")
                        || name.endsWith(".opus")
                        || name.endsWith(".wma");
            }).forEach(path -> {
                String fileName = path.getFileName().toString();
                String displayName = formatDisplayName(fileName);
                String fileSize = getFileSize(fileName);
                String sampleRate = getSampleRate(fileName);
                newMusicFiles.add(new MusicFile(fileName, displayName, fileSize, sampleRate));
            });

            newMusicFiles.sort(Comparator.comparing(MusicFile::displayName));

            // CopyOnWriteArrayList ensures thread-safe replacement
            musicFiles.clear();
            musicFiles.addAll(newMusicFiles);

            plugin.getLogger().info("Loaded " + musicFiles.size() + " music files");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load music files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Format file name to display name
     */
    private String formatDisplayName(String fileName) {
        // Remove extension
        String name = fileName.replaceAll("\\.(mp3|m4a|wav|flac|ogg|aac|opus|wma)$", "");

        // Remove quotes
        name = name.replace("\"", "").replace("'", "");

        // Replace underscores with spaces
        name = name.replace("_", " ");

        // Add spaces around hyphens
        name = name.replace("-", " - ");

        return name;
    }

    /**
     * Get formatted file size
     */
    private String getFileSize(String fileName) {
        try {
            Path filePath = Paths.get(MUSIC_FOLDER, fileName);
            long bytes = Files.size(filePath);

            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get audio sample rate
     */
    private String getSampleRate(String fileName) {
        try {
            Path filePath = Paths.get(MUSIC_FOLDER, fileName);
            File audioFile = filePath.toFile();

            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(audioFile);
            AudioFormat format = fileFormat.getFormat();

            float sampleRate = format.getSampleRate();
            if (sampleRate == AudioSystem.NOT_SPECIFIED) {
                return null;
            }

            // Convert to kHz
            if (sampleRate >= 1000) {
                return String.format("%.1f kHz", sampleRate / 1000.0);
            } else {
                return String.format("%.0f Hz", sampleRate);
            }
        } catch (Exception e) {
            // AudioSystem doesn't support all formats (e.g., MP3 without additional libraries)
            return null;
        }
    }

    /**
     * Open music inventory for player
     */
    public void openMusicInventory(Player player) {
        openMusicInventory(player, null, 0);
    }

    /**
     * Open music inventory for player with search keyword
     */
    public void openMusicInventory(Player player, String keyword) {
        // Check if search will return results
        if (keyword != null && !keyword.isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            long matchCount = musicFiles.stream()
                    .filter(music -> music.displayName().toLowerCase().contains(lowerKeyword)
                            || music.fileName().toLowerCase().contains(lowerKeyword))
                    .count();

            if (matchCount == 0) {
                player.sendMessage(Component.text()
                        .append(Component.text("未找到包含 \"")
                                .color(NamedTextColor.RED))
                        .append(Component.text(keyword)
                                .color(NamedTextColor.YELLOW))
                        .append(Component.text("\" 的歌曲")
                                .color(NamedTextColor.RED))
                        .build());
                return;
            }
        }

        openMusicInventory(player, keyword, 0);
    }

    /**
     * Open music inventory for player at specific page
     */
    public void openMusicInventory(Player player, int page) {
        openMusicInventory(player, null, page);
    }

    /**
     * Open music inventory for player with search keyword at specific page
     */
    public void openMusicInventory(Player player, String keyword, int page) {
        UUID uuid = player.getUniqueId();
        playerPages.put(uuid, page);

        if (keyword != null && !keyword.isEmpty()) {
            playerSearchKeywords.put(uuid, keyword);
        } else {
            playerSearchKeywords.remove(uuid);
        }

        Inventory inv = createMusicInventory(player, page);
        player.openInventory(inv);
    }

    /**
     * Give a random music disc to player
     */
    public void giveRandomDisc(Player player) {
        if (musicFiles.isEmpty()) {
            player.sendMessage(Component.text("没有可用的音乐文件")
                    .color(NamedTextColor.RED));
            return;
        }

        // Select random music file
        MusicFile randomMusic = musicFiles.get(RANDOM.nextInt(musicFiles.size()));

        // Create clean disc and add to inventory
        ItemStack disc = createMusicDisc(randomMusic, false);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(disc);

        // Check if item was added successfully
        if (leftover.isEmpty()) {
            // Send success message to player
            player.sendMessage(Component.text()
                    .append(Component.text("获得随机音乐唱片: ")
                            .color(NamedTextColor.GREEN))
                    .append(Component.text(randomMusic.displayName())
                            .color(NamedTextColor.YELLOW))
                    .build());
        } else {
            // Inventory is full
            player.sendMessage(Component.text("背包已满，无法获得音乐唱片")
                    .color(NamedTextColor.RED));
        }
    }

    /**
     * Play a random music disc
     */
    public void playRandomDisc(Player player) {
        if (musicFiles.isEmpty()) {
            player.sendMessage(Component.text("没有可用的音乐文件")
                    .color(NamedTextColor.RED));
            return;
        }

        // Select random music file
        MusicFile randomMusic = musicFiles.get(RANDOM.nextInt(musicFiles.size()));

        // Create disc
        ItemStack disc = createMusicDisc(randomMusic);

        // Play the disc
        playDisc(player, disc, randomMusic.displayName());
    }

    /**
     * Play a music disc on the nearest jukebox
     */
    private void playDisc(Player player, ItemStack disc, String musicName) {
        // Find nearest jukebox within 50 blocks
        Block nearestJukebox = findNearestJukebox(player, 50);

        if (nearestJukebox == null) {
            player.sendMessage(Component.text("附近50格内没有找到唱片机")
                    .color(NamedTextColor.RED));
            return;
        }

        // Get the music file from the disc to create a clean version
        ItemMeta meta = disc.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey identifierKey = new NamespacedKey("pv-addon-discs", "identifier");
            String identifier = pdc.get(identifierKey, PersistentDataType.STRING);

            if (identifier != null && identifier.startsWith("local://")) {
                String fileName = identifier.substring(8); // Remove "local://"
                MusicFile musicFile = musicFiles.stream()
                        .filter(m -> m.fileName().equals(fileName))
                        .findFirst()
                        .orElse(null);

                if (musicFile != null) {
                    // Create clean disc without operation hints
                    disc = createMusicDisc(musicFile, false);
                }
            }
        }

        // Clear existing record first
        if (nearestJukebox.getState() instanceof Jukebox jukebox) {
            jukebox.setRecord(null);
            jukebox.update();
        }

        // Simulate player interaction with jukebox
        PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                disc,
                nearestJukebox,
                org.bukkit.block.BlockFace.UP
        );

        // Call the event
        Bukkit.getPluginManager().callEvent(interactEvent);

        // Ensure the disc is in the jukebox
        if (nearestJukebox.getState() instanceof Jukebox jukebox) {
            if (jukebox.getRecord().getType() == Material.AIR) {
                // If event didn't place the disc, do it manually
                jukebox.setRecord(disc);
                jukebox.update();
            }
        }

        // Send success message
        org.bukkit.Location jukeboxLoc = nearestJukebox.getLocation();
        player.sendMessage(Component.text()
                .append(Component.text("正在播放: ")
                        .color(NamedTextColor.GREEN))
                .append(Component.text(musicName)
                        .color(NamedTextColor.YELLOW))
                .append(Component.text(" 于唱片机 ")
                        .color(NamedTextColor.GREEN))
                .append(Component.text(String.format("(%d, %d, %d)",
                        jukeboxLoc.getBlockX(),
                        jukeboxLoc.getBlockY(),
                        jukeboxLoc.getBlockZ()))
                        .color(NamedTextColor.AQUA))
                .build());
    }

    /**
     * Find the nearest jukebox within specified radius
     */
    private Block findNearestJukebox(Player player, int radius) {
        org.bukkit.Location playerLoc = player.getLocation();
        Block nearestJukebox = null;
        double nearestDistance = Double.MAX_VALUE;

        // Search in a cube around the player
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = playerLoc.getWorld().getBlockAt(
                            playerLoc.getBlockX() + x,
                            playerLoc.getBlockY() + y,
                            playerLoc.getBlockZ() + z
                    );

                    if (block.getType() == Material.JUKEBOX) {
                        double distance = block.getLocation().distance(playerLoc);
                        if (distance < nearestDistance) {
                            nearestDistance = distance;
                            nearestJukebox = block;
                        }
                    }
                }
            }
        }

        return nearestJukebox;
    }

    /**
     * Create music disc inventory GUI
     */
    private Inventory createMusicInventory(Player player, int page) {
        UUID uuid = player.getUniqueId();
        String keyword = playerSearchKeywords.get(uuid);

        // Filter music files based on search keyword
        List<MusicFile> filteredFiles = musicFiles;
        if (keyword != null && !keyword.isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            filteredFiles = musicFiles.stream()
                    .filter(music -> music.displayName().toLowerCase().contains(lowerKeyword)
                            || music.fileName().toLowerCase().contains(lowerKeyword))
                    .toList();
        }

        int totalPages = (int) Math.ceil((double) filteredFiles.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = "Music Discs - Page " + (page + 1) + "/" + totalPages;
        if (keyword != null && !keyword.isEmpty()) {
            title = "搜索: " + keyword + " - " + (page + 1) + "/" + totalPages;
        }

        Inventory inv = Bukkit.createInventory(null, GUI_SIZE,
                Component.text(title).color(NamedTextColor.DARK_PURPLE));

        // Add music discs
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, filteredFiles.size());

        for (int i = startIndex; i < endIndex; i++) {
            MusicFile music = filteredFiles.get(i);
            ItemStack disc = createMusicDisc(music);
            inv.setItem(i - startIndex, disc);
        }

        // Add navigation buttons in bottom row
        if (page > 0) {
            inv.setItem(45, createNavigationButton(Material.ARROW, "上一页", "Previous Page"));
        }

        if (page < totalPages - 1) {
            inv.setItem(53, createNavigationButton(Material.ARROW, "下一页", "Next Page"));
        }

        // Add search button
        inv.setItem(47, createSearchButton());

        // Add random disc button
        inv.setItem(46, createRandomDiscButton());

        // Add help button
        inv.setItem(51, createHelpButton(player));

        // Add clear search button if searching
        if (keyword != null && !keyword.isEmpty()) {
            inv.setItem(48, createClearSearchButton());
        }

        // Add page info in center
        inv.setItem(49, createPageInfo(page + 1, totalPages, filteredFiles.size()));

        return inv;
    }

    /**
     * Create navigation button
     */
    private ItemStack createNavigationButton(Material material, String name, String lore) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text(name)
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text(lore)
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Create page info item
     */
    private ItemStack createPageInfo(int currentPage, int totalPages, int totalResults) {
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("第 " + currentPage + " 页 / 共 " + totalPages + " 页")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("Page " + currentPage + " of " + totalPages)
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("共 " + totalResults + " 首歌曲")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            info.setItemMeta(meta);
        }

        return info;
    }

    /**
     * Create search button
     */
    private ItemStack createSearchButton() {
        ItemStack button = new ItemStack(Material.COMPASS);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("搜索歌曲")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("点击后输入")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("/music search <关键词>")
                            .color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("来搜索歌曲")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Create clear search button
     */
    private ItemStack createClearSearchButton() {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("清除搜索")
                    .color(NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(List.of(
                    Component.text("点击返回完整列表")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Create random disc button
     */
    private ItemStack createRandomDiscButton() {
        ItemStack button = new ItemStack(Material.MUSIC_DISC_13);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("随机唱片")
                    .color(NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            meta.lore(List.of(
                    Component.text("左键: 获得一张随机音乐唱片")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("右键: 在最近的唱片机播放")
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("")
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("命令:")
                            .color(NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("/music random")
                            .color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false),
                    Component.text("/music randomplay")
                            .color(NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            ));

            button.setItemMeta(meta);

            // Use TooltipDisplay to hide additional components
            TooltipDisplay tooltipDisplay = TooltipDisplay.tooltipDisplay()
                    .addHiddenComponents(
                            DataComponentTypes.JUKEBOX_PLAYABLE,
                            DataComponentTypes.ENCHANTMENTS,
                            DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            DataComponentTypes.UNBREAKABLE,
                            DataComponentTypes.CAN_BREAK,
                            DataComponentTypes.CAN_PLACE_ON,
                            DataComponentTypes.STORED_ENCHANTMENTS,
                            DataComponentTypes.DYED_COLOR,
                            DataComponentTypes.TRIM
                    )
                    .build();

            button.setData(DataComponentTypes.TOOLTIP_DISPLAY, tooltipDisplay);
        }

        return button;
    }

    /**
     * Create help button
     */
    private ItemStack createHelpButton(Player player) {
        ItemStack button = new ItemStack(Material.BOOK);
        ItemMeta meta = button.getItemMeta();

        if (meta != null) {
            meta.displayName(Component.text("命令帮助")
                    .color(NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("可用命令:")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  打开音乐界面")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music search <关键词>")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  搜索歌曲")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music page <页码>")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  跳转到指定页面")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music random")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  获得随机音乐唱片")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("")
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("/music randomplay")
                    .color(NamedTextColor.AQUA)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  在最近的唱片机播放随机音乐")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));

            // Only show reload command if player has permission
            if (player.hasPermission("group.manager")) {
                lore.add(Component.text("")
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("/music reload")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  重新加载歌曲列表 (管理员)")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            button.setItemMeta(meta);
        }

        return button;
    }

    /**
     * Create a music disc item for GUI display (with detailed info)
     */
    private ItemStack createMusicDisc(MusicFile music) {
        return createMusicDisc(music, true);
    }

    /**
     * Create a music disc item
     * @param music The music file
     * @param detailed Whether to include detailed information in lore
     */
    private ItemStack createMusicDisc(MusicFile music, boolean detailed) {
        // Use hash to determine disc type
        int hash = music.fileName().hashCode();
        Material discType = DISC_TYPES[Math.abs(hash) % DISC_TYPES.length];

        ItemStack disc = new ItemStack(discType);
        ItemMeta meta = disc.getItemMeta();

        if (meta != null) {
            // Get file extension
            String extension = music.fileName().substring(music.fileName().lastIndexOf('.') + 1).toUpperCase();

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(music.displayName())
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(""));
            lore.add(Component.text("格式: " + extension)
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            if (music.fileSize() != null) {
                lore.add(Component.text("大小: " + music.fileSize())
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            if (music.sampleRate() != null) {
                lore.add(Component.text("采样率: " + music.sampleRate())
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }

            if (detailed) {
                // Add operation hints for GUI display
                lore.add(Component.text(""));
                lore.add(Component.text("左键: 拿取唱片")
                        .color(NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("右键: 在最近的唱片机播放")
                        .color(NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false));
            }

            lore.add(Component.text(""));
            lore.add(Component.text("♪ Plasmo Voice 音乐唱片 ♪")
                    .color(NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.ITALIC, true));

            meta.lore(lore);

            // Set custom model data using new API
            float modelValue = (float) (Math.abs(hash) % 1000);
            CustomModelDataComponent customModelData = meta.getCustomModelDataComponent();
            customModelData.setFloats(List.of(modelValue));
            meta.setCustomModelDataComponent(customModelData);

            // Add pv-addon-discs custom data
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            NamespacedKey identifierKey = new NamespacedKey("pv-addon-discs", "identifier");
            NamespacedKey forbidGrindstoneKey = new NamespacedKey("pv-addon-discs", "forbid_grindstone");

            pdc.set(identifierKey, PersistentDataType.STRING, "local://" + music.fileName());
            pdc.set(forbidGrindstoneKey, PersistentDataType.BYTE, (byte) 1);

            // Add mending enchantment
            meta.addEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true);

            // Hide enchantments and other tooltips
            meta.addItemFlags(
                    ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_DESTROYS,
                    ItemFlag.HIDE_PLACED_ON
            );

            disc.setItemMeta(meta);
        }

        // Use TooltipDisplay to hide additional components
        TooltipDisplay tooltipDisplay = TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(
                        DataComponentTypes.JUKEBOX_PLAYABLE,
                        DataComponentTypes.ENCHANTMENTS,
                        DataComponentTypes.ATTRIBUTE_MODIFIERS,
                        DataComponentTypes.UNBREAKABLE,
                        DataComponentTypes.CAN_BREAK,
                        DataComponentTypes.CAN_PLACE_ON,
                        DataComponentTypes.STORED_ENCHANTMENTS,
                        DataComponentTypes.DYED_COLOR,
                        DataComponentTypes.TRIM
                )
                .build();

        disc.setData(DataComponentTypes.TOOLTIP_DISPLAY, tooltipDisplay);

        return disc;
    }

    /**
     * Handle inventory click events
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().title().toString();
        if (!title.contains("Music Discs") && !title.contains("搜索:")) return;

        int slot = event.getRawSlot();
        boolean isRightClick = event.isRightClick();

        // Handle music disc clicks (slots 0-44)
        if (slot >= 0 && slot < 45) {
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType().toString().startsWith("MUSIC_DISC_")) {
                // Get the music file from the clicked item
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta == null) return;

                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                NamespacedKey identifierKey = new NamespacedKey("pv-addon-discs", "identifier");
                String identifier = pdc.get(identifierKey, PersistentDataType.STRING);

                if (identifier == null || !identifier.startsWith("local://")) return;

                String fileName = identifier.substring(8); // Remove "local://"
                MusicFile musicFile = musicFiles.stream()
                        .filter(m -> m.fileName().equals(fileName))
                        .findFirst()
                        .orElse(null);

                if (musicFile == null) return;

                if (isRightClick) {
                    // Right click: play the disc
                    event.setCancelled(true);
                    player.closeInventory();
                    playDisc(player, clickedItem, musicFile.displayName());
                } else {
                    // Left click: replace with clean version for pickup
                    ItemStack cleanDisc = createMusicDisc(musicFile, false);
                    event.setCurrentItem(cleanDisc);
                    // Don't cancel - allow normal pickup behavior
                }
            }
            return;
        }

        // Navigation buttons
        if (slot == 45) { // Previous page
            event.setCancelled(true);
            Integer currentPage = playerPages.get(player.getUniqueId());
            if (currentPage != null && currentPage > 0) {
                String keyword = playerSearchKeywords.get(player.getUniqueId());
                openMusicInventory(player, keyword, currentPage - 1);
            }
        } else if (slot == 53) { // Next page
            event.setCancelled(true);
            Integer currentPage = playerPages.get(player.getUniqueId());
            if (currentPage != null) {
                String keyword = playerSearchKeywords.get(player.getUniqueId());
                openMusicInventory(player, keyword, currentPage + 1);
            }
        } else if (slot == 47) { // Search button
            event.setCancelled(true);
            player.closeInventory();
            // Send clickable message with command suggestion
            player.sendMessage(Component.text()
                    .append(Component.text("点击这里搜索: ")
                            .color(NamedTextColor.YELLOW))
                    .append(Component.text("[/music search]")
                            .color(NamedTextColor.GREEN)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/music search "))
                            .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                    Component.text("点击填充命令").color(NamedTextColor.GRAY)
                            )))
                    .build());
        } else if (slot == 46) { // Random disc button
            event.setCancelled(true);
            if (isRightClick) {
                // Right click: play random disc
                player.closeInventory();
                playRandomDisc(player);
            } else {
                // Left click: give random disc
                player.closeInventory();
                giveRandomDisc(player);
            }
        } else if (slot == 51) { // Help button
            event.setCancelled(true);
        } else if (slot == 48) { // Clear search button
            event.setCancelled(true);
            playerSearchKeywords.remove(player.getUniqueId());
            openMusicInventory(player, null, 0);
            player.sendMessage(Component.text("已清除搜索")
                    .color(NamedTextColor.GREEN));
        } else if (slot == 49) { // Page info
            event.setCancelled(true);
        } else if (slot >= 45 && slot < 54) { // Bottom row
            event.setCancelled(true);
        }
        // Allow taking music discs (slots 0-44)
    }

    /**
     * Clean up player data when they quit
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerPages.remove(uuid);
        playerSearchKeywords.remove(uuid);
    }

    /**
     * Music file record
     */
    public record MusicFile(String fileName, String displayName, String fileSize, String sampleRate) {
    }
}
