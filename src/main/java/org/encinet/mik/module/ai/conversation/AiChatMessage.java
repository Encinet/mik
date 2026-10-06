package org.encinet.mik.module.ai.conversation;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.encinet.mik.module.ai.tool.AiToolCall;

import java.util.List;
import java.util.Objects;

/** One OpenAI-compatible chat message, including assistant tool calls. */
public record AiChatMessage(
        String role,
        String content,
        List<AiToolCall> toolCalls,
        String toolCallId
) {
    public AiChatMessage {
        role = Objects.requireNonNull(role, "role");
        toolCalls = List.copyOf(Objects.requireNonNullElse(toolCalls, List.of()));
    }

    public static AiChatMessage system(String content) {
        return new AiChatMessage("system", Objects.requireNonNull(content), List.of(), null);
    }

    public static AiChatMessage user(String content) {
        return new AiChatMessage("user", Objects.requireNonNull(content), List.of(), null);
    }

    public static AiChatMessage assistant(String content) {
        return new AiChatMessage("assistant", Objects.requireNonNull(content), List.of(), null);
    }

    public static AiChatMessage assistant(String content, List<AiToolCall> toolCalls) {
        return new AiChatMessage("assistant", content, toolCalls, null);
    }

    public static AiChatMessage tool(String toolCallId, String content) {
        return new AiChatMessage("tool", Objects.requireNonNull(content), List.of(),
                Objects.requireNonNull(toolCallId));
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("role", role);
        if (content == null) {
            json.add("content", JsonNull.INSTANCE);
        } else {
            json.addProperty("content", content);
        }
        if (!toolCalls.isEmpty()) {
            JsonArray calls = new JsonArray();
            toolCalls.forEach(call -> calls.add(call.toJson()));
            json.add("tool_calls", calls);
        }
        if (toolCallId != null) {
            json.addProperty("tool_call_id", toolCallId);
        }
        return json;
    }
}
