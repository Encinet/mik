package org.encinet.mik.feature.community.chat.web;

import org.encinet.mik.shared.i18n.Language;

import com.google.gson.*;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.bridge.ChatMessagePublisher;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.runtime.ChatSendLedger;
import org.encinet.mik.feature.community.chat.history.ChatHistoryStore;
import org.encinet.mik.feature.community.chat.bridge.ServerChatPublisher;
import org.encinet.mik.feature.community.chat.model.ChatServerMessage;
import org.encinet.mik.feature.community.chat.model.ChatParticipant;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatReferences;
import org.encinet.mik.feature.community.chat.model.ChatConversation;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatPlaceholder;
import org.encinet.mik.feature.community.chat.mention.PlayerMentions;
import org.encinet.mik.feature.player.security.WebAccountPolicy;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.shared.role.RolePermissions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

/** Owns ephemeral chat connections and routing. All business state belongs to the main thread. */
public final class WebChatModule implements WebChatSocketServer.Events, ChatParticipants, ChatMessagePublisher, ServerChatPublisher {
    public record ControlResult(int status, String error, long lastActivityAt) {
        public ControlResult(int status, String error) { this(status, error, 0); }
    }
    private static final Gson JSON = new Gson();
    private static final int MAX_MESSAGE_LENGTH = 256;
    private final Plugin plugin;
    private final LanguageService language;
    private final Function<Player, BooleanSupplier> gameAuthentication;
    private final Function<UUID, WebAccountPolicy> policyReader;
    private final Consumer<UUID> prepareIdentity;
    private final BiPredicate<UUID, String> allowed;
    private final Consumer<List<ChatParticipant>> tabUpdate;
    private final Function<ChatSubmission, ChatMessage> processMessage;
    private final Consumer<ChatDelivery> acceptedMessages;
    private final SecureRandom random = new SecureRandom();
    private final ChatSendLedger ledger;
    private final ConcurrentLinkedQueue<Runnable> inbox = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final ConcurrentLinkedQueue<Runnable> updates = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queuedUpdates = new AtomicInteger();
    private final Map<Channel, Connection> connections = new HashMap<>();
    private final ConcurrentHashMap<String, Connection> byId = new ConcurrentHashMap<>();
    private final Map<String, Login> logins = new HashMap<>();
    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<String, Long> revoked = new HashMap<>();
    private final ConcurrentHashMap<UUID, AtomicLong> revisions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> authorizations = new ConcurrentHashMap<>();
    private final Set<UUID> hidden = new HashSet<>();
    private final Map<UUID, String> announced = new HashMap<>();
    private WebChatSocketServer socket;
    private ThreadPoolExecutor policies;
    private ChatHistoryStore history;
    private BukkitTask task;
    private volatile boolean running;
    private long lastMaintenance;
    private long lastHistoryMaintenance;
    private record SendKey(UUID player, String request) { }
    private record PendingSend(String fingerprint, Set<Connection> waiters) {
        PendingSend(String fingerprint, Connection connection) {
            this(fingerprint, ConcurrentHashMap.newKeySet()); waiters.add(connection);
        }
    }
    private final Map<SendKey, PendingSend> pendingSends = new ConcurrentHashMap<>();
    private record CompletedSend(String fingerprint, String messageId) { }
    private final LinkedHashMap<SendKey, CompletedSend> completedSends = new LinkedHashMap<>();
    private final Map<String, String> committedWeb = new HashMap<>();
    private long lastHistoryFailure;
    private volatile Map<UUID, ChatParticipant> peerSnapshot = Map.of();
    private volatile Map<UUID, Set<UUID>> visibilitySnapshot = Map.of();
    private volatile Set<UUID> staffSnapshot = Set.of();
    private volatile Set<UUID> gameStaffSnapshot = Set.of();
    private int maxConnections;
    private int maxPerPlayer;

    private static final class Connection {
        final Channel channel;
        final String id, address;
        final long openedAt;
        Login login;
        PendingAuthorization pending;
        String clientPublicKey;
        long lastPong, lastPing;
        String lastPresence = "";
        boolean historyLoading;
        long lastHistoryRequest;
        Connection(Channel channel, String id, String address, long now) {
            this.channel = channel; this.id = id; this.address = address;
            this.openedAt = now; this.lastPong = now;
        }
    }
    private record PendingAuthorization(WebChatAuthorization authority, String name, WebAccountPolicy policy,
            long revision, long startedAt) { }
    private static final class Login {
        final String ref;
        final UUID player;
        final long authenticatedAt;
        long checkedAt, expiresAt, renewRequestedAt;
        long lastActivityAt;
        String renewalConnection = "";
        Login(String ref, UUID player, long authenticatedAt) {
            this.ref = ref; this.player = player; this.authenticatedAt = authenticatedAt;
        }
    }
    private static final class Account {
        String name;
        WebAccountPolicy policy;
        final AtomicBoolean loading = new AtomicBoolean();
        long loadedAt;
        long approvedRevision;
        Account(String name, WebAccountPolicy policy) { this.name = name; this.policy = policy; }
    }

    public WebChatModule(Plugin plugin, LanguageService language,
            Function<Player, BooleanSupplier> gameAuthentication,
            Function<UUID, WebAccountPolicy> policyReader, Consumer<UUID> prepareIdentity, BiPredicate<UUID, String> allowed, Consumer<List<ChatParticipant>> tabUpdate,
            Function<ChatSubmission, ChatMessage> processMessage, Consumer<ChatDelivery> acceptedMessages, ChatSendLedger ledger) {
        this.plugin = plugin; this.language = language; this.gameAuthentication = gameAuthentication;
        this.policyReader = policyReader; this.prepareIdentity = prepareIdentity; this.allowed = allowed; this.tabUpdate = tabUpdate;
        this.processMessage = processMessage; this.acceptedMessages = acceptedMessages; this.ledger = ledger;
    }

