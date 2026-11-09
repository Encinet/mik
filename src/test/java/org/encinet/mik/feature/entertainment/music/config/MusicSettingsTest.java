package org.encinet.mik.feature.entertainment.music.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MusicSettingsTest {
    @TempDir Path directory;
    private static final long MIB = 1024L * 1024;

    @Test
    void provisionsDocumentedDefaultsAndPreservesExistingContent() throws Exception {
        Path file = directory.resolve("music/settings.yml");
        MusicSettings defaults = MusicSettings.load(file);
        assertEquals(new MusicSettings(Duration.ofHours(6), 512 * MIB, 8192 * MIB, 256 * MIB), defaults);
        assertTrue(Files.readString(file).contains("#"));

        String custom = "# Keep this comment\nsources:\n  refresh-interval-minutes: 0\n";
        Files.writeString(file, custom);
        MusicSettings configured = MusicSettings.load(file);
        assertEquals(Duration.ZERO, configured.sourceRefreshInterval());
        assertEquals(defaults.maximumAudioCacheBytes(), configured.maximumAudioCacheBytes());
        assertEquals(custom, Files.readString(file));
        assertFalse(Files.exists(directory.resolve("settings.yml")));
    }

    @Test
    void readsAllOperationalBudgetsInMebibytes() throws Exception {
        Path file = directory.resolve("settings.yml");
        Files.writeString(file, """
                sources:
                  refresh-interval-minutes: 30
                cache:
                  audio:
                    maximum-file-mib: 32
                    maximum-total-mib: 128
                  lyrics:
                    maximum-total-mib: 8
                """);
        assertEquals(new MusicSettings(Duration.ofMinutes(30), 32 * MIB, 128 * MIB, 8 * MIB),
                MusicSettings.load(file));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"sources: [broken", "sources: false", "sources:\n  refresh-interval-minutes: -1",
                    "sources:\n  refresh-interval-minutes: 10081",
                    "sources:\n  refresh-interval-minutes: '30'", "sources:\n  refresh-interval-minutes: 0.5",
                    "cache:\n  audio:\n    maximum-file-mib: 0",
                    "cache:\n  audio:\n    maximum-file-mib: 4097",
                    "cache:\n  audio:\n    maximum-file-mib: 64\n    maximum-total-mib: 32",
                    "cache:\n  audio:\n    maximum-total-mib: 9223372036854775807",
                    "cache:\n  lyrics:\n    maximum-total-mib: 3",
                    "cache:\n  audio-directory: /somewhere/else", "soruces: {}"})
    void rejectsInvalidSettingsWithoutReplacingUserFile(String content) throws Exception {
        Path file = directory.resolve("settings.yml");
        Files.writeString(file, content);
        IOException error = assertThrows(IOException.class, () -> MusicSettings.load(file));
        assertTrue(error.getMessage().contains(file.toString()));
        assertEquals(content, Files.readString(file));
    }
}
