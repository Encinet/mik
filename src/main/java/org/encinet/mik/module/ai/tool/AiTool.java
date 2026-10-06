package org.encinet.mik.module.ai.tool;

import com.google.gson.JsonObject;

import java.util.concurrent.CompletableFuture;

/** A read-only function made available to the model. */
public interface AiTool {
    String name();

    String description();

    JsonObject parameters();

    CompletableFuture<String> execute(JsonObject arguments);

    default JsonObject definition() {
        JsonObject function = new JsonObject();
        function.addProperty("name", name());
        function.addProperty("description", description());
        function.add("parameters", parameters());
        JsonObject definition = new JsonObject();
        definition.addProperty("type", "function");
        definition.add("function", function);
        return definition;
    }
}
