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
            "\\b(?:org\\.encinet\\.mik|org\\.bukkit|io\\.papermc|net\\.kyori|com\\.google\\.gson|io\\.netty)\\.[\\w.*]+");
    private static final Set<String> CORE = Set.of("model", "mention", "modifier", "pipeline", "bridge", "runtime");
    private static final Map<String, Set<String>> DEPENDENCIES = Map.ofEntries(
            Map.entry("", Set.of("bridge", "game", "runtime", "settings")),
            Map.entry("mention", Set.of()),
            Map.entry("model", Set.of("mention")),
            Map.entry("modifier", Set.of("model", "mention")),
            Map.entry("pipeline", Set.of("model", "modifier")),
            Map.entry("bridge", Set.of("model")),
            Map.entry("runtime", Set.of("bridge", "model", "pipeline")),
            Map.entry("settings", Set.of()),
            Map.entry("menu", Set.of("settings")),
            Map.entry("render", Set.of("model")),
            Map.entry("game", Set.of("bridge", "model", "mention", "pipeline", "runtime", "settings", "menu", "render")),
            Map.entry("web", Set.of("bridge", "model", "mention", "runtime")));

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

    @Test void coreDoesNotDependOnBrowserProtocols() throws IOException {
        try (var paths = Files.walk(CHAT)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .filter(value -> !value.startsWith(CHAT.resolve("web"))).toList()) {
                for (String name : references(path)) {
                    assertFalse(name.startsWith("org.encinet.mik.feature.community.chat.web.")
                            || name.startsWith("com.google.gson.") || name.startsWith("io.netty."), path + ": " + name);
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
                        if (directory.equals("runtime")) assertFalse(name.startsWith("net.kyori."), path + ": " + name);
                        if (name.startsWith(PREFIX)) assertTrue(CORE.contains(owner(name)), path + ": " + name);
                    }
                }
            }
        }
    }
}
