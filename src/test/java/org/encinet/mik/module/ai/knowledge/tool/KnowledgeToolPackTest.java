package org.encinet.mik.module.ai.knowledge.tool;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.encinet.mik.module.ai.knowledge.application.KnowledgeService;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeDocument;
import org.encinet.mik.module.ai.knowledge.model.KnowledgeScope;
import org.encinet.mik.module.ai.tool.AiTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeToolPackTest {
    @TempDir
    Path directory;

    @Test
    void returnsDirectlyOpenableMarkdownLinksAndKeepsMemoryPrivate() {
        UUID owner = UUID.randomUUID();
        try (KnowledgeService service = new KnowledgeService(
                directory, 1_000_000, 1_000, 100)) {
            service.save(document("rules", KnowledgeScope.PUBLIC, null,
                    "服务器规则", List.of("server rules"),
                    "# 建造\n\n请尊重其他玩家的建筑。"), 0);
            service.save(document("preference", KnowledgeScope.USER, owner,
                    "回答偏好", List.of("concise answer"),
                    "用户喜欢简洁回答。"), 0);

            var pack = KnowledgeToolPack.create(service, 8);
            AiTool search = pack.tools().stream()
                    .filter(tool -> tool.name().equals("search_knowledge"))
                    .findFirst().orElseThrow();
            JsonObject searchArguments = JsonParser.parseString(
                    "{\"queries\":[\"建造规则\",\"server rules\"]}")
                    .getAsJsonObject();
            JsonObject result = JsonParser.parseString(
                    search.execute(searchArguments).join()).getAsJsonObject();

            assertTrue(result.get("ok").getAsBoolean());
            JsonObject first = result.getAsJsonArray("results").get(0).getAsJsonObject();
            assertTrue(first.get("citation_markdown").getAsString()
                    .contains("knowledge://public/rules"));

            AiTool open = pack.tools().stream()
                    .filter(tool -> tool.name().equals("open_knowledge"))
                    .findFirst().orElseThrow();
            JsonObject openArguments = new JsonObject();
            openArguments.addProperty("uri", first.get("uri").getAsString());
            JsonObject opened = JsonParser.parseString(
                    open.execute(openArguments).join()).getAsJsonObject();
            assertTrue(opened.get("markdown").getAsString().contains("尊重"));

            String memory = service.memoryContext(owner, "concise answer", 4, 4_000)
                    .orElseThrow();
            assertTrue(memory.contains("简洁回答"));
            assertFalse(service.openPublic(URI.create(
                    "knowledge://user/" + owner + "/preference#chunk-0"), 1_000)
                    .isPresent());
        }
    }

    private static KnowledgeDocument document(
            String id,
            KnowledgeScope scope,
            UUID owner,
            String title,
            List<String> aliases,
            String body
    ) {
        return new KnowledgeDocument(id, scope, Optional.ofNullable(owner), title, "zh_cn",
                aliases, List.of(), "note", List.of(), false, 1,
                Instant.EPOCH, Instant.EPOCH, Optional.empty(), body);
    }
}
