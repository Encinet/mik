package org.encinet.mik.module.ai.conversation;

import com.google.gson.JsonObject;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.ai.config.AiConfig;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolCall;
import org.encinet.mik.module.ai.tool.AiToolCatalog;
import org.encinet.mik.module.ai.tool.AiToolPack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiConversationServiceTest {

    @Test
    void runsDiscoveryThenAConcreteToolAndUsesTheLocalizedSystemPrompt() throws Exception {
        AiConfig config = config(8);
        AtomicInteger step = new AtomicInteger();
        AiCompletionClient client = (messages, tools) -> switch (step.getAndIncrement()) {
            case 0 -> {
                assertEquals("中文系统提示", messages.getFirst().content());
                assertTrue(messages.get(1).content().contains(
                        "\"preferred_interface_language\":\"zh_cn\""));
                assertEquals(List.of("tool_search"), names(tools));
                yield CompletableFuture.completedFuture(AiChatMessage.assistant(null, List.of(
                        new AiToolCall("search", "tool_search",
                                "{\"packs\":[\"knowledge\"]}"))));
            }
            case 1 -> {
                assertEquals(List.of("tool_search", "lookup_fact"), names(tools));
                assertTrue(messages.getLast().content().contains("knowledge"));
                yield CompletableFuture.completedFuture(AiChatMessage.assistant(null, List.of(
                        new AiToolCall("lookup", "lookup_fact", "{}"))));
            }
            case 2 -> {
                assertEquals("{\"ok\":true,\"fact\":42}",
                        messages.getLast().content());
                yield CompletableFuture.completedFuture(
                        AiChatMessage.assistant("答案是 42。"));
            }
            default -> throw new AssertionError("Unexpected completion step");
        };
        AiToolCatalog catalog = new AiToolCatalog(List.of(new AiToolPack(
                "knowledge", "Knowledge lookup", List.of("fact", "知识"),
                List.of(tool()))));

        try (AiConversationService conversations =
                     new AiConversationService(client, config)) {
            assertEquals("答案是 42。", conversations.ask(
                    "conversation", "Alice", "zh_cn", "答案是什么？", catalog).join());
        }
        assertEquals(3, step.get());
    }

    @Test
    void boundsTheNumberOfRememberedGameAndSocialConversations() throws Exception {
        AiConfig config = config(2);
        AiCompletionClient client = (messages, tools) ->
                CompletableFuture.completedFuture(AiChatMessage.assistant("ok"));

        try (AiConversationService conversations =
                     new AiConversationService(client, config)) {
            for (int index = 0; index < 3; index++) {
                conversations.ask("conversation-" + index, "Player", "en_us",
                        "question " + index, new AiToolCatalog(List.of())).join();
            }
            assertEquals(2, conversations.conversationCount());
        }
    }

    @Test
    void injectsPrivateMemoryAsUntrustedToolDataAndMarksTheTurn() throws Exception {
        AiConfig config = config(2);
        AiCompletionClient client = (messages, tools) -> {
            assertTrue(messages.stream()
                    .filter(message -> message.role().equals("system"))
                    .noneMatch(message -> message.content().contains("prefers concise answers")));
            assertEquals("user", messages.get(messages.size() - 3).role());
            assertEquals("assistant", messages.get(messages.size() - 2).role());
            assertEquals("memory_recall",
                    messages.get(messages.size() - 2).toolCalls().getFirst().name());
            assertEquals("tool", messages.getLast().role());
            assertTrue(messages.getLast().content().contains("prefers concise answers"));
            return CompletableFuture.completedFuture(AiChatMessage.assistant("Concise answer."));
        };

        try (AiConversationService conversations =
                     new AiConversationService(client, config)) {
            AiTurnResult turn = conversations.askDetailed(
                    "private", "Alice", "en_us", "How should you reply?",
                    "{\"memories\":[{\"memory\":\"prefers concise answers\"}]}",
                    new AiToolCatalog(List.of())).join();
            assertTrue(turn.privateMemoryUsed());
            assertEquals("Concise answer.", turn.answer());
            assertTrue(turn.tools().isEmpty());
        }
    }

    @Test
    void appendsOnlyCitationsReturnedByKnowledgeSearch() throws Exception {
        AiConfig config = config(2);
        AtomicInteger step = new AtomicInteger();
        AiCompletionClient client = (messages, tools) -> switch (step.getAndIncrement()) {
            case 0 -> CompletableFuture.completedFuture(AiChatMessage.assistant(null, List.of(
                    new AiToolCall("discover", "tool_search",
                            "{\"packs\":[\"knowledge\"]}"))));
            case 1 -> CompletableFuture.completedFuture(AiChatMessage.assistant(null, List.of(
                    new AiToolCall("search", "search_knowledge",
                            "{\"queries\":[\"rules\"]}"))));
            case 2 -> CompletableFuture.completedFuture(
                    AiChatMessage.assistant("Use the documented rule."));
            default -> throw new AssertionError("Unexpected completion step");
        };
        AiToolCatalog catalog = new AiToolCatalog(List.of(new AiToolPack(
                "knowledge", "Knowledge lookup", List.of("rules"),
                List.of(knowledgeSearchTool()))));

        try (AiConversationService conversations =
                     new AiConversationService(client, config)) {
            String answer = conversations.ask("citations", "Alice", "en_us",
                    "What is the rule?", catalog).join();
            assertTrue(answer.contains("Sources:"));
            assertTrue(answer.contains("[K1: Rules](knowledge://public/rules#chunk-0)"));
            assertFalse(answer.contains("https://invented.example"));
        }
    }

    private static AiConfig config(int maximumConversations) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                active-provider: test
                providers:
                  test:
                    endpoint: http://127.0.0.1:12345/v1/chat/completions
                    model: test-model
                limits:
                  max-conversations: %d
                tools:
                  enabled-packs: []
                system-prompts:
                  default: Default system prompt
                  zh_cn: 中文系统提示
                """.formatted(maximumConversations));
        return AiConfig.from(yaml);
    }

    private static AiTool tool() {
        return new AiTool() {
            @Override
            public String name() {
                return "lookup_fact";
            }

            @Override
            public String description() {
                return "Look up the test fact";
            }

            @Override
            public JsonObject parameters() {
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");
                schema.add("properties", new JsonObject());
                return schema;
            }

            @Override
            public CompletableFuture<String> execute(JsonObject arguments) {
                return CompletableFuture.completedFuture("{\"ok\":true,\"fact\":42}");
            }
        };
    }

    private static AiTool knowledgeSearchTool() {
        return new AiTool() {
            @Override
            public String name() {
                return "search_knowledge";
            }

            @Override
            public String description() {
                return "Search test knowledge";
            }

            @Override
            public JsonObject parameters() {
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");
                schema.add("properties", new JsonObject());
                return schema;
            }

            @Override
            public CompletableFuture<String> execute(JsonObject arguments) {
                return CompletableFuture.completedFuture("""
                        {"ok":true,"results":[{"uri":"knowledge://public/rules#chunk-0","citation_markdown":"[K1: Rules](knowledge://public/rules#chunk-0)"}]}
                        """.strip());
            }
        };
    }

    private static List<String> names(List<AiTool> tools) {
        return tools.stream().map(AiTool::name).toList();
    }
}
