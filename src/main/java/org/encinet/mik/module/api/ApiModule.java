package org.encinet.mik.module.api;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.communication.AnnouncementModule;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class ApiModule implements Listener {
    private static final String COMMAND_PERMISSION = "mik.command.api";
    private static final String LOCAL_BIND_ADDRESS = "127.0.0.1";
    private static final String STATE_FILE_NAME = "api-state.yml";
    private static final String PEAK_ONLINE_PATH = "peak-online";
    private static final long WEB_LOGIN_CONFIRMATION_MILLIS = TimeUnit.MINUTES.toMillis(5);
    private static final Pattern WEB_LOGIN_CODE_PATTERN = Pattern.compile("^[0-9]{6,10}$");
    private static final byte[] NOT_FOUND_BODY = bytes("{\"error\":\"not_found\"}");
    private static final byte[] METHOD_NOT_ALLOWED_BODY = bytes("{\"error\":\"method_not_allowed\"}");
    private static final byte[] INTERNAL_ERROR_BODY = bytes("{\"error\":\"internal_error\"}");

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final File stateFile;
    private final Map<UUID, OnlinePlayerInfo> onlinePlayers = new HashMap<>();
    private final Map<String, WebLoginConfirmation> webLoginConfirmations = new ConcurrentHashMap<>();

    private HttpServer server;
    private ExecutorService httpExecutor;
    private AnnouncementModule announcementModule;
    private LuckPerms luckPerms;
    private volatile byte[] playersJsonBytes = bytes("{\"online\":0,\"peak_online\":0,\"players\":[]}");
    private int peakOnline;
    private int listenPort;

    private record OnlinePlayerInfo(UUID uuid, String name, String joinedAt) {
    }

    private record WebLoginConfirmation(
            UUID playerUuid,
            String playerName,
            String role,
            String confirmedAt,
            long expiresAtMillis,
            AtomicBoolean consumed
    ) {
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    public ApiModule(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.stateFile = new File(plugin.getDataFolder(), STATE_FILE_NAME);
    }

    public void setAnnouncementModule(AnnouncementModule module) {
        this.announcementModule = module;
    }

    public void start(int port) {
        listenPort = port;

        loadState();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        RegisteredServiceProvider<LuckPerms> luckPermsProvider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (luckPermsProvider != null) {
            luckPerms = luckPermsProvider.getProvider();
        } else {
            plugin.getLogger().warning("LuckPerms not found; member resolver will only resolve online players.");
        }
        bootstrapOnlinePlayers();

        try {
            InetSocketAddress address = new InetSocketAddress(InetAddress.getByName(LOCAL_BIND_ADDRESS), port);
            server = HttpServer.create(address, 0);

            createLocalContext("/api/players", "GET", this::handlePlayersOnline);
            createLocalContext("/api/announcements", "GET", this::handleAnnouncements);
            createLocalContext("/api/bans", "GET", this::handleBans);
            createLocalContext("/api/members/resolve", "GET", this::handleMemberResolve);
            createAuthChallengeContext();

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
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(Commands.literal("mikapi")
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
                        .build(), "Show Mik API status");

            event.registrar().register(Commands.literal("weblogin")
                    .then(Commands.argument("code", StringArgumentType.word())
                            .executes(ctx -> {
                                CommandSender sender = ctx.getSource().getSender();
                                String code = StringArgumentType.getString(ctx, "code");
                                confirmWebLogin(sender, code);
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), languageService.t(Language.DEFAULT, Message.WEBLOGIN_COMMAND_DESCRIPTION));
        });
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

    private void handleMemberResolve(HttpExchange exchange) throws IOException {
        String name = normalizePlayerName(queryParam(exchange, "name"));

        if (name.isEmpty()) {
            sendJson(exchange, 400, bytes("{\"error\":\"invalid_name\"}"), "no-store");
            return;
        }

        byte[] body = memberResolveJson(name);
        sendJson(exchange, body == NOT_FOUND_BODY ? 404 : 200, body, "no-store");
    }

    private void confirmWebLogin(CommandSender sender, String rawCode) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
            return;
        }

        String code = normalizeWebLoginCode(rawCode);

        if (!WEB_LOGIN_CODE_PATTERN.matcher(code).matches()) {
            player.sendMessage(Component.text(languageService.t(player, Message.WEBLOGIN_INVALID_CODE), NamedTextColor.RED));
            return;
        }

        if (!canUseWebLogin(player)) {
            player.sendMessage(Component.text(languageService.t(player, Message.WEBLOGIN_MEMBER_REQUIRED), NamedTextColor.RED));
            return;
        }

        cleanupExpiredWebLoginConfirmations();

        WebLoginConfirmation confirmation = new WebLoginConfirmation(
                player.getUniqueId(),
                player.getName(),
                playerRole(player),
                formatIso(Instant.now()),
                System.currentTimeMillis() + WEB_LOGIN_CONFIRMATION_MILLIS,
                new AtomicBoolean(false));

        webLoginConfirmations.put(code, confirmation);
        player.sendMessage(Component.text(languageService.t(player, Message.WEBLOGIN_CONFIRMED), NamedTextColor.GREEN));
    }

    private boolean canUseWebLogin(Player player) {
        return player.hasPermission("group.member")
                || player.hasPermission("group.helper")
                || player.hasPermission("group.manager");
    }

    private String normalizeWebLoginCode(String code) {
        return code == null ? "" : code.trim();
    }

    private String playerRole(Player player) {
        if (player.hasPermission("group." + Mik.GROUP_MANAGER)) {
            return Mik.GROUP_MANAGER;
        }
        if (player.hasPermission("group." + Mik.GROUP_HELPER)) {
            return Mik.GROUP_HELPER;
        }
        if (player.hasPermission("group." + Mik.GROUP_MEMBER)) {
            return Mik.GROUP_MEMBER;
        }

        return "";
    }

    private byte[] memberResolveJson(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            String role = playerRole(online);
            return role.isEmpty() ? NOT_FOUND_BODY : memberJson(online.getUniqueId(), online.getName(), role);
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore()) {
            return NOT_FOUND_BODY;
        }

        String role = luckPermsRole(offline.getUniqueId());
        if (role.isEmpty()) {
            return NOT_FOUND_BODY;
        }

        String resolvedName = offline.getName() != null ? offline.getName() : name;
        return memberJson(offline.getUniqueId(), resolvedName, role);
    }

    private String luckPermsRole(UUID uuid) {
        if (luckPerms == null) {
            return "";
        }

        User user = luckPerms.getUserManager().getUser(uuid);
        if (user == null) {
            try {
                user = luckPerms.getUserManager().loadUser(uuid).get(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "";
            } catch (ExecutionException | TimeoutException e) {
                return "";
            }
        }

        var permissions = user.getCachedData().getPermissionData();
        if (permissions.checkPermission("group." + Mik.GROUP_MANAGER).asBoolean()) {
            return Mik.GROUP_MANAGER;
        }
        if (permissions.checkPermission("group." + Mik.GROUP_HELPER).asBoolean()) {
            return Mik.GROUP_HELPER;
        }
        if (permissions.checkPermission("group." + Mik.GROUP_MEMBER).asBoolean()) {
            return Mik.GROUP_MEMBER;
        }
        return "";
    }

    private byte[] memberJson(UUID uuid, String name, String role) {
        return bytes("{\"member\":{"
                + "\"uuid\":\"" + uuid + "\","
                + "\"name\":\"" + escapeJson(name) + "\","
                + "\"role\":\"" + escapeJson(role) + "\""
                + "}}");
    }

    private void createAuthChallengeContext() {
        server.createContext("/api/auth/challenges", exchange -> {
            try {
                if (!isLocalRequest(exchange)) {
                    drop(exchange);
                    return;
                }

                handleAuthChallenge(exchange);
            } catch (Exception ignored) {
                sendJson(exchange, 500, INTERNAL_ERROR_BODY, "no-store");
            }
        });
    }

    private void handleAuthChallenge(HttpExchange exchange) throws IOException {
        String prefix = "/api/auth/challenges/";
        String path = exchange.getRequestURI().getPath();

        if (!path.startsWith(prefix)) {
            sendJson(exchange, 404, NOT_FOUND_BODY, "no-store");
            return;
        }

        String suffix = path.substring(prefix.length());
        boolean consume = suffix.endsWith("/consume");
        String code = consume ? suffix.substring(0, suffix.length() - "/consume".length()) : suffix;

        if (code.contains("/") || !WEB_LOGIN_CODE_PATTERN.matcher(code).matches()) {
            sendJson(exchange, 404, NOT_FOUND_BODY, "no-store");
            return;
        }

        if (consume) {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendJson(exchange, 405, METHOD_NOT_ALLOWED_BODY, "no-store");
                return;
            }

            sendJson(exchange, 200, webLoginConsumeJson(code), "no-store");
            return;
        }

        if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            sendJson(exchange, 405, METHOD_NOT_ALLOWED_BODY, "no-store");
            return;
        }

        sendJson(exchange, 200, webLoginStatusJson(code), "no-store");
    }

    private byte[] webLoginStatusJson(String code) {
        WebLoginConfirmation confirmation = readWebLoginConfirmation(code);

        if (confirmation == null) {
            return bytes("{\"status\":\"not_found\"}");
        }

        if (confirmation.consumed().get()) {
            return bytes("{\"status\":\"consumed\"}");
        }

        return bytes(confirmedWebLoginJson(confirmation));
    }

    private byte[] webLoginConsumeJson(String code) {
        WebLoginConfirmation confirmation = readWebLoginConfirmation(code);

        if (confirmation == null) {
            return bytes("{\"status\":\"not_found\"}");
        }

        if (!confirmation.consumed().compareAndSet(false, true)) {
            return bytes("{\"status\":\"consumed\"}");
        }

        webLoginConfirmations.remove(code);
        return bytes(confirmedWebLoginJson(confirmation));
    }

    private WebLoginConfirmation readWebLoginConfirmation(String code) {
        WebLoginConfirmation confirmation = webLoginConfirmations.get(code);

        if (confirmation == null) {
            return null;
        }

        if (confirmation.expiresAtMillis() <= System.currentTimeMillis()) {
            webLoginConfirmations.remove(code);
            return null;
        }

        return confirmation;
    }

    private String confirmedWebLoginJson(WebLoginConfirmation confirmation) {
        return "{\"status\":\"confirmed\",\"player\":{"
                + "\"uuid\":\"" + confirmation.playerUuid() + "\","
                + "\"name\":\"" + escapeJson(confirmation.playerName()) + "\","
                + "\"role\":\"" + escapeJson(confirmation.role()) + "\""
                + "},\"confirmedAt\":\"" + confirmation.confirmedAt() + "\"}";
    }

    private void cleanupExpiredWebLoginConfirmations() {
        long now = System.currentTimeMillis();
        webLoginConfirmations.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
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

    private String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return "";
        }

        String prefix = key + "=";
        for (String part : query.split("&")) {
            if (part.startsWith(prefix)) {
                return URLDecoder.decode(part.substring(prefix.length()), StandardCharsets.UTF_8);
            }
        }
        return "";
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

    private String normalizePlayerName(String name) {
        String normalized = name == null ? "" : name.trim();
        return normalized.matches("^[a-zA-Z0-9_]{3,16}$") ? normalized : "";
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
        webLoginConfirmations.clear();
    }
}
