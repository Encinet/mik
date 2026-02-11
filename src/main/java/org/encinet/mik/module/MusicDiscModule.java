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
import org.bukkit.*;
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
                String duration = getDuration(fileName);
                newMusicFiles.add(new MusicFile(fileName, displayName, fileSize, sampleRate, duration));
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
     * Get audio duration
     */
    private String getDuration(String fileName) {
        try {
            Path filePath = Paths.get(MUSIC_FOLDER, fileName);
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

            int durationSeconds = switch (extension) {
                case "mp3" -> getMp3Duration(filePath);
                case "flac" -> getFlacDuration(filePath);
                case "ogg", "opus" -> getOggDuration(filePath);
                case "m4a", "aac" -> getM4aDuration(filePath);
                default -> getWavDuration(filePath);
            };

            if (durationSeconds > 0) {
                int minutes = durationSeconds / 60;
                int seconds = durationSeconds % 60;
                return String.format("%d:%02d", minutes, seconds);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private int getWavDuration(Path filePath) {
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(filePath.toFile());
            if (fileFormat.getFrameLength() != AudioSystem.NOT_SPECIFIED) {
                AudioFormat format = fileFormat.getFormat();
                float frameRate = format.getFrameRate();
                if (frameRate > 0) {
                    return (int) (fileFormat.getFrameLength() / frameRate);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getMp3Duration(Path filePath) {
        try {
            long fileSize = Files.size(filePath);
            int sampleRate = getMp3SampleRate(filePath);
            if (sampleRate <= 0) return -1;

            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
                byte[] header = new byte[4];

                // Skip ID3v2 tag
                raf.read(header, 0, 3);
                long dataStart = 0;
                if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                    raf.seek(6);
                    raf.read(header, 0, 4);
                    int tagSize = ((header[0] & 0x7F) << 21) | ((header[1] & 0x7F) << 14) |
                                 ((header[2] & 0x7F) << 7) | (header[3] & 0x7F);
                    dataStart = tagSize + 10;
                }

                // Estimate duration based on bitrate
                raf.seek(dataStart);
                raf.read(header);
                if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) {
                    int bitrateIndex = (header[2] >> 4) & 0x0F;
                    int version = (header[1] >> 3) & 0x03;

                    int[][] bitrates = {
                        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0}, // MPEG 2.5 Layer 3
                        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0},    // MPEG 2 Layer 3
                        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0} // MPEG 1 Layer 3
                    };

                    int bitrate = bitrates[version][bitrateIndex];
                    if (bitrate > 0) {
                        long audioSize = fileSize - dataStart;
                        return (int) (audioSize * 8 / (bitrate * 1000));
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getFlacDuration(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] marker = new byte[4];
            raf.read(marker);

            if (marker[0] == 'f' && marker[1] == 'L' && marker[2] == 'a' && marker[3] == 'C') {
                raf.skipBytes(4);
                byte[] streamInfo = new byte[18];
                raf.read(streamInfo);

                // Sample rate (20 bits)
                int sampleRate = ((streamInfo[10] & 0xFF) << 12) |
                                ((streamInfo[11] & 0xFF) << 4) |
                                ((streamInfo[12] & 0xF0) >> 4);

                // Total samples (36 bits)
                long totalSamples = ((long)(streamInfo[13] & 0x0F) << 32) |
                                   ((long)(streamInfo[14] & 0xFF) << 24) |
                                   ((long)(streamInfo[15] & 0xFF) << 16) |
                                   ((long)(streamInfo[16] & 0xFF) << 8) |
                                   (streamInfo[17] & 0xFF);

                if (sampleRate > 0 && totalSamples > 0) {
                    return (int) (totalSamples / sampleRate);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getOggDuration(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            // Seek to last OGG page to get granule position
            long fileSize = raf.length();
            raf.seek(Math.max(0, fileSize - 65536));

            byte[] buffer = new byte[65536];
            int bytesRead = raf.read(buffer);

            // Find last OGG page
            for (int i = bytesRead - 4; i >= 0; i--) {
                if (buffer[i] == 'O' && buffer[i+1] == 'g' && buffer[i+2] == 'g' && buffer[i+3] == 'S') {
                    long granulePos = ((long)(buffer[i+6] & 0xFF)) |
                                     ((long)(buffer[i+7] & 0xFF) << 8) |
                                     ((long)(buffer[i+8] & 0xFF) << 16) |
                                     ((long)(buffer[i+9] & 0xFF) << 24) |
                                     ((long)(buffer[i+10] & 0xFF) << 32) |
                                     ((long)(buffer[i+11] & 0xFF) << 40) |
                                     ((long)(buffer[i+12] & 0xFF) << 48) |
                                     ((long)(buffer[i+13] & 0xFF) << 56);

                    int sampleRate = getOggSampleRate(filePath);
                    if (sampleRate > 0 && granulePos > 0) {
                        return (int) (granulePos / sampleRate);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getM4aDuration(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] buffer = new byte[8];
            int sampleRate = -1;
            long duration = -1;

            while (raf.getFilePointer() < raf.length() - 8) {
                raf.read(buffer);
                int boxSize = ((buffer[0] & 0xFF) << 24) | ((buffer[1] & 0xFF) << 16) |
                             ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                String boxType = new String(buffer, 4, 4);

                if ("moov".equals(boxType) || "trak".equals(boxType) || "mdia".equals(boxType)) {
                    continue;
                }

                if ("mdhd".equals(boxType)) {
                    byte[] mdhd = new byte[24];
                    raf.read(mdhd);
                    int version = mdhd[0];

                    if (version == 0) {
                        sampleRate = ((mdhd[12] & 0xFF) << 24) | ((mdhd[13] & 0xFF) << 16) |
                                    ((mdhd[14] & 0xFF) << 8) | (mdhd[15] & 0xFF);
                        duration = ((long) (mdhd[16] & 0xFF) << 24) | ((mdhd[17] & 0xFF) << 16) |
                                  ((mdhd[18] & 0xFF) << 8) | (mdhd[19] & 0xFF);
                    }

                    if (sampleRate > 0 && duration > 0) {
                        return (int) (duration / sampleRate);
                    }
                }

                if (boxSize > 8) {
                    raf.skipBytes(boxSize - 8);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    /**
     * Get audio sample rate
     */
    private String getSampleRate(String fileName) {
        try {
            Path filePath = Paths.get(MUSIC_FOLDER, fileName);
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

            int sampleRate = switch (extension) {
                case "mp3" -> getMp3SampleRate(filePath);
                case "flac" -> getFlacSampleRate(filePath);
                case "ogg", "opus" -> getOggSampleRate(filePath);
                case "m4a", "aac" -> getM4aSampleRate(filePath);
                default -> getWavSampleRate(filePath);
            };

            if (sampleRate > 0) {
                if (sampleRate >= 1000) {
                    return String.format("%.1f kHz", sampleRate / 1000.0);
                } else {
                    return String.format("%d Hz", sampleRate);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private int getWavSampleRate(Path filePath) {
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(filePath.toFile());
            AudioFormat format = fileFormat.getFormat();
            float rate = format.getSampleRate();
            return rate != AudioSystem.NOT_SPECIFIED ? (int) rate : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private int getMp3SampleRate(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] header = new byte[4];

            // Skip ID3v2 tag if present
            raf.read(header, 0, 3);
            if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                raf.seek(6);
                raf.read(header, 0, 4);
                int tagSize = ((header[0] & 0x7F) << 21) | ((header[1] & 0x7F) << 14) |
                             ((header[2] & 0x7F) << 7) | (header[3] & 0x7F);
                raf.seek(tagSize + 10);
            } else {
                raf.seek(0);
            }

            // Find MP3 frame sync
            for (int i = 0; i < 8192; i++) {
                raf.read(header);
                if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) {
                    int version = (header[1] >> 3) & 0x03;
                    int sampleRateIndex = (header[2] >> 2) & 0x03;

                    int[][] sampleRates = {
                        {11025, 12000, 8000, 0},   // MPEG 2.5
                        {0, 0, 0, 0},              // Reserved
                        {22050, 24000, 16000, 0},  // MPEG 2
                        {44100, 48000, 32000, 0}   // MPEG 1
                    };

                    return sampleRates[version][sampleRateIndex];
                }
                raf.seek(raf.getFilePointer() - 3);
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getFlacSampleRate(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] marker = new byte[4];
            raf.read(marker);

            if (marker[0] == 'f' && marker[1] == 'L' && marker[2] == 'a' && marker[3] == 'C') {
                raf.skipBytes(4); // Skip metadata block header
                byte[] streamInfo = new byte[18];
                raf.read(streamInfo);

                // Sample rate is in bytes 10-12 (20 bits)
                return ((streamInfo[10] & 0xFF) << 12) |
                                ((streamInfo[11] & 0xFF) << 4) |
                                ((streamInfo[12] & 0xF0) >> 4);
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getOggSampleRate(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] header = new byte[58];
            raf.read(header);

            // Check OGG signature
            if (header[0] == 'O' && header[1] == 'g' && header[2] == 'g' && header[3] == 'S') {
                // Skip to packet data
                int segments = header[26] & 0xFF;
                raf.skipBytes(segments);

                byte[] packet = new byte[19];
                raf.read(packet);

                // Check for Opus header
                if (packet[0] == 'O' && packet[1] == 'p' && packet[2] == 'u' && packet[3] == 's') {
                    return 48000; // Opus always uses 48kHz internally
                }

                // Check for Vorbis header
                if (packet[1] == 'v' && packet[2] == 'o' && packet[3] == 'r' &&
                    packet[4] == 'b' && packet[5] == 'i' && packet[6] == 's') {
                    return ((packet[12] & 0xFF) | ((packet[13] & 0xFF) << 8) |
                           ((packet[14] & 0xFF) << 16) | ((packet[15] & 0xFF) << 24));
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    private int getM4aSampleRate(Path filePath) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(filePath.toFile(), "r")) {
            byte[] buffer = new byte[8];

            while (raf.getFilePointer() < raf.length() - 8) {
                raf.read(buffer);
                int boxSize = ((buffer[0] & 0xFF) << 24) | ((buffer[1] & 0xFF) << 16) |
                             ((buffer[2] & 0xFF) << 8) | (buffer[3] & 0xFF);
                String boxType = new String(buffer, 4, 4);

                if ("moov".equals(boxType) || "trak".equals(boxType) || "mdia".equals(boxType)) {
                    continue; // Enter container
                }

                if ("mdhd".equals(boxType)) {
                    raf.skipBytes(4); // version + flags
                    raf.read(buffer);
                    return ((buffer[4] & 0xFF) << 24) | ((buffer[5] & 0xFF) << 16) |
                           ((buffer[6] & 0xFF) << 8) | (buffer[7] & 0xFF);
                }

                if (boxSize > 8) {
                    raf.skipBytes(boxSize - 8);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
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
        MusicFile currentMusicFile = null;
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
                    currentMusicFile = musicFile;
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

        // Send success message to nearby players
        org.bukkit.Location jukeboxLoc = nearestJukebox.getLocation();
        String tpCommand = String.format("/tp @s %d %d %d",
                jukeboxLoc.getBlockX(),
                jukeboxLoc.getBlockY(),
                jukeboxLoc.getBlockZ());

        // Build hover text for music info
        Component musicNameComponent = Component.text(musicName).color(NamedTextColor.YELLOW);
        if (currentMusicFile != null) {
            List<Component> hoverLines = new ArrayList<>();
            hoverLines.add(Component.text(currentMusicFile.displayName())
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true));
            hoverLines.add(Component.text(""));

            String extension = currentMusicFile.fileName().substring(
                    currentMusicFile.fileName().lastIndexOf('.') + 1).toUpperCase();
            hoverLines.add(Component.text("格式: " + extension)
                    .color(NamedTextColor.GRAY));

            if (currentMusicFile.fileSize() != null) {
                hoverLines.add(Component.text("大小: " + currentMusicFile.fileSize())
                        .color(NamedTextColor.GRAY));
            }
            if (currentMusicFile.sampleRate() != null) {
                hoverLines.add(Component.text("采样率: " + currentMusicFile.sampleRate())
                        .color(NamedTextColor.GRAY));
            }
            if (currentMusicFile.duration() != null) {
                hoverLines.add(Component.text("时长: " + currentMusicFile.duration())
                        .color(NamedTextColor.GRAY));
            }

            Component hoverText = Component.empty();
            for (int i = 0; i < hoverLines.size(); i++) {
                if (i > 0) hoverText = hoverText.append(Component.newline());
                hoverText = hoverText.append(hoverLines.get(i));
            }

            musicNameComponent = musicNameComponent.hoverEvent(
                    net.kyori.adventure.text.event.HoverEvent.showText(hoverText)
            );
        }

        Component message = Component.text()
                .append(Component.text("正在播放: ")
                        .color(NamedTextColor.GREEN))
                .append(musicNameComponent)
                .append(Component.text(" 于唱片机 ")
                        .color(NamedTextColor.GREEN))
                .append(Component.text(String.format("(%d, %d, %d)",
                        jukeboxLoc.getBlockX(),
                        jukeboxLoc.getBlockY(),
                        jukeboxLoc.getBlockZ()))
                        .color(NamedTextColor.AQUA)
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(tpCommand))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                Component.text("点击传送到唱片机").color(NamedTextColor.GRAY)
                        )))
                .build();

        // Broadcast to all players within 50 blocks
        World world = jukeboxLoc.getWorld();
        if (world != null) {
            for (Player nearbyPlayer : world.getPlayers()) {
                if (nearbyPlayer.getLocation().distance(jukeboxLoc) <= 50) {
                    nearbyPlayer.sendMessage(message);
                }
            }
        }
    }

    /**
     * Find the nearest jukebox within specified radius
     */
    private Block findNearestJukebox(Player player, int radius) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();

        int radiusSquared = radius * radius;
        Block nearestJukebox = null;
        double nearestDistance = Double.MAX_VALUE;

        for (int distance = 0; distance <= radius; distance++) {
            if (nearestJukebox != null && distance > nearestDistance) {
                break;
            }

            for (int x = -distance; x <= distance; x++) {
                for (int y = -distance; y <= distance; y++) {
                    for (int z = -distance; z <= distance; z++) {
                        if (Math.abs(x) < distance && Math.abs(y) < distance && Math.abs(z) < distance) {
                            continue;
                        }

                        int distSquared = x * x + y * y + z * z;
                        if (distSquared > radiusSquared) {
                            continue;
                        }

                        Block block = world.getBlockAt(
                                playerLoc.getBlockX() + x,
                                playerLoc.getBlockY() + y,
                                playerLoc.getBlockZ() + z
                        );

                        if (block.getType() == Material.JUKEBOX) {
                            double actualDistance = Math.sqrt(distSquared);
                            if (actualDistance < nearestDistance) {
                                nearestDistance = actualDistance;
                                nearestJukebox = block;
                            }
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
            if (music.duration() != null) {
                lore.add(Component.text("时长: " + music.duration())
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
            player.closeInventory();
            if (isRightClick) {
                // Right click: play random disc
                playRandomDisc(player);
            } else {
                // Left click: give random disc
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
    public record MusicFile(String fileName, String displayName, String fileSize, String sampleRate, String duration) {
    }
}
