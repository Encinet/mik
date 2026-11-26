package org.encinet.mik.integration.mikd;

import com.google.gson.JsonParser;
import org.encinet.mik.feature.community.chat.bridge.ChatRecordCodec;
import org.encinet.mik.feature.community.chat.input.AuthorizedChatMarkup;
import org.encinet.mik.feature.community.chat.model.ChatMentions;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatPresence;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.parse.ChatProcessor;
import org.encinet.mik.test.ChatTestMessages;
import org.encinet.mik.protocol.ChatGamePeer;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ChatDestination;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.ChatPublication;
import org.encinet.mik.protocol.ChatPart;
import org.encinet.mik.protocol.ChatTextRun;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.AccountPolicy;
import org.encinet.mik.protocol.GameQueryExchange;
import org.encinet.mik.protocol.GameQueryReply;
import org.encinet.mik.protocol.GameRoster;
import org.encinet.mik.protocol.GameStatusView;
import org.encinet.mik.protocol.PaperStateUpdate;
import org.encinet.mik.protocol.PaperChatDelivery;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WebChatInteropHarness {
    private WebChatInteropHarness() { }

    public static void main(String[] args) throws Exception {
        var script = Path.of(args[0]).toAbsolutePath();
        var webProject = Path.of(args[1]).toAbsolutePath();
        var directory = Files.createTempDirectory("mik-chat-interop-");
        try (var backend = MikdTestBackend.webApi(directory);
             var http = HttpClient.newHttpClient()) {
            var daemon = backend.requests();
            var writer = ChatPlayer.newBuilder().setId("22222222-2222-4222-8222-222222222222").setName("Writer");
            var owner = ChatPlayer.newBuilder().setId("11111111-1111-4111-8111-111111111111").setName("Owner");
            var privatePost = ChatPublication.Post.newBuilder().setAuthor(writer)
                    .setDirect(ChatPublication.Direct.newBuilder().setRecipient(owner))
                    .addParts(ChatPublication.Post.Part.newBuilder().setResolved(ChatPart.newBuilder()
                            .setText(ChatTextRun.newBuilder().setText("仅接收者可读 ".repeat(160)))));
            daemon.publishChat(ChatPublication.newBuilder().setMessageId("private-interop")
                    .setCreatedAtMs(System.currentTimeMillis()).setPost(privatePost).build(), () -> true)
                    .get(5, TimeUnit.SECONDS);
            var other = ChatPlayer.newBuilder().setId("33333333-3333-4333-8333-333333333333").setName("Other");
            daemon.publishChat(ChatPublication.newBuilder().setMessageId("private-other")
                    .setCreatedAtMs(System.currentTimeMillis()).setPost(privatePost.setAuthor(other)).build(), () -> true)
                    .get(5, TimeUnit.SECONDS);
            for (int index = 0; index < 270; index++) {
                String id = "interop-" + index;
                var post = ChatPublication.Post.newBuilder().setRoom(ChatDestination.Room.PUBLIC).setAuthor(writer)
                        .addParts(ChatPublication.Post.Part.newBuilder().setResolved(ChatPart.newBuilder().setText(ChatTextRun.newBuilder()
                                .setText("跨端历史 " + index + " 🌍 " + "repeated multilingual text 历史内容 ".repeat(10)))));
                var publication = ChatPublication.newBuilder().setMessageId(id)
                        .setCreatedAtMs(System.currentTimeMillis()).setPost(post).build();
                daemon.publishChat(publication, () -> true).get(5, TimeUnit.SECONDS);
            }
            String wiki = "zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3";
            var content = new ChatProcessor(new AuthorizedChatMarkup()::parse).process(wiki, ChatProcessingContext.linksOnly());
            var publication = ChatRecordCodec.publication(ChatTestMessages.minecraft("Writer", content),
                    java.util.Optional.empty(), false);
            daemon.publishChat(publication, () -> true).get(5, TimeUnit.SECONDS);
            daemon.publishChat(ChatRecordCodec.publication(new ChatPresence(ChatMessageId.random(), java.time.Instant.now(),
                    UUID.fromString(writer.getId()), writer.getName(), true),
                    java.util.Optional.empty()), () -> true)
                    .get(5, TimeUnit.SECONDS);
            var status = daemon.status(ConnectionRole.GENERAL).get(5, TimeUnit.SECONDS).daemon();
            if (status.getWebChatPort() == 0) throw new IllegalStateException(backend.log());
            var delivery = new CompletableFuture<PaperChatDelivery>();
            var addPeer = new AtomicBoolean();
            var initialRoster = new CompletableFuture<Void>();
            var state = Thread.ofVirtual().start(() -> {
                try {
                    var update = PaperStateUpdate.newBuilder().setServer(GameStatusView.getDefaultInstance())
                            .setPublicRoster(GameRoster.getDefaultInstance()).setChatRoster(ChatGameRoster.newBuilder()
                                    .addPlayers(ChatGamePeer.newBuilder().setPlayer(writer).setEligible(true)));
                    for (long version = 1; !Thread.currentThread().isInterrupted(); version++) {
                        if (addPeer.compareAndSet(true, false)) {
                            update.setChatRoster(update.getChatRoster().toBuilder()
                                    .addPlayers(ChatGamePeer.newBuilder().setPlayer(other).setEligible(true)));
                        }
                        daemon.publish(update.setVersion(version).build()).get();
                        initialRoster.complete(null);
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                catch (Exception error) { initialRoster.completeExceptionally(error); delivery.completeExceptionally(error); }
            });
            var paper = Thread.ofVirtual().start(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        for (var entry : daemon.pollChat().get()) {
                            if (entry.getEvent().getRecord().hasUser()) delivery.complete(entry);
                        }
                    }
                } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                catch (Exception error) { delivery.completeExceptionally(error); }
            });
            var policy = Thread.ofVirtual().start(() -> {
                try {
                    GameQueryExchange exchange = GameQueryExchange.getDefaultInstance();
                    while (!Thread.currentThread().isInterrupted()) {
                        var result = daemon.accountQuery(exchange).get();
                        exchange = result.hasJob() ? GameQueryExchange.newBuilder().setReply(GameQueryReply.newBuilder()
                                .setId(result.getJob().getId()).setAccount(AccountPolicy.newBuilder()
                                        .setLocalCustodian(!result.getJob().getAccount().getUuid().equals(owner.getId())))).build()
                                : GameQueryExchange.getDefaultInstance();
                    }
                } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                catch (Exception error) { delivery.completeExceptionally(error); }
            });
            try {
                initialRoster.get(5, TimeUnit.SECONDS);
                var command = new ProcessBuilder("bun", script.toString()).directory(webProject.toFile())
                        .redirectError(ProcessBuilder.Redirect.INHERIT);
                command.environment().put("MIK_CHAT_WEB_PROJECT", webProject.toString());
                String prefix = "mikd Web chat public key: ";
                var publicKey = backend.log().lines().filter(line -> line.startsWith(prefix))
                        .map(line -> line.substring(prefix.length())).findFirst().orElseThrow();
                command.environment().put("MIK_CHAT_PUBLIC_KEY", publicKey);
                command.environment().put("MIK_CHAT_INTEROP_URL", "ws://127.0.0.1:" + status.getWebChatPort());
                var client = command.start();
                var output = new CompletableFuture<Boolean>();
                var reader = Thread.ofVirtual().start(() -> {
                    try (var lines = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                        boolean passed = false;
                        for (String line; (line = lines.readLine()) != null;) {
                            if (line.startsWith("AUTHORIZE ")) {
                                var grant = JsonParser.parseString(line.substring(10)).getAsJsonObject();
                                long now = System.currentTimeMillis();
                                String uuid = grant.get("playerUuid").getAsString();
                                var player = List.of(owner, writer, other).stream().filter(p -> p.getId().equals(uuid))
                                        .findFirst().orElseThrow();
                                grant.addProperty("playerName", player.getName());
                                grant.addProperty("sessionRef", uuid.substring(0, 1).repeat(43));
                                grant.addProperty("renewalToken", "b".repeat(43));
                                grant.addProperty("authenticatedAt", now);
                                grant.addProperty("checkedAt", now);
                                grant.addProperty("expiresAt", now + 60_000);
                                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + status.getWebApiPort() + "/api/chat/authorize"))
                                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(grant.toString())).build();
                                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                                if (response.statusCode() != 200) throw new IllegalStateException(response.body());
                            } else if (line.equals("REVOKE_OWNER")) {
                                var revoke = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + status.getWebApiPort() + "/api/chat/revoke"))
                                        .header("Content-Type", "application/json")
                                        .POST(HttpRequest.BodyPublishers.ofString("{\"sessionRefs\":[\"" + "1".repeat(43) + "\"]}")).build();
                                if (http.send(revoke, HttpResponse.BodyHandlers.discarding()).statusCode() != 200)
                                    throw new IllegalStateException("Could not revoke browser session");
                            } else if (line.equals("ADD_PEER")) addPeer.set(true);
                            else if (line.equals("BROADCAST_STAFF")) {
                                var post = ChatPublication.Post.newBuilder().setAuthor(writer).setRoom(ChatDestination.Room.STAFF)
                                        .addParts(ChatPublication.Post.Part.newBuilder().setResolved(ChatPart.newBuilder()
                                                .setText(ChatTextRun.newBuilder().setText("management broadcast ".repeat(12).trim()))));
                                var broadcast = ChatPublication.newBuilder().setCreatedAtMs(System.currentTimeMillis());
                                daemon.publishChat(broadcast.setMessageId("staff-live").setPost(post).build(), () -> true)
                                        .get(5, TimeUnit.SECONDS);
                                daemon.publishChat(broadcast.setMessageId("after-staff")
                                        .setPost(post.setRoom(ChatDestination.Room.PUBLIC)).build(), () -> true).get(5, TimeUnit.SECONDS);
                            } else if (line.equals("PASSED")) passed = true;
                            else System.out.println(line);
                        }
                        output.complete(passed);
                    } catch (Exception error) { output.completeExceptionally(error); }
                });
                try {
                    if (!output.get(55, TimeUnit.SECONDS) || !client.waitFor(5, TimeUnit.SECONDS) || client.exitValue() != 0) {
                        throw new IllegalStateException("Browser-client interoperability failed");
                    }
                    var incoming = delivery.get(5, TimeUnit.SECONDS);
                    var received = incoming.getEvent().getRecord().getUser().getBody();
                    if (!received.getParts(0).getText().getText().equals("跨端发送 🌍 ")
                            || !received.getParts(1).getReference().getLink().getUri().equals("https://minecraft.wiki/w/Diamond")
                            || !received.getParts(3).getReference().getLink().getUri().equals("mailto:help@example.org")
                            || !received.getParts(5).getReference().getPerson().getPlayer().getId().equals(writer.getId())
                            || !received.getParts(5).getReference().getRuns(0).getText().equals("@ Writer")
                            || !incoming.getMentionPlayerIdsList().equals(List.of(writer.getId()))
                            || !ChatRecordCodec.delivery(incoming).mentions().equals(
                                    new ChatMentions(Set.of(UUID.fromString(writer.getId())), false))) {
                        throw new IllegalStateException("Paper delivery did not preserve the browser message and its mention targets");
                    }
                } finally {
                    if (client.isAlive()) client.destroyForcibly();
                    reader.interrupt();
                }
            } finally {
                state.interrupt();
                paper.interrupt();
                policy.interrupt();
            }
        }
    }
}
