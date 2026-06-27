package org.encinet.mik.module.api;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.communication.AnnouncementModule;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ApiModule implements Listener {
    private static final String COMMAND_PERMISSION = "mik.command.api";
    private static final String LOCAL_BIND_ADDRESS = "127.0.0.1";
    private static final String STATE_FILE_NAME = "api-state.yml";
    private static final String PEAK_ONLINE_PATH = "peak-online";
    private static final byte[] NOT_FOUND_BODY = bytes("{\"error\":\"not_found\"}");
    private static final byte[] METHOD_NOT_ALLOWED_BODY = bytes("{\"error\":\"method_not_allowed\"}");
    private static final byte[] INTERNAL_ERROR_BODY = bytes("{\"error\":\"internal_error\"}");

    private final JavaPlugin plugin;
    private final File stateFile;
    private final Map<UUID, OnlinePlayerInfo> onlinePlayers = new HashMap<>();

    private HttpServer server;
    private ExecutorService httpExecutor;
    private AnnouncementModule announcementModule;
    private volatile byte[] playersJsonBytes = bytes("{\"online\":0,\"peak_online\":0,\"players\":[]}");
    private int peakOnline;
    private int listenPort;

    private record OnlinePlayerInfo(UUID uuid, String name, String joinedAt) {
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    public ApiModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), STATE_FILE_NAME);
    }

    public void setAnnouncementModule(AnnouncementModule module) {
        this.announcementModule = module;
    }

    public void start(int port) {
        listenPort = port;

        loadState();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        bootstrapOnlinePlayers();

        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(LOCAL_BIND_ADDRESS), port);
            server = HttpServer.create(address, 0);

            createLocalContext("/api/players", "GET", this::handlePlayersOnline);
            createLocalContext("/api/announcements", "GET", this::handleAnnouncements);
            createLocalContext("/api/bans", "GET", this::handleBans);

            server.createContext("/", exchange -> {
                if (!isLocalRequest(exchange)) {
                    drop(exchange);
                    return;
                }
                sendJson(exchange, 404, NOT_FOUND_BODY, "no-store");
            });

            httpExecutor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(httpExecutor);
            server.start();
            plugin.getLogger().info("API server started on " + LOCAL_BIND_ADDRESS + ":" + port + " (local only)");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to start API server: " + e.getMessage());
        }
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(
                Commands.literal("mikapi")
                        .requires(source -> source.getSender().hasPermission(COMMAND_PERMISSION))
                        .executes(ctx -> {
                            sendStatus(ctx.getSource().getSender());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("status")
                                .executes(ctx -> {
                                    sendStatus(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(), "Show Mik API status"));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(statusComponent());
    }

    private Component statusComponent() {
        boolean running = server != null && httpExecutor != null && !httpExecutor.isShutdown();

        return Component.text("Mik API status", NamedTextColor.AQUA)
                .append(Component.newline())
                .append(Component.text("Server: " + (running ? "running" : "stopped")
                        + " bind=" + LOCAL_BIND_ADDRESS + " port=" + listenPort,
                        running ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Online players: " + onlinePlayers.size()
                        + " peak=" + peakOnline, NamedTextColor.GRAY));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        onlinePlayers.put(player.getUniqueId(), new OnlinePlayerInfo(
                player.getUniqueId(), player.getName(), formatIso(Instant.now())));
        boolean peakChanged = updatePeakOnline();
        rebuildPlayersJson();
        if (peakChanged) {
            saveState();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        onlinePlayers.remove(event.getPlayer().getUniqueId());
        rebuildPlayersJson();
    }

    private void bootstrapOnlinePlayers() {
        onlinePlayers.clear();
        String now = formatIso(Instant.now());
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.put(player.getUniqueId(), new OnlinePlayerInfo(player.getUniqueId(), player.getName(), now));
        }
        boolean peakChanged = updatePeakOnline();
        rebuildPlayersJson();
        if (peakChanged) {
            saveState();
        }
    }

    private void handlePlayersOnline(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, playersJsonBytes, "no-store");
    }

    private void handleAnnouncements(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, announcementModule.getAnnouncementsJsonBytes(), null);
    }

    private void rebuildPlayersJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"online\":").append(onlinePlayers.size())
                .append(",\"peak_online\":").append(peakOnline)
                .append(",\"players\":[");

        boolean first = true;
        for (OnlinePlayerInfo player : onlinePlayers.values()) {
            if (first) {
                first = false;
            } else {
                sb.append(",");
            }
            sb.append("{\"uuid\":\"").append(player.uuid()).append("\"")
                    .append(",\"name\":\"").append(escapeJson(player.name())).append("\"")
                    .append(",\"joined_at\":\"").append(player.joinedAt()).append("\"}");
        }
        sb.append("]}");

        playersJsonBytes = bytes(sb.toString());
    }

    private boolean updatePeakOnline() {
        int online = onlinePlayers.size();
        if (online <= peakOnline) {
            return false;
        }
        peakOnline = online;
        return true;
    }

    private void loadState() {
        if (!stateFile.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(stateFile);
        peakOnline = Math.max(0, config.getInt(PEAK_ONLINE_PATH, 0));
    }

    private void saveState() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Failed to create plugin data folder for " + STATE_FILE_NAME);
            return;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set(PEAK_ONLINE_PATH, peakOnline);
        try {
            config.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + STATE_FILE_NAME + ": " + e.getMessage());
        }
    }

    private void handleBans(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, bansJson(), null);
    }

    private byte[] bansJson() throws IOException {
        if (Bukkit.isPrimaryThread()) {
            return buildBansJson();
        }

        try {
            return Bukkit.getScheduler()
                    .callSyncMethod(plugin, this::buildBansJson)
                    .get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading bans", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("Failed to read bans", e);
        }
    }

    private byte[] buildBansJson() {
        var banList = Bukkit.getBanList(BanListType.PROFILE);
        var banEntries = banList.getEntries();

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var entry : banEntries) {
            if (!first) {
                sb.append(",");
            }
            first = false;

            PlayerProfile profile = (PlayerProfile) entry.getBanTarget();
            String playerName = profile.getName() != null ? profile.getName() : "";
            String playerUuid = profile.getId() != null ? profile.getId().toString() : "";
            String reason = entry.getReason() != null ? entry.getReason() : "";
            String bannedBy = entry.getSource();
            String bannedAt = entry.getCreated().toInstant().toString();
            String expiresAt = entry.getExpiration() != null ? entry.getExpiration().toInstant().toString() : null;
            boolean isPermanent = expiresAt == null;

            sb.append("{\"playerName\":\"").append(escapeJson(playerName)).append("\"")
                    .append(",\"playerUuid\":\"").append(escapeJson(playerUuid)).append("\"")
                    .append(",\"reason\":\"").append(escapeJson(reason)).append("\"")
                    .append(",\"bannedBy\":\"").append(escapeJson(bannedBy)).append("\"")
                    .append(",\"bannedAt\":\"").append(escapeJson(bannedAt)).append("\"")
                    .append(",\"expiresAt\":").append(expiresAt == null ? "null" : "\"" + escapeJson(expiresAt) + "\"")
                    .append(",\"isPermanent\":").append(isPermanent)
                    .append("}");
        }
        sb.append("]");
        return bytes(sb.toString());
    }

    private void createLocalContext(String path, String method, ExchangeHandler handler) {
        server.createContext(path, exchange -> {
            try {
                if (!isLocalRequest(exchange)) {
                    drop(exchange);
                    return;
                }

                if (!exchange.getRequestURI().getPath().equals(path)) {
                    sendJson(exchange, 404, NOT_FOUND_BODY, "no-store");
                    return;
                }

                if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
                    sendJson(exchange, 405, METHOD_NOT_ALLOWED_BODY, "no-store");
                    return;
                }

                handler.handle(exchange);
            } catch (Exception ignored) {
                sendJson(exchange, 500, INTERNAL_ERROR_BODY, "no-store");
            }
        });
    }

    private boolean isLocalRequest(HttpExchange exchange) {
        var address = exchange.getRemoteAddress().getAddress();
        return address != null && (address.isLoopbackAddress() || address.isAnyLocalAddress());
    }

    private void sendJson(HttpExchange exchange, int code, byte[] body, String cacheControl) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        if (cacheControl != null) {
            exchange.getResponseHeaders().set("Cache-Control", cacheControl);
        }
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body);
            os.flush();
        }
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private String formatIso(Instant instant) {
        return instant.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Immediately closes the exchange without writing a single byte.
     */
    public static void drop(HttpExchange exchange) {
        try {
            exchange.close();
        } catch (Exception ignored) {
            // Already closed or reset.
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (httpExecutor != null) {
            httpExecutor.shutdown();
        }
        onlinePlayers.clear();
    }
}
