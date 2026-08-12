package org.encinet.mik.module.social.platform.qq.gateway;

import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialPlatformSession;
import org.encinet.mik.module.social.api.SocialSessionStatus;
import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;
import org.encinet.mik.module.social.platform.qq.client.QqAccessTokenProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Maintains an authenticated QQ Gateway WebSocket, including heartbeat and resume. */
public final class QqGatewaySession implements SocialPlatformSession {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final long MINIMUM_HEARTBEAT_MILLIS = 1L;
    private static final long MAXIMUM_HEARTBEAT_MILLIS = 24 * 60 * 60_000L;
    private static final int MAXIMUM_MISSED_HEARTBEAT_ACKS = 3;

    private final Object lock = new Object();
    private final QqPlatformConfig config;
    private final Supplier<CompletableFuture<URI>> endpoints;
    private final Supplier<CompletableFuture<String>> accessTokens;
    private final Runnable invalidateToken;
    private final Connector connector;
    private final Function<QqGatewayGroupMessage, SocialEventSink.Acceptance> events;
    private final Consumer<SocialSessionStatus> statusUpdates;
    private final Logger logger;
    private final QqGatewayReconnectPolicy reconnectPolicy;
    private final QqGatewayTimers timers;

    private SocialSessionStatus.State state = SocialSessionStatus.State.STOPPED;
    private String endpoint;
    private String lastError = "";
    private boolean closed;
    private long generation;
    private int reconnectAttempts;
    private boolean reconnectScheduled;
    private WebSocket socket;
    private long heartbeatIntervalMillis;
    private String sessionId;
    private Long sequence;
    private int missedHeartbeatAcks;

    public QqGatewaySession(
            HttpClient httpClient,
            QqPlatformConfig config,
            QqAccessTokenProvider tokenProvider,
            Function<QqGatewayGroupMessage, SocialEventSink.Acceptance> events,
            Consumer<SocialSessionStatus> statusUpdates,
            Logger logger
    ) {
        this(config,
                new QqGatewayDiscovery(httpClient, config, tokenProvider::accessToken,
                        tokenProvider::invalidate)::discover,
                tokenProvider::accessToken,
                tokenProvider::invalidate,
                (uri, listener) -> httpClient.newWebSocketBuilder()
                        .connectTimeout(CONNECT_TIMEOUT)
                        .buildAsync(uri, listener),
                events,
                statusUpdates,
                logger);
    }

    QqGatewaySession(
            QqPlatformConfig config,
            Supplier<CompletableFuture<URI>> endpoints,
            Supplier<CompletableFuture<String>> accessTokens,
            Runnable invalidateToken,
            Connector connector,
            Function<QqGatewayGroupMessage, SocialEventSink.Acceptance> events,
            Consumer<SocialSessionStatus> statusUpdates,
            Logger logger
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints");
        this.accessTokens = Objects.requireNonNull(accessTokens, "accessTokens");
        this.invalidateToken = Objects.requireNonNull(invalidateToken, "invalidateToken");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.events = Objects.requireNonNull(events, "events");
        this.statusUpdates = Objects.requireNonNull(statusUpdates, "statusUpdates");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.endpoint = config.apiUri("/gateway/bot").toString();
        this.reconnectPolicy = new QqGatewayReconnectPolicy(config);
        this.timers = new QqGatewayTimers();
    }

    /** Begins Gateway discovery and connection without blocking plugin startup. */
    public void start() {
        final long connectionGeneration;
        synchronized (lock) {
            if (closed || state != SocialSessionStatus.State.STOPPED) {
                return;
            }
            state = SocialSessionStatus.State.STARTING;
            lastError = "";
            connectionGeneration = ++generation;
        }
        publishStatus();
        connect(connectionGeneration);
    }

