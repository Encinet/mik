package org.encinet.mik.feature.entertainment.music.config;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/** Validated startup policy; player choices and source subscriptions are stored elsewhere. */
public record MusicSettings(Duration sourceRefreshInterval, long maximumAudioFileBytes,
        long maximumAudioCacheBytes, long maximumLyricsCacheBytes) {
    private static final long MIB = 1024L * 1024;
    private static final Set<String> KEYS = Set.of("sources", "sources.refresh-interval-minutes", "cache",
            "cache.audio", "cache.audio.maximum-file-mib", "cache.audio.maximum-total-mib", "cache.lyrics",
            "cache.lyrics.maximum-total-mib");

    public MusicSettings {
        if (sourceRefreshInterval == null || sourceRefreshInterval.isNegative()
                || sourceRefreshInterval.compareTo(Duration.ofDays(7)) > 0) {
            throw new IllegalArgumentException(
                    "sources.refresh-interval-minutes must be between 0 and 10080");
        }
        if (maximumAudioFileBytes < MIB || maximumAudioFileBytes > 4096 * MIB) {
            throw new IllegalArgumentException("cache.audio.maximum-file-mib must be between 1 and 4096");
        }
        if (maximumAudioCacheBytes < maximumAudioFileBytes || maximumAudioCacheBytes > 1048576 * MIB) {
            throw new IllegalArgumentException(
                    "cache.audio.maximum-total-mib must cover one file and be at most 1048576");
        }
        if (maximumLyricsCacheBytes < 4 * MIB || maximumLyricsCacheBytes > 65536 * MIB) {
            throw new IllegalArgumentException("cache.lyrics.maximum-total-mib must be between 4 and 65536");
        }
    }

    /** Creates the shipped template only if missing, then reads strictly without rewriting user content. */
    public static MusicSettings load(Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (!Files.exists(file)) {
            try (var template = MusicSettings.class.getResourceAsStream("/music/settings.yml")) {
                if (template == null)
                    throw new IOException("Missing bundled music/settings.yml");
                try {
                    Files.copy(template, file);
                } catch (FileAlreadyExistsException ignored) {
                    // Another initializer already created the settings; read that file below.
                }
            }
        }
        var yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
            for (String key : yaml.getKeys(true)) {
                if (!KEYS.contains(key))
                    throw new IllegalArgumentException("Unknown music setting: " + key);
            }
            for (String section : Set.of("sources", "cache", "cache.audio", "cache.lyrics")) {
                if (yaml.contains(section) && !yaml.isConfigurationSection(section)) {
                    throw new IllegalArgumentException(section + " must be a mapping");
                }
            }
            return new MusicSettings(
                    Duration.ofMinutes(integer(yaml, "sources.refresh-interval-minutes", 360)),
                    Math.multiplyExact(integer(yaml, "cache.audio.maximum-file-mib", 512), MIB),
                    Math.multiplyExact(integer(yaml, "cache.audio.maximum-total-mib", 8192), MIB),
                    Math.multiplyExact(integer(yaml, "cache.lyrics.maximum-total-mib", 256), MIB));
        } catch (InvalidConfigurationException | IllegalArgumentException | ArithmeticException exception) {
            throw new IOException(
                    "Invalid music settings at " + file + ": " + exception.getMessage(), exception);
        }
    }

    private static long integer(YamlConfiguration yaml, String path, long fallback) {
        Object value = yaml.get(path);
        if (value == null)
            return fallback;
        if (value instanceof Integer number)
            return number.longValue();
        if (value instanceof Long number)
            return number;
        throw new IllegalArgumentException(path + " must be an integer");
    }
}
