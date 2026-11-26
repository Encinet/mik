package org.encinet.mik.integration.mikd;

import jdk.net.ExtendedSocketOptions;
import org.encinet.mik.protocol.AnnouncementCommand;
import org.encinet.mik.protocol.AnnouncementQuery;
import org.encinet.mik.protocol.AnnouncementResult;
import org.encinet.mik.protocol.AssistantCommand;
import org.encinet.mik.protocol.AssistantResult;
import org.encinet.mik.protocol.ChatCommand;
import org.encinet.mik.protocol.ChatPoll;
import org.encinet.mik.protocol.ChatPublication;
import org.encinet.mik.protocol.ChatWebRoster;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.ConsoleRegistration;
import org.encinet.mik.protocol.Envelope;
import org.encinet.mik.protocol.GameQueryExchange;
import org.encinet.mik.protocol.GameQueryWork;
import org.encinet.mik.protocol.GovernanceCommand;
import org.encinet.mik.protocol.GovernanceResult;
import org.encinet.mik.protocol.Hello;
import org.encinet.mik.protocol.IdentityCommand;
import org.encinet.mik.protocol.IdentityResult;
import org.encinet.mik.protocol.PaperChatDelivery;
import org.encinet.mik.protocol.PaperStateAccepted;
import org.encinet.mik.protocol.PaperStateUpdate;
import org.encinet.mik.protocol.Request;
import org.encinet.mik.protocol.Response;
import org.encinet.mik.protocol.StatusRequest;
import org.encinet.mik.protocol.WebLoginApproval;
import org.encinet.mik.protocol.WebLoginReceipt;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Independently bounded connections; every reconnect discards the old session. */
public final class MikdClient implements RequestPort, ConsolePort, AutoCloseable {
    private static final List<String> BASE_CAPABILITIES = List.of("status.v1", "paper-state.v1");
    private static final String ASSISTANT_TURNS = "assistant-turns.v9";
    private static final String CHAT_MESSAGES = "chat-messages.v1";
    private static final String LOGIN_RECEIPT = "web-login-receipt.v1";
    private static final String GOVERNANCE_DELIVERY = "governance-delivery.v1";
    private static final String CONSOLE = "console.v1";
    private static final List<String> WORKFLOWS = List.of(ASSISTANT_TURNS, CHAT_MESSAGES, LOGIN_RECEIPT, GOVERNANCE_DELIVERY);
    private static final int MAX_QUEUE_ITEMS = 32;
    private static final int MAX_QUEUE_BYTES = 1024 * 1024;
    private static final long TIMEOUT_NANOS = Duration.ofSeconds(4).toNanos();
    private static final long STATE_TIMEOUT_NANOS = Duration.ofSeconds(2).toNanos();
    private static final Request CHAT_POLL = Request.newBuilder()
            .setChat(ChatCommand.newBuilder().setPoll(ChatPoll.getDefaultInstance())).build();
    private static final Request ANNOUNCEMENT_SNAPSHOT = Request.newBuilder()
            .setAnnouncements(AnnouncementCommand.newBuilder().setSnapshot(AnnouncementQuery.getDefaultInstance()))
            .build();

    private final Path socket;
    private final String daemonUser;
    private final String gameInstance;
    private final String startupId = UUID.randomUUID().toString();
    private final ScheduledThreadPoolExecutor timer;
    private final Lane control;
    private final Lane general;
    private final Lane paperState;
    private final Lane gameQueries;
    private final Lane accountQueries;
    private final Lane chatUplink;
    private final Lane chatDownlink;
    private final Lane identity;
    private final Lane governance;
    private final Map<String, List<Lane>> consoles = new HashMap<>();
    private volatile boolean started;
    private volatile boolean closed;