    @Override
    public void close() {
        final WebSocket currentSocket;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            generation++;
            state = SocialSessionStatus.State.STOPPED;
            reconnectScheduled = false;
            cancelHandshakeLocked();
            cancelHeartbeatLocked();
            currentSocket = socket;
            socket = null;
        }
        publishStatus();
        if (currentSocket != null) {
            try {
                currentSocket.sendClose(WebSocket.NORMAL_CLOSURE, "plugin stopping")
                        .orTimeout(2, TimeUnit.SECONDS)
                        .exceptionally(error -> {
                            currentSocket.abort();
                            return null;
                        });
            } catch (RuntimeException ignored) {
                currentSocket.abort();
            }
        }
        timers.close();
    }

    private void connect(long connectionGeneration) {
        final CompletableFuture<URI> discovery;
        try {
            discovery = Objects.requireNonNull(endpoints.get(), "Gateway discovery future");
        } catch (RuntimeException error) {
            connectionLost(connectionGeneration, error, true);
            return;
        }
        discovery.thenCompose(discoveredEndpoint -> {
            synchronized (lock) {
                if (!isCurrentLocked(connectionGeneration)) {
                    return CompletableFuture.failedFuture(new CancellationException());
                }
                endpoint = discoveredEndpoint.toString();
            }
            return connector.connect(discoveredEndpoint,
                    new QqGatewaySocketListener(connectionGeneration,
                            config.gatewayMaxFrameBytes(), socketCallbacks()));
        }).whenComplete((ignored, error) -> {
            if (error != null && !(unwrap(error) instanceof CancellationException)) {
                connectionLost(connectionGeneration, error, true);
            }
        });
    }

    private void handleFrame(long connectionGeneration, String payload) {
        QqGatewayProtocol.Frame frame = QqGatewayProtocol.parse(payload).orElse(null);
        if (frame == null) {
            debug("Ignored an invalid Gateway frame");
            return;
        }
        final Long previousSequence;
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return;
            }
            previousSequence = sequence;
            if (frame.sequence() != null) {
                sequence = frame.sequence();
            }
        }

        switch (frame.opcode()) {
            case QqGatewayProtocol.OP_HELLO -> handleHello(connectionGeneration, frame);
            case QqGatewayProtocol.OP_HEARTBEAT -> sendHeartbeat(connectionGeneration);
            case QqGatewayProtocol.OP_HEARTBEAT_ACK -> {
                synchronized (lock) {
                    if (isCurrentLocked(connectionGeneration)) {
                        missedHeartbeatAcks = 0;
                    }
                }
            }
            case QqGatewayProtocol.OP_RECONNECT ->
                    connectionLost(connectionGeneration,
                            new IllegalStateException("QQ requested a Gateway reconnect"), true);
            case QqGatewayProtocol.OP_INVALID_SESSION -> {
                synchronized (lock) {
                    if (isCurrentLocked(connectionGeneration)) {
                        sessionId = null;
                        sequence = null;
                    }
                }
                connectionLost(connectionGeneration,
                        new IllegalStateException("QQ rejected the Gateway session"), false);
            }
            case QqGatewayProtocol.OP_DISPATCH ->
                    handleDispatch(connectionGeneration, frame, previousSequence);
            default -> debug("Ignored QQ Gateway opcode " + frame.opcode());
        }
    }

    private void handleHello(long connectionGeneration, QqGatewayProtocol.Frame frame) {
        long heartbeatMillis = QqGatewayProtocol.heartbeatInterval(frame).orElse(-1L);
        if (heartbeatMillis < MINIMUM_HEARTBEAT_MILLIS
                || heartbeatMillis > MAXIMUM_HEARTBEAT_MILLIS) {
            connectionLost(connectionGeneration,
                    new IllegalStateException("QQ Gateway supplied an invalid heartbeat interval"),
                    true);
            return;
        }
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return;
            }
            heartbeatIntervalMillis = heartbeatMillis;
            cancelHeartbeatLocked();
            missedHeartbeatAcks = 0;
        }

        final CompletableFuture<String> tokenFuture;
        try {
            tokenFuture = Objects.requireNonNull(accessTokens.get(), "Access token future");
        } catch (RuntimeException error) {
            connectionLost(connectionGeneration, error, true);
            return;
        }
        tokenFuture.whenComplete((token, error) -> {
            if (error != null) {
                connectionLost(connectionGeneration, error, true);
                return;
            }
            final String command;
            synchronized (lock) {
                if (!isCurrentLocked(connectionGeneration)) {
                    return;
                }
                if (sessionId != null && sequence != null) {
                    command = QqGatewayProtocol.resume(token, sessionId, sequence);
                } else {
                    command = QqGatewayProtocol.identify(token);
                }
            }
            sendText(connectionGeneration, command);
        });
    }

    private void handleDispatch(long connectionGeneration, QqGatewayProtocol.Frame frame,
                                Long previousSequence) {
        if (QqGatewayProtocol.EVENT_READY.equals(frame.type())) {
            String readySessionId = QqGatewayProtocol.readySessionId(frame).orElse("");
            if (readySessionId.isBlank()) {
                connectionLost(connectionGeneration,
                        new IllegalStateException("QQ READY omitted session_id"), false);
                return;
            }
            synchronized (lock) {
                if (!isCurrentLocked(connectionGeneration)) {
                    return;
                }
                sessionId = readySessionId;
                reconnectAttempts = 0;
                state = SocialSessionStatus.State.READY;
                lastError = "";
                cancelHandshakeLocked();
            }
            publishStatus();
            startHeartbeat(connectionGeneration);
            logger.info("QQ Bot Gateway is ready on " + endpoint);
            return;
        }
        if (QqGatewayProtocol.EVENT_RESUMED.equals(frame.type())) {
            synchronized (lock) {
                if (!isCurrentLocked(connectionGeneration)) {
                    return;
                }
                reconnectAttempts = 0;
                state = SocialSessionStatus.State.READY;
                lastError = "";
                cancelHandshakeLocked();
            }
            publishStatus();
            startHeartbeat(connectionGeneration);
            logger.info("QQ Bot Gateway session resumed");
            return;
        }

        QqGatewayGroupMessage event = QqGatewayProtocol.groupMessage(frame).orElse(null);
        if (event == null) {
            return;
        }
        try {
            SocialEventSink.Acceptance acceptance = events.apply(event);
            if (acceptance == SocialEventSink.Acceptance.RETRY_LATER) {
                retryEvent(connectionGeneration, previousSequence,
                        "QQ Gateway event could not enter the social work queue");
            }
        } catch (RuntimeException error) {
            logger.log(Level.WARNING, "Failed to admit a QQ Gateway event", error);
            retryEvent(connectionGeneration, previousSequence,
                    "QQ Gateway event admission failed");
        }
    }

    private void retryEvent(long connectionGeneration, Long previousSequence, String reason) {
        final boolean resumable;
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return;
            }
            sequence = previousSequence;
            resumable = previousSequence != null && sessionId != null;
        }
        connectionLost(connectionGeneration, new IllegalStateException(reason),
                resumable);
    }

    private void startHeartbeat(long connectionGeneration) {
        final long intervalMillis;
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return;
            }
            cancelHeartbeatLocked();
            missedHeartbeatAcks = 0;
            intervalMillis = heartbeatIntervalMillis;
            if (intervalMillis < MINIMUM_HEARTBEAT_MILLIS) {
                connectionLost(connectionGeneration,
                        new IllegalStateException("QQ Gateway sent READY before HELLO"), false);
                return;
            }
            timers.scheduleHeartbeat(() -> heartbeatTick(connectionGeneration), intervalMillis);
        }
        sendHeartbeat(connectionGeneration);
    }

    private void heartbeatTick(long connectionGeneration) {
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return;
            }
            if (missedHeartbeatAcks >= MAXIMUM_MISSED_HEARTBEAT_ACKS) {
                connectionLost(connectionGeneration,
                        new IllegalStateException("QQ Gateway heartbeat acknowledgement timed out"),
                        true);
                return;
            }
        }
        sendHeartbeat(connectionGeneration);
    }

    private void sendHeartbeat(long connectionGeneration) {
        final String heartbeat;
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return;
            }
            missedHeartbeatAcks++;
            heartbeat = QqGatewayProtocol.heartbeat(sequence);
        }
        sendText(connectionGeneration, heartbeat);
    }

    private void sendText(long connectionGeneration, String payload) {
        final WebSocket currentSocket;
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return;
            }
            currentSocket = socket;
        }
        if (currentSocket == null) {
            connectionLost(connectionGeneration,
                    new IllegalStateException("QQ Gateway socket is not open"), true);
            return;
        }
        try {
            currentSocket.sendText(payload, true).whenComplete((ignored, error) -> {
                if (error != null) {
                    connectionLost(connectionGeneration, error, true);
                }
            });
        } catch (RuntimeException error) {
            connectionLost(connectionGeneration, error, true);
        }
    }

    private void connectionLost(long connectionGeneration, Throwable error,
                                boolean preserveSession) {
        final WebSocket currentSocket;
        final long nextGeneration;
        final long delayMillis;
        final boolean failed;
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration) || reconnectScheduled) {
                return;
            }
            cancelHeartbeatLocked();
            cancelHandshakeLocked();
            missedHeartbeatAcks = 0;
            currentSocket = socket;
            socket = null;
            if (!preserveSession) {
                sessionId = null;
                sequence = null;
            }
            lastError = errorMessage(error);
            if (reconnectPolicy.exhausted(reconnectAttempts)) {
                state = SocialSessionStatus.State.FAILED;
                generation++;
                failed = true;
                nextGeneration = generation;
                delayMillis = 0;
            } else {
                failed = false;
                reconnectAttempts++;
                delayMillis = reconnectPolicy.delayMillis(reconnectAttempts);
                state = SocialSessionStatus.State.STARTING;
                reconnectScheduled = true;
                nextGeneration = ++generation;
            }
        }
        publishStatus();
        if (currentSocket != null) {
            currentSocket.abort();
        }
        if (failed) {
            logger.warning("QQ Bot Gateway failed after " + reconnectAttempts
                    + " reconnect attempts: " + lastError);
            return;
        }
        logger.warning("QQ Bot Gateway disconnected; reconnecting in " + delayMillis
                + " ms (attempt " + reconnectAttempts + "/"
                + reconnectPolicy.maximumAttempts() + "): " + lastError);
        timers.scheduleReconnect(() -> {
            synchronized (lock) {
                if (!isCurrentLocked(nextGeneration)) {
                    return;
                }
                reconnectScheduled = false;
            }
            connect(nextGeneration);
        }, delayMillis);
    }

    private boolean isCurrentLocked(long connectionGeneration) {
        return !closed && generation == connectionGeneration
                && state != SocialSessionStatus.State.FAILED;
    }

    private void cancelHeartbeatLocked() {
        timers.cancelHeartbeat();
    }

    private void cancelHandshakeLocked() {
        timers.cancelHandshake();
    }

    private void debug(String message) {
        if (config.gatewayDebugEnabled()) {
            logger.info("QQ Gateway debug: " + message);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable result = error;
        while ((result instanceof CompletionException || result instanceof ExecutionException)
                && result.getCause() != null) {
            result = result.getCause();
        }
        return result;
    }

    private static String errorMessage(Throwable error) {
        Throwable cause = unwrap(error);
        String message = cause.getMessage();
        String result = message == null || message.isBlank()
                ? cause.getClass().getSimpleName() : message.strip();
        return result.length() <= 300 ? result : result.substring(0, 300);
    }

    @FunctionalInterface
    interface Connector {
        CompletableFuture<WebSocket> connect(URI endpoint, WebSocket.Listener listener);
    }

    private QqGatewaySocketListener.Callbacks socketCallbacks() {
        return new QqGatewaySocketListener.Callbacks() {
            @Override
            public boolean opened(long connectionGeneration, WebSocket openedSocket) {
                return socketOpened(connectionGeneration, openedSocket);
            }

            @Override
            public void text(long connectionGeneration, String payload) {
                handleFrame(connectionGeneration, payload);
            }

            @Override
            public void closed(long connectionGeneration, int statusCode, String reason) {
                socketClosed(connectionGeneration, statusCode, reason);
            }

            @Override
            public void failed(long connectionGeneration, Throwable error,
                               boolean preserveSession) {
                connectionLost(connectionGeneration, error, preserveSession);
            }
        };
    }

    private boolean socketOpened(long connectionGeneration, WebSocket openedSocket) {
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return false;
            }
            socket = openedSocket;
            cancelHandshakeLocked();
            timers.scheduleHandshake(
                    () -> connectionLost(connectionGeneration,
                            new IllegalStateException(
                                    "QQ Gateway authentication handshake timed out"), false),
                    config.gatewayHandshakeTimeoutMillis());
            return true;
        }
    }

    private void socketClosed(long connectionGeneration, int statusCode, String reason) {
        QqGatewayReconnectPolicy.CloseAction action = reconnectPolicy.closeAction(statusCode);
        if (action == QqGatewayReconnectPolicy.CloseAction.FAIL) {
            failPermanently(connectionGeneration,
                    "QQ Gateway closed with fatal code " + statusCode + closeReason(reason));
            return;
        }
        if (action == QqGatewayReconnectPolicy.CloseAction.REFRESH_TOKEN) {
            invalidateToken.run();
        }
        connectionLost(connectionGeneration,
                new IllegalStateException("QQ Gateway closed with code " + statusCode
                        + closeReason(reason)),
                action == QqGatewayReconnectPolicy.CloseAction.RESUME);
    }

    private void failPermanently(long connectionGeneration, String message) {
        final WebSocket currentSocket;
        synchronized (lock) {
            if (!isCurrentLocked(connectionGeneration)) {
                return;
            }
            cancelHeartbeatLocked();
            cancelHandshakeLocked();
            state = SocialSessionStatus.State.FAILED;
            lastError = message;
            generation++;
            currentSocket = socket;
            socket = null;
        }
        publishStatus();
        if (currentSocket != null) {
            currentSocket.abort();
        }
        logger.warning(message);
    }

    private static String closeReason(String reason) {
        return reason == null || reason.isBlank() ? "" : " (" + reason.strip() + ")";
    }

    private void publishStatus() {
        SocialSessionStatus status;
        synchronized (lock) {
            status = new SocialSessionStatus(state, endpoint, lastError);
        }
        try {
            statusUpdates.accept(status);
        } catch (RuntimeException error) {
            logger.log(Level.WARNING, "Could not publish QQ Gateway status", error);
        }
    }
}
