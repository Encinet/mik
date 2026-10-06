package org.encinet.mik.module.ai.knowledge.application;

import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.ai.api.AiRequest;
import org.encinet.mik.module.ai.config.AiConfig;
import org.encinet.mik.module.ai.conversation.AiChatMessage;
import org.encinet.mik.module.ai.tool.AiToolTrace;
import org.encinet.mik.module.ai.conversation.AiTurnResult;
import org.encinet.mik.module.ai.conversation.AiCompletionClient;
import org.encinet.mik.module.ai.tool.AiToolCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeLearningServiceTest {
    @TempDir
    Path directory;

    @Test
    void extractsAndCuratesStandaloneKnowledgeWithEvidenceBackedSources() throws Exception {
        AiConfig config = config();
        AtomicInteger calls = new AtomicInteger();
        AiCompletionClient client = (messages, tools) -> {
            if (calls.getAndIncrement() == 0) {
                assertTrue(messages.getFirst().content().contains("post-answer knowledge extractor"));
                assertTrue(messages.getLast().content().contains(
                        "\"public_learning_allowed\":true"));
                return java.util.concurrent.CompletableFuture.completedFuture(
                        AiChatMessage.assistant(null, List.of(new AiToolCall(
                                "extract", "submit_learning_candidates", """
                                {"candidates":[{"scope":"public","title":"Building rule","language":"en_us","kind":"rule","body":"# Building rule\nBuild at least 100 blocks from spawn.","aliases":["spawn distance"],"tags":["rules"],"sources":[{"title":"Rules","url":"https://docs.example/rules"}]}]}
                                """.strip()))));
            }
            assertTrue(messages.getFirst().content().contains("canonical Markdown knowledge"));
            return java.util.concurrent.CompletableFuture.completedFuture(
                    AiChatMessage.assistant(null, List.of(new AiToolCall(
                            "curate", "apply_knowledge_change", """
                            {"action":"upsert","target_id":"building-rule","title":"Building rule","language":"en_us","kind":"rule","body":"# Building rule\n\nBuild at least 100 blocks from spawn.","aliases":["spawn distance"],"tags":["rules"],"sources":[{"title":"Rules","url":"https://docs.example/rules"}]}
                            """.strip()))));
        };

        try (KnowledgeService knowledge = new KnowledgeService(directory,
                1_000_000, 1_000, 100);
             KnowledgeLearningService learning = new KnowledgeLearningService(
                     directory, knowledge, client, config.knowledge(),
                     Logger.getLogger("knowledge-test"))) {
            AiRequest request = new AiRequest("c", "Alice", "en_us",
                    Optional.empty(), "What is the building rule?");
            AiTurnResult turn = new AiTurnResult(request.prompt(),
                    "Build at least 100 blocks from spawn.", "en_us",
                    List.of(new AiToolTrace("fetch_web_page",
                            "{\"url\":\"https://docs.example/rules\"}")), false);

            assertTrue(learning.capture(request, turn));
            awaitCandidateCount(knowledge, 1);
            KnowledgeLearningService.CurateResult result = learning.curateNow()
                    .get(5, TimeUnit.SECONDS);

            assertEquals(1, result.processed());
            assertEquals(1, result.applied());
            assertEquals(0, result.failed());
            assertEquals(0, knowledge.repository().candidateCount());
            var stored = knowledge.repository().findPublic("building-rule").orElseThrow();
            assertEquals("https://docs.example/rules",
                    stored.sources().getFirst().url().toASCIIString());
            assertFalse(knowledge.searchPublic(List.of("spawn distance"),
                    List.of("rules"), 4).isEmpty());
        }
    }

    @Test
    void privateMemoryTurnsCannotProducePublicKnowledge() throws Exception {
        AiConfig config = config();
        UUID owner = UUID.randomUUID();
        AiCompletionClient client = (messages, tools) ->
                java.util.concurrent.CompletableFuture.completedFuture(
                        AiChatMessage.assistant(null, List.of(new AiToolCall(
                                "extract", "submit_learning_candidates", """
                                {"candidates":[
                                  {"scope":"public","title":"Leaked preference","language":"en_us","kind":"note","body":"Prefer concise answers."},
                                  {"scope":"user","title":"Answer preference","language":"en_us","kind":"profile","body":"Prefer concise answers."}
                                ]}
                                """.strip()))));

        try (KnowledgeService knowledge = new KnowledgeService(directory,
                1_000_000, 1_000, 100);
             KnowledgeLearningService learning = new KnowledgeLearningService(
                     directory, knowledge, client, config.knowledge(),
                     Logger.getLogger("knowledge-test"))) {
            AiRequest request = new AiRequest("c", "Alice", "en_us",
                    Optional.of(owner), "Remember how I like answers.");
            AiTurnResult turn = new AiTurnResult(request.prompt(),
                    "I will keep answers concise.", "en_us", List.of(), true);

            assertTrue(learning.capture(request, turn));
            awaitCandidateCount(knowledge, 1);
            var candidates = knowledge.repository().candidates(4);
            assertEquals(1, candidates.size());
            assertEquals(owner, candidates.getFirst().document().owner().orElseThrow());
            assertTrue(candidates.getFirst().document().scope()
                    == org.encinet.mik.module.ai.knowledge.model.KnowledgeScope.USER);
        }
    }

    private void awaitCandidateCount(KnowledgeService knowledge, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (knowledge.repository().candidateCount() != expected
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, knowledge.repository().candidateCount());
    }

    private static AiConfig config() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                active-provider: test
                providers:
                  test:
                    endpoint: http://127.0.0.1:12345/v1/chat/completions
                    model: test-model
                knowledge:
                  learning:
                    provider: active
                    curation-interval-minutes: 60
                    candidate-queue-capacity: 16
                system-prompts:
                  default: Test prompt
                """);
        return AiConfig.from(yaml);
    }
}
