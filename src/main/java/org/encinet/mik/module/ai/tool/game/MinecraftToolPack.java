package org.encinet.mik.module.ai.tool.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolPack;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Version-accurate Minecraft material and entity registry lookup. */
public final class MinecraftToolPack {
    private MinecraftToolPack() {
    }

    public static AiToolPack create(AiGameSnapshot snapshot) {
        JsonObject category = GameToolSupport.stringField(
                "Optional category: item, block, or entity");
        JsonArray categories = new JsonArray();
        List.of("item", "block", "entity").forEach(categories::add);
        category.add("enum", categories);
        JsonObject schema = GameToolSupport.objectSchema(GameToolSupport.fields()
                        .add("query", GameToolSupport.stringField(
                                "Case-insensitive registry id fragment"))
                        .add("category", category)
                        .add("limit", GameToolSupport.integerField(
                                "Maximum result count", 1, 50)),
                List.of("query"));
        AiTool search = GameToolSupport.tool("search_minecraft_registry",
                "Search item, block, and entity ids from the exact Minecraft server version.",
                schema, arguments -> search(snapshot, arguments));
        return new AiToolPack("minecraft",
                "Version-accurate Minecraft item, block, and entity identifier lookup.",
                List.of("minecraft", "item", "block", "entity", "registry", "物品",
                        "方块", "方塊", "实体", "實體", "アイテム", "ブロック", "아이템",
                        "bloc", "gegenstand", "objeto", "предмет"), List.of(search));
    }

    private static String search(AiGameSnapshot snapshot, JsonObject arguments) {
        String query = GameToolSupport.string(arguments, "query").toLowerCase(Locale.ROOT);
        String category = GameToolSupport.string(arguments, "category")
                .toLowerCase(Locale.ROOT);
        int limit = GameToolSupport.integer(arguments, "limit", 20, 1, 50);
        if (query.isBlank() || query.length() > 100) {
            return GameToolSupport.error("invalid_query",
                    "query must contain 1-100 characters");
        }
        List<AiGameSnapshot.RegistryEntry> matches = snapshot.registry().stream()
                .filter(entry -> category.isBlank() || entry.category().equals(category))
                .filter(entry -> entry.id().toLowerCase(Locale.ROOT).contains(query))
                .sorted(Comparator.comparingInt(entry -> matchRank(entry.id(), query)))
                .limit(limit).toList();
        JsonObject result = GameToolSupport.success(snapshot);
        JsonArray entries = new JsonArray();
        for (AiGameSnapshot.RegistryEntry match : matches) {
            JsonObject entry = new JsonObject();
            entry.addProperty("category", match.category());
            entry.addProperty("id", match.id());
            entries.add(entry);
        }
        result.addProperty("minecraft_version", snapshot.minecraftVersion());
        result.addProperty("count", matches.size());
        result.add("matches", entries);
        return result.toString();
    }

    private static int matchRank(String id, String query) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (normalized.equals(query) || normalized.equals("minecraft:" + query)) {
            return 0;
        }
        if (normalized.endsWith(":" + query)) {
            return 1;
        }
        return normalized.startsWith(query) ? 2 : 3;
    }
}
