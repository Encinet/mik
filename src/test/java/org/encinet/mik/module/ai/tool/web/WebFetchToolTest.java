package org.encinet.mik.module.ai.tool.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.encinet.mik.module.ai.config.AiConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebFetchToolTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchesHtmlAsMarkdownMetadataAndLinks() throws Exception {
        server = server();
        server.createContext("/page", exchange -> respond(exchange, 200, "text/html; charset=utf-8",
                """
                        <html><head><title>News</title></head><body><main>
                        <h1>Today</h1><p>Details at <a href="/details">the next page</a>.</p>
                        </main></body></html>
                        """));
        server.start();
        WebFetchTool tool = tool(uri -> uri);
        JsonObject arguments = new JsonObject();
        arguments.addProperty("url", baseUrl() + "/page");

        JsonObject output = JsonParser.parseString(tool.execute(arguments).join())
                .getAsJsonObject();

        assertTrue(output.get("ok").getAsBoolean());
        assertEquals("News", output.get("title").getAsString());
        assertTrue(output.get("content_markdown").getAsString().contains(
                "[the next page](<" + baseUrl() + "/details>)"));
        assertFalse(output.has("links"));
        assertTrue(output.get("security_notice").getAsString().contains("untrusted"));
        assertFalse(output.get("truncated").getAsBoolean());
    }

    @Test
    void validatesEveryRedirectBeforeIssuingTheNextRequest() throws Exception {
        AtomicInteger privateRequests = new AtomicInteger();
        AtomicInteger validations = new AtomicInteger();
        server = server();
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().set("Location", "/private");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/private", exchange -> {
            privateRequests.incrementAndGet();
            respond(exchange, 200, "text/plain", "must not be reached");
        });
        server.start();
        WebFetchTool tool = tool(uri -> {
            validations.incrementAndGet();
            if (uri.getPath().equals("/private")) {
                throw new WebFetchException("blocked_address", "blocked in test");
            }
            return uri;
        });
        JsonObject arguments = new JsonObject();
        arguments.addProperty("url", baseUrl() + "/redirect");

        JsonObject output = JsonParser.parseString(tool.execute(arguments).join())
                .getAsJsonObject();

        assertFalse(output.get("ok").getAsBoolean());
        assertEquals("blocked_address", output.get("error").getAsString());
        assertEquals(2, validations.get());
        assertEquals(0, privateRequests.get());
    }

    @Test
    void formatsJsonAndRejectsBinaryResponses() throws Exception {
        server = server();
        server.createContext("/data", exchange -> respond(exchange, 200,
                "application/json", "{\"value\":42,\"next\":\"https://example.org/next\"}"));
        server.createContext("/image", exchange -> respond(exchange, 200,
                "image/png", "not really an image"));
        server.createContext("/fragment", exchange -> respond(exchange, 200,
                "", "<!-- lead --><main><h2>Detected fragment</h2></main>"));
        server.start();
        WebFetchTool tool = tool(uri -> uri);

        JsonObject json = execute(tool, baseUrl() + "/data");
        JsonObject binary = execute(tool, baseUrl() + "/image");
        JsonObject fragment = execute(tool, baseUrl() + "/fragment");

        assertTrue(json.get("content_markdown").getAsString().startsWith("```json"));
        assertTrue(json.get("content_markdown").getAsString()
                .contains("https://example.org/next"));
        assertFalse(json.has("links"));
        assertEquals("unsupported_content_type", binary.get("error").getAsString());
        assertEquals("text/html", fragment.get("content_type").getAsString());
        assertTrue(fragment.get("content_markdown").getAsString()
                .contains("## Detected fragment"));
    }

    private WebFetchTool tool(WebUriPolicy policy) {
        AiConfig.WebFetch config = new AiConfig.WebFetch(
                true, Duration.ofSeconds(5), 100_000, 10_000, 3, 10);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new WebFetchTool(config, client, policy);
    }

    private JsonObject execute(WebFetchTool tool, String url) {
        JsonObject arguments = new JsonObject();
        arguments.addProperty("url", url);
        return JsonParser.parseString(tool.execute(arguments).join()).getAsJsonObject();
    }

    private HttpServer server() throws Exception {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(
            com.sun.net.httpserver.HttpExchange exchange,
            int status,
            String contentType,
            String body
    ) throws java.io.IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        if (!contentType.isBlank()) {
            exchange.getResponseHeaders().set("Content-Type", contentType);
        }
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
