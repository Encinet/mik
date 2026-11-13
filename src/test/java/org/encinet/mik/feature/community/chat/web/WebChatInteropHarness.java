package org.encinet.mik.feature.community.chat.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.Channel;
import org.encinet.mik.feature.community.chat.history.ChatHistoryStore;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit cross-repository integration task: actual Bouncy Castle server and browser-client HPKE code. */
public final class WebChatInteropHarness implements WebChatSocketServer.Events {
    private static final UUID OWNER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final Map<String, Channel> connections = new ConcurrentHashMap<>();
    private final ChatHistoryStore history;
    private WebChatSocketServer server;

    private WebChatInteropHarness(Path database) { history = new ChatHistoryStore(database); }

    public static void main(String[] args) throws Exception {
        Path script = Path.of(args[0]).toAbsolutePath();
        var harness = new WebChatInteropHarness(Files.createTempDirectory("mik-chat-interop-").resolve("history.db"));
        var encryption = WebChatEncryption.generate();
        harness.server = new WebChatSocketServer(harness, "https://mcmik.top", 8, encryption);
        try {
            for (int index = 0; index < 270; index++) {
                var message = ChatServerMessage.publicMessage(new ChatServerMessage.RichText(ChatContent.plain(
                        "跨端历史 " + index + " 🌍 " + "repeated multilingual text 历史内容 ".repeat(10))), null, true);
                harness.history.append(WebChatHistory.server(message, java.util.Optional.empty())).get(5, TimeUnit.SECONDS);
            }
            harness.server.start(0);
            var listener = WebChatSocketServer.class.getDeclaredField("listener"); listener.setAccessible(true);
            int port = ((java.net.InetSocketAddress) ((Channel) listener.get(harness.server)).localAddress()).getPort();
            for (boolean compression : new boolean[]{true, false}) harness.runClient(script, port, encryption.publicKey(), compression);
        } finally {
            harness.server.close(); harness.history.close(); harness.history.stopped().get(5, TimeUnit.SECONDS);
        }
    }

    private void runClient(Path script, int port, String publicKey, boolean compression) throws Exception {
        var builder = new ProcessBuilder("bun", script.toString()).directory(script.getParent().getParent().getParent().toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("NEXT_PUBLIC_WEB_CHAT_PUBLIC_KEY", publicKey);
        builder.environment().put("MIK_CHAT_INTEROP_URL", "ws://127.0.0.1:" + port + "/ws");
        builder.environment().put("MIK_CHAT_NO_DEFLATE", Boolean.toString(!compression));
        Process client = builder.start();
        var passed = new AtomicBoolean();
        var failure = new AtomicReference<Exception>();
        Thread output = Thread.ofVirtual().start(() -> {
            try (var reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    if (line.startsWith("AUTHORIZE ")) {
                        var grant = JsonParser.parseString(line.substring(10)).getAsJsonObject();
                        Channel channel = connections.get(grant.get("connectionId").getAsString());
                        if (channel == null) throw new IllegalStateException("Unknown integration connection");
                        WebChatSocketServer.authorize(channel, grant.get("clientPublicKey").getAsString());
                    } else if (line.equals("PASSED")) passed.set(true);
                    else System.out.println(line);
                }
            } catch (Exception error) { failure.set(error); }
        });
        try {
            if (!client.waitFor(25, TimeUnit.SECONDS)) throw new IllegalStateException("Browser-client integration timed out");
            output.join(2000);
            if (client.exitValue() != 0 || !passed.get() || failure.get() != null)
                throw new IllegalStateException("Browser-client integration failed", failure.get());
            System.out.println("Chat interoperability passed (deflate=" + compression + ")");
        } finally { if (client.isAlive()) client.destroyForcibly(); }
    }

    @Override public void opened(Channel channel, String address) {
        byte[] bytes = new byte[24]; new java.security.SecureRandom().nextBytes(bytes);
        String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        connections.put(id, channel);
        WebChatSocketServer.send(channel, "{\"type\":\"hello\",\"connectionId\":\"" + id + "\"}");
    }
    @Override public void ready(Channel channel) {
        WebChatSocketServer.send(channel, new com.google.gson.Gson().toJson(Map.of("type", "authorized", "playerUuid", OWNER.toString(),
                "name", "Owner", "staff", false, "expiresAt", System.currentTimeMillis() + 60_000, "history", true)));
    }
    @Override public void message(Channel channel, String input) {
        try {
            JsonObject request = WebChatModule.readMessage(input);
            String id = request.get("requestId").getAsString();
            if (request.get("type").getAsString().equals("history")) {
                var query = new ChatHistoryStore.SyncQuery(OWNER, false, ChatChannel.PUBLIC, null,
                        request.has("since") ? request.get("since").getAsString() : null,
                        request.has("page") ? request.get("page").getAsString() : null, Set.of());
                history.sync(query, page -> WebChatPacket.history(WebChatHistory.responseParts(page, ChatChannel.PUBLIC, id,
                        new WebChatHistory.Viewer(OWNER, Set.of(), Map.of())), WebChatSocketServer.compression(channel)))
                        .thenAccept(packet -> WebChatSocketServer.send(channel, packet));
            } else {
                var message = new ChatProcessor().process(new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Web(OWNER),
                        ChatSender.minecraft(OWNER, "Owner"), ChatConversation.minecraftPublic(), request.get("text").getAsString(),
                        ChatReferences.empty(), Instant.now(), ChatProcessingContext.external()));
                history.append(WebChatHistory.user(ChatDelivery.managed(message, null, id))).thenAccept(saved ->
                    channel.eventLoop().execute(() -> {
                        try {
                            var cipher = channel.pipeline().get(WebChatCipher.class);
                            var budget = WebChatCipher.class.getDeclaredField("writtenRecords"); budget.setAccessible(true);
                            budget.setInt(cipher, WebChatCipher.MAX_RECORDS);
                            JsonObject payload = new WebChatMessage(message, id, null).payload();
                            payload.addProperty("order", saved.order()); payload.addProperty("mentioned", false);
                            JsonObject event = new JsonObject(); event.addProperty("type", "message"); event.add("message", payload);
                            WebChatSocketServer.send(channel, event.toString());
                            WebChatSocketServer.send(channel, new com.google.gson.Gson().toJson(Map.of("type", "accepted", "requestId", id, "messageId", saved.id())));
                        } catch (ReflectiveOperationException error) { channel.close(); }
                    }));
            }
        } catch (Exception error) { channel.close(); }
    }
    @Override public void closed(Channel channel) { connections.values().removeIf(value -> value == channel); }
    @Override public void pong(Channel channel) { }
}
