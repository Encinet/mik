package org.encinet.mik.module.ai.provider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.encinet.mik.module.ai.config.AiConfig;
import org.encinet.mik.module.ai.conversation.AiChatMessage;
import org.encinet.mik.module.ai.conversation.AiCompletionClient;
import org.encinet.mik.module.ai.runtime.LimitedHttpBody;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolCall;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Minimal non-streaming client for the widely supported Chat Completions protocol. */
public final class OpenAiCompatibleClient implements AiCompletionClient {
    private final AiConfig.Provider provider;
    private final HttpClient httpClient;

    public OpenAiCompatibleClient(AiConfig.Provider provider) {
        this(provider, HttpClient.newBuilder()
                .connectTimeout(provider.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    OpenAiCompatibleClient(AiConfig.Provider provider, HttpClient httpClient) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
    }

    @Override
    public CompletableFuture<AiChatMessage> complete(
            List<AiChatMessage> messages,
            List<AiTool> tools
    ) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(tools, "tools");
        JsonObject body = provider.requestOptions().deepCopy();
        body.addProperty("model", provider.model());
        body.addProperty("stream", false);
        JsonArray serializedMessages = new JsonArray();
        messages.forEach(message -> serializedMessages.add(message.toJson()));
        body.add("messages", serializedMessages);
        if (!tools.isEmpty()) {
            JsonArray definitions = new JsonArray();
            tools.forEach(tool -> definitions.add(tool.definition()));
            body.add("tools", definitions);
            body.addProperty("tool_choice", "auto");
        }

        HttpRequest request;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(provider.endpoint())
                    .timeout(provider.requestTimeout())
                    .setHeader("Accept", "application/json")
                    .setHeader("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(),
                            StandardCharsets.UTF_8));
            if (!provider.apiKey().isEmpty()) {
                builder.setHeader(provider.authHeader(),
                        provider.authPrefix() + provider.apiKey());
            }
            for (Map.Entry<String, String> header : provider.headers().entrySet()) {
                builder.setHeader(header.getKey(), header.getValue());
            }
            request = builder.build();
        } catch (IllegalArgumentException error) {
            return CompletableFuture.failedFuture(new AiProviderException(
                    "提供商请求配置无效", error));
        }

        return httpClient.sendAsync(request, LimitedHttpBody.bytes(provider.maxResponseBytes()))
                .thenApply(this::parseResponse)
                .exceptionallyCompose(error -> CompletableFuture.failedFuture(
                        providerFailure(error)));
    }

    private AiChatMessage parseResponse(HttpResponse<byte[]> response) {
        String body = new String(response.body(), StandardCharsets.UTF_8);
        JsonObject root;
        try {
            root = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException error) {
            throw new AiProviderException("提供商返回了无效的 JSON", error);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new AiProviderException("提供商返回 HTTP " + response.statusCode()
                    + errorDetail(root));
        }
        JsonArray choices = array(root, "choices");
        if (choices.isEmpty() || !choices.get(0).isJsonObject()) {
            throw new AiProviderException("提供商响应中没有可用结果");
        }
        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject message = object(choice, "message");
        String content = content(message.get("content"));
        List<AiToolCall> calls = toolCalls(message);
        if ((content == null || content.isBlank()) && calls.isEmpty()) {
            throw new AiProviderException("提供商返回了空响应");
        }
        return AiChatMessage.assistant(content, calls);
    }

    private static List<AiToolCall> toolCalls(JsonObject message) {
        JsonArray serialized = array(message, "tool_calls");
        List<AiToolCall> result = new ArrayList<>();
        for (int index = 0; index < serialized.size(); index++) {
            JsonElement entry = serialized.get(index);
            if (!entry.isJsonObject()) {
                continue;
            }
            JsonObject call = entry.getAsJsonObject();
            JsonObject function = object(call, "function");
            String name = string(function, "name");
            if (name.isBlank()) {
                continue;
            }
            String id = string(call, "id");
            if (id.isBlank()) {
                id = "call_" + index;
            }
            JsonElement arguments = function.get("arguments");
            String serializedArguments = arguments == null || arguments.isJsonNull()
                    ? "{}" : arguments.isJsonPrimitive()
                    ? arguments.getAsString() : arguments.toString();
            result.add(new AiToolCall(id, name, serializedArguments));
        }
        return List.copyOf(result);
    }

    private static String content(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (value.isJsonPrimitive()) {
            return value.getAsString();
        }
        if (!value.isJsonArray()) {
            return value.toString();
        }
        StringBuilder text = new StringBuilder();
        for (JsonElement part : value.getAsJsonArray()) {
            if (!part.isJsonObject()) {
                continue;
            }
            JsonElement partText = part.getAsJsonObject().get("text");
            if (partText != null && partText.isJsonPrimitive()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(partText.getAsString());
            }
        }
        return text.isEmpty() ? null : text.toString();
    }

    private static String errorDetail(JsonObject root) {
        JsonObject error = object(root, "error");
        String detail = string(error, "message");
        if (detail.isBlank()) {
            detail = string(root, "message");
        }
        detail = detail.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").strip();
        if (detail.length() > 300) {
            detail = detail.substring(0, 300) + "…";
        }
        return detail.isEmpty() ? "" : "：" + detail;
    }

    private static JsonObject object(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonObject()
                ? value.getAsJsonObject() : new JsonObject();
    }

    private static JsonArray array(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray() : new JsonArray();
    }

    private static String string(JsonObject parent, String name) {
        JsonElement value = parent.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString() : "";
    }

    private static AiProviderException providerFailure(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof AiProviderException providerError) {
            return providerError;
        }
        return new AiProviderException("无法连接 AI 提供商", cause);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
