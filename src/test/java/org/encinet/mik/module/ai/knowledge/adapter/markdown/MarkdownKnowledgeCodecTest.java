package org.encinet.mik.module.ai.knowledge.adapter.markdown;

import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownKnowledgeCodecTest {
    private final MarkdownKnowledgeCodec codec = new MarkdownKnowledgeCodec();

    @Test
    void roundTripsFrontMatterAndRichMarkdownWithoutElevatingDeclaredScope() {
        String source = """
                ---
                schema: 1
                id: connection-guide
                scope: public
                title: 连接指南
                language: zh_cn
                aliases: [加入服务器, server connection]
                tags: [server, faq]
                kind: guide
                sources:
                  - title: Documentation
                    url: https://example.test/docs
                protected: true
                revision: 3
                created-at: 2026-08-20T01:02:03Z
                updated-at: 2026-08-21T01:02:03Z
                ---

                # 连接指南

                使用 [状态页](https://status.example.test) 检查服务。

                ```text
                server.example.test
                ```
                """;
        var parsed = codec.parse(Path.of("guides/connect.md"), KnowledgeScope.PUBLIC,
                Optional.empty(), source, Instant.EPOCH);

        assertEquals("connection-guide", parsed.id());
        assertEquals("连接指南", parsed.title());
        assertTrue(parsed.protectedDocument());
        assertEquals(3, parsed.revision());
        assertEquals("https://example.test/docs",
                parsed.sources().getFirst().url().toString());
        assertTrue(parsed.body().contains("[状态页](https://status.example.test)"));

        var roundTrip = codec.parse(Path.of("connection-guide.md"),
                KnowledgeScope.PUBLIC, Optional.empty(), codec.render(parsed), Instant.EPOCH);
        assertEquals(parsed, roundTrip);
    }

    @Test
    void derivesUsefulDefaultsForPlainMarkdown() {
        var parsed = codec.parse(Path.of("faq/first-steps.md"), KnowledgeScope.PUBLIC,
                Optional.empty(), "# First Steps\n\nWelcome.", Instant.EPOCH);

        assertEquals("faq.first-steps", parsed.id());
        assertEquals("First Steps", parsed.title());
        assertEquals("und", parsed.language());
    }

    @Test
    void rejectsScopeAndOwnerConflicts() {
        assertThrows(IllegalArgumentException.class, () -> codec.parse(
                Path.of("bad.md"), KnowledgeScope.PUBLIC, Optional.empty(),
                "---\nscope: user\n---\n# Bad", Instant.EPOCH));
        UUID owner = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> codec.parse(
                Path.of("bad.md"), KnowledgeScope.USER, Optional.of(owner),
                "---\nowner: " + UUID.randomUUID() + "\n---\n# Bad", Instant.EPOCH));
    }
}
