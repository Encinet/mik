package org.encinet.mik.feature.entertainment.music;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MusicPackageArchitectureTest {
    private static final Path MUSIC = Path.of("src/main/java/org/encinet/mik/feature/entertainment/music");

    @Test
    void capabilityDependenciesRemainDirected() throws IOException {
        assertNoImport(MUSIC.resolve("catalog"), "org.encinet.mik.feature.entertainment.music.disc.");
        assertNoImport(MUSIC.resolve("catalog"), "org.encinet.mik.feature.entertainment.music.online.");
        assertNoImport(MUSIC.resolve("jukebox"), "org.encinet.mik.feature.entertainment.music.ui.");
        assertNoImport(MUSIC.resolve("online"), "org.encinet.mik.feature.entertainment.music.ui.");
        assertNoImport(MUSIC.resolve("online"), "org.encinet.mik.feature.entertainment.music.listener.");
        assertNoImport(MUSIC.resolve("lyrics"), "org.encinet.mik.feature.entertainment.music.jukebox.");
        assertNoImport(MUSIC.resolve("rhythm"), "org.encinet.mik.feature.entertainment.music.ui.");
        assertNoImport(MUSIC.resolve("rhythm"), "org.encinet.mik.feature.entertainment.music.listener.");
        assertNoImport(MUSIC.resolve("ui"), "org.encinet.mik.feature.entertainment.music.listener.");
        assertNoImport(MUSIC.resolve("listener"), "org.encinet.mik.feature.entertainment.music.online.LxCustomSource");
    }

    @Test
    void rhythmModesRemainIndependent() throws IOException {
        Path modes = MUSIC.resolve("rhythm/mode");
        assertNoImport(modes, "org.encinet.mik.feature.entertainment.music.rhythm.RhythmGameService");
        assertNoImport(modes.resolve("falling"),
                "org.encinet.mik.feature.entertainment.music.rhythm.mode.radial.");
        assertNoImport(modes.resolve("falling"),
                "org.encinet.mik.feature.entertainment.music.rhythm.mode.spatial.");
        assertNoImport(modes.resolve("radial"),
                "org.encinet.mik.feature.entertainment.music.rhythm.mode.spatial.");
        assertNoImport(MUSIC.resolve("rhythm/input"),
                "org.encinet.mik.feature.entertainment.music.rhythm.mode.");
    }

    private static void assertNoImport(Path directory, String forbidden) throws IOException {
        List<String> violations;
        try (Stream<Path> files = Files.walk(directory)) {
            violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, "import " + forbidden))
                    .map(Path::toString).sorted().toList();
        }
        assertEquals(List.of(), violations,
                () -> "Forbidden package dependency " + forbidden + " in " + violations);
    }

    private static boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