    public void enable() {
        maxPerPlayer = Math.clamp(plugin.getConfig().getInt("web-chat.max-connections-per-player", 8), 1, 32);
        maxConnections = Math.clamp(plugin.getConfig().getInt("web-chat.max-connections", 32), 1, 256);
        policies = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(64),
                runnable -> { Thread thread = new Thread(runnable, "mik-chat-policy"); thread.setDaemon(true); return thread; });
        history = new ChatHistoryStore(plugin.getDataFolder().toPath().resolve("chat-history.db"));
        observeHistory(history.maintain());
        try {
            var encryption = WebChatEncryption.load(plugin.getDataFolder().toPath().resolve("web-chat.key"));
            socket = new WebChatSocketServer(this, "https://mcmik.top", maxConnections, encryption);
            running = true;
            socket.start(plugin.getConfig().getInt("web-chat.port", 35354));
            plugin.getLogger().info("Web chat public key: " + encryption.publicKey());
        }
        catch (Exception error) { disable(); throw new IllegalStateException("Could not start loopback web chat", error); }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void disable() {
        running = false;
        if (task != null) task.cancel();
        if (socket != null) socket.close();
        if (policies != null) policies.shutdownNow();
        if (history != null) {
            history.close();
            try { history.stopped().get(5, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                plugin.getLogger().warning("Interrupted while draining chat history");
            } catch (ExecutionException | TimeoutException failure) {
                plugin.getLogger().warning("Could not finish draining chat history: " + failure.getClass().getSimpleName());
            }
            history = null;
        }
        inbox.clear(); queued.set(0); updates.clear(); queuedUpdates.set(0);
        pendingSends.clear(); committedWeb.clear(); completedSends.clear();
        connections.clear(); byId.clear(); logins.clear(); accounts.clear(); revoked.clear();
        hidden.clear(); announced.clear(); revisions.clear(); authorizations.clear();
        peerSnapshot = Map.of(); visibilitySnapshot = Map.of(); staffSnapshot = Set.of(); gameStaffSnapshot = Set.of();
        tabUpdate.accept(List.of());
    }

    private boolean enqueueUpdate(Runnable action) { return queue(updates, queuedUpdates, 512, action); }
    private boolean enqueue(Runnable action) { return queue(inbox, queued, 128, action); }
    private boolean queue(ConcurrentLinkedQueue<Runnable> target, AtomicInteger size, int capacity, Runnable action) {
        if (!running) return false;
        if (size.incrementAndGet() > capacity) { size.decrementAndGet(); return false; }
        target.add(action); return true;
    }

    @Override public void opened(Channel channel, String address) {
        if (!enqueueUpdate(() -> {
            long now = System.currentTimeMillis();
            long pending = connections.values().stream().filter(c -> c.login == null && c.pending == null).count();
            long sameIp = connections.values().stream().filter(c -> c.login == null && c.pending == null && c.address.equals(address)).count();
            if (!channel.isActive() || pending >= 16 || sameIp >= 4) { channel.close(); return; }
            byte[] bytes = new byte[24]; random.nextBytes(bytes);
            String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            Connection connection = new Connection(channel, id, address, now);
            connections.put(channel, connection); byId.put(id, connection);
            send(connection, Map.of("type", "hello", "connectionId", id));
        })) channel.close();
    }

    @Override public void message(Channel channel, String text) {
        if (!enqueue(() -> receive(channel, text))) channel.close();
    }
    @Override public void ready(Channel channel) {
        if (!enqueueUpdate(() -> {
            Connection c = connections.get(channel);
            if (c == null) { channel.close(); return; }
            PendingAuthorization pending = c.pending;
            if (pending == null || !channel.isActive() || !validAuthorization(pending)) {
                close(c, "chat_forbidden"); return;
            }
            applyAuthorization(c, pending);
        })) channel.close();
    }
    @Override public void closed(Channel channel) { enqueueUpdate(() -> remove(channel)); }
    @Override public void pong(Channel channel) {
        if (!enqueueUpdate(() -> { Connection c = connections.get(channel); if (c != null) c.lastPong = System.currentTimeMillis(); })) channel.close();
    }

    /** Called exclusively by the private loopback API, never by the public WebSocket listener. */
    public CompletableFuture<ControlResult> authorize(JsonObject grant) {
        CompletableFuture<ControlResult> result = new CompletableFuture<>();
        UUID authorizingPlayer = null;
        try {
            WebChatAuthorization authority = WebChatAuthorization.read(grant, System.currentTimeMillis());
            UUID player = authority.playerUuid();
            String ref = authority.sessionRef(), id = authority.connectionId(), name = authority.playerName();
            long authenticated = authority.authenticatedAt();
            Connection pendingConnection = byId.get(id);
            if (pendingConnection == null || !pendingConnection.channel.isActive()) {
                result.complete(new ControlResult(409, "connection_unavailable")); return result;
            }
            authorizingPlayer = player;
            authorizations.merge(player, 1, Integer::sum);
            long revisionBefore = revisions.computeIfAbsent(player, _ -> new AtomicLong()).get();
            // Password/LuckPerms lookups may block; this method is called off the game thread.
            prepareIdentity.accept(player);
            WebAccountPolicy initialPolicy = policyReader.apply(player);
            long revision = revisions.get(player).get();
            WebAccountPolicy policy = revision == revisionBefore ? initialPolicy : policyReader.apply(player);
            if (!enqueueUpdate(() -> {
                try {
                if (result.isCancelled()) return;
                Connection c = byId.get(id);
                long now = System.currentTimeMillis();
                if (c == null || !c.channel.isActive() || (c.login == null && c.pending == null && now - c.openedAt >= 10_000)
                        || (c.pending != null && now - c.pending.startedAt() >= WebChatCipher.EXCHANGE_TIMEOUT.toMillis())) {
                    result.complete(new ControlResult(409, "connection_unavailable")); return;
                }
                Player gamePlayer = Bukkit.getPlayer(player);
                String knownName = gamePlayer == null ? Bukkit.getOfflinePlayer(player).getName() : gamePlayer.getName();
                String currentName = knownName != null && !knownName.isBlank() && knownName.length() <= 64 && knownName.codePoints().noneMatch(Character::isISOControl) ? knownName : name;
                PendingAuthorization approval = new PendingAuthorization(authority, currentName, policy, revision,
                        c.pending == null ? now : c.pending.startedAt());
                if (!validAuthorization(approval)) {
                    result.complete(new ControlResult(403, "chat_forbidden")); return;
                }
                if ((c.clientPublicKey != null && !c.clientPublicKey.equals(authority.clientPublicKey()))
                        || (c.login != null && (!c.login.ref.equals(ref) || !c.login.player.equals(player) || c.login.authenticatedAt != authenticated))
                        || (c.pending != null && (!c.pending.authority().sessionRef().equals(ref)
                            || !c.pending.authority().playerUuid().equals(player) || c.pending.authority().authenticatedAt() != authenticated))) {
                    c.channel.close(); result.complete(new ControlResult(409, "session_changed")); return;
                }
                if (c.login == null && c.pending == null && connections.values().stream().filter(other -> connectionPlayer(other) != null && other.channel.isActive()).count() >= maxConnections) {
                    result.complete(new ControlResult(429, "chat_capacity")); c.channel.close(); return;
                }
                if (c.login == null && c.pending == null && connections.values().stream().filter(other -> player.equals(connectionPlayer(other)) && other.channel.isActive()).count() >= maxPerPlayer) {
                    result.complete(new ControlResult(429, "connection_limit")); c.channel.close(); return;
                }
                Login login = logins.get(ref);
                if (login != null && (!login.player.equals(player) || login.authenticatedAt != authenticated)) {
                    c.channel.close(); result.complete(new ControlResult(403, "chat_forbidden")); return;
                }
                if (c.login != null) applyAuthorization(c, approval);
                else {
                    boolean initial = c.pending == null;
                    c.clientPublicKey = authority.clientPublicKey();
                    c.pending = approval;
                    if (initial) WebChatSocketServer.authorize(c.channel, c.clientPublicKey);
                }
                result.complete(new ControlResult(200, "", c.login == null ? 0 : c.login.lastActivityAt));
                } finally { releaseAuthorization(player); }
            })) { releaseAuthorization(player); result.complete(new ControlResult(503, "chat_busy")); }
        } catch (RuntimeException error) {
            if (authorizingPlayer != null) releaseAuthorization(authorizingPlayer);
            result.complete(new ControlResult(503, "chat_unavailable"));
        }
        return result;
    }

    private boolean validAuthorization(PendingAuthorization approval) {
        WebChatAuthorization authority = approval.authority();
        AtomicLong revision = revisions.get(authority.playerUuid());
        return authority.expiresAt() > System.currentTimeMillis() && !revoked.containsKey(authority.sessionRef())
                && eligible(approval.policy()) && authority.authenticatedAt() > approval.policy().credentialsChangedAt()
                && allowed.test(authority.playerUuid(), approval.name()) && revision != null && revision.get() == approval.revision();
    }

    private void applyAuthorization(Connection c, PendingAuthorization approval) {
        WebChatAuthorization authority = approval.authority();
        UUID player = authority.playerUuid();
        Login login = logins.computeIfAbsent(authority.sessionRef(), _ -> new Login(authority.sessionRef(), player, authority.authenticatedAt()));
        if (!login.player.equals(player) || login.authenticatedAt != authority.authenticatedAt()) {
            close(c, "chat_forbidden"); return;
        }
        boolean first = c.login == null;
        c.login = login; c.pending = null;
        login.checkedAt = Math.max(login.checkedAt, authority.checkedAt());
        login.expiresAt = Math.max(login.expiresAt, authority.expiresAt());
        login.renewRequestedAt = 0;
        Account account = accounts.computeIfAbsent(player, _ -> new Account(approval.name(), approval.policy()));
        account.name = approval.name(); account.policy = approval.policy(); account.loadedAt = System.currentTimeMillis();
        account.approvedRevision = approval.revision();
        for (Connection peer : connections.values()) if (peer.login == login) {
            send(peer, Map.of("type", "authorized", "playerUuid", player.toString(), "name", account.name,
                    "staff", staff(account.policy), "expiresAt", login.expiresAt, "history", history != null));
        }
        if (first) { presenceChanged(player); sendPresence(c); }
    }

    private static UUID connectionPlayer(Connection c) {
        return c.login != null ? c.login.player : c.pending == null ? null : c.pending.authority().playerUuid();
    }

    private static String connectionSession(Connection c) {
        return c.login != null ? c.login.ref : c.pending == null ? "" : c.pending.authority().sessionRef();
    }

    private void releaseAuthorization(UUID id) {
        authorizations.computeIfPresent(id, (_, count) -> count <= 1 ? null : count - 1);
    }

    public CompletableFuture<ControlResult> revoke(List<String> refs) {
        CompletableFuture<ControlResult> result = new CompletableFuture<>();
        if (!enqueueUpdate(() -> {
            long until = System.currentTimeMillis() + 120_000;
            for (String ref : refs) {
                if (!ref.matches("[A-Za-z0-9_-]{43}")) continue;
                revoked.put(ref, until);
                for (Connection c : List.copyOf(connections.values())) if (connectionSession(c).equals(ref)) close(c, "session_revoked");
            }
            result.complete(new ControlResult(200, ""));
        })) result.complete(new ControlResult(503, "chat_busy"));
        return result;
    }

    public void invalidatePlayer(UUID player) {
        revisions.computeIfAbsent(player, _ -> new AtomicLong()).incrementAndGet();
        enqueueUpdate(() -> {
            for (Connection c : List.copyOf(connections.values())) if (player.equals(connectionPlayer(c))) {
                revoked.put(connectionSession(c), System.currentTimeMillis() + 120_000); close(c, "credentials_changed");
            }
        });
    }

    public void invalidatePolicy(UUID player) {
        AtomicLong revision = revisions.get(player);
        if (revision == null) return;
        revision.incrementAndGet();
        enqueueUpdate(() -> { Account account = accounts.get(player); if (account != null) { account.policy = null; refreshPolicy(player, account); } });
    }

    private void receive(Channel channel, String text) {
        Connection c = connections.get(channel);
        if (c == null || !active(c)) { if (c != null) close(c, "authorization_expired"); else channel.close(); return; }
        String request = "";
        try {
            Account actor = accounts.get(c.login.player);
            long now = System.currentTimeMillis();
            JsonObject body = readMessage(text);
            request = string(body, "requestId");
            if (!request.matches("[A-Za-z0-9_-]{16,64}")) throw new IllegalArgumentException();
            if (string(body, "type").equals("history")) { receiveHistory(c, body, request); return; }
            if (!string(body, "type").equals("send")
                    || !Set.of("type", "requestId", "channel", "targetUuid", "text").containsAll(body.keySet())) throw new IllegalArgumentException();
            ChatChannel kind = ChatChannel.valueOf(string(body, "channel").toUpperCase(Locale.ROOT));
            String value = string(body, "text").strip();
            if (value.isEmpty() || value.codePointCount(0, value.length()) > MAX_MESSAGE_LENGTH
                    || value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029)) throw new IllegalArgumentException();
            UUID target = kind == ChatChannel.PRIVATE ? UUID.fromString(string(body, "targetUuid")) : null;
            if (kind != ChatChannel.PRIVATE && body.has("targetUuid")) throw new IllegalArgumentException();
            String fingerprint = kind + ":" + target + ":" + value;
            String digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(fingerprint.getBytes(StandardCharsets.UTF_8)));
            SendKey sendKey = new SendKey(c.login.player, request);
            CompletedSend completed = completedSends.get(sendKey);
            if (completed != null) {
                if (!completed.fingerprint().equals(digest)) { error(c, request, "request_conflict"); return; }
                send(c, Map.of("type", "accepted", "requestId", request, "messageId", completed.messageId())); return;
            }
            PendingSend previous = pendingSends.get(sendKey);
            if (previous != null) {
                if (!previous.fingerprint().equals(digest)) { error(c, request, "request_conflict"); return; }
                previous.waiters().add(c); return;
            }
            if (history == null) { error(c, request, "chat_unavailable"); return; }
            if (!ledger.allowMessage(c.login.player)) { error(c, request, "rate_limited"); return; }
            if (kind == ChatChannel.STAFF && !staff(actor.policy)) { error(c, request, "staff_required"); return; }
            ChatParticipant peer = target == null ? null : peer(c.login.player, target);
            if (target != null && (target.equals(c.login.player) || peer == null)) { error(c, request, "recipient_unavailable"); return; }
            PlayerMentions mentions = PlayerMentions.of(channelPeers(c.login.player, kind, target).stream()
                    .filter(participant -> !participant.playerUuid().equals(c.login.player))
                    .map(ChatParticipant::mentionTarget).toList());
            ChatConversation conversation = switch (kind) {
                case PUBLIC -> ChatConversation.minecraftPublic();
                case STAFF -> ChatConversation.minecraftStaff();
                case PRIVATE -> ChatConversation.minecraftPrivate(c.login.player, target);
            };
            ChatSubmission submission = new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Web(c.login.player),
                    ChatSender.minecraft(c.login.player, actor.name), conversation, value, ChatReferences.empty(),
                    Instant.ofEpochMilli(now), ChatProcessingContext.playerText(mentions));
            ChatMessage accepted = processMessage.apply(submission);
            ChatDelivery delivery = ChatDelivery.managed(accepted,
                    peer == null ? null : ChatSender.minecraft(peer.playerUuid(), peer.name()), request);
            var snapshot = WebChatHistory.snapshot(delivery);
            var pending = new PendingSend(digest, c);
            pendingSends.put(sendKey, pending);
            var receipt = new ChatHistoryStore.Request(c.login.player, request, digest);
            String acceptedRequest = request;
            history.append(() -> WebChatHistory.user(snapshot), receipt).whenComplete((saved, failure) -> {
                if (!enqueueUpdate(() -> {
                    if (pendingSends.remove(sendKey) != pending) return;
                    if (failure != null) {
                        Throwable reason = failure;
                        while (reason.getCause() != null) reason = reason.getCause();
                        String code = "request_conflict".equals(reason.getMessage()) ? "request_conflict" : "chat_unavailable";
                        reportHistoryFailure(failure);
                        for (Connection waiter : pending.waiters()) if (active(waiter)) error(waiter, acceptedRequest, code);
                        return;
                    }
                    completedSends.put(sendKey, new CompletedSend(digest, saved.id()));
                    while (completedSends.size() > 512) completedSends.pollFirstEntry();
                    if (saved.inserted()) {
                        committedWeb.put(saved.id(), saved.order());
                        try { acceptedMessages.accept(delivery); }
                        finally { committedWeb.remove(saved.id()); }
                    }
                    for (Connection waiter : pending.waiters()) if (active(waiter)) {
                        waiter.login.lastActivityAt = System.currentTimeMillis();
                        send(waiter, Map.of("type", "accepted", "requestId", acceptedRequest, "messageId", saved.id()));
                    }
                })) {
                    pendingSends.remove(sendKey, pending);
                    for (Connection waiter : pending.waiters()) waiter.channel.close();
                }
            });
        } catch (Exception invalid) { error(c, request, "invalid_message"); }
    }

    private void receiveHistory(Connection c, JsonObject body, String request) {
        updateHidden();
        if (!Set.of("type", "requestId", "channel", "targetUuid", "since", "page").containsAll(body.keySet()))
            throw new IllegalArgumentException();
        boolean compression = WebChatSocketServer.compression(c.channel);
        ChatChannel channel = ChatChannel.valueOf(string(body, "channel").toUpperCase(Locale.ROOT));
        UUID target = body.has("targetUuid") ? UUID.fromString(string(body, "targetUuid")) : null;
        String since = body.has("since") ? string(body, "since") : null;
        String continuation = body.has("page") ? string(body, "page") : null;
        if ((since != null && !since.matches("[A-Za-z0-9_-]{91}")) || (continuation != null && !continuation.matches("[A-Za-z0-9_-]{91}")))
            throw new IllegalArgumentException();
        boolean staff = webStaff(c.login.player);
        if (channel == ChatChannel.STAFF && !staff) { error(c, request, "staff_required"); return; }
        if (history == null) { error(c, request, "history_unavailable"); return; }
        long now = System.currentTimeMillis();
        if (c.historyLoading || now - c.lastHistoryRequest < 200) { error(c, request, "rate_limited"); return; }
        c.lastHistoryRequest = now;
        WebChatHistory.Viewer viewer;
        try { viewer = historyViewer(c.login.player); }
        catch (RuntimeException failure) { error(c, request, "history_unavailable"); return; }
        c.historyLoading = true;
        var query = new ChatHistoryStore.SyncQuery(c.login.player, staff, channel, target, since, continuation, viewer.hidden());
        history.sync(query, page -> WebChatPacket.history(WebChatHistory.responseParts(page, channel, request, viewer), compression))
                .whenComplete((response, failure) -> {
            if (!enqueueUpdate(() -> {
                c.historyLoading = false;
                if (connections.get(c.channel) != c) return;
                if (!active(c)) { close(c, "authorization_expired"); return; }
                if (channel == ChatChannel.STAFF && !webStaff(c.login.player)) { error(c, request, "staff_required"); return; }
                if (failure != null) { error(c, request, "history_unavailable"); return; }
                updateHidden();
                if (!viewer.hidden().equals(historyHidden(c.login.player))) { error(c, request, "history_unavailable"); return; }
                WebChatSocketServer.send(c.channel, response);
            })) c.channel.close();
        });
    }

    private void observeHistory(CompletableFuture<?> operation) {
        operation.whenComplete((_, failure) -> { if (failure != null) reportHistoryFailure(failure); });
    }

    private synchronized void reportHistoryFailure(Throwable failure) {
        long now = System.currentTimeMillis();
        if (now - lastHistoryFailure < 30_000) return;
        lastHistoryFailure = now;
        while (failure.getCause() != null) failure = failure.getCause();
        var health = history == null ? null : history.health();
        plugin.getLogger().warning("Chat history unavailable: " + failure.getClass().getSimpleName() + ": " + failure.getMessage()
                + (health == null ? "" : " (queued writes=" + health.queuedWrites() + ", pending reads=" + health.pendingReads()
                + ", failed writes=" + health.failedWrites() + ", rejected reads=" + health.rejectedReads()
                + ", last commit=" + health.lastCommitAt() + ")"));
    }

    private WebChatHistory.Viewer historyViewer(UUID viewer) {
        var viewerLanguage = language.preferredLanguage(viewer).orElse(Language.DEFAULT);
        Map<Message, String> descriptions = new EnumMap<>(Message.class);
        for (var placeholder : ChatPlaceholder.values())
            descriptions.put(placeholder.message(), language.t(viewerLanguage, placeholder.message()));
        return new WebChatHistory.Viewer(viewer, historyHidden(viewer), descriptions);
    }

    private Set<UUID> historyHidden(UUID viewer) {
        Set<UUID> denied = new HashSet<>(hidden);
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (visible(viewer, id)) denied.remove(id); else denied.add(id);
        }
        denied.remove(viewer);
        return Set.copyOf(denied);
    }

    /** Captures on the main thread; durable assignment completes before any web publication. */
    @Override public void publish(ChatDelivery delivery) {
        if (!running) return;
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Web chat delivery requires the primary thread");
        String committed = committedWeb.get(delivery.message().submission().id().value());
        if (committed != null) { distribute(delivery, committed); return; }
        if (history == null) return;
        var snapshot = WebChatHistory.snapshot(delivery);
        persist(() -> WebChatHistory.user(snapshot), saved -> distribute(delivery, saved.order()));
    }

    private void persist(Supplier<ChatHistoryStore.Entry> snapshot, Consumer<ChatHistoryStore.Saved> publish) {
        if (history == null) return;
        history.append(snapshot).whenComplete((saved, failure) -> {
            if (failure != null) { reportHistoryFailure(failure); return; }
            if (saved.inserted()) enqueueUpdate(() -> { if (running) publish.accept(saved); });
        });
    }

    @Override public Set<UUID> gameRecipients(ChatAudience audience) {
        if (!Bukkit.isPrimaryThread()) {
            return peerSnapshot.values().stream().filter(ChatParticipant::game)
                    .filter(peer -> audience.includes(peer.playerUuid(), gameStaffSnapshot.contains(peer.playerUuid())))
                    .map(ChatParticipant::playerUuid).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        return Bukkit.getOnlinePlayers().stream().filter(this::gameEligible)
                .filter(player -> audience.includes(player.getUniqueId(),
                        audience.channel() == ChatChannel.STAFF && RolePermissions.canModerate(player)))
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void distribute(ChatDelivery delivery, String order) {
        ChatAudience audience = delivery.audience();
        WebChatMessage message = new WebChatMessage(delivery.message(), delivery.requestId(), delivery.target());
        Map<MessageVariant, String> frames = new HashMap<>();
        for (Connection c : connections.values()) if (active(c) && audience.includes(c.login.player, webStaff(c.login.player))) {
            boolean own = c.login.player.equals(message.senderUuid());
            boolean mentioned = !own && message.mentions(c.login.player);
            var viewerLanguage = language.preferredLanguage(c.login.player).orElse(Language.DEFAULT);
            String frame = frames.computeIfAbsent(new MessageVariant(viewerLanguage, own, mentioned), _ -> {
                JsonObject recipientPayload = message.payload(key -> language.t(viewerLanguage, key));
                stamp(recipientPayload, order);
                // Correlation IDs stay with the sender; mention status belongs to this viewer.
                if (!own) recipientPayload.remove("requestId");
                recipientPayload.addProperty("mentioned", mentioned);
                JsonObject event = new JsonObject(); event.addProperty("type", "message"); event.add("message", recipientPayload);
                return JSON.toJson(event);
            });
            WebChatSocketServer.send(c.channel, frame);
        }
    }

    private record MessageVariant(Language language, boolean own, boolean mentioned) { }

    @Override
    public void publish(ChatServerMessage message, ServerChatPublisher.Visibility visibility) {
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(message, "message");
        Runnable action = () -> {
            if (!running) return;
            updateHidden();
            distributeServerMessage(message, visibility);
        };
        if (Bukkit.isPrimaryThread()) action.run(); else enqueueUpdate(action);
    }

    private void distributeServerMessage(ChatServerMessage message, ServerChatPublisher.Visibility visibility) {
        if (history != null) {
            Set<UUID> candidates = new HashSet<>(message.participants().keySet());
            Bukkit.getOnlinePlayers().stream().filter(this::gameEligible).map(Player::getUniqueId).forEach(candidates::add);
            connections.values().stream().filter(this::active).map(c -> c.login.player).forEach(candidates::add);
            Set<UUID> viewers = candidates.stream().filter(viewer -> serverVisible(message, viewer, visibility))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            boolean offlineVisible = visibility.includesOffline()
                    && message.audience().participants().stream().allMatch(id -> visible(null, id))
                    && message.subject().map(subject -> visible(null, subject.playerUuid())).orElse(true);
            Optional<Set<UUID>> restriction = offlineVisible && viewers.size() == candidates.size()
                    ? Optional.empty() : Optional.of(viewers);
            var snapshot = WebChatHistory.snapshot(message);
            persist(() -> WebChatHistory.server(snapshot, restriction), saved -> deliverServerMessage(message, visibility, saved.order()));
        }
    }

    private void deliverServerMessage(ChatServerMessage message, ServerChatPublisher.Visibility visibility, String order) {
        updateHidden();
        ChatAudience audience = message.audience();
        Map<UUID, String> frames = new HashMap<>();
        for (Connection c : connections.values()) {
            if (!active(c)) continue;
            UUID viewer = c.login.player;
            if (!audience.includes(viewer, webStaff(viewer))) continue;
            if (!serverVisible(message, viewer, visibility)) continue;
            UUID frameOwner = message.conversation().channel() == ChatChannel.PRIVATE ? viewer : null;
            String frame = frames.computeIfAbsent(frameOwner, _ -> {
                JsonObject payload = WebChatServerMessage.payload(message, viewer);
                stamp(payload, order);
                JsonObject event = new JsonObject();
                event.addProperty("type", "message");
                event.add("message", payload);
                return JSON.toJson(event);
            });
            WebChatSocketServer.send(c.channel, frame);
        }
    }

    private boolean serverVisible(ChatServerMessage message, UUID viewer, ServerChatPublisher.Visibility visibility) {
        return visibility.includes().test(viewer)
                && message.audience().participants().stream().allMatch(id -> visible(viewer, id))
                && message.subject().map(subject -> visible(viewer, subject.playerUuid())).orElse(true);
    }

    private static void stamp(JsonObject message, String order) { message.addProperty("order", order); }

    /** Visible online channel participants, shared by presence and both message pipelines. */
    @Override
    public List<ChatParticipant> channelPeers(UUID viewer, ChatChannel channel, UUID privateTarget) {
        if (channel == ChatChannel.STAFF && !staffPeer(viewer)) return List.of();
        Set<UUID> members = channel == ChatChannel.PRIVATE
                ? privateTarget == null ? Set.of(viewer) : Set.copyOf(List.of(viewer, privateTarget)) : Set.of();
        ChatAudience audience = new ChatAudience(channel, members);
        return peers(viewer).stream().filter(peer -> audience.includes(peer.playerUuid(),
                channel == ChatChannel.STAFF && staffPeer(peer.playerUuid()))).toList();
    }

    private boolean staffPeer(UUID id) {
        if (!Bukkit.isPrimaryThread()) return staffSnapshot.contains(id);
        Player game = Bukkit.getPlayer(id);
        Account account = accounts.get(id);
        return (game != null && gameEligible(game) && RolePermissions.canModerate(game))
                || (account != null && staff(account.policy) && webCount(id) > 0);
    }

    private boolean webStaff(UUID viewer) {
        Account account = accounts.get(viewer);
        return account != null && staff(account.policy);
    }

    @Override
    public ChatParticipant peer(UUID viewer, UUID target) {
        if (!Bukkit.isPrimaryThread()) {
            Set<UUID> visible = visibilitySnapshot.getOrDefault(viewer, Set.of());
            return target != null && visible.contains(target) ? peerSnapshot.get(target) : null;
        }
        if (target == null || !visible(viewer, target)) return null;
        Player game = Bukkit.getPlayer(target);
        boolean online = game != null && gameEligible(game);
        int web = webCount(target);
        if (!online && web == 0) return null;
        Account account = accounts.get(target);
        return new ChatParticipant(target, online ? game.getName() : account.name, online, web);
    }

    @Override
    public List<ChatParticipant> peers(UUID viewer) {
        if (!Bukkit.isPrimaryThread()) {
            Set<UUID> visible = visibilitySnapshot.getOrDefault(viewer, Set.of());
            return peerSnapshot.values().stream().filter(peer -> visible.contains(peer.playerUuid())).toList();
        }
        Set<UUID> ids = new HashSet<>(accounts.keySet());
        for (Player player : Bukkit.getOnlinePlayers()) ids.add(player.getUniqueId());
        return ids.stream().map(id -> peer(viewer, id)).filter(Objects::nonNull)
                .sorted(Comparator.comparing(ChatParticipant::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    private boolean visible(UUID viewer, UUID target) {
        if (target.equals(viewer)) return true;
        Player subject = Bukkit.getPlayer(target), observer = viewer == null ? null : Bukkit.getPlayer(viewer);
        if (subject != null && observer != null) return observer.canSee(subject);
        return !hidden.contains(target);
    }
    public boolean canSee(UUID viewer, UUID target) { return visible(viewer, target); }

    /** The event's subject remains authoritative even after Bukkit removes its UUID lookup. */
    public boolean canSee(UUID viewer, Player subject) {
        if (subject.getUniqueId().equals(viewer)) return true;
        Player observer = viewer == null ? null : Bukkit.getPlayer(viewer);
        return observer == null ? !concealed(subject) : observer.canSee(subject);
    }

    private boolean concealed(Player subject) {
        return !subject.isVisibleByDefault()
                || Bukkit.getOnlinePlayers().stream().anyMatch(viewer -> !viewer.getUniqueId().equals(subject.getUniqueId()) && !viewer.canSee(subject));
    }
    private boolean gameEligible(Player player) { return player.isOnline() && allowed.test(player.getUniqueId(), player.getName()) && gameAuthentication.apply(player).getAsBoolean(); }
    private int webCount(UUID player) { return (int) connections.values().stream().filter(c -> c.login != null && c.login.player.equals(player) && active(c)).count(); }
    private boolean active(Connection c) {
        Account account = c.login == null ? null : accounts.get(c.login.player);
        AtomicLong revision = c.login == null ? null : revisions.get(c.login.player);
        return c.channel.isActive() && c.login != null && c.login.expiresAt > System.currentTimeMillis()
                && account != null && revision != null && account.approvedRevision == revision.get()
                && allowed.test(c.login.player, account.name) && eligible(account.policy) && c.login.authenticatedAt > account.policy.credentialsChangedAt();
    }
    private static boolean eligible(WebAccountPolicy policy) { return policy != null && Set.of("default", "member", "moderator", "custodian").contains(policy.role()); }
    private static boolean staff(WebAccountPolicy policy) { return policy != null && Set.of("moderator", "custodian").contains(policy.role()); }

    private void drain(ConcurrentLinkedQueue<Runnable> source, AtomicInteger size) {
        for (int i = 0; i < 64; i++) {
            Runnable action = source.poll(); if (action == null) break; size.decrementAndGet();
            try { action.run(); } catch (RuntimeException error) { plugin.getLogger().warning("Web chat operation failed: " + error.getClass().getSimpleName()); }
        }
    }

    private void tick() {
        drain(updates, queuedUpdates);
        drain(inbox, queued);
        long now = System.currentTimeMillis();
        if (now - lastMaintenance < 1000) return;
        lastMaintenance = now;
        if (history != null && now - lastHistoryMaintenance >= 3_600_000) {
            lastHistoryMaintenance = now;
            observeHistory(history.maintain());
        }
        revoked.entrySet().removeIf(entry -> entry.getValue() <= now);
        for (Connection c : List.copyOf(connections.values())) {
            if (!c.channel.isActive()) { remove(c.channel); continue; }
            if (c.login == null) {
                if (c.pending == null ? now - c.openedAt >= 10_000
                        : now - c.pending.startedAt() >= WebChatCipher.EXCHANGE_TIMEOUT.toMillis() || !validAuthorization(c.pending))
                    close(c, "authorization_timeout");
                continue;
            }
            if (!allowed.test(c.login.player, accounts.get(c.login.player).name)) { close(c, "chat_forbidden"); continue; }
            if (c.login.expiresAt <= now) { close(c, "authorization_expired"); continue; }
            if (now - c.lastPong > 55_000) { close(c, "connection_timeout"); continue; }
            if (now - c.lastPing > 20_000) { c.lastPing = now; c.channel.writeAndFlush(new PingWebSocketFrame()); }
        }
        for (Login login : logins.values()) {
            if (now - login.checkedAt < 45_000 || now - login.renewRequestedAt < 5000) continue;
            List<Connection> candidates = connections.values().stream().filter(c -> c.login == login && c.channel.isActive())
                    .sorted(Comparator.comparing(c -> c.id)).toList();
            if (!candidates.isEmpty()) {
                int previous = -1;
                for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).id.equals(login.renewalConnection)) previous = i;
                Connection candidate = candidates.get((previous + 1) % candidates.size());
                login.renewalConnection = candidate.id; login.renewRequestedAt = now;
                send(candidate, Map.of("type", "reauthorize"));
            }
        }
        for (var entry : accounts.entrySet()) if (now - entry.getValue().loadedAt >= 5000) refreshPolicy(entry.getKey(), entry.getValue());
        updateHidden();
        Map<UUID, ChatParticipant> snapshot = new HashMap<>();
        Set<UUID> ids = new HashSet<>(accounts.keySet());
        for (Player player : Bukkit.getOnlinePlayers()) ids.add(player.getUniqueId());
        for (UUID id : ids) { ChatParticipant peer = peer(id, id); if (peer != null) snapshot.put(id, peer); }
        peerSnapshot = Map.copyOf(snapshot);
        staffSnapshot = snapshot.keySet().stream().filter(this::staffPeer).collect(java.util.stream.Collectors.toUnmodifiableSet());
        gameStaffSnapshot = Bukkit.getOnlinePlayers().stream().filter(this::gameEligible)
                .filter(RolePermissions::canModerate).map(Player::getUniqueId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Map<UUID, Set<UUID>> visibility = new HashMap<>();
        for (UUID viewer : snapshot.keySet()) visibility.put(viewer, snapshot.keySet().stream().filter(id -> visible(viewer, id)).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        visibilitySnapshot = Map.copyOf(visibility);
        tabUpdate.accept(snapshot.values().stream().filter(peer -> peer.webConnections() > 0).toList());
        for (Connection c : connections.values()) if (active(c)) sendPresence(c);
        accounts.entrySet().removeIf(entry -> webCount(entry.getKey()) == 0 && !announced.containsKey(entry.getKey())
                && connections.values().stream().noneMatch(c -> c.login != null && c.login.player.equals(entry.getKey())));
        for (UUID id : revisions.keySet()) revisions.computeIfPresent(id, (key, revision) ->
                !accounts.containsKey(key) && !authorizations.containsKey(key)
                        && connections.values().stream().noneMatch(c -> key.equals(connectionPlayer(c))) ? null : revision);
    }

    private void updateHidden() {
        for (Player subject : Bukkit.getOnlinePlayers()) {
            if (concealed(subject)) hidden.add(subject.getUniqueId()); else hidden.remove(subject.getUniqueId());
        }
        hidden.removeIf(id -> Bukkit.getPlayer(id) == null && webCount(id) == 0 && !announced.containsKey(id));
    }

    private void refreshPolicy(UUID id, Account account) {
        if (policies == null || policies.isShutdown() || !account.loading.compareAndSet(false, true)) return;
        long revision = revisions.computeIfAbsent(id, _ -> new AtomicLong()).get();
        try { policies.execute(() -> {
            WebAccountPolicy policy;
            try { prepareIdentity.accept(id); policy = policyReader.apply(id); } catch (RuntimeException error) { policy = null; }
            WebAccountPolicy loaded = policy;
            if (!enqueueUpdate(() -> {
                if (accounts.get(id) != account) return;
                account.loading.set(false);
                AtomicLong latest = revisions.get(id);
                if (latest == null || latest.get() != revision) { refreshPolicy(id, account); return; }
                String previousRole = account.policy == null ? "" : account.policy.role();
                account.policy = loaded; account.loadedAt = System.currentTimeMillis(); account.approvedRevision = revision;
                for (Connection c : List.copyOf(connections.values())) if (c.login != null && c.login.player.equals(id)) {
                    if (!eligible(loaded) || c.login.authenticatedAt <= loaded.credentialsChangedAt()) close(c, "chat_forbidden");
                    else if (!previousRole.equals(loaded.role())) send(c, Map.of("type", "permissions", "staff", staff(loaded)));
                }
            })) account.loading.set(false);
        }); } catch (RejectedExecutionException busy) { account.loading.set(false); account.policy = null; }
    }

    private void presenceChanged(UUID id) {
        boolean present = webCount(id) > 0;
        if (present == announced.containsKey(id)) return;
        updateHidden();
        String name = present ? accounts.get(id).name : announced.get(id);
        if (present) announced.put(id, name); else announced.remove(id);
        // Publish on the main thread before the next queued chat operation can run.
        for (Player player : Bukkit.getOnlinePlayers()) if (gameEligible(player) && visible(player.getUniqueId(), id)) {
            player.sendMessage(Component.text(language.t(player, present ? Message.WEBCHAT_JOINED : Message.WEBCHAT_LEFT, name), NamedTextColor.AQUA));
        }
        var subject = new ChatServerMessage.Subject(id, name);
        var body = new ChatServerMessage.Translation(present ? ChatServerMessage.Template.WEB_JOINED
                : ChatServerMessage.Template.WEB_LEFT, Map.of("name", name));
        distributeServerMessage(ChatServerMessage.publicMessage(body, subject, false), new ServerChatPublisher.Visibility(_ -> true, true));
    }
    private void remove(Channel channel) {
        Connection c = connections.remove(channel); if (c == null) return;
        byId.remove(c.id);
        if (c.login != null) {
            Login login = c.login;
            if (connections.values().stream().noneMatch(other -> other.login == login)) logins.remove(login.ref);
            presenceChanged(login.player);
        }
    }
    private void close(Connection c, String reason) { send(c, Map.of("type", "closed", "reason", reason)); c.channel.close(); remove(c.channel); }
    private void sendPresence(Connection c) {
        if (!active(c)) return;
        List<ChatParticipant> players = peers(c.login.player);
        List<UUID> staffPlayers = staff(accounts.get(c.login.player).policy)
                ? channelPeers(c.login.player, ChatChannel.STAFF, null).stream().map(ChatParticipant::playerUuid).toList()
                : List.of();
        String payload = JSON.toJson(Map.of("type", "presence", "players", players, "staffPlayers", staffPlayers));
        if (!payload.equals(c.lastPresence)) { c.lastPresence = payload; WebChatSocketServer.send(c.channel, payload); }
    }
    private void error(Connection c, String request, String error) { send(c, Map.of("type", "error", "requestId", request.matches("[A-Za-z0-9_-]{16,64}") ? request : "", "error", error)); }
    private static void send(Connection c, Object payload) { WebChatSocketServer.send(c.channel, JSON.toJson(payload)); }
    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return "";
        return value.getAsString();
    }
    /** A browser message is a flat object of strings. Reject nesting and duplicate keys before recursion. */
    static JsonObject readMessage(String input) throws java.io.IOException {
        try (var reader = new com.google.gson.stream.JsonReader(new java.io.StringReader(input))) {
            reader.setStrictness(Strictness.STRICT);
            JsonObject object = new JsonObject();
            reader.beginObject();
            while (reader.hasNext()) {
                if (object.size() >= 6) throw new IllegalArgumentException("too_many_fields");
                String key = reader.nextName();
                if (object.has(key) || reader.peek() != com.google.gson.stream.JsonToken.STRING) throw new IllegalArgumentException("invalid_field");
                object.addProperty(key, reader.nextString());
            }
            reader.endObject();
            if (reader.peek() != com.google.gson.stream.JsonToken.END_DOCUMENT) throw new IllegalArgumentException("trailing_data");
            return object;
        }
    }
}
