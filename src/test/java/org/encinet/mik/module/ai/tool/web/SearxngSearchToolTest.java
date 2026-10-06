package org.encinet.mik.module.ai.tool.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.encinet.mik.module.ai.config.AiConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearxngSearchToolTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void requestsJsonSearchAndReturnsOnlySanitizedHttpResults() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/search", exchange -> {
            query.set(exchange.getRequestURI().getRawQuery());
            byte[] response = """
                    {"answers":["<strong>Direct answer</strong>"],"results":[
                      {"title":"<b>First</b>","url":"https://example.test/one",
                       "content":"Useful <em>snippet</em>","engine":"engine-a"},
                      {"title":"Unsafe","url":"javascript:alert(1)","content":"skip"},
                      {"title":"Second","url":"http://example.test/two","content":"More"}
                    ]}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        AiConfig.WebSearch config = new AiConfig.WebSearch(true,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"),
                Duration.ofSeconds(5), 2, 100_000, "zh-CN", 2);
        JsonObject arguments = new JsonObject();
        arguments.addProperty("query", "Minecraft 新闻");

        String output = new SearxngSearchTool(config).execute(arguments).join();

        assertTrue(query.get().contains("format=json"));
        assertTrue(query.get().contains("safesearch=2"));
        assertTrue(query.get().contains("language=zh-CN"));
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();
        assertTrue(result.get("ok").getAsBoolean());
        assertEquals("Direct answer", result.getAsJsonArray("answers").get(0).getAsString());
        assertEquals(2, result.getAsJsonArray("results").size());
        assertEquals("First", result.getAsJsonArray("results").get(0).getAsJsonObject()
                .get("title").getAsString());
        assertEquals("Useful snippet", result.getAsJsonArray("results").get(0)
                .getAsJsonObject().get("snippet").getAsString());
        assertFalse(output.contains("javascript:"));
        assertFalse(output.contains("<b>"));
    }
}
