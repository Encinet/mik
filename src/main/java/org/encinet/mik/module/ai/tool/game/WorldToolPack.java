package org.encinet.mik.module.ai.tool.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolPack;

import java.util.List;
import java.util.Locale;

/** Loaded-world, weather, time, border, and game-rule capabilities. */
public final class WorldToolPack {
    private WorldToolPack() {
    }

    public static AiToolPack create(AiGameSnapshot snapshot) {
        List<AiTool> tools = List.of(
                GameToolSupport.tool("get_world_info",
                        "Get weather, time, environment, difficulty, loaded chunks, player "
                                + "count, and border size for all worlds or one exact world.",
                        GameToolSupport.stringSchema(
                                "world", "Optional exact loaded world name", false),
                        arguments -> worldInfo(snapshot,
                                GameToolSupport.string(arguments, "world"))),
                GameToolSupport.tool("get_world_game_rules",
                        "Get every read-only Minecraft game-rule value for one loaded world.",
                        GameToolSupport.stringSchema(
                                "world", "Exact loaded world name", true),
                        arguments -> gameRules(snapshot,
                                GameToolSupport.string(arguments, "world"))));
        return new AiToolPack("world",
                "Loaded worlds, dimensions, weather, time, borders, and game rules.",
                List.of("world", "dimension", "weather", "gamerule", "rules", "世界",
                        "天气", "天氣", "规则", "規則", "ワールド", "날씨", "monde",
                        "welt", "mundo", "мир"), tools);
    }

    private static String worldInfo(AiGameSnapshot snapshot, String requested) {
        List<AiGameSnapshot.WorldInfo> selected = requested.isBlank()
                ? snapshot.worlds() : snapshot.worlds().stream()
                .filter(world -> world.name().equalsIgnoreCase(requested)).toList();
        if (selected.isEmpty()) {
            return GameToolSupport.error("world_not_loaded",
                    "No loaded world has that exact name");
        }
        JsonObject result = GameToolSupport.success(snapshot);
        JsonArray worlds = new JsonArray();
        selected.forEach(world -> worlds.add(worldJson(world)));
        result.add("worlds", worlds);
        return result.toString();
    }

    private static String gameRules(AiGameSnapshot snapshot, String requested) {
        AiGameSnapshot.WorldInfo world = snapshot.worlds().stream()
                .filter(entry -> entry.name().equalsIgnoreCase(requested))
                .findFirst().orElse(null);
        if (world == null) {
            return GameToolSupport.error("world_not_loaded",
                    "No loaded world has that exact name");
        }
        JsonObject result = GameToolSupport.success(snapshot);
        result.addProperty("world", world.name());
        JsonObject rules = new JsonObject();
        world.gameRules().forEach(rules::addProperty);
        result.add("game_rules", rules);
        return result.toString();
    }

    private static JsonObject worldJson(AiGameSnapshot.WorldInfo world) {
        JsonObject result = new JsonObject();
        result.addProperty("name", world.name());
        result.addProperty("environment", world.environment().toLowerCase(Locale.ROOT));
        result.addProperty("difficulty", world.difficulty().toLowerCase(Locale.ROOT));
        result.addProperty("time", world.time());
        result.addProperty("storm", world.storm());
        result.addProperty("thunder", world.thunder());
        result.addProperty("online_players", world.onlinePlayers());
        result.addProperty("loaded_chunks", world.loadedChunks());
        result.addProperty("world_border_size",
                GameToolSupport.decimal(world.worldBorderSize()));
        result.addProperty("game_rule_count", world.gameRules().size());
        return result;
    }
}
