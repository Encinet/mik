package org.encinet.mik.module.ai.tool;

import com.google.gson.JsonObject;

import java.util.Objects;

/** A function call requested by an AI provider. */
public record AiToolCall(String id, String name, String arguments) {
    public AiToolCall {
        id = Objects.requireNonNull(id, "id");
        name = Objects.requireNonNull(name, "name");
        arguments = Objects.requireNonNullElse(arguments, "{}");
    }

    public JsonObject toJson() {
        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("arguments", arguments);
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("type", "function");
        result.add("function", function);
        return result;
    }
}
