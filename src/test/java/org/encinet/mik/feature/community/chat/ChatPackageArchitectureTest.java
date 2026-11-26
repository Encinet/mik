package org.encinet.mik.feature.community.chat;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatPackageArchitectureTest {
    private static final Path CHAT = Path.of("src/main/java/org/encinet/mik/feature/community/chat");
    private static final String PREFIX = "org.encinet.mik.feature.community.chat.";
    // Include fully qualified uses as well as imports, so a qualified type cannot bypass a boundary.
    private static final Pattern REFERENCE = Pattern.compile(
            "\\b(?:org\\.encinet\\.mik|org\\.bukkit|io\\.papermc|net\\.kyori|io\\.netty|java\\.net\\.http|com\\.google\\.protobuf)\\.[\\w.*]+");
    private static final Set<String> CORE = Set.of("model", "link", "mention", "parse");
    private static final Map<String, Set<String>> DEPENDENCIES = Map.ofEntries(
            Map.entry("", Set.of("game", "settings")),
            Map.entry("input", Set.of("link", "model")),
            Map.entry("link", Set.of("model")),
            Map.entry("mention", Set.of()),
            Map.entry("model", Set.of("mention")),
            Map.entry("parse", Set.of("link", "model", "mention")),
            Map.entry("bridge", Set.of("game", "model")),
            Map.entry("settings", Set.of()),
            Map.entry("menu", Set.of("settings")),
            Map.entry("render", Set.of("link", "model")),
            Map.entry("game", Set.of("model", "mention", "parse", "settings", "menu", "render", "input")));

    private static List<String> references(Path path) throws IOException {
        return REFERENCE.matcher(Files.readString(path)).results().map(match -> match.group()).toList();
    }

    private static String owner(String name) {
        String first = name.substring(PREFIX.length()).split("\\.")[0];
        return DEPENDENCIES.containsKey(first) ? first : "";
    }

    @Test void packagesFollowTheDeclaredDependencyDirectionWithoutCycles() throws IOException {
        Map<String, Set<String>> graph = new HashMap<>();
        try (var paths = Files.walk(CHAT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                Path relative = CHAT.relativize(path);
                String source = relative.getNameCount() == 1 ? "" : relative.getName(0).toString();
                assertTrue(DEPENDENCIES.containsKey(source), "Declare the responsibility and dependencies of " + path);
                for (String name : references(path)) {
                    if (!name.startsWith(PREFIX)) continue;
                    String target = owner(name);
                    if (source.equals(target)) continue;
                    assertTrue(DEPENDENCIES.get(source).contains(target), path + ": forbidden dependency on " + name);
                    graph.computeIfAbsent(source, _ -> new HashSet<>()).add(target);
                }
            }
        }
        Set<String> visited = new HashSet<>();
        for (String source : graph.keySet()) checkAcyclic(source, graph, new ArrayList<>(), visited);
    }

    private static void checkAcyclic(String source, Map<String, Set<String>> graph, List<String> stack, Set<String> visited) {
        assertFalse(stack.contains(source), "Chat package cycle: " + stack + " -> " + source);
        if (!visited.add(source)) return;
        stack.add(source);
        for (String target : graph.getOrDefault(source, Set.of())) checkAcyclic(target, graph, stack, visited);
        stack.removeLast();
    }

    @Test void onlyTheBridgeUsesMikdTransportAndPaperDoesNotHostBrowserTransport() throws IOException {
        try (var paths = Files.walk(CHAT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                for (String name : references(path)) {
                    assertFalse(name.startsWith("java.net.http.") || name.startsWith("io.netty."), path + ": " + name);
                    if (path.startsWith(CHAT.resolve("bridge"))) {
                        assertFalse(name.startsWith("org.bukkit.entity."), path + ": bridge cannot access live game recipients");
                    }
                    if (name.startsWith("org.encinet.mik.integration.mikd.")
                            || name.startsWith("org.encinet.mik.protocol.")
                            || name.startsWith("com.google.protobuf.")) {
                        assertTrue(path.startsWith(CHAT.resolve("bridge")), path + ": " + name);
                    }
                }
            }
        }
    }

    @Test void messageModelDoesNotDependOnGameRenderingOrServerObjects() throws IOException {
        try (var paths = Files.walk(CHAT.resolve("model"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                for (String name : references(path)) {
                    assertFalse(name.startsWith("org.bukkit.") || name.startsWith("io.papermc.")
                            || name.startsWith("net.kyori."), path + ": " + name);
                }
            }
        }
    }

    @Test void semanticCoreDoesNotReadServerStateOrDependOnDeliveryAdapters() throws IOException {
        List<String> adapterPackages = List.of(
                "org.bukkit.", "io.papermc.",
                "org.encinet.mik.feature.player.profile.",
                "org.encinet.mik.integration.");
        for (String directory : CORE) {
            try (var paths = Files.walk(CHAT.resolve(directory))) {
                for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                    for (String name : references(path)) {
                        assertFalse(adapterPackages.stream().anyMatch(name::startsWith), path + ": " + name);
                        if (directory.equals("parse")) assertFalse(name.startsWith("net.kyori."), path + ": " + name);
                        if (name.startsWith(PREFIX)) assertTrue(CORE.contains(owner(name)), path + ": " + name);
                    }
                }
            }
        }
    }

    @Test void renderingUsesComponentsAndPreparedValuesNotBukkitState() throws IOException {
        try (var paths = Files.walk(CHAT.resolve("render"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                for (String name : references(path)) {
                    assertFalse(name.startsWith("org.bukkit.") || name.startsWith("io.papermc."), path + ": " + name);
                }
            }
        }
    }
}
