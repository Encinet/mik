package org.encinet.mik;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectArchitectureTest {
    private static final Path MAIN = Path.of("src/main/java/org/encinet/mik");
    private static final String PREFIX = "org.encinet.mik.";
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package ([\\w.]+);");
    private static final Pattern IMPORT = Pattern.compile("(?m)^import (?:static )?([\\w.*]+);");

    @Test
    void topLevelDirectoriesSeparateFeaturesSharedServicesAdaptersAndEntryPoints() throws IOException {
        assertEquals(Set.of("bootstrap", "feature", "shared", "integration", "shell"), directories(MAIN));
        assertEquals(Set.of("player", "community", "world", "entertainment", "server"),
                directories(MAIN.resolve("feature")));
        assertEquals(Set.of("i18n", "menu", "role", "util"), directories(MAIN.resolve("shared")));
        assertEquals(Set.of("api", "menu", "skript"), directories(MAIN.resolve("shell")));
        try (var paths = Files.list(MAIN)) {
            assertEquals(Set.of("Mik.java", "MikLoader.java"), paths.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString()).collect(Collectors.toSet()));
        }
        for (Path source : sources()) {
            Path relative = MAIN.relativize(source);
            if (relative.startsWith("feature") && !source.getFileName().toString().equals("package-info.java")) {
                assertTrue(relative.getNameCount() >= 4, source + " needs a domain and a feature owner");
            }
        }
    }

    @Test
    void javaPackagesMatchTheirPhysicalDirectories() throws IOException {
        for (Path source : sources()) {
            var declaration = PACKAGE.matcher(Files.readString(source));
            assertTrue(declaration.find(), () -> "Missing package: " + source);
            String relative = MAIN.relativize(source.getParent()).toString().replace('/', '.');
            assertEquals("org.encinet.mik" + (relative.isEmpty() ? "" : "." + relative),
                    declaration.group(1), source.toString());
        }
    }

    @Test
    void lowerLayersNeverImportTheCompositionRootOrEntryPoints() throws IOException {
        for (Path source : sources()) {
            Path relative = MAIN.relativize(source);
            String layer = relative.getName(0).toString();
            if (layer.equals("bootstrap") || relative.getNameCount() == 1) continue;
            for (String imported : imports(source)) {
                assertFalse(imported.startsWith(PREFIX + "bootstrap.")
                                || imported.equals(PREFIX + "Mik")
                                || imported.startsWith(PREFIX + "Mik."),
                        source + " imports the composition root: " + imported);
                if (!layer.equals("shell")) {
                    assertFalse(imported.startsWith(PREFIX + "shell."),
                            source + " imports a cross-feature entry point: " + imported);
                }
                if (layer.equals("shared") || layer.equals("integration")) {
                    assertFalse(imported.startsWith(PREFIX + "feature."),
                            source + " imports a business feature: " + imported);
                }
            }
        }
        for (String imported : imports(MAIN.resolve("Mik.java"))) {
            assertFalse(imported.startsWith(PREFIX + "feature.")
                            || imported.startsWith(PREFIX + "shell.")
                            || imported.startsWith(PREFIX + "shared.")
                            || imported.startsWith(PREFIX + "integration."),
                    "Paper entry point delegates feature wiring to bootstrap: " + imported);
        }
    }

    @Test
    void featuresUseCapabilitiesAndTheirDependenciesStayAcyclic() throws IOException {
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (Path source : sources()) {
            var declaration = PACKAGE.matcher(Files.readString(source));
            assertTrue(declaration.find());
            String owner = capability(declaration.group(1));
            if (owner.isEmpty()) continue;
            dependencies.computeIfAbsent(owner, ignored -> new HashSet<>());
            for (String imported : imports(source)) {
                String target = capability(imported);
                if (target.isEmpty() || owner.equals(target)) continue;
                if (owner.startsWith("feature.") && target.startsWith("feature.")) {
                    assertFalse(Arrays.stream(imported.split("\\."))
                                    .anyMatch(part -> part.endsWith("Module")),
                            source + " imports another feature's module instead of a capability: " + imported);
                }
                dependencies.get(owner).add(target);
                dependencies.computeIfAbsent(target, ignored -> new HashSet<>());
            }
        }
        Set<String> remaining = new HashSet<>(dependencies.keySet());
        while (!remaining.isEmpty()) {
            List<String> leaves = remaining.stream()
                    .filter(owner -> dependencies.get(owner).stream().noneMatch(remaining::contains))
                    .toList();
            assertFalse(leaves.isEmpty(), () -> "Circular capability imports: " + remaining);
            remaining.removeAll(leaves);
        }
    }

    private static String capability(String qualified) {
        if (!qualified.startsWith(PREFIX)) return "";
        String[] parts = qualified.substring(PREFIX.length()).split("\\.");
        int length = parts[0].equals("feature") ? 3
                : parts[0].equals("shared") || parts[0].equals("integration") ? 2 : 0;
        return length == 0 || parts.length < length ? ""
                : String.join(".", Arrays.copyOf(parts, length));
    }

    private static List<String> imports(Path source) throws IOException {
        return IMPORT.matcher(Files.readString(source)).results().map(match -> match.group(1)).toList();
    }

    private static List<Path> sources() throws IOException {
        try (var paths = Files.walk(MAIN)) {
            return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static Set<String> directories(Path parent) throws IOException {
        try (var paths = Files.list(parent)) {
            return paths.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }
}
