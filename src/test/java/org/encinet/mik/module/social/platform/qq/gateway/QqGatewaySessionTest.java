package org.encinet.mik.module.social.platform.qq.gateway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.encinet.mik.module.social.api.SocialEventSink;
import org.encinet.mik.module.social.api.SocialSessionStatus;
import org.encinet.mik.module.social.platform.qq.QqPlatformConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqGatewaySessionTest {

    private static final Map<QqGatewaySession, AtomicReference<SocialSessionStatus>> STATUSES =
            new ConcurrentHashMap<>();

    @Test
    void identifiesBecomesReadyAndAdmitsGroupMessages() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        AtomicReference<QqGatewayGroupMessage> received = new AtomicReference<>();
        QqGatewaySession session = session(config(3), connector,
                event -> {
                    received.set(event);
                    return SocialEventSink.Acceptance.ACCEPTED;
                });

        session.start();
        Connection connection = connector.only();
        connection.emitFragment("{\"op\":10,", false);
        connection.emitFragment("\"d\":{\"heartbeat_interval\":10000}}", true);

        JsonObject identify = JsonParser.parseString(connection.socket.sent.getFirst())
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_IDENTIFY, identify.get("op").getAsInt());
        assertEquals("QQBot access-token",
                identify.getAsJsonObject("d").get("token").getAsString());

        connection.emit("""
                {"op":0,"s":40,"t":"READY","d":{"session_id":"session-one"}}
                """);
        assertEquals(SocialSessionStatus.State.READY, status(session).state());
        assertEquals("wss://gateway.example/socket", status(session).endpoint());
        JsonObject heartbeat = JsonParser.parseString(connection.socket.sent.get(1))
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_HEARTBEAT, heartbeat.get("op").getAsInt());
        assertEquals(40L, heartbeat.get("d").getAsLong());
        connection.emit("{\"op\":11,\"d\":null}");

        connection.emit("""
                {"op":0,"s":41,"t":"GROUP_AT_MESSAGE_CREATE","d":{
                  "id":"message","group_openid":"group","content":"/状态",
                  "author":{"username":"name","member_openid":"member","bot":false}
                }}
                """);
        assertEquals("message", received.get().messageId());
        assertEquals("group", received.get().groupOpenId());

        session.close();
        assertEquals(SocialSessionStatus.State.STOPPED, status(session).state());
        assertTrue(connection.socket.outputClosed);
    }

    @Test
    void reconnectRequestResumesWithTheLastSessionAndSequence() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);
        session.start();
        Connection first = connector.only();
        first.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");
        first.emit("{\"op\":0,\"s\":72,\"t\":\"READY\","
                + "\"d\":{\"session_id\":\"resume-me\"}}");

        first.emit("{\"op\":7,\"d\":null}");
        await(() -> connector.connections.size() == 2);
        Connection second = connector.connections.get(1);
        second.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");

        JsonObject resume = JsonParser.parseString(second.socket.sent.getFirst())
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_RESUME, resume.get("op").getAsInt());
        assertEquals("resume-me",
                resume.getAsJsonObject("d").get("session_id").getAsString());
        assertEquals(72L, resume.getAsJsonObject("d").get("seq").getAsLong());
        second.emit("{\"op\":0,\"s\":73,\"t\":\"RESUMED\",\"d\":{}}");
        assertEquals(SocialSessionStatus.State.READY, status(session).state());
        assertTrue(first.socket.aborted);
        session.close();
    }

    @Test
    void expiredConnectionCloseCodeResumesTheSession() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);
        session.start();
        Connection first = connector.only();
        first.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");
        first.emit("{\"op\":0,\"s\":55,\"t\":\"READY\","
                + "\"d\":{\"session_id\":\"expiring-session\"}}");

        first.close(4009, "connection expired");
        await(() -> connector.connections.size() == 2);
        Connection second = connector.connections.get(1);
        second.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");

        JsonObject resume = JsonParser.parseString(second.socket.sent.getFirst())
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_RESUME, resume.get("op").getAsInt());
        assertEquals("expiring-session",
                resume.getAsJsonObject("d").get("session_id").getAsString());
        assertEquals(55L, resume.getAsJsonObject("d").get("seq").getAsLong());
        session.close();
    }

    @Test
    void invalidSequenceCloseCodeStartsANewIdentify() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);
        session.start();
        Connection first = connector.only();
        first.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");
        first.emit("{\"op\":0,\"s\":56,\"t\":\"READY\","
                + "\"d\":{\"session_id\":\"invalid-sequence\"}}");

        first.close(4007, "invalid sequence");
        await(() -> connector.connections.size() == 2);
        Connection second = connector.connections.get(1);
        second.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");

        JsonObject identify = JsonParser.parseString(second.socket.sent.getFirst())
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_IDENTIFY, identify.get("op").getAsInt());
        session.close();
    }

    @Test
    void invalidSessionReconnectsWithAReplacementIdentify() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);
        session.start();
        Connection first = connector.only();
        first.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");
        first.emit("{\"op\":0,\"s\":8,\"t\":\"READY\","
                + "\"d\":{\"session_id\":\"expired\"}}");
        first.emit("{\"op\":9,\"d\":false}");

        await(() -> connector.connections.size() == 2);
        Connection second = connector.connections.get(1);
        second.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");
        JsonObject command = JsonParser.parseString(second.socket.sent.getFirst())
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_IDENTIFY, command.get("op").getAsInt());
        session.close();
    }

    @Test
    void queueBackpressureResumesBeforeTheRejectedEvent() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.RETRY_LATER);
        session.start();
        Connection first = connector.only();
        first.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");
        first.emit("{\"op\":0,\"s\":10,\"t\":\"READY\","
                + "\"d\":{\"session_id\":\"backpressure\"}}");
        first.emit("""
                {"op":0,"s":11,"t":"GROUP_AT_MESSAGE_CREATE","d":{
                  "id":"message","group_openid":"group","content":"/状态",
                  "author":{"username":"name","member_openid":"member","bot":false}
                }}
                """);

        await(() -> connector.connections.size() == 2);
        Connection second = connector.connections.get(1);
        second.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");
        JsonObject resume = JsonParser.parseString(second.socket.sent.getFirst())
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_RESUME, resume.get("op").getAsInt());
        assertEquals(10L, resume.getAsJsonObject("d").get("seq").getAsLong());
        session.close();
    }

    @Test
    void fatalCloseDoesNotReconnect() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);
        session.start();

        connector.only().close(4014, "disallowed intent");

        assertEquals(SocialSessionStatus.State.FAILED, status(session).state());
        assertTrue(status(session).lastError().contains("4014"));
        TimeUnit.MILLISECONDS.sleep(150);
        assertEquals(1, connector.connections.size());
        session.close();
    }

    @Test
    void authenticationCloseRefreshesTokenAndStartsANewSession() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        AtomicReference<String> token = new AtomicReference<>("token-one");
        AtomicInteger invalidations = new AtomicInteger();
        QqPlatformConfig platformConfig = config(3);
        QqGatewaySession session = tracked(statusUpdates -> new QqGatewaySession(platformConfig,
                () -> CompletableFuture.completedFuture(
                        URI.create("wss://gateway.example/socket")),
                () -> CompletableFuture.completedFuture(token.get()),
                () -> {
                    invalidations.incrementAndGet();
                    token.set("token-two");
                }, connector, event -> SocialEventSink.Acceptance.ACCEPTED, statusUpdates,
                Logger.getLogger("QqGatewaySessionTest")));
        session.start();
        Connection first = connector.only();
        first.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");
        assertEquals("QQBot token-one", JsonParser.parseString(
                        first.socket.sent.getFirst()).getAsJsonObject()
                .getAsJsonObject("d").get("token").getAsString());

        first.close(4004, "authentication failed");
        await(() -> connector.connections.size() == 2);
        Connection second = connector.connections.get(1);
        second.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":10000}}");

        JsonObject identify = JsonParser.parseString(second.socket.sent.getFirst())
                .getAsJsonObject();
        assertEquals(QqGatewayProtocol.OP_IDENTIFY, identify.get("op").getAsInt());
        assertEquals("QQBot token-two",
                identify.getAsJsonObject("d").get("token").getAsString());
        assertEquals(1, invalidations.get());
        session.close();
    }

    @Test
    void incompleteAuthenticationHandshakeIsReconnected() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3, 1_000), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);

        session.start();
        await(() -> connector.connections.size() == 2);

        assertTrue(connector.connections.getFirst().socket.aborted);
        assertTrue(status(session).lastError().contains("handshake timed out"));
        session.close();
    }

    @Test
    void missingHeartbeatAcknowledgementsForceSessionRecovery() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);
        session.start();
        Connection first = connector.only();
        first.emit("{\"op\":10,\"d\":{\"heartbeat_interval\":100}}");
        first.emit("{\"op\":0,\"s\":1,\"t\":\"READY\","
                + "\"d\":{\"session_id\":\"heartbeat-session\"}}");

        await(() -> connector.connections.size() == 2);

        assertTrue(first.socket.aborted);
        assertTrue(status(session).lastError().contains("heartbeat acknowledgement timed out"));
        session.close();
    }

    @Test
    void repeatedConnectFailureEventuallyBecomesFailed() throws Exception {
        QqGatewayConfigurableConnector connector = new QqGatewayConfigurableConnector();
        QqPlatformConfig platformConfig = config(1);
        QqGatewaySession session = tracked(statusUpdates -> new QqGatewaySession(platformConfig,
                () -> CompletableFuture.completedFuture(
                        URI.create("wss://gateway.example/socket")),
                () -> CompletableFuture.completedFuture("access-token"),
                () -> { }, connector, event -> SocialEventSink.Acceptance.ACCEPTED,
                statusUpdates, Logger.getLogger("QqGatewaySessionTest")));

        session.start();
        await(() -> status(session).state() == SocialSessionStatus.State.FAILED);

        assertEquals(2, connector.attempts);
        assertTrue(status(session).lastError().contains("connect failed"));
        session.close();
    }

    @Test
    void oversizedTextFrameIsRejectedAndReconnected() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);
        session.start();

        connector.only().emit("x".repeat(4_097));
        await(() -> connector.connections.size() == 2);

        assertTrue(connector.connections.getFirst().socket.aborted);
        assertTrue(status(session).lastError().contains("exceeded the size limit"));
        session.close();
    }

    @Test
    void closeCancelsAPendingReconnect() throws Exception {
        RecordingConnector connector = new RecordingConnector();
        QqGatewaySession session = session(config(3), connector,
                event -> SocialEventSink.Acceptance.ACCEPTED);
        session.start();
        connector.only().emit("{\"op\":7,\"d\":null}");

        session.close();
        TimeUnit.MILLISECONDS.sleep(150);

        assertEquals(SocialSessionStatus.State.STOPPED, status(session).state());
        assertEquals(1, connector.connections.size());
    }

    private static QqGatewaySession session(
            QqPlatformConfig config,
            RecordingConnector connector,
            java.util.function.Function<QqGatewayGroupMessage,
                    SocialEventSink.Acceptance> events
    ) {
        return tracked(statusUpdates -> new QqGatewaySession(config,
                () -> CompletableFuture.completedFuture(
                        URI.create("wss://gateway.example/socket")),
                () -> CompletableFuture.completedFuture("access-token"),
                () -> { }, connector, events, statusUpdates,
                Logger.getLogger("QqGatewaySessionTest")));
    }

    private static QqGatewaySession tracked(
            java.util.function.Function<java.util.function.Consumer<SocialSessionStatus>,
                    QqGatewaySession> factory
    ) {
        AtomicReference<SocialSessionStatus> latest = new AtomicReference<>();
        QqGatewaySession session = factory.apply(latest::set);
        STATUSES.put(session, latest);
        return session;
    }

    private static SocialSessionStatus status(QqGatewaySession session) {
        return STATUSES.get(session).get();
    }

    private static QqPlatformConfig config(int reconnectAttempts) throws Exception {
        return config(reconnectAttempts, 30_000);
    }

    private static QqPlatformConfig config(int reconnectAttempts,
                                           long handshakeTimeoutMillis) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                enabled: true
                app-id: app
                app-secret: secret
                gateway:
                  reconnect-base-delay-millis: 100
                  reconnect-max-delay-millis: 100
                  max-reconnect-attempts: %d
                  handshake-timeout-millis: %d
                  max-frame-bytes: 4096
                """.formatted(reconnectAttempts, handshakeTimeoutMillis));
        return QqPlatformConfig.from(yaml);
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "condition did not become true before timeout");
    }

    private static final class RecordingConnector implements QqGatewaySession.Connector {
        private final List<Connection> connections = new CopyOnWriteArrayList<>();

        @Override
        public CompletableFuture<WebSocket> connect(URI endpoint, WebSocket.Listener listener) {
            FakeWebSocket socket = new FakeWebSocket();
            Connection connection = new Connection(listener, socket);
            connections.add(connection);
            listener.onOpen(socket);
            return CompletableFuture.completedFuture(socket);
        }

        private Connection only() {
            assertEquals(1, connections.size());
            return connections.getFirst();
        }
    }

    private static final class QqGatewayConfigurableConnector
            implements QqGatewaySession.Connector {
        private volatile int attempts;

        @Override
        public CompletableFuture<WebSocket> connect(URI endpoint, WebSocket.Listener listener) {
            attempts++;
            return CompletableFuture.failedFuture(
                    new IllegalStateException("connect failed"));
        }
    }

    private record Connection(WebSocket.Listener listener, FakeWebSocket socket) {
        private void emit(String payload) {
            emitFragment(payload, true);
        }

        private void emitFragment(String payload, boolean last) {
            listener.onText(socket, payload, last).toCompletableFuture().join();
        }

        private void close(int code, String reason) {
            listener.onClose(socket, code, reason).toCompletableFuture().join();
        }
    }

    private static final class FakeWebSocket implements WebSocket {
        private final List<String> sent = new ArrayList<>();
        private boolean outputClosed;
        private boolean inputClosed;
        private boolean aborted;

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            sent.add(data.toString());
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            outputClosed = true;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return outputClosed;
        }

        @Override
        public boolean isInputClosed() {
            return inputClosed;
        }

        @Override
        public void abort() {
            aborted = true;
            outputClosed = true;
            inputClosed = true;
        }
    }
}
