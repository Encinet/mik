package org.encinet.mik.module.musicdisc;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * Loader for music files from directory
 */
public class MusicFileLoader {

    private static final String MUSIC_FOLDER = "plugins/pv-addon-discs/local";
    private static final String[] SUPPORTED_EXTENSIONS = {
            ".mp3", ".m4a", ".wav", ".flac", ".ogg", ".aac", ".opus", ".wma"
    };

    private final JavaPlugin plugin;
    private final AudioMetadataParser metadataParser;
    private final List<MusicFile> musicFiles = new CopyOnWriteArrayList<>();
    private volatile boolean loading = false;

    public MusicFileLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.metadataParser = new AudioMetadataParser();
    }

    /**
     * Load music files from directory synchronously
     */
    public void loadMusicFiles() {
        loadMusicFilesAsync().join();
    }

    /**
     * Load music files from directory asynchronously
     */
    public CompletableFuture<Void> loadMusicFilesAsync() {
        if (loading) {
            plugin.getLogger().warning("Music files are already being loaded");
            return CompletableFuture.completedFuture(null);
        }

        loading = true;
        return CompletableFuture.runAsync(() -> {
            try {
                loadMusicFilesInternal();
            } finally {
                loading = false;
            }
        });
    }

    private void loadMusicFilesInternal() {
        Path musicPath = Paths.get(MUSIC_FOLDER);
        if (!Files.exists(musicPath)) {
            plugin.getLogger().warning("Music folder not found: " + MUSIC_FOLDER);
            return;
        }

        try (Stream<Path> paths = Files.list(musicPath)) {
            List<Path> audioFiles = paths.filter(this::isSupportedAudioFile).toList();

            plugin.getLogger().info("Found " + audioFiles.size() + " audio files, parsing metadata...");

            // Parse files in parallel
            List<CompletableFuture<MusicFile>> futures = audioFiles.stream()
                    .map(path -> CompletableFuture.supplyAsync(() -> parseAudioFile(path)))
                    .toList();

            // Wait for all parsing to complete
            List<MusicFile> newMusicFiles = futures.stream()
                    .map(CompletableFuture::join)
                    .sorted(Comparator.comparing(MusicFile::displayName))
                    .toList();

            // Update on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                musicFiles.clear();
                musicFiles.addAll(newMusicFiles);
                plugin.getLogger().info("Loaded " + musicFiles.size() + " music files");
            });

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load music files: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private MusicFile parseAudioFile(Path path) {
        String fileName = path.getFileName().toString();
        String displayName = formatDisplayName(fileName);
        String extension = getFileExtension(fileName);

        String fileSize = metadataParser.getFileSize(path);
        String sampleRate = metadataParser.getSampleRate(path, extension);
        String duration = metadataParser.getDuration(path, extension);

        return new MusicFile(fileName, displayName, fileSize, sampleRate, duration);
    }

    /**
     * Get all loaded music files
     */
    public List<MusicFile> getMusicFiles() {
        return musicFiles;
    }

    /**
     * Check if file is a supported audio file
     */
    private boolean isSupportedAudioFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get file extension without dot
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(lastDot + 1);
        }
        return "";
    }

    /**
     * Format file name to display name
     */
    private String formatDisplayName(String fileName) {
        String name = fileName.replaceAll("\\.(mp3|m4a|wav|flac|ogg|aac|opus|wma)$", "");
        name = name.replace("\"", "").replace("'", "");
        name = name.replace("_", " ");
        name = name.replace("-", " - ");
        return name;
    }

    /**
     * Music file record
     */
    public record MusicFile(String fileName, String displayName, String fileSize,
                           String sampleRate, String duration) {
    }
}
