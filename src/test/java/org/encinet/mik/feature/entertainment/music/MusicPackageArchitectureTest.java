package org.encinet.mik.feature.entertainment.music;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MusicPackageArchitectureTest {
    private static final Path MUSIC = Path.of("src/main/java/org/encinet/mik/feature/entertainment/music");
    private static final String PREFIX = "org.encinet.mik.feature.entertainment.music.";
    private static final Pattern IMPORT = Pattern.compile("(?m)^import (?:static )?([\\w.*]+);");

    @Test
    void musicCapabilitiesStayAcyclicAndNeverImportTheirAssembly() throws IOException {
        Map<String, Set<String>> dependencies = new HashMap<>();
        List<Path> sources;
        try (Stream<Path> files = Files.walk(MUSIC)) {
            sources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }
        for (Path source : sources) {
            Path relative = MUSIC.relativize(source);
            if (relative.getNameCount() == 1) continue;
            String owner = relative.getName(0).toString();
            dependencies.computeIfAbsent(owner, _ -> new HashSet<>());
            for (String imported : IMPORT.matcher(Files.readString(source)).results()
                    .map(match -> match.group(1)).filter(name -> name.startsWith(PREFIX)).toList()) {
                String target = imported.substring(PREFIX.length()).split("\\.")[0];
                assertFalse(Files.isRegularFile(MUSIC.resolve(target + ".java")),
                        () -> source + " imports the music assembly: " + imported);
                if (!owner.equals(target)) dependencies.get(owner).add(target);
            }
        }
        Set<String> remaining = new HashSet<>(dependencies.keySet());
        while (!remaining.isEmpty()) {
            List<String> leaves = remaining.stream()
                    .filter(owner -> dependencies.get(owner).stream().noneMatch(remaining::contains))
                    .toList();
            assertFalse(leaves.isEmpty(), () -> "Circular music capability imports: " + dependencies);
            remaining.removeAll(leaves);
        }
    }

    @Test
    void capabilityDependenciesRemainDirected() throws IOException {
        assertNoImport(MUSIC.resolve("catalog"), "org.encinet.mik.feature.entertainment.music.disc.");
        assertNoImport(MUSIC.resolve("catalog"), "org.encinet.mik.feature.entertainment.music.online.");
        assertNoImport(MUSIC.resolve("catalog"), "org.encinet.mik.feature.entertainment.music.jukebox.");
        assertNoImport(MUSIC.resolve("catalog"), "org.encinet.mik.feature.entertainment.music.rhythm.");
        assertNoImport(MUSIC.resolve("disc"), "org.encinet.mik.feature.entertainment.music.jukebox.");
        assertNoImport(MUSIC.resolve("jukebox"), "org.encinet.mik.feature.entertainment.music.ui.");
        assertNoImport(MUSIC.resolve("online"), "org.encinet.mik.feature.entertainment.music.ui.");
        assertNoImport(MUSIC.resolve("online"), "org.encinet.mik.feature.entertainment.music.listener.");
        assertNoImport(MUSIC.resolve("lyrics"), "org.encinet.mik.feature.entertainment.music.jukebox.");
        assertNoImport(MUSIC.resolve("rhythm"), "org.encinet.mik.feature.entertainment.music.ui.");
        assertNoImport(MUSIC.resolve("rhythm"), "org.encinet.mik.feature.entertainment.music.listener.");
        assertNoImport(MUSIC.resolve("rhythm"), "org.encinet.mik.feature.entertainment.music.jukebox.");
        assertNoImport(MUSIC.resolve("rhythm"), "org.encinet.mik.feature.entertainment.music.online.");
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

    @Test
    void pureProgramsAndSavedPlaylistsDoNotDependOnPresentationOrRuntime() throws IOException {
        for (String owner : List.of("playback", "playlist")) {
            assertNoImport(MUSIC.resolve(owner), "org.bukkit.");
            assertNoImport(MUSIC.resolve(owner), "org.encinet.mik.shared.menu.");
            assertNoImport(MUSIC.resolve(owner), PREFIX + "storage.");
            assertNoImport(MUSIC.resolve(owner), PREFIX + "application.");
        }
        assertNoImport(MUSIC.resolve("application"), PREFIX + "ui.");
        assertNoImport(MUSIC.resolve("ui"), PREFIX + "storage.MusicStore");
        assertNoImport(MUSIC.resolve("ui"), "java.sql.");
        assertNoImport(MUSIC.resolve("storage"), PREFIX + "application.");
        assertNoImport(MUSIC.resolve("storage"), PREFIX + "ui.");
    }

    @Test
    void roomViewsCannotExposeTheMutableRoomAggregate() {
        assertFalse(java.util.Arrays.stream(org.encinet.mik.feature.entertainment.music.application.MusicRooms.class.getMethods())
                .anyMatch(method -> method.getReturnType() == org.encinet.mik.feature.entertainment.music.rhythm.RhythmRoom.class));
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
