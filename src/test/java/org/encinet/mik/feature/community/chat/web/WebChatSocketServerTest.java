package org.encinet.mik.feature.community.chat.web;

import io.netty.channel.Channel;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.hpke.HPKE;
import org.bouncycastle.crypto.hpke.HPKEContext;
import org.bouncycastle.crypto.hpke.HPKEContextWithEncapsulation;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class WebChatSocketServerTest {
    private static final java.util.List<String> HISTORY = java.util.List.of("{\"type\":\"history\",\"messages\":[",
            "{\"text\":\"" + "私聊 private message 🌍".repeat(300) + "\"}", "]}");
    private static final String LIVE = "{\"type\":\"message\",\"text\":\"" + "较长的日常富文本 🌍".repeat(300) + "\"}";

    @Test void ordinaryAndCompressedHistoryBothRoundTripThroughAnEncryptedSocket() throws Exception {
        try (Fixture f = new Fixture(); HttpClient client = HttpClient.newHttpClient()) {
            SocketListener listener = new SocketListener();
            WebSocket socket = connect(client, f, listener);
            try {
                assertTrue(listener.hello.await(3, TimeUnit.SECONDS));
                for (String request : java.util.List.of("history-plain", "history-compressed")) {
                    listener.send(socket, request);
                    assertEquals(String.join("", HISTORY), listener.responses.poll(3, TimeUnit.SECONDS));
                }
                listener.send(socket, "live-rich");
                assertEquals(LIVE, listener.responses.poll(3, TimeUnit.SECONDS));
                assertEquals(WebChatPacket.COMPRESSED_APPLICATION, listener.lastKind);
            } finally { socket.abort(); }
        }
    }

    @Test void clientsWithoutCompressionReceiveTheSameLargeLiveMessageUncompressed() throws Exception {
        try (Fixture f = new Fixture(); HttpClient client = HttpClient.newHttpClient()) {
            SocketListener listener = new SocketListener(false);
            WebSocket socket = connect(client, f, listener);
            try {
                assertTrue(listener.hello.await(3, TimeUnit.SECONDS));
                listener.send(socket, "live-rich");
                assertEquals(LIVE, listener.responses.poll(3, TimeUnit.SECONDS));
                assertEquals(WebChatPacket.APPLICATION, listener.lastKind);
            } finally { socket.abort(); }
        }
    }

    @Test void anUnsupportedUpgradeCannotHoldAnUnauthenticatedConnectionOpen() throws Exception {
        try (Fixture f = new Fixture(); Socket socket = new Socket("127.0.0.1", URI.create(f.http).getPort())) {
            socket.setSoTimeout(6500);
            socket.getOutputStream().write(("GET /ws HTTP/1.1\r\nHost: localhost\r\n"
                    + "Origin: https://mcmik.top\r\nX-Forwarded-Proto: https\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Version: 12\r\nSec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n")
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertDoesNotThrow(() -> { while (socket.getInputStream().read() != -1) { } });
            assertEquals(0, f.opened.get());
        }
    }

    @Test void publicListenerRejectsOtherOriginsAndNeverExposesPrivateControlRoutes() throws Exception {
        try (Fixture f = new Fixture(); HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create(f.http + "/api/chat/authorize"))
                    .header("Origin", "https://mcmik.top").header("X-Forwarded-Proto", "https").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, response.statusCode()); assertTrue(response.body().isEmpty());
            assertThrows(ExecutionException.class, () -> client.newWebSocketBuilder().header("Origin", "https://evil.example")
                    .header("X-Forwarded-Proto", "https")
                    .buildAsync(URI.create(f.ws + "/ws"), new SocketListener()).get(3, TimeUnit.SECONDS));
            assertEquals(0, f.opened.get());
        }
    }

    @Test void upgradesRequireTheSecureTransportReportedByTheTrustedProxy() throws Exception {
        try (Fixture f = new Fixture(); HttpClient client = HttpClient.newHttpClient()) {
            for (String protocol : java.util.List.of("", "http", "https, http")) {
                var builder = client.newWebSocketBuilder().header("Origin", "https://mcmik.top");
                if (!protocol.isEmpty()) builder.header("X-Forwarded-Proto", protocol);
                assertThrows(ExecutionException.class, () -> builder.buildAsync(URI.create(f.ws + "/ws"),
                        new SocketListener()).get(3, TimeUnit.SECONDS));
            }
            assertEquals(0, f.opened.get());
            SocketListener listener = new SocketListener();
            WebSocket socket = connect(client, f, listener);
            try { assertTrue(listener.hello.await(3, TimeUnit.SECONDS)); }
            finally { socket.abort(); }
        }
    }

    @Test void handshakeAndTextWorkButBinaryAndOversizedFragmentedMessagesCloseTheConnection() throws Exception {
        try (Fixture f = new Fixture(); HttpClient client = HttpClient.newHttpClient()) {
            SocketListener listener = new SocketListener();
            WebSocket socket = connect(client, f, listener);
            try {
                assertTrue(listener.hello.await(3, TimeUnit.SECONDS));
                listener.send(socket, "hello");
                assertEquals("hello", f.messages.poll(3, TimeUnit.SECONDS));
                socket.sendBinary(ByteBuffer.wrap(new byte[]{1, 2}), true).join();
                assertTrue(listener.closed.await(3, TimeUnit.SECONDS));
            } finally { socket.abort(); }
            SocketListener fragmented = new SocketListener();
            WebSocket second = connect(client, f, fragmented);
            try {
                assertTrue(fragmented.hello.await(3, TimeUnit.SECONDS));
                second.sendBinary(ByteBuffer.wrap(new byte[5000]), false).join();
                second.sendBinary(ByteBuffer.wrap(new byte[5000]), true).join();
                assertTrue(fragmented.closed.await(3, TimeUnit.SECONDS));
                assertTrue(f.messages.isEmpty(), "oversized fragments must not reach business code");
            } finally { second.abort(); }
        }
    }

    private WebSocket connect(HttpClient client, Fixture f, SocketListener listener) throws Exception {
        f.authorizations.add(listener.authorizationKey);
        return client.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(2)).header("Origin", "https://mcmik.top")
                .header("X-Forwarded-Proto", "https")
                .buildAsync(URI.create(f.ws + "/ws"), listener).get(3, TimeUnit.SECONDS);
    }
    private static final class SocketListener implements WebSocket.Listener {
        final boolean compression;
        volatile byte lastKind = -1;
        SocketListener() { this(true); }
        SocketListener(boolean compression) { this.compression = compression; }
        final CountDownLatch hello = new CountDownLatch(1), closed = new CountDownLatch(1);
        final StringBuilder text = new StringBuilder();
        final java.io.ByteArrayOutputStream binary = new java.io.ByteArrayOutputStream();
        final BlockingQueue<String> responses = new LinkedBlockingQueue<>();
        String id;
        long generation;
        final AsymmetricCipherKeyPair reverse = WebChatEncryption.suite(HPKE.mode_base).generatePrivateKey();
        final String authorizationKey = WebChatEncryptionTest.publicKey(reverse);
        org.bouncycastle.crypto.params.AsymmetricKeyParameter serverKey;
        HPKEContextWithEncapsulation outgoing;
        HPKEContext incoming;
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence text, boolean last) {
            this.text.append(text);
            if (last) {
                try {
                    JsonObject frame = JsonParser.parseString(this.text.toString()).getAsJsonObject(); this.text.setLength(0);
                    if (frame.get("type").getAsString().equals("hello")) id = frame.get("connectionId").getAsString();
                    else if (frame.has("key")) {
                        generation = frame.get("generation").getAsLong();
                        var suite = WebChatEncryption.suite(HPKE.mode_base);
                        serverKey = suite.deserializePublicKey(WebChatEncryption.decode(frame.get("key").getAsString(), 65));
                        outgoing = suite.setupBaseS(serverKey, WebChatEncryption.info(id, generation, authorizationKey, "client"));
                        var reply = new JsonObject(); reply.addProperty("type", "key"); reply.addProperty("generation", generation);
                        reply.addProperty("enc", WebChatEncryption.encode(outgoing.getEncapsulation()));
                        reply.addProperty("ciphertext", WebChatEncryption.encode(outgoing.seal(WebChatEncryption.EMPTY, suite.serializePublicKey(reverse.getPublic()))));
                        socket.sendText(reply.toString(), true);
                    } else {
                        incoming = WebChatEncryption.suite(HPKE.mode_auth).setupAuthR(WebChatEncryption.decode(frame.get("enc").getAsString(), 65),
                                reverse, WebChatEncryption.info(id, generation, authorizationKey, "server"), serverKey);
                        assertArrayEquals(WebChatEncryption.READY, incoming.open(WebChatEncryption.EMPTY, WebChatEncryption.decode(frame.get("ciphertext").getAsString(), 21)));
                        byte[] proof = incoming.export(WebChatEncryption.info(id, generation, authorizationKey, "client-finished"), 32);
                        try {
                            var finished = new JsonObject(); finished.addProperty("type", "finished"); finished.addProperty("generation", generation);
                            finished.addProperty("verify", WebChatEncryption.encode(proof));
                            if (compression) finished.addProperty("compression", "deflate");
                            byte[] clear = ("\1" + finished).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                            try { socket.sendBinary(ByteBuffer.wrap(outgoing.seal(WebChatEncryption.EMPTY, clear)), true); }
                            finally { java.util.Arrays.fill(clear, (byte) 0); }
                        } finally { java.util.Arrays.fill(proof, (byte) 0); }
                    }
                } catch (Exception invalid) { socket.abort(); closed.countDown(); }
            }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        @Override public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
            byte[] part = new byte[data.remaining()]; data.get(part); binary.writeBytes(part);
            if (last) {
                try {
                    byte[] clear = incoming.open(WebChatEncryption.EMPTY, binary.toByteArray());
                    lastKind = clear[0];
                    String body;
                    if (clear[0] == WebChatPacket.COMPRESSED_APPLICATION)
                        body = WebChatPacketTest.expand(java.util.Arrays.copyOfRange(clear, 1, clear.length));
                    else {
                        assertEquals(0, clear[0]);
                        body = new String(clear, 1, clear.length - 1, java.nio.charset.StandardCharsets.UTF_8);
                    }
                    if (body.contains("authorized")) hello.countDown();
                    else responses.add(body);
                    binary.reset();
                } catch (Exception invalid) { socket.abort(); closed.countDown(); }
            }
            socket.request(1); return CompletableFuture.completedFuture(null);
        }
        void send(WebSocket socket, String text) throws Exception {
            byte[] bytes = ("\0" + text).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            socket.sendBinary(ByteBuffer.wrap(outgoing.seal(WebChatEncryption.EMPTY, bytes)), true).join();
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            closed.countDown(); return CompletableFuture.completedFuture(null);
        }
        @Override public void onError(WebSocket socket, Throwable error) { closed.countDown(); }
    }
    private static final class Fixture implements AutoCloseable {
        final AtomicInteger opened = new AtomicInteger();
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<String> authorizations = new LinkedBlockingQueue<>();
        final WebChatSocketServer server;
        final String http, ws;
        Fixture() throws Exception {
            server = new WebChatSocketServer(new WebChatSocketServer.Events() {
                @Override public void opened(Channel channel, String address) {
                    opened.incrementAndGet();
                    WebChatSocketServer.send(channel, "{\"type\":\"hello\",\"connectionId\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}");
                    WebChatSocketServer.authorize(channel, authorizations.remove());
                }
                @Override public void ready(Channel channel) { WebChatSocketServer.send(channel, "{\"type\":\"authorized\"}"); }
                @Override public void message(Channel channel, String text) {
                    if (text.equals("history-plain") || text.equals("history-compressed"))
                        WebChatSocketServer.send(channel, WebChatPacket.history(HISTORY, text.equals("history-compressed")));
                    else if (text.equals("live-rich")) WebChatSocketServer.send(channel, LIVE);
                    else messages.add(text);
                }
                @Override public void closed(Channel channel) { }
                @Override public void pong(Channel channel) { }
            }, "https://mcmik.top", 8, WebChatEncryption.generate());
            server.start(0);
            Field listener = WebChatSocketServer.class.getDeclaredField("listener"); listener.setAccessible(true);
            int port = ((InetSocketAddress) ((Channel) listener.get(server)).localAddress()).getPort();
            http = "http://127.0.0.1:" + port; ws = "ws://127.0.0.1:" + port;
        }
        @Override public void close() { server.close(); }
    }
}
