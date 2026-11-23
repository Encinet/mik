package org.encinet.mik.integration.mikd;

import com.google.gson.JsonParser;
import org.encinet.mik.protocol.ChatBody;
import org.encinet.mik.protocol.ChatCommand;
import org.encinet.mik.protocol.ChatGameRoster;
import org.encinet.mik.protocol.ChatNoticeRecord;
import org.encinet.mik.protocol.ChatPart;
import org.encinet.mik.protocol.ChatPoll;
import org.encinet.mik.protocol.ChatRecord;
import org.encinet.mik.protocol.ConnectionRole;
import org.encinet.mik.protocol.HistoryChannel;
import org.encinet.mik.protocol.HistoryEntry;
import org.encinet.mik.protocol.AccountPolicy;
import org.encinet.mik.protocol.GameQueryExchange;
import org.encinet.mik.protocol.GameQueryReply;
import org.encinet.mik.protocol.GameRoster;
import org.encinet.mik.protocol.GameStatusView;
import org.encinet.mik.protocol.PaperStateUpdate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class WebChatInteropHarness {
    private WebChatInteropHarness() { }

    public static void main(String[] args) throws Exception {
        var script = Path.of(args[0]).toAbsolutePath();
        try (var backend = MikdTestBackend.webApi(Files.createTempDirectory("mik-chat-interop-"));
             var http = HttpClient.newHttpClient()) {
            var daemon = backend.requests();
            for (int index = 0; index < 270; index++) {
                var message = HistoryEntry.newBuilder().setChannel(HistoryChannel.HISTORY_PUBLIC).setMessageId("interop-" + index)
                        .setRecord(ChatRecord.newBuilder().setCreatedAt(System.currentTimeMillis()).setNotice(ChatNoticeRecord.newBuilder()
                                .setCountsAsUnread(true).setBody(ChatBody.newBuilder().addParts(ChatPart.newBuilder()
                                        .setText("跨端历史 " + index + " 🌍 " + "repeated multilingual text 历史内容 ".repeat(10))))));
                daemon.chat(ChatCommand.newBuilder().setNotice(message).build()).get(5, TimeUnit.SECONDS);
            }
            var status = daemon.status(ConnectionRole.GENERAL).get(5, TimeUnit.SECONDS).daemon();
            var key = backend.log().lines().filter(line -> line.startsWith("Web chat public key: ")).findFirst().orElseThrow().substring(21);
            var delivery = new CompletableFuture<HistoryEntry>();
            var state = Thread.ofVirtual().start(() -> {
                try {
                    var update = PaperStateUpdate.newBuilder().setServer(GameStatusView.getDefaultInstance())
                            .setPublicRoster(GameRoster.getDefaultInstance()).setChatRoster(ChatGameRoster.getDefaultInstance());
                    for (long version = 1; !Thread.currentThread().isInterrupted(); version++) {
                        daemon.publish(update.setVersion(version).build()).get();
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                catch (Exception error) { delivery.completeExceptionally(error); }
            });
            var paper = Thread.ofVirtual().start(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        var result = daemon.chat(ChatCommand.newBuilder().setPoll(ChatPoll.getDefaultInstance()).build()).get();
                        for (var entry : result.getDeliveries().getEntriesList()) {
                            if (entry.getRecord().hasUser()) delivery.complete(entry);
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
                                .setId(result.getJob().getId()).setAccount(AccountPolicy.getDefaultInstance())).build()
                                : GameQueryExchange.getDefaultInstance();
                    }
                } catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                catch (Exception error) { delivery.completeExceptionally(error); }
            });
            try {
                var command = new ProcessBuilder("bun", script.toString()).directory(script.getParent().getParent().getParent().toFile())
                        .redirectError(ProcessBuilder.Redirect.INHERIT);
                command.environment().put("NEXT_PUBLIC_WEB_CHAT_PUBLIC_KEY", key);
                command.environment().put("MIK_CHAT_INTEROP_URL", "ws://127.0.0.1:" + status.getWebChatPort() + "/ws");
                var client = command.start();
                var output = new CompletableFuture<Boolean>();
                var reader = Thread.ofVirtual().start(() -> {
                    try (var lines = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                        boolean passed = false;
                        for (String line; (line = lines.readLine()) != null;) {
                            if (line.startsWith("AUTHORIZE ")) {
                                var grant = JsonParser.parseString(line.substring(10)).getAsJsonObject();
                                long now = System.currentTimeMillis();
                                grant.addProperty("playerUuid", "11111111-1111-4111-8111-111111111111");
                                grant.addProperty("playerName", "Owner");
                                grant.addProperty("sessionRef", "a".repeat(43));
                                grant.addProperty("authenticatedAt", now);
                                grant.addProperty("checkedAt", now);
                                grant.addProperty("expiresAt", now + 60_000);
                                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + status.getWebApiPort() + "/api/chat/authorize"))
                                        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(grant.toString())).build();
                                var response = http.send(request, HttpResponse.BodyHandlers.ofString());
                                if (response.statusCode() != 200) throw new IllegalStateException(response.body());
                            } else if (line.equals("PASSED")) passed = true;
                            else System.out.println(line);
                        }
                        output.complete(passed);
                    } catch (Exception error) { output.completeExceptionally(error); }
                });
                try {
                    if (!output.get(25, TimeUnit.SECONDS) || !client.waitFor(5, TimeUnit.SECONDS) || client.exitValue() != 0) {
                        throw new IllegalStateException("Browser-client interoperability failed");
                    }
                    var received = delivery.get(5, TimeUnit.SECONDS).getRecord().getUser().getBody().getParts(0).getText();
                    if (!received.equals("跨端发送 🌍")) throw new IllegalStateException("Paper delivery did not match the browser message");
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
