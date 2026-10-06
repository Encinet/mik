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

import static org.junit.jupiter.api.Assertions.assertFalse;

class ModuleDependencyArchitectureTest {
    private static final Path MODULES = Path.of("src/main/java/org/encinet/mik/module");
    private static final String MODULE_PACKAGE = "org.encinet.mik.module.";

    @Test
    void featureModulesUseCapabilitiesWithoutImportingOtherModulesOrCycles()
            throws IOException {
        Set<String> modules = new HashSet<>();
        try (var directories = Files.list(MODULES)) {
            directories.filter(Files::isDirectory)
                    .forEach(path -> modules.add(path.getFileName().toString()));
        }

        Map<String, Set<String>> dependencies = new HashMap<>();
        for (String module : modules) dependencies.put(module, new HashSet<>());
        try (var sources = Files.walk(MODULES)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String owner = MODULES.relativize(source).getName(0).toString();
                for (String line : Files.readAllLines(source)) {
                    String trimmed = line.strip();
                    String imported = trimmed.startsWith("import static ")
                            ? trimmed.substring("import static ".length())
                            : trimmed.startsWith("import ")
                            ? trimmed.substring("import ".length()) : "";
                    assertFalse(imported.startsWith("org.encinet.mik.Mik;")
                                    || imported.startsWith("org.encinet.mik.Mik.")
                                    || imported.startsWith("org.encinet.mik.shell."),
                            source + " imports the composition layer");
                    if (!imported.startsWith(MODULE_PACKAGE)) continue;
                    String moduleImport = imported.substring(MODULE_PACKAGE.length())
                            .replace(";", "");
                    int separator = moduleImport.indexOf('.');
                    if (separator < 0) continue;
                    String target = moduleImport.substring(0, separator);
                    if (!owner.equals(target) && modules.contains(target)) {
                        assertFalse(Arrays.stream(moduleImport.split("\\."))
                                        .anyMatch(part -> part.endsWith("Module")),
                                source + " imports a feature module instead of a capability: " + imported);
                        dependencies.get(owner).add(target);
                    }
                }
            }
        }

        Set<String> remaining = new HashSet<>(modules);
        while (!remaining.isEmpty()) {
            List<String> leaves = remaining.stream()
                    .filter(module -> dependencies.get(module).stream()
                            .noneMatch(remaining::contains))
                    .toList();
            assertFalse(leaves.isEmpty(),
                    () -> "Circular feature-module imports: " + remaining);
            remaining.removeAll(leaves);
        }
    }
}
