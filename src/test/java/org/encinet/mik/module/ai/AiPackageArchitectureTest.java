package org.encinet.mik.module.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPackageArchitectureTest {
    private static final Path AI = Path.of("src/main/java/org/encinet/mik/module/ai");

    @Test
    void publicGatewayHasNoBukkitSocialOrImplementationDependencies() throws IOException {
        try (Stream<Path> paths = Files.walk(AI.resolve("api"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("org.bukkit"), path.toString());
                assertFalse(source.contains("module.social"), path.toString());
                assertFalse(source.contains("module.ai.config"), path.toString());
                assertFalse(source.contains("module.ai.conversation"), path.toString());
                assertFalse(source.contains("module.ai.provider"), path.toString());
                assertFalse(source.contains("module.ai.tool"), path.toString());
            }
        }
    }

    @Test
    void implementationIsSplitIntoExplicitArchitecturalLayers() {
        for (String layer : List.of(
                "api", "config", "conversation", "provider", "runtime", "tool")) {
            assertTrue(Files.isDirectory(AI.resolve(layer)), layer);
        }
        for (String capability : List.of("game", "utility", "web")) {
            assertTrue(Files.isDirectory(AI.resolve("tool").resolve(capability)), capability);
        }
        Path knowledge = AI.resolve("knowledge");
        for (String layer : List.of("model", "application", "tool")) {
            assertTrue(Files.isDirectory(knowledge.resolve(layer)), "knowledge/" + layer);
        }
        for (String adapter : List.of("markdown", "lucene", "sqlite")) {
            assertTrue(Files.isDirectory(knowledge.resolve("adapter").resolve(adapter)),
                    "knowledge/adapter/" + adapter);
        }
    }

    @Test
    void webContentPipelineKeepsParsingRenderingLinksAndTruncationSeparate() {
        Path web = AI.resolve("tool").resolve("web");
        for (String component : List.of(
                "HtmlToMarkdownConverter.java", "HtmlMarkdownRenderer.java",
                "HtmlTableGrid.java", "InlineLinkBudget.java",
                "MarkdownTruncator.java")) {
            assertTrue(Files.isRegularFile(web.resolve(component)), component);
        }
    }

    @Test
    void rootModuleOwnsCompositionWhileCapabilityPacksRemainIndependent() throws IOException {
        String root = Files.readString(AI.resolve("AiModule.java"));
        for (String component : List.of(
                "AiConfig", "AiConversationService", "OpenAiCompatibleClient",
                "GameToolPacks", "UtilityToolPack", "WebToolPack",
                "KnowledgeService", "KnowledgeLearningService", "KnowledgeToolPack")) {
            assertTrue(root.contains(component), component);
        }
        try (Stream<Path> paths = Files.walk(AI.resolve("tool"))) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java")).toList()) {
                String source = Files.readString(path);
                assertFalse(source.contains("module.ai.AiModule"), path.toString());
                assertFalse(source.contains("module.social"), path.toString());
            }
        }
    }

    @Test
    void aiComponentsDoNotDependOnTheCompositionRoot() throws IOException {
        try (Stream<Path> paths = Files.walk(AI)) {
            for (Path path : paths.filter(value -> value.toString().endsWith(".java"))
                    .filter(value -> !value.getFileName().toString().equals("AiModule.java"))
                    .toList()) {
                assertFalse(Files.readString(path).contains("AiModule."), path.toString());
            }
        }
    }

    @Test
    void knowledgeModelAndApplicationDoNotDependOnBukkitOrSocialPlatforms()
            throws IOException {
        for (String layer : List.of("model", "application")) {
            try (Stream<Path> paths = Files.walk(AI.resolve("knowledge").resolve(layer))) {
                for (Path path : paths.filter(value -> value.toString()
                        .endsWith(".java")).toList()) {
                    String source = Files.readString(path);
                    assertFalse(source.contains("org.bukkit"), path.toString());
                    assertFalse(source.contains("module.social"), path.toString());
                }
            }
        }
    }
}
