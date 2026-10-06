package org.encinet.mik.module.ai.provider;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.encinet.mik.module.ai.config.AiConfig;
import org.encinet.mik.module.ai.conversation.AiChatMessage;
import org.encinet.mik.module.ai.tool.AiTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCompatibleClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsCustomProviderFieldsAndParsesStandardToolCalls() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/custom/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            tenant.set(exchange.getRequestHeaders().getFirst("X-Tenant"));
            byte[] response = """
                    {"choices":[{"message":{"role":"assistant","content":null,
                    "tool_calls":[{"id":"call-7","type":"function","function":{
                    "name":"lookup","arguments":"{\\"q\\":\\"天气\\"}"}}]}}]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.25);
        AiConfig.Provider provider = new AiConfig.Provider(
                "custom", URI.create("http://127.0.0.1:"
                + server.getAddress().getPort() + "/custom/chat"), "vendor-model",
                "top-secret", "Authorization", "Bearer ", Map.of("X-Tenant", "alpha"),
                Duration.ofSeconds(2), Duration.ofSeconds(5), 100_000, options);

        AiChatMessage reply = new OpenAiCompatibleClient(provider).complete(
                List.of(AiChatMessage.system("system"), AiChatMessage.user("question")),
                List.of(tool())).join();

        JsonObject sent = JsonParser.parseString(requestBody.get()).getAsJsonObject();
        assertEquals("vendor-model", sent.get("model").getAsString());
        assertFalse(sent.get("stream").getAsBoolean());
        assertEquals(0.25, sent.get("temperature").getAsDouble());
        assertEquals("auto", sent.get("tool_choice").getAsString());
        assertEquals("lookup", sent.getAsJsonArray("tools").get(0).getAsJsonObject()
                .getAsJsonObject("function").get("name").getAsString());
        assertEquals("Bearer top-secret", authorization.get());
        assertEquals("alpha", tenant.get());
        assertNull(reply.content());
        assertEquals(1, reply.toolCalls().size());
        assertEquals("call-7", reply.toolCalls().getFirst().id());
        assertEquals("lookup", reply.toolCalls().getFirst().name());
        assertTrue(reply.toolCalls().getFirst().arguments().contains("天气"));
    }

    private static AiTool tool() {
        return new AiTool() {
            @Override
            public String name() {
                return "lookup";
            }

            @Override
            public String description() {
                return "Look up current information";
            }

            @Override
            public JsonObject parameters() {
                JsonObject query = new JsonObject();
                query.addProperty("type", "string");
                JsonObject properties = new JsonObject();
                properties.add("q", query);
                JsonObject schema = new JsonObject();
                schema.addProperty("type", "object");
                schema.add("properties", properties);
                return schema;
            }

            @Override
            public CompletableFuture<String> execute(JsonObject arguments) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
