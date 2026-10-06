package org.encinet.mik.module.ai.tool.game;

import com.google.gson.JsonObject;
import org.encinet.mik.module.ai.tool.AiTool;
import org.encinet.mik.module.ai.tool.AiToolPack;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Server-wide health and clock capabilities. */
public final class ServerToolPack {
    private ServerToolPack() {
    }

    public static AiToolPack create(AiGameSnapshot snapshot) {
        List<AiTool> tools = List.of(
                GameToolSupport.tool("server_status",
                        "Get the current read-only server version, player counts, TPS, MSPT, "
                                + "uptime, memory, and loaded-world count.",
                        GameToolSupport.emptySchema(), ignored -> status(snapshot)),
                GameToolSupport.tool("get_server_time",
                        "Get the current instant and local time in the server time zone.",
                        GameToolSupport.emptySchema(), ignored -> time(snapshot)));
        return new AiToolPack("server",
                "Server health, performance, version, uptime, and clock queries.",
                List.of("status", "tps", "mspt", "time", "clock", "服务器", "狀態",
                        "状态", "时间", "時間", "サーバー", "서버", "servidor", "serveur"),
                tools);
    }

    private static String status(AiGameSnapshot snapshot) {
        JsonObject result = GameToolSupport.success(snapshot);
        result.addProperty("minecraft_version", snapshot.minecraftVersion());
        result.addProperty("online_players", snapshot.onlinePlayers());
        result.addProperty("maximum_players", snapshot.maximumPlayers());
        result.addProperty("tps_1m", GameToolSupport.decimal(snapshot.tpsOneMinute()));
        result.addProperty("average_tick_ms",
                GameToolSupport.decimal(snapshot.averageTickMillis()));
        result.addProperty("uptime_seconds", snapshot.uptime().toSeconds());
        result.addProperty("memory_used_bytes", snapshot.memory().usedBytes());
        result.addProperty("memory_maximum_bytes", snapshot.memory().maximumBytes());
        result.addProperty("loaded_worlds", snapshot.worlds().size());
        return result.toString();
    }

    private static String time(AiGameSnapshot snapshot) {
        JsonObject result = GameToolSupport.success(snapshot);
        ZonedDateTime local = snapshot.capturedAt()
                .atZone(java.time.ZoneId.of(snapshot.serverZoneId()));
        result.addProperty("time_zone", snapshot.serverZoneId());
        result.addProperty("local_time", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local));
        result.addProperty("unix_seconds", snapshot.capturedAt().getEpochSecond());
        return result.toString();
    }
}
