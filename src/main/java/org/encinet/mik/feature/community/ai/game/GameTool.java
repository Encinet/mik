package org.encinet.mik.feature.community.ai.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.entity.Player;
import org.encinet.mik.protocol.AssistantToolDefinition;
import org.encinet.mik.protocol.AssistantToolFailure;
import org.encinet.mik.protocol.AssistantToolFailure.Reason;
import org.encinet.mik.protocol.AssistantToolLink;
import org.encinet.mik.protocol.AssistantToolObservation;
import org.encinet.mik.protocol.AssistantToolResult;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class GameTool {
    private static final Pattern NAME = Pattern.compile("game_[a-z][a-z0-9_]{0,58}");
    private static final int MAX_OBSERVATION_BYTES = 12 * 1024;
    private static final int MAX_LINKS = 16;

    @FunctionalInterface
    public interface Read {
        Supplier<AssistantToolResult> prepare(Player viewer, JsonObject arguments);
    }

    private final AssistantToolDefinition definition;
    private final Predicate<Player> available;
    private final Read reader;

    private GameTool(String name, String description, JsonObject schema, Predicate<Player> available, Read reader) {
        if (!NAME.matcher(name).matches() || description.isBlank()
                || !new JsonPrimitive("object").equals(schema.get("type"))) {
            throw new IllegalArgumentException("Invalid assistant tool definition: " + name);
        }
        this.definition = AssistantToolDefinition.newBuilder().setName(name).setDescription(description)
                .setInputSchemaJson(schema.toString()).build();
        this.available = Objects.requireNonNull(available);
        this.reader = Objects.requireNonNull(reader);
    }

    public static GameTool read(String name, String description, Function<Player, JsonElement> snapshot) {
        return read(name, description, _ -> true, snapshot);
    }

    public static GameTool read(String name, String description, Predicate<Player> available,
                                Function<Player, JsonElement> snapshot) {
        return query(name, description, objectSchema(new JsonObject()), available, (player, arguments) -> {
            if (!arguments.isEmpty()) return () -> invalidArguments("this tool takes no arguments");
            var data = snapshot.apply(player);
            return () -> observed(data, List.of());
        });
    }

    public static GameTool query(String name, String description, JsonObject schema, Predicate<Player> available,
                                 Read reader) {
        return new GameTool(name, description, schema, available, reader);
    }

    public static JsonObject objectSchema(JsonObject properties) {
        var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    AssistantToolDefinition definition() {
        return definition;
    }

    boolean available(Player viewer) {
        return available.test(viewer);
    }

    Supplier<AssistantToolResult> prepare(Player viewer, JsonObject arguments) {
        return reader.prepare(viewer, arguments);
    }

    public static AssistantToolResult observed(JsonElement data, List<AssistantToolLink> links) {
        var observation = AssistantToolObservation.newBuilder().setDataJson(data.toString()).addAllLinks(links).build();
        return links.size() <= MAX_LINKS && observation.getSerializedSize() <= MAX_OBSERVATION_BYTES
                ? AssistantToolResult.newBuilder().setObservation(observation).build()
                : unavailable("observation is too large; use a narrower query");
    }

    public static AssistantToolResult invalidArguments(String detail) {
        return failure(Reason.INVALID_ARGUMENTS, detail);
    }

    public static AssistantToolResult unavailable(String detail) {
        return failure(Reason.UNAVAILABLE, detail);
    }

    static AssistantToolResult accessUnavailable() {
        return failure(Reason.ACCESS_UNAVAILABLE, "this game tool is not available to this turn");
    }

    private static AssistantToolResult failure(Reason reason, String detail) {
        return AssistantToolResult.newBuilder().setFailure(
                AssistantToolFailure.newBuilder().setReason(reason).setDetail(detail)).build();
    }
}
