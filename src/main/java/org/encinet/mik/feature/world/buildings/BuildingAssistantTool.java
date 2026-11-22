package org.encinet.mik.feature.world.buildings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.community.ai.game.GameTool;
import org.encinet.mik.protocol.AssistantToolLink;
import org.encinet.mik.protocol.AssistantToolResult;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class BuildingAssistantTool {
    private static final int DEFAULT_LIMIT = 8;

    @FunctionalInterface
    public interface Lookup {
        Supplier<Optional<NearbyBuildings>> prepare(Player viewer, NearbyBuildings.Search search);
    }

    private BuildingAssistantTool() { }

    public static GameTool create(Lookup lookup) {
        var filter = new JsonObject();
        filter.addProperty("type", "string");
        filter.addProperty("maxLength", NearbyBuildings.Search.MAX_FILTER_LENGTH);
        var properties = new JsonObject();
        properties.add("query", filter);
        properties.add("offset", integerSchema(0, BuildingCatalog.MAX_ENTRIES, 0));
        properties.add("limit", integerSchema(1, NearbyBuildings.Search.MAX_PAGE_SIZE, DEFAULT_LIMIT));
        var fields = Set.copyOf(properties.keySet());
        return GameTool.query("game_nearby_buildings",
                "Find public buildings in the requester's current world, nearest first. The optional query matches a "
                        + "name, build type, or exact public building ID (the final URL path segment). To see more, "
                        + "reuse the query and pass next_offset as offset. Coordinates do not imply an accessible route "
                        + "or teleport point.",
                GameTool.objectSchema(properties), _ -> true, (viewer, arguments) -> {
                    NearbyBuildings.Search search;
                    try {
                        if (!fields.containsAll(arguments.keySet())) throw new IllegalArgumentException();
                        search = new NearbyBuildings.Search(filter(arguments), integer(arguments, "offset", 0),
                                integer(arguments, "limit", DEFAULT_LIMIT));
                    } catch (ArithmeticException | IllegalArgumentException invalid) {
                        return () -> GameTool.invalidArguments("invalid building query, offset or limit");
                    }
                    var prepared = lookup.prepare(viewer, search);
                    return () -> prepared.get().map(BuildingAssistantTool::observe).orElseGet(() ->
                            GameTool.unavailable("the public building catalog is not currently verified"));
                });
    }

    private static String filter(JsonObject arguments) {
        var value = arguments.get("query");
        if (value == null) return "";
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException();
        return value.getAsString();
    }

    private static int integer(JsonObject arguments, String field, int fallback) {
        var value = arguments.get(field);
        if (value == null) return fallback;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException();
        return value.getAsBigDecimal().intValueExact();
    }

    private static JsonObject integerSchema(int minimum, int maximum, int fallback) {
        var schema = new JsonObject();
        schema.addProperty("type", "integer");
        schema.addProperty("minimum", minimum);
        schema.addProperty("maximum", maximum);
        schema.addProperty("default", fallback);
        return schema;
    }

    private static AssistantToolResult observe(NearbyBuildings nearby) {
        var data = new JsonObject();
        data.addProperty("world", nearby.world());
        data.addProperty("filter", nearby.filter());
        data.addProperty("offset", nearby.offset());
        data.addProperty("total_matches", nearby.totalMatches());
        data.addProperty("next_offset", nearby.nextOffset());
        var buildings = new JsonArray();
        var links = new ArrayList<AssistantToolLink>();
        for (var building : nearby.buildings()) {
            var record = new JsonObject();
            record.addProperty("id", building.id());
            record.addProperty("name", building.name());
            record.addProperty("type", building.buildType());
            record.addProperty("x", building.x());
            record.addProperty("y", building.y());
            record.addProperty("z", building.z());
            record.addProperty("distance_blocks", building.distanceBlocks());
            buildings.add(record);
            if (BuildingCatalog.isId(building.id())) {
                links.add(AssistantToolLink.newBuilder().setLabel(building.name())
                        .setUrl(BuildingCatalog.publicUrl(building.id())).build());
            }
        }
        data.add("buildings", buildings);
        return GameTool.observed(data, links);
    }
}
