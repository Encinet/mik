package org.encinet.mik.module.ai.knowledge.adapter.markdown;

import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownChunkerTest {
    @Test
    void keepsHeadingsLinksAndNormalFencedBlocksTogether() {
        String body = """
                # 安装

                阅读 [说明](https://example.test/docs)。

                ```yaml
                enabled: true
                nested:
                  value: 42
                ```

                ## 排错

                重启服务并检查日志。
                """;
        KnowledgeDocument document = new KnowledgeDocument(
                "guide", KnowledgeScope.PUBLIC, Optional.empty(), "指南", "zh_cn",
                List.of("guide"), List.of("server"), "guide", List.of(), false,
                1, Instant.EPOCH, Instant.EPOCH, Optional.empty(), body);

        var chunks = new MarkdownChunker(500, 50).chunk(document);

        assertEquals(2, chunks.size());
        assertEquals("安装", chunks.getFirst().heading());
        assertTrue(chunks.getFirst().text().contains("```yaml"));
        assertTrue(chunks.getFirst().text().contains("https://example.test/docs"));
        assertEquals("knowledge", chunks.getFirst().uri().getScheme());
    }
}
