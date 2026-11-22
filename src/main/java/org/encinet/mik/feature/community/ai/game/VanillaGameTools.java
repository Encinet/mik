package org.encinet.mik.feature.community.ai.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public final class VanillaGameTools {
    private VanillaGameTools() { }

    public static List<GameTool> tools() {
        return List.of(
                GameTool.read("game_server_status",
                        "Check this Minecraft server's version, capacity, TPS and tick time.", _ -> server()),
                GameTool.read("game_visible_players",
                        "List the online players visible to the requester. Does not count hidden players.",
                        VanillaGameTools::visiblePlayers),
                GameTool.read("game_my_player",
                        "Check the requester's game mode, world, health, food and experience level.",
                        VanillaGameTools::player),
                GameTool.read("game_my_inventory",
                        "Check the requester's carried items and amounts. Does not inspect storage or other players.",
                        VanillaGameTools::inventory),
                GameTool.read("game_world_info",
                        "Check the requester's world, dimension, difficulty and time of day in ticks.",
                        VanillaGameTools::world));
    }

    private static JsonObject server() {
        var server = Bukkit.getServer();
        var data = new JsonObject();
        data.addProperty("version", server.getMinecraftVersion());
        data.addProperty("capacity", server.getMaxPlayers());
        data.addProperty("tps", server.getTPS()[0]);
        data.addProperty("mspt", server.getAverageTickTime());
        return data;
    }

    private static JsonObject visiblePlayers(Player viewer) {
        var names = Bukkit.getOnlinePlayers().stream().filter(viewer::canSee)
                .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        var visible = new JsonArray();
        names.stream().limit(48).forEach(visible::add);
        var data = new JsonObject();
        data.add("names", visible);
        data.addProperty("count", names.size());
        return data;
    }

    private static JsonObject player(Player viewer) {
        var data = new JsonObject();
        data.addProperty("name", viewer.getName());
        data.addProperty("game_mode", viewer.getGameMode().name());
        data.addProperty("world", viewer.getWorld().getName());
        data.addProperty("health", viewer.getHealth());
        data.addProperty("food_level", viewer.getFoodLevel());
        data.addProperty("level", viewer.getLevel());
        return data;
    }

    private static JsonObject inventory(Player viewer) {
        var items = new JsonArray();
        var slots = viewer.getInventory().getContents();
        for (int slot = 0; slot < slots.length; slot++) {
            var item = slots[slot];
            if (item == null || item.getType().isAir()) continue;
            var data = new JsonObject();
            data.addProperty("slot", slot);
            data.addProperty("material", item.getType().getKey().toString());
            data.addProperty("amount", item.getAmount());
            items.add(data);
        }
        var data = new JsonObject();
        data.add("items", items);
        return data;
    }

    private static JsonObject world(Player viewer) {
        var world = viewer.getWorld();
        var data = new JsonObject();
        data.addProperty("name", world.getName());
        data.addProperty("environment", world.getEnvironment().name());
        data.addProperty("difficulty", world.getDifficulty().name());
        data.addProperty("day_time_ticks", world.getTime());
        return data;
    }
}
