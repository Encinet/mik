package org.encinet.mik.module.ai.tool.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.encinet.mik.module.ai.tool.AiTool;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

final class GameToolSupport {
    private GameToolSupport() {
    }

    static AiTool tool(
            String name,
            String description,
            JsonObject parameters,
            Function<JsonObject, String> operation
    ) {
        return new SnapshotTool(name, description, parameters, operation);
    }

    static JsonObject emptySchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", new JsonObject());
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    static JsonObject stringSchema(
            String name,
            String description,
            boolean required
    ) {
        JsonObject property = new JsonObject();
        property.addProperty("type", "string");
        property.addProperty("description", description);
        return objectSchema(java.util.Map.of(name, property),
                required ? List.of(name) : List.of());
    }

    static JsonObject objectSchema(MapBuilder properties, List<String> required) {
        return objectSchema(properties.build(), required);
    }

    static JsonObject objectSchema(
            java.util.Map<String, JsonObject> fields,
            List<String> required
    ) {
        JsonObject properties = new JsonObject();
        fields.forEach(properties::add);
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.addProperty("additionalProperties", false);
        if (!required.isEmpty()) {
            JsonArray requiredFields = new JsonArray();
            required.forEach(requiredFields::add);
            schema.add("required", requiredFields);
        }
        return schema;
    }

    static JsonObject stringField(String description) {
        JsonObject field = new JsonObject();
        field.addProperty("type", "string");
        field.addProperty("description", description);
        return field;
    }

    static JsonObject integerField(String description, int minimum, int maximum) {
        JsonObject field = new JsonObject();
        field.addProperty("type", "integer");
        field.addProperty("description", description);
        field.addProperty("minimum", minimum);
        field.addProperty("maximum", maximum);
        return field;
    }

    static JsonObject success(AiGameSnapshot snapshot) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", true);
        result.addProperty("captured_at", snapshot.capturedAt().toString());
        return result;
    }

    static String error(String code, String message) {
        JsonObject result = new JsonObject();
        result.addProperty("ok", false);
        result.addProperty("error", code);
        result.addProperty("message", message);
        return result.toString();
    }

    static String string(JsonObject object, String name) {
        JsonElement value = object.get(name);
        return value != null && value.isJsonPrimitive()
                ? value.getAsString().strip() : "";
    }

    static int integer(JsonObject object, String name, int fallback, int minimum, int maximum) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return Math.max(minimum, Math.min(maximum, value.getAsInt()));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static double decimal(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static JsonObject position(AiGameSnapshot.Position position) {
        JsonObject coordinates = new JsonObject();
        coordinates.addProperty("x", decimal(position.x()));
        coordinates.addProperty("y", decimal(position.y()));
        coordinates.addProperty("z", decimal(position.z()));
        return coordinates;
    }

    static MapBuilder fields() {
        return new MapBuilder();
    }

    static final class MapBuilder {
        private final java.util.LinkedHashMap<String, JsonObject> fields =
                new java.util.LinkedHashMap<>();

        MapBuilder add(String name, JsonObject field) {
            fields.put(name, field);
            return this;
        }

        java.util.Map<String, JsonObject> build() {
            return java.util.Map.copyOf(fields);
        }
    }

    private record SnapshotTool(
            String name,
            String description,
            JsonObject parameters,
            Function<JsonObject, String> operation
    ) implements AiTool {
        private SnapshotTool {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            parameters = parameters.deepCopy();
            Objects.requireNonNull(operation, "operation");
        }

        @Override
        public CompletableFuture<String> execute(JsonObject arguments) {
            return CompletableFuture.completedFuture(operation.apply(arguments));
        }

        @Override
        public JsonObject parameters() {
            return parameters.deepCopy();
        }
    }
}