    public MikdClient(Path socket, String daemonUser, String gameInstance) {
        this.socket = Objects.requireNonNull(socket, "socket");
        this.daemonUser = Objects.requireNonNull(daemonUser, "daemonUser");
        this.gameInstance = Objects.requireNonNull(gameInstance, "gameInstance");
        if (!socket.isAbsolute() || socket.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 100
                || daemonUser.isBlank() || !gameInstance.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("Invalid mikd connection configuration");
        }
        timer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform().daemon().name("mikd-deadlines").factory());
        timer.setRemoveOnCancelPolicy(true);
        control = new Lane(ConnectionRole.CONTROL);
        general = new Lane(ConnectionRole.GENERAL);
        paperState = new Lane(ConnectionRole.PAPER_STATE);
        gameQueries = new Lane(ConnectionRole.GAME_QUERIES);
        accountQueries = new Lane(ConnectionRole.ACCOUNT_QUERIES);
        chatUplink = new Lane(ConnectionRole.CHAT_UPLINK);
        chatDownlink = new Lane(ConnectionRole.CHAT_DOWNLINK);
        identity = new Lane(ConnectionRole.IDENTITY);
        governance = new Lane(ConnectionRole.GOVERNANCE);
    }

    public synchronized void start() {
        if (closed || started) throw new IllegalStateException("mikd client already started or closed");
        started = true;
        control.worker.start();
        general.worker.start();
        paperState.worker.start();
    }

    SocketChannel openMusicChannel() throws IOException {
        if (closed) throw new IOException("mikd client is closed");
        var channel = SocketChannel.open(StandardProtocolFamily.UNIX);
        try {
            channel.connect(UnixDomainSocketAddress.of(socket + ".music.sock"));
            if (!channel.getOption(ExtendedSocketOptions.SO_PEERCRED).user().getName().equals(daemonUser)) {
                throw new IOException("Unexpected mikd music peer");
            }
            return channel;
        } catch (IOException | RuntimeException error) {
            channel.close();
            throw error;
        }
    }

    @Override
    public CompletableFuture<Diagnostics> status(ConnectionRole role) {
        Lane lane = switch (role) {
            case CONTROL -> control;
            case GENERAL -> general;
            default -> throw new IllegalArgumentException("Connection role required");
        };
        return mapResponse(lane.submit(Request.newBuilder().setStatus(StatusRequest.getDefaultInstance()).build()),
                response -> {
                    if (!response.hasStatus()) throw new IllegalStateException("Missing mikd status");
                    return new Diagnostics(response.getStatus(), lane.missingWorkflows);
                });
    }

    @Override
    public CompletableFuture<GameQueryWork> gameQuery(GameQueryExchange exchange) {
        return query(gameQueries, exchange);
    }

    @Override
    public CompletableFuture<GameQueryWork> accountQuery(GameQueryExchange exchange) {
        return query(accountQueries, exchange);
    }

    private synchronized CompletableFuture<GameQueryWork> query(Lane lane, GameQueryExchange exchange) {
        if (started && !closed && lane.worker.getState() == Thread.State.NEW) lane.worker.start();
        return mapResponse(lane.submit(Request.newBuilder().setGameQuery(exchange).build()), response -> {
            if (!response.hasGameQuery()) throw new IllegalStateException("Missing mikd game query work");
            return response.getGameQuery();
        });
    }

    @Override
    public CompletableFuture<WebLoginReceipt> approveWebLogin(WebLoginApproval approval, BooleanSupplier authorized) {
        return mapResponse(control.submit(Request.newBuilder().setWebLogin(approval).build(), authorized), response -> {
            if (!response.hasWebLogin()) throw new IllegalStateException("Missing mikd login approval receipt");
            return response.getWebLogin();
        });
    }

    @Override
    public CompletableFuture<PaperStateAccepted> publish(PaperStateUpdate snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.getVersion() <= 0 || !snapshot.hasServer() || !snapshot.hasPublicRoster()
                || !snapshot.hasChatRoster() || snapshot.getPublicRoster().getPlayersCount() > 256
                || snapshot.getChatRoster().getPlayersCount() > 256) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid game snapshot"));
        }
        return mapResponse(paperState.submit(Request.newBuilder().setPaperState(snapshot).build()), Response::getPaperState);
    }

    private static <T> CompletableFuture<T> mapResponse(CompletableFuture<Exchange> source, Function<Response, T> convert) {
        return mapExchange(source, exchange -> convert.apply(exchange.response()));
    }

    private static <T> CompletableFuture<T> mapExchange(CompletableFuture<Exchange> source, Function<Exchange, T> convert) {
        CompletableFuture<T> mapped = source.thenApply(convert);
        mapped.whenComplete((_, _) -> {
            if (mapped.isCancelled()) source.cancel(false);
        });
        return mapped;
    }

    @Override
    public synchronized CompletableFuture<Void> publishChat(ChatPublication publication, BooleanSupplier authorized) {
        if (started && !closed && chatUplink.worker.getState() == Thread.State.NEW) chatUplink.worker.start();
        var request = Request.newBuilder().setChat(ChatCommand.newBuilder().setPublish(publication)).build();
        return mapResponse(chatUplink.submit(request, authorized), _ -> null);
    }

    @Override
    public synchronized CompletableFuture<List<PaperChatDelivery>> pollChat() {
        if (started && !closed && chatDownlink.worker.getState() == Thread.State.NEW) chatDownlink.worker.start();
        return mapResponse(chatDownlink.submit(CHAT_POLL), response -> response.getChat().getDeliveries().getEntriesList());
    }

    @Override
    public CompletableFuture<Void> invalidateWebChat(UUID player) {
        var change = org.encinet.mik.protocol.WebChatInvalidation.newBuilder().setPlayerId(player.toString());
        return mapResponse(control.submit(Request.newBuilder().setInvalidateWebChat(change).build()), _ -> null);
    }

    @Override
    public CompletableFuture<AssistantResult> assistant(AssistantCommand command) {
        return mapResponse(general.submit(Request.newBuilder().setAssistant(command).build()), response -> {
            if (!response.hasAssistant()) throw new IllegalStateException("Missing mikd assistant result");
            return response.getAssistant();
        });
    }

    @Override
    public CompletableFuture<AnnouncementResult> fetchAnnouncements() {
        return mapResponse(general.submit(ANNOUNCEMENT_SNAPSHOT), response -> {
            if (!response.hasAnnouncements()) throw new IllegalStateException("Missing mikd announcement result");
            return response.getAnnouncements();
        });
    }

    @Override
    public synchronized CompletableFuture<IdentityResult> identity(IdentityCommand command, BooleanSupplier authorized) {
        if (started && !closed && identity.worker.getState() == Thread.State.NEW) identity.worker.start();
        return mapResponse(identity.submit(Request.newBuilder().setIdentity(command).build(), authorized), response -> {
            if (!response.hasIdentity()) throw new IllegalStateException("Missing mikd identity result");
            return response.getIdentity();
        });
    }

    @Override
    public synchronized CompletableFuture<GovernanceResult> governance(GovernanceCommand command) {
        if (started && !closed && governance.worker.getState() == Thread.State.NEW) governance.worker.start();
        return mapResponse(governance.submit(Request.newBuilder().setGovernance(command).build()), response -> {
            if (!response.hasGovernance()) throw new IllegalStateException("Missing mikd governance result");
            return response.getGovernance();
        });
    }

    @Override
    public synchronized Registration register(String id, String label, CommandInput input, LogHistory logs) {
        if (!started || closed) throw new IllegalStateException("mikd client is not running");
        if (!id.matches("[A-Za-z0-9._-]{1,64}") || label.isBlank()
                || label.getBytes(StandardCharsets.UTF_8).length > 128
                || label.codePoints().anyMatch(Character::isISOControl) || (input == null && logs == null)) {
            throw new IllegalArgumentException("Invalid console registration");
        }
        if (consoles.containsKey(id)) throw new IllegalStateException("Console is already registered: " + id);
        var registration = ConsoleRegistration.newBuilder().setId(id).setLabel(label)
                .setAcceptsInput(input != null).setOffersLogs(logs != null).build();
        List<Lane> lanes = new ArrayList<>(2);
        if (input != null) lanes.add(new Lane(ConnectionRole.CONSOLE_COMMANDS, ConsoleChannel.commands(registration, input)));
        if (logs != null) lanes.add(new Lane(ConnectionRole.CONSOLE_LOGS, ConsoleChannel.logs(registration, logs)));
        consoles.put(id, lanes);
        lanes.forEach(lane -> lane.worker.start());
        return () -> closeConsole(id, lanes);
    }

    private void closeConsole(String id, List<Lane> lanes) {
        synchronized (this) {
            if (consoles.get(id) != lanes) return;
            lanes.forEach(Lane::stop);
            consoles.remove(id);
        }
        join(lanes);
    }

    private static String requiredWorkflow(Request request) {
        return switch (request.getOperationCase()) {
            case ASSISTANT -> ASSISTANT_TURNS;
            case CHAT, INVALIDATE_WEB_CHAT -> CHAT_MESSAGES;
            case WEB_LOGIN -> LOGIN_RECEIPT;
            case GOVERNANCE -> GOVERNANCE_DELIVERY;
            case CONSOLE_COMMAND_POLL, CONSOLE_LOG_POLL -> CONSOLE;
            default -> null;
        };
    }

    private static void verifyChatReply(ChatCommand command, Response response) throws IOException {
        var chat = response.getChat();
        boolean matches = switch (command.getOperationCase()) {
            case PUBLISH -> chat.hasPublishedId() && chat.getPublishedId().equals(command.getPublish().getMessageId());
            case POLL -> chat.hasDeliveries();
            case OPERATION_NOT_SET -> false;
        };
        if (!matches) throw new IOException("Mismatched mikd chat reply");
    }

    private static final class Rejection extends IOException {
        private static final long serialVersionUID = 1L;

        Rejection(Response.Code code) {
            super("mikd rejected request: " + code);
        }
    }

    private static IOException rejection(Response.Code code) {
        return switch (code) {
            case INVALID_REQUEST, UNSUPPORTED, STALE_VERSION, REQUEST_CONFLICT, RECIPIENT_UNAVAILABLE ->
                    new NotSubmitted("mikd rejected request: " + code);
            // BUSY can be returned after passing work to storage. A lost storage receipt is ambiguous.
            default -> new Rejection(code);
        };
    }

    @Override
    public void close() {
        List<Lane> lanes = new ArrayList<>(List.of(
                control, general, paperState, gameQueries, accountQueries, chatUplink, chatDownlink, identity, governance));
        synchronized (this) {
            if (closed) return;
            closed = true;
            consoles.values().forEach(lanes::addAll);
            consoles.clear();
        }
        lanes.forEach(Lane::stop);
        timer.shutdownNow();
        join(lanes);
    }

    private static void join(List<Lane> lanes) {
        boolean interrupted = false;
        for (Lane lane : lanes) {
            Thread worker = lane.worker;
            try {
                if (worker != Thread.currentThread() && worker.isAlive() && !worker.join(Duration.ofSeconds(2))) {
                    throw new IllegalStateException("mikd connection did not stop");
                }
            } catch (InterruptedException error) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    private final class Lane {
        private final ConnectionRole role;
        private final ConsoleChannel console;
        private final Thread worker;
        private final ArrayDeque<Pending> queue = new ArrayDeque<>();
        private int queuedBytes;
        private Pending active;
        private volatile SocketChannel channel;
        private volatile boolean stopped;
        private volatile List<String> missingWorkflows = List.of();
        private String session = "";
        private long requestId;
        private PaperStateUpdate lastState;
        private ChatWebRoster webRoster = ChatWebRoster.getDefaultInstance();

        Lane(ConnectionRole role) {
            this(role, null);
        }

        Lane(ConnectionRole role, ConsoleChannel console) {
            this.role = role;
            this.console = console;
            worker = Thread.ofVirtual().name("mikd-" + role.name().toLowerCase(java.util.Locale.ROOT)).unstarted(this::run);
        }

        CompletableFuture<Exchange> submit(Request request) {
            return submit(request, () -> true);
        }

        CompletableFuture<Exchange> submit(Request request, BooleanSupplier authorized) {
            int bytes = request.getSerializedSize() + 128;
            List<Pending> superseded = new ArrayList<>();
            Pending pending;
            synchronized (this) {
                if (closed || stopped || !started) {
                    return CompletableFuture.failedFuture(new NotSubmitted("mikd connection is not running"));
                }
                if (bytes > MikdFrames.MAX_BYTES) return CompletableFuture.failedFuture(new NotSubmitted("mikd request too large"));
                // Projections keep the newest queued state; admitted writes will never use this policy.
                if (request.hasPaperState()) {
                    superseded.addAll(queue);
                }
                int reclaimed = superseded.stream().mapToInt(previous -> previous.bytes).sum();
                if (queue.size() - superseded.size() >= MAX_QUEUE_ITEMS || bytes > MAX_QUEUE_BYTES - (queuedBytes - reclaimed)) {
                    return CompletableFuture.failedFuture(new NotSubmitted("mikd queue full"));
                }
                for (Pending previous : superseded) {
                    queue.remove(previous);
                    queuedBytes -= previous.bytes;
                    previous.deadline.cancel(false);
                }
                long timeout = request.hasPaperState() ? STATE_TIMEOUT_NANOS : TIMEOUT_NANOS;
                pending = new Pending(request, bytes, authorized, timeout);
                queue.addLast(pending);
                queuedBytes += bytes;
                pending.deadline = timer.schedule(() -> expire(pending), timeout, TimeUnit.NANOSECONDS);
                pending.result.whenComplete((_, _) -> {
                    if (pending.result.isCancelled()) abandon(pending);
                });
                notifyAll();
            }
            // User callbacks may submit another request or close the client.
            // Complete futures outside the queue monitor and after iterator use.
            for (Pending previous : superseded) {
                previous.result.completeExceptionally(new NotSubmitted("Snapshot superseded"));
            }
            return pending.result;
        }

        private synchronized void abandon(Pending pending) {
            pending.abandoned = true;
            if (queue.remove(pending)) queuedBytes -= pending.bytes;
            if (active == pending && pending.writeAttempted) disconnect();
            pending.deadline.cancel(false);
        }

        private void expire(Pending pending) {
            boolean attempted;
            synchronized (this) {
                if (pending.result.isDone() || pending.abandoned) return;
                abandon(pending);
                attempted = pending.writeAttempted;
            }
            pending.result.completeExceptionally(attempted
                    ? new TimeoutException("mikd request timed out after send was attempted; no automatic replay")
                    : new NotSubmitted("mikd request expired before send"));
        }

        private synchronized Pending next() throws InterruptedException {
            if (queue.isEmpty() && !closed && !stopped) wait(1000);
            Pending pending = queue.pollFirst();
            if (pending != null) queuedBytes -= pending.bytes;
            active = pending;
            return pending;
        }

        private void run() {
            long backoff = 100;
            Request poll = console == null ? null : console.initial();
            try {
                while (!closed && !stopped) {
                    try {
                        if (channel == null) {
                            connect();
                            if (console != null) poll = console.initial();
                        }
                        backoff = 100;
                        if (console != null) {
                            poll = console.handler().next(exchange(poll, null));
                            continue;
                        }
                        Pending pending = next();
                        if (closed || stopped) break;
                        if (pending == null) {
                            // Heartbeats also detect a daemon restart while no business request is queued.
                            exchange(Request.newBuilder().setStatus(StatusRequest.getDefaultInstance()).setTimeoutMs(2000).build(), null);
                        } else if (!pending.result.isDone()) {
                            execute(pending);
                        }
                    } catch (IOException | RuntimeException error) {
                        Pending failed;
                        synchronized (this) {
                            failed = active;
                        }
                        if (failed != null) {
                            if (failed.writeAttempted) failed.result.completeExceptionally(error);
                            else {
                                NotSubmitted unsent = new NotSubmitted("mikd request failed before send");
                                unsent.initCause(error);
                                failed.result.completeExceptionally(unsent);
                            }
                        }
                        disconnect();
                        if (!closed && !stopped) {
                            try { Thread.sleep(backoff); }
                            catch (InterruptedException interrupted) { break; }
                            backoff = Math.min(5000, backoff * 2);
                        }
                    } catch (InterruptedException error) {
                        break;
                    } finally {
                        synchronized (this) {
                            if (active != null) active.deadline.cancel(false);
                            active = null;
                        }
                    }
                }
            } finally {
                disconnect();
            }
        }

        private void execute(Pending pending) throws IOException {
            try {
                long remaining = pending.expiresAt - System.nanoTime();
                if (remaining <= 0) {
                    expire(pending);
                    return;
                }
                if (!pending.authorized.getAsBoolean()) throw new NotSubmitted("Game authorization changed");
                Request request = pending.request.toBuilder().setTimeoutMs((int) Math.max(1,
                        TimeUnit.NANOSECONDS.toMillis(remaining))).build();
                Exchange response = request.hasPaperState() ? exchangeState(request, pending) : exchange(request, pending);
                if (pending.expiresAt - System.nanoTime() <= 0) expire(pending);
                else pending.result.complete(response);
            } catch (NotSubmitted | Rejection rejected) {
                pending.result.completeExceptionally(rejected);
            }
        }

        private Exchange exchangeState(Request request, Pending pending) throws IOException {
            var next = request.getPaperState();
            var compact = next.toBuilder().setKnownWebRevision(webRoster.getRevision());
            if (lastState != null) {
                if (next.getPublicRoster().equals(lastState.getPublicRoster())) compact.clearPublicRoster();
                if (next.getChatRoster().equals(lastState.getChatRoster())) compact.clearChatRoster();
            }
            var exchange = exchange(request.toBuilder().setPaperState(compact).build(), pending);
            var response = exchange.response();
            var accepted = response.getPaperState();
            if (accepted.getVersion() != next.getVersion()) throw new IOException("Mismatched mikd state receipt");
            lastState = next;
            if (accepted.hasWebRoster()) webRoster = accepted.getWebRoster();
            return new Exchange(response.toBuilder().setPaperState(accepted.toBuilder().setWebRoster(webRoster)).build(),
                    exchange.connection(), exchange.receivedAt());
        }

        private void connect() throws IOException {
            SocketChannel opened = SocketChannel.open(StandardProtocolFamily.UNIX);
            synchronized (this) {
                if (closed || stopped) { opened.close(); return; }
                channel = opened;
            }
            ScheduledFuture<?> deadline = timer.schedule(() -> closeChannel(opened), 2, TimeUnit.SECONDS);
            try {
                opened.connect(UnixDomainSocketAddress.of(socket));
                if (!opened.getOption(ExtendedSocketOptions.SO_PEERCRED).user().getName().equals(daemonUser)) {
                    throw new IOException("Unexpected mikd peer user");
                }
                var hello = Hello.newBuilder().setProtocolVersion(MikdFrames.VERSION).setRole(role)
                        .setGameInstance(gameInstance).setStartupId(startupId).setConnectionId(UUID.randomUUID().toString())
                        .addAllCapabilities(BASE_CAPABILITIES).addAllCapabilities(WORKFLOWS);
                if (console != null) hello.setConsole(console.registration()).addCapabilities(CONSOLE);
                MikdFrames.write(opened, Envelope.newBuilder().setHello(hello).build());
                Envelope welcome = MikdFrames.read(opened);
                if (!welcome.hasWelcome() || welcome.getRequestId() != 0 || welcome.getSessionId().isBlank()
                        || welcome.getWelcome().getProtocolVersion() != MikdFrames.VERSION
                        || welcome.getWelcome().getMaximumFrameBytes() != MikdFrames.MAX_BYTES
                        || !welcome.getWelcome().getCapabilitiesList().containsAll(BASE_CAPABILITIES)
                        || (console != null && !welcome.getWelcome().getCapabilitiesList().contains(CONSOLE))) {
                    throw new IOException("Incompatible mikd handshake");
                }
                var capabilities = welcome.getWelcome().getCapabilitiesList();
                missingWorkflows = WORKFLOWS.stream().filter(name -> !capabilities.contains(name)).toList();
                session = welcome.getSessionId();
                requestId = 0;
                lastState = null;
                webRoster = ChatWebRoster.getDefaultInstance();
            } finally {
                deadline.cancel(false);
            }
        }

        private Exchange exchange(Request request, Pending pending) throws IOException {
            String workflow = requiredWorkflow(request);
            if (workflow != null && missingWorkflows.contains(workflow)) {
                throw new NotSubmitted("Incompatible mikd capability: " + workflow);
            }
            SocketChannel current;
            synchronized (this) {
                current = channel;
                if (current == null || closed || stopped || (pending != null && (pending.abandoned || pending.result.isDone()
                        || pending.expiresAt - System.nanoTime() <= 0))) {
                    throw new NotSubmitted("mikd request did not reach the socket");
                }
                if (pending != null) pending.writeAttempted = true;
            }
            ScheduledFuture<?> deadline = timer.schedule(() -> closeChannel(current), request.getTimeoutMs(), TimeUnit.MILLISECONDS);
            try {
                long id = ++requestId;
                MikdFrames.write(current, Envelope.newBuilder().setRequestId(id).setSessionId(session).setRequest(request).build());
                Envelope envelope = MikdFrames.read(current);
                long receivedAt = System.nanoTime();
                if (envelope.getRequestId() != id || !envelope.getSessionId().equals(session) || !envelope.hasResponse()) {
                    throw new IOException("Stale or mismatched mikd response");
                }
                Response response = envelope.getResponse();
                if (response.getCode() != Response.Code.OK) throw rejection(response.getCode());
                if (request.hasChat()) verifyChatReply(request.getChat(), response);
                return new Exchange(response, current, receivedAt);
            } finally {
                deadline.cancel(false);
            }
        }

        private synchronized void disconnect() {
            SocketChannel current = channel;
            channel = null;
            if (current != null) closeChannel(current);
        }

        void stop() {
            List<Pending> cancelled;
            synchronized (this) {
                if (stopped) return;
                stopped = true;
                disconnect();
                worker.interrupt();
                cancelled = new ArrayList<>(queue);
                queue.clear();
                queuedBytes = 0;
                if (active != null) cancelled.add(active);
                for (Pending pending : cancelled) {
                    pending.abandoned = true;
                    pending.deadline.cancel(false);
                }
                notifyAll();
            }
            for (Pending pending : cancelled) {
                pending.result.completeExceptionally(pending.writeAttempted
                        ? new IOException("mikd client closed after send was attempted")
                        : new NotSubmitted("mikd client closed before send"));
            }
        }
    }

    record Exchange(Response response, SocketChannel connection, long receivedAt) {}

    private static void closeChannel(SocketChannel channel) {
        try { channel.close(); }
        catch (IOException ignored) { /* Socket is unusable; its owner reconnects with a new session. */ }
    }

    private static final class Pending {
        private final Request request;
        private final int bytes;
        private final BooleanSupplier authorized;
        private final CompletableFuture<Exchange> result = new CompletableFuture<>();
        private final long expiresAt;
        private ScheduledFuture<?> deadline;
        // Transitioned under the Lane monitor; no send can begin after abandonment.
        private boolean abandoned;
        private boolean writeAttempted;

        Pending(Request request, int bytes, BooleanSupplier authorized, long timeout) {
            this.request = request;
            this.bytes = bytes;
            this.authorized = authorized;
            expiresAt = System.nanoTime() + timeout;
        }
    }
}
