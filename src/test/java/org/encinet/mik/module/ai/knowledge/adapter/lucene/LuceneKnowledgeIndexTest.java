package org.encinet.mik.module.ai.knowledge.adapter.lucene;

import org.encinet.mik.module.ai.knowledge.adapter.markdown.MarkdownChunker;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuceneKnowledgeIndexTest {
    @TempDir
    Path directory;

    @Test
    void searchesMultilingualPublicKnowledgeAndEnforcesPrivateOwnerFilter() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        try (LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(
                directory, new MarkdownChunker(1_000, 100))) {
            index.replaceAll(List.of(
                    document("rules", KnowledgeScope.PUBLIC, null, "服务器规则",
                            "zh_cn", List.of("server rules"), List.of("rules"),
                            "# 建造规则\n\n禁止破坏其他玩家的建筑。"),
                    document("alice-style", KnowledgeScope.USER, alice, "回答偏好",
                            "zh_cn", List.of("brief answers"), List.of("preference"),
                            "用户喜欢简洁的中文回答。"),
                    document("bob-style", KnowledgeScope.USER, bob, "回答偏好",
                            "en_us", List.of("detailed answers"), List.of("preference"),
                            "The user prefers detailed English answers.")));

            var publicHits = index.searchPublic(
                    List.of("建造规则", "server rules"), List.of("rules"), 5);
            assertEquals("rules", publicHits.getFirst().documentId());
            assertTrue(publicHits.getFirst().uri().toString().startsWith(
                    "knowledge://public/rules"));

            assertEquals("alice-style", index.searchUser(
                    alice, List.of("brief answers"), 5).getFirst().documentId());
            assertTrue(index.searchUser(bob, List.of("brief answers"), 5).isEmpty());
        }
    }

    @Test
    void persistsAndAtomicallySwitchesIndexGenerations() throws Exception {
        MarkdownChunker chunker = new MarkdownChunker(1_000, 100);
        Path pointer = directory.resolve("cache/ai-knowledge/CURRENT");
        String firstGeneration;
        try (LuceneKnowledgeIndex index = new LuceneKnowledgeIndex(directory, chunker)) {
            index.replaceAll(List.of(document("first", KnowledgeScope.PUBLIC, null,
                    "First guide", "en_us", List.of(), List.of(), "# First\n\nAlpha.")));
            firstGeneration = Files.readString(pointer).strip();
            assertEquals("first", index.searchPublic(
                    List.of("Alpha"), List.of(), 4).getFirst().documentId());
        }

        try (LuceneKnowledgeIndex reopened = new LuceneKnowledgeIndex(directory, chunker)) {
            assertEquals("first", reopened.searchPublic(
                    List.of("Alpha"), List.of(), 4).getFirst().documentId());
            reopened.replaceAll(List.of(document("second", KnowledgeScope.PUBLIC, null,
                    "Second guide", "en_us", List.of(), List.of(), "# Second\n\nBeta.")));
            assertTrue(reopened.searchPublic(List.of("Alpha"), List.of(), 4).isEmpty());
            assertEquals("second", reopened.searchPublic(
                    List.of("Beta"), List.of(), 4).getFirst().documentId());
            assertNotEquals(firstGeneration, Files.readString(pointer).strip());
        }
    }

    private static KnowledgeDocument document(
            String id,
            KnowledgeScope scope,
            UUID owner,
            String title,
            String language,
            List<String> aliases,
            List<String> tags,
            String body
    ) {
        return new KnowledgeDocument(id, scope, Optional.ofNullable(owner), title, language,
                aliases, tags, "note", List.of(), false, 1, Instant.EPOCH,
                Instant.EPOCH, Optional.empty(), body);
    }
}
