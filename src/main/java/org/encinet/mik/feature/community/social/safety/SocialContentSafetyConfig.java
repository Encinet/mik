package org.encinet.mik.feature.community.social.safety;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/** Shared provider credentials and transport limits; switches stay with each adapter. */
public record SocialContentSafetyConfig(
        String apiKey,
        String edenaiApiKey,
        Duration connectTimeout,
        Duration requestTimeout
) {
    public static final String FILE_NAME = "social/content-safety.yml";

    public SocialContentSafetyConfig {
        apiKey = apiKey(apiKey, "api-key");
        edenaiApiKey = apiKey(edenaiApiKey, "edenai-api-key");
        requireTimeout(connectTimeout, "connect-timeout-millis");
        requireTimeout(requestTimeout, "request-timeout-millis");
    }

    public static SocialContentSafetyConfig load(JavaPlugin plugin) {
        Path file = plugin.getDataFolder().toPath().resolve(FILE_NAME);
        if (!file.toFile().isFile()) {
            plugin.saveResource(FILE_NAME, false);
        }
        return load(file);
    }

    static SocialContentSafetyConfig load(Path file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file.toFile());
        } catch (IOException | InvalidConfigurationException error) {
            // YAML diagnostics may contain credentials; do not retain the source exception.
            throw new IllegalArgumentException("Could not read " + FILE_NAME);
        }
        return from(yaml);
    }

    public static SocialContentSafetyConfig from(YamlConfiguration yaml) {
        return new SocialContentSafetyConfig(
                apiKey(yaml, "api-key"),
                apiKey(yaml, "edenai-api-key"),
                timeout(yaml, "connect-timeout-millis", 5_000),
                timeout(yaml, "request-timeout-millis", 10_000));
    }

    private static String apiKey(YamlConfiguration yaml, String field) {
        if (yaml.contains(field) && !yaml.isString(field)) {
            throw new IllegalArgumentException(FILE_NAME + " " + field + " must be a string");
        }
        return yaml.getString(field, "");
    }

    private static String apiKey(String value, String field) {
        String key = value == null ? "" : value.strip();
        if (key.codePoints().anyMatch(code -> code <= 32 || code >= 127)) {
            throw new IllegalArgumentException(FILE_NAME + " " + field
                    + " must not contain whitespace or non-ASCII characters");
        }
        return key;
    }

    private static Duration timeout(YamlConfiguration yaml, String field, long fallback) {
        if (yaml.contains(field) && !yaml.isInt(field) && !yaml.isLong(field)) {
            throw new IllegalArgumentException(FILE_NAME + " " + field + " must be an integer");
        }
        return Duration.ofMillis(yaml.getLong(field, fallback));
    }

    private static void requireTimeout(Duration value, String field) {
        if (value == null || value.compareTo(Duration.ofMillis(100)) < 0
                || value.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalArgumentException(FILE_NAME + " " + field
                    + " must be between 100 and 120000");
        }
    }

    @Override
    public String toString() {
        return "SocialContentSafetyConfig[apiKey=<redacted>, edenaiApiKey=<redacted>, connectTimeout="
                + connectTimeout + ", requestTimeout=" + requestTimeout + ']';
    }
}
