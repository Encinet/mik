package org.encinet.mik.module.ai.tool;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiToolRegistryTest {

    @Test
    void exposesOnlyDiscoveryUntilARelevantCapabilityPackIsActivated() {
        AiToolRegistry registry = registry();

        assertEquals(List.of("tool_search"), names(registry.definitions()));
        String inactive = registry.execute(new AiToolCall(
                "1", "server_probe", "{}")) .join();
        assertTrue(inactive.contains("unknown_or_inactive_tool"));

        String activation = registry.execute(new AiToolCall(
                "2", "tool_search", "{\"query\":\"服务器 tps\"}")) .join();

        assertTrue(activation.contains("server"));
        assertEquals(List.of("tool_search", "server_probe"),
                names(registry.definitions()));
        assertFalse(names(registry.definitions()).contains("web_probe"));
        assertEquals("{\"ok\":true,\"source\":\"server\"}", registry.execute(
                new AiToolCall("3", "server_probe", "{}")) .join());
    }

    @Test
    void discoveryRunsBeforeConcreteToolsInTheSameAssistantBatch() {
        AiToolRegistry registry = registry();

        List<String> outputs = registry.executeAll(List.of(
                new AiToolCall("discover", "tool_search",
                        "{\"packs\":[\"web\"]}"),
                new AiToolCall("use", "web_probe", "{}"))) .join();

        assertTrue(outputs.get(0).contains("web"));
        assertEquals("{\"ok\":true,\"source\":\"web\"}", outputs.get(1));
    }

    @Test
    void unknownSearchFallsBackToAllEnabledPacks() {
        AiToolRegistry registry = registry();

        String result = registry.execute(new AiToolCall(
                "1", "tool_search", "{\"query\":\"完全未知能力\"}")) .join();

        assertTrue(result.contains("\"fallback_loaded_all\":true"));
        assertEquals(List.of("tool_search", "server_probe", "web_probe"),
                names(registry.definitions()));
    }

    private static AiToolRegistry registry() {
        return new AiToolRegistry(new AiToolCatalog(List.of(
                new AiToolPack("server", "Server status and TPS",
                        List.of("服务器", "tps"), List.of(tool("server_probe", "server"))),
                new AiToolPack("web", "Live web search",
                        List.of("网页", "current"), List.of(tool("web_probe", "web"))))),
                1_000);
    }

    private static AiTool tool(String name, String source) {
        return new AiTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return source + " probe";
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
                return CompletableFuture.completedFuture(
                        "{\"ok\":true,\"source\":\"" + source + "\"}");
            }
        };
    }

    private static List<String> names(List<AiTool> tools) {
        return tools.stream().map(AiTool::name).toList();
    }
}
