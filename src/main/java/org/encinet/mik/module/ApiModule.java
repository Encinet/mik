package org.encinet.mik.module;

import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.Command;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ApiModule — Lightweight HTTP API server for external integrations.
 *
 * <p>Built on the JDK built-in {@link com.sun.net.httpserver.HttpServer}.
 * Intended for use by websites, bots, or dashboards that need to query
 * server state without direct Minecraft protocol access.
 *
 * <hr>
 * <h2>Authentication</h2>
 * Every request must include the following header:
 * <pre>
 *   X-API-Key: &lt;value of api.key in config.yml&gt;
 * </pre>
 * If {@code api.key} is empty the server will not start.
 * An incorrect or missing key returns {@code 401 Unauthorized}.
 *
 * <hr>
 * <h2>Endpoints</h2>
 *
 * <h3>GET /api/players</h3>
 * Returns the current number of online players.
 * <pre>
 * Response 200 OK
 * Content-Type: application/json
 *
 * {"count": 42}
 * </pre>
 * Fields:
 * <ul>
 *   <li>{@code count} (integer) — number of players currently online</li>
 * </ul>
 *
 * <h3>GET /api/announcements</h3>
 * Returns all announcements loaded from {@code announcements.txt}.
 * <pre>
 * Response 200 OK
 * Content-Type: application/json
 *
 * [
 *   {"timestamp": 1704067200, "content": "Server is now open!"},
 *   {"timestamp": 1707868800, "content": "Valentine event started!"}
 * ]
 * </pre>
 * Fields per object:
 * <ul>
 *   <li>{@code timestamp} (integer) — Unix epoch seconds (server local timezone)</li>
 *   <li>{@code content}   (string)  — announcement body, may contain newlines</li>
 * </ul>
 *
 * <hr>
 * <h2>Error Responses</h2>
 * <pre>
 * 401 Unauthorized  {"error": "unauthorized"}  — missing or invalid X-API-Key
 * </pre>
 *
 * <hr>
 * <h2>Announcement File Format (announcements.txt)</h2>
 * Located in the plugin data folder. Entries are separated by {@code ---}.
 * The first line of each entry is the timestamp ({@code yyyy-MM-dd HH:mm:ss});
 * the remaining lines are the announcement body.
 * <pre>
 *   2024-01-01 12:00:00
 *   Server is now open. Welcome!
 *   ---
 *   2024-02-14 08:00:00
 *   Valentine's Day event has started!
 * </pre>
 * Use {@code /reloadannouncements} (alias: {@code /reloadannounce}) to hot-reload
 * the file without restarting the server.
 * Required permission: {@code mik.command.reloadannouncements}
 *
 * <hr>
 * <h2>Configuration (config.yml)</h2>
 * <pre>
 *   api:
 *     key: "your-secret-key"   # leave empty to disable the API server
 *     port: 8080               # passed to start(port) by the plugin main class
 * </pre>
 */
public class ApiModule {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaPlugin plugin;
    private HttpServer server;
    private String apiKey;

    private record Announcement(long timestamp, String content) {}
    private final List<Announcement> announcements = new ArrayList<>();

    public ApiModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(int port) {
        plugin.saveDefaultConfig();
        apiKey = plugin.getConfig().getString("api.key", "");
        if (apiKey.isEmpty()) {
            plugin.getLogger().warning("api.key is not set in config.yml, API server will not start.");
            return;
        }

        saveDefaultAnnouncements();
        reloadAnnouncements();

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);

            server.createContext("/api/players", exchange -> {
                if (checkAuth(exchange)) {
                    int count = Bukkit.getOnlinePlayers().size();
                    sendJson(exchange, 200, "{\"count\":" + count + "}");
                }
            });

            server.createContext("/api/announcements", exchange -> {
                if (!checkAuth(exchange)) return;
                StringBuilder sb = new StringBuilder("[");
                for (int i = 0; i < announcements.size(); i++) {
                    Announcement a = announcements.get(i);
                    if (i > 0) sb.append(",");
                    sb.append("{\"timestamp\":").append(a.timestamp())
                      .append(",\"content\":\"").append(escapeJson(a.content())).append("\"}");
                }
                sb.append("]");
                sendJson(exchange, 200, sb.toString());
            });

            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("API server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    public void reloadAnnouncements() {
        announcements.clear();
        File file = new File(plugin.getDataFolder(), "announcements.txt");
        if (!file.exists()) {
            plugin.getLogger().warning("announcements.txt not found.");
            return;
        }
        try {
            String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String[] blocks = raw.split("---");
            for (String block : blocks) {
                String trimmed = block.strip();
                if (trimmed.isEmpty()) continue;
                int newline = trimmed.indexOf('\n');
                if (newline == -1) continue;
                String dateLine = trimmed.substring(0, newline).strip();
                String content = trimmed.substring(newline + 1).strip();
                try {
                    LocalDateTime ldt = LocalDateTime.parse(dateLine, DATE_FMT);
                    long ts = ldt.atZone(ZoneId.systemDefault()).toEpochSecond();
                    announcements.add(new Announcement(ts, content));
                } catch (Exception e) {
                    plugin.getLogger().warning("Invalid date in announcements.txt: " + dateLine);
                }
            }
            plugin.getLogger().info("Loaded " + announcements.size() + " announcement(s).");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to read announcements.txt: " + e.getMessage());
        }
    }

    private void saveDefaultAnnouncements() {
        File file = new File(plugin.getDataFolder(), "announcements.txt");
        if (!file.exists()) {
            plugin.saveResource("announcements.txt", false);
        }
    }

    private boolean checkAuth(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String key = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (!apiKey.equals(key)) {
            sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
            return false;
        }
        return true;
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(Commands.literal("reloadannouncements")
                .requires(source -> source.getSender().hasPermission("mik.command.reloadannouncements"))
                .executes(ctx -> {
                    reloadAnnouncements();
                    ctx.getSource().getSender().sendMessage(
                            Component.text("公告已重新加载。", NamedTextColor.GREEN)
                    );
                    return Command.SINGLE_SUCCESS;
                }).build(), "重新加载公告文件", List.of("reloadannounce")));
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}