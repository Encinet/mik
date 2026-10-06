package org.encinet.mik.module.ai.tool.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolPack;

import java.util.List;
import java.util.Locale;

/** Online-player, requester inventory, statistics, and nearby-entity capabilities. */
public final class PlayerToolPack {
    private PlayerToolPack() {
    }

    public static AiToolPack create(AiGameSnapshot snapshot) {
        List<AiTool> tools = List.of(
                GameToolSupport.tool("list_online_players",
                        "List players in the current read-only online snapshot.",
                        GameToolSupport.emptySchema(), ignored -> onlinePlayers(snapshot)),
                GameToolSupport.tool("get_player_info",
                        "Get read-only information for one currently online player by exact name.",
                        GameToolSupport.stringSchema(
                                "player", "Exact Minecraft player name", true),
                        arguments -> playerInfo(snapshot,
                                GameToolSupport.string(arguments, "player"))),
                GameToolSupport.tool("get_my_inventory",
                        "Get the current requester's own inventory. Other players' inventories "
                                + "are never exposed.",
                        GameToolSupport.emptySchema(), ignored -> inventory(snapshot)),
                GameToolSupport.tool("get_player_statistics",
                        "Get play time, deaths, kills, jumps, and movement statistics for one "
                                + "currently online player.",
                        GameToolSupport.stringSchema(
                                "player", "Exact Minecraft player name", true),
                        arguments -> statistics(snapshot,
                                GameToolSupport.string(arguments, "player"))),
                GameToolSupport.tool("get_nearby_entities",
                        "Get entities within 32 blocks of the current requester. This is only "
                                + "available when location queries are enabled by the server owner.",
                        GameToolSupport.emptySchema(), ignored -> nearby(snapshot)));
        return new AiToolPack("player",
                "Online players, profiles, own inventory, statistics, and nearby entities.",
                List.of("player", "online", "inventory", "stats", "nearby", "entity",
                        "玩家", "在线", "在線", "背包", "统计", "統計", "附近", "实体",
                        "プレイヤー", "インベントリ", "플레이어", "인벤토리", "joueur",
                        "spieler", "jugador", "игрок"), tools);
    }

    private static String onlinePlayers(AiGameSnapshot snapshot) {
        JsonObject result = GameToolSupport.success(snapshot);
        result.addProperty("count", snapshot.players().size());
        JsonArray players = new JsonArray();
        for (AiGameSnapshot.PlayerInfo player : snapshot.players()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", player.name());
            entry.addProperty("world", player.world());
            entry.addProperty("game_mode", player.gameMode().toLowerCase(Locale.ROOT));
            players.add(entry);
        }
        result.add("players", players);
        return result.toString();
    }

    private static String playerInfo(AiGameSnapshot snapshot, String requested) {
        AiGameSnapshot.PlayerInfo player = player(snapshot, requested);
        if (player == null) {
            return GameToolSupport.error("player_not_online",
                    "No online player has that exact name");
        }
        JsonObject result = GameToolSupport.success(snapshot);
        result.addProperty("name", player.name());
        result.addProperty("uuid", player.id().toString());
        result.addProperty("game_mode", player.gameMode().toLowerCase(Locale.ROOT));
        result.addProperty("world", player.world());
        result.addProperty("health", GameToolSupport.decimal(player.health()));
        result.addProperty("food", player.food());
        result.addProperty("experience_level", player.experienceLevel());
        result.addProperty("ping_ms", player.pingMillis());
        player.position().ifPresent(position -> result.add(
                "position", GameToolSupport.position(position)));
        return result.toString();
    }

    private static String inventory(AiGameSnapshot snapshot) {
        AiGameSnapshot.PlayerInfo requester = snapshot.players().stream()
                .filter(player -> snapshot.requesterId().map(player.id()::equals).orElse(false))
                .findFirst().orElse(null);
        if (requester == null || requester.requesterInventory().isEmpty()) {
            return GameToolSupport.error("inventory_unavailable",
                    "The requester is not a currently online Minecraft player");
        }
        JsonObject result = GameToolSupport.success(snapshot);
        result.addProperty("player", requester.name());
        JsonArray items = new JsonArray();
        for (AiGameSnapshot.InventoryEntry item : requester.requesterInventory().orElseThrow()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("slot", item.slot());
            entry.addProperty("item", item.item());
            entry.addProperty("amount", item.amount());
            JsonObject enchantments = new JsonObject();
            item.enchantments().forEach(enchantments::addProperty);
            entry.add("enchantments", enchantments);
            items.add(entry);
        }
        result.add("items", items);
        return result.toString();
    }

    private static String statistics(AiGameSnapshot snapshot, String requested) {
        AiGameSnapshot.PlayerInfo player = player(snapshot, requested);
        if (player == null) {
            return GameToolSupport.error("player_not_online",
                    "No online player has that exact name");
        }
        AiGameSnapshot.PlayerStatistics stats = player.statistics();
        JsonObject result = GameToolSupport.success(snapshot);
        result.addProperty("player", player.name());
        result.addProperty("play_time_seconds", stats.playTime().toSeconds());
        result.addProperty("deaths", stats.deaths());
        result.addProperty("mob_kills", stats.mobKills());
        result.addProperty("player_kills", stats.playerKills());
        result.addProperty("jumps", stats.jumps());
        result.addProperty("walked_metres", GameToolSupport.decimal(stats.walkedMetres()));
        result.addProperty("sprinted_metres", GameToolSupport.decimal(stats.sprintedMetres()));
        result.addProperty("flown_metres", GameToolSupport.decimal(stats.flownMetres()));
        return result.toString();
    }

    private static String nearby(AiGameSnapshot snapshot) {
        if (!snapshot.nearbyEntitiesAvailable()) {
            return GameToolSupport.error("nearby_entities_disabled",
                    "Location queries are disabled or the requester is not online in Minecraft");
        }
        JsonObject result = GameToolSupport.success(snapshot);
        JsonArray entities = new JsonArray();
        for (AiGameSnapshot.NearbyEntity entity : snapshot.nearbyEntities()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", entity.type());
            entry.addProperty("name", entity.name());
            entry.addProperty("distance", GameToolSupport.decimal(entity.distance()));
            entry.add("position", GameToolSupport.position(entity.position()));
            entities.add(entry);
        }
        result.add("entities", entities);
        return result.toString();
    }

    private static AiGameSnapshot.PlayerInfo player(
            AiGameSnapshot snapshot,
            String requested
    ) {
        if (requested.isBlank()) {
            return null;
        }
        return snapshot.players().stream()
                .filter(entry -> entry.name().equalsIgnoreCase(requested))
                .findFirst().orElse(null);
    }
}
