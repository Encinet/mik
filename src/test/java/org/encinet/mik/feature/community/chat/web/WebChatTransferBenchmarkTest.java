package org.encinet.mik.feature.community.chat.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.encinet.mik.feature.community.chat.history.ChatHistoryStore;
import org.encinet.mik.feature.community.chat.model.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("benchmark")
class WebChatTransferBenchmarkTest {
    @Test void transfersSevenDaysOfMixedTrafficThroughRealHistoryPages(@org.junit.jupiter.api.io.TempDir Path temp) throws Exception {
        UUID viewer = UUID.randomUUID();
        var peers = java.util.stream.IntStream.range(0, 5).mapToObj(_ -> UUID.randomUUID()).toList();
        String stream = UUID.randomUUID().toString();
        int total = 7 * 1000;
        try (var store = new ChatHistoryStore(temp.resolve("seven-days.db"))) {
            for (int index = 0; index < total; index++) {
                var channel = index % 20 < 16 ? ChatChannel.PUBLIC : index % 20 == 16 ? ChatChannel.STAFF : ChatChannel.PRIVATE;
                store.append(entry(index, viewer, peers.get((index / 10) % peers.size()), channel, stream)).join();
            }
            int pages = 0, count = 0;
            long raw = 0, encoded = 0;
            long start = System.nanoTime();
            for (var channel : ChatChannel.values()) {
                long before = Long.MAX_VALUE;
                for (;;) {
                    var page = store.read(new ChatHistoryStore.Query(viewer, true, channel, null, before)).join();
                    var parts = WebChatHistory.responseParts(page, channel, "0123456789abcdef",
                            new WebChatHistory.Viewer(viewer, Set.of(), Map.of()));
                    String json = String.join("", parts);
                    var response = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
                    var packet = WebChatPacket.history(parts, true);
                    raw += json.getBytes(StandardCharsets.UTF_8).length;
                    encoded += packet.size();
                    count += response.getAsJsonArray("messages").size();
                    pages++;
                    if (!response.get("hasMore").getAsBoolean()) break;
                    long next = Long.parseLong(response.get("nextBefore").getAsString());
                    assertTrue(next < before);
                    before = next;
                }
            }
            assertEquals(total, count);
            assertTrue(encoded < raw);
            System.out.printf(java.util.Locale.ROOT, "Synthetic 7 days / 7000 messages: %d pages; %d -> %d bytes; %.1f%% saved; %.1f ms local read/project/encode (network and pacing excluded)%n",
                    pages, raw, encoded, 100.0 * (1 - (double) encoded / raw), (System.nanoTime() - start) / 1_000_000.0);
        }
    }

    @Test void preparesAnIsolatedHundredMessagePageAndExportsABrowserInteropVector() throws Exception {
        var rows = new ArrayList<ChatHistoryStore.Row>();
        UUID viewer = UUID.randomUUID(), peer = UUID.randomUUID();
        String stream = UUID.randomUUID().toString();
        for (int index = 0; index < 100; index++) {
            var entry = entry(index, viewer, peer, ChatChannel.PRIVATE, stream);
            rows.addFirst(new ChatHistoryStore.Row(index + 1, entry.payload(), 1_800_604_800_000L + index * 1000,
                    "private:" + peer + ":" + viewer));
        }
        var parts = WebChatHistory.responseParts(new ChatHistoryStore.Page(rows, false, 1, UUID.randomUUID().toString(), 100),
                ChatChannel.PRIVATE, "0123456789abcdef", new WebChatHistory.Viewer(viewer, Set.of(), Map.of()));
        byte[] original = String.join("", parts).getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 200; i++) WebChatPacket.history(parts, true);
        long start = System.nanoTime();
        long bytes = 0;
        for (int i = 0; i < 1000; i++) bytes += WebChatPacket.history(parts, true).copyPayload().length;
        double micros = (System.nanoTime() - start) / 1_000_000.0;
        var packet = WebChatPacket.history(parts, true);
        assertEquals(WebChatPacket.COMPRESSED_APPLICATION, packet.kind());
        assertTrue(bytes < 1000L * original.length);
        System.out.printf(java.util.Locale.ROOT, "Synthetic 100-message history: %d -> %d bytes; %.1f%% saved; %.1f µs/page (1000 iterations)%n",
                original.length, packet.copyPayload().length, 100.0 * (1.0 - (double) bytes / (1000L * original.length)), micros);
        var first = com.google.gson.JsonParser.parseString(String.join("", parts)).getAsJsonObject().getAsJsonArray("messages").get(0).getAsJsonObject();
        first.remove("expiresAt");
        var live = new com.google.gson.JsonObject(); live.addProperty("type", "message"); live.add("message", first);
        String shortJson = live.toString();
        var shortPacket = WebChatPacket.application(shortJson, true);
        assertEquals(WebChatPacket.APPLICATION, shortPacket.kind());
        first.add("content", WebChatContentRenderer.render(ChatContent.plain("较长日常富文本 🌍".repeat(500))));
        String richJson = live.toString();
        var richPacket = WebChatPacket.application(richJson, true);
        assertEquals(WebChatPacket.COMPRESSED_APPLICATION, richPacket.kind());
        System.out.printf(java.util.Locale.ROOT, "Synthetic live traffic: short %d bytes stays raw; rich %d -> %d bytes%n",
                shortPacket.size(), richJson.getBytes(StandardCharsets.UTF_8).length, richPacket.size());
        Path vector = Path.of("build/reports/benchmarks/chat-history-wire.js");
        Files.createDirectories(vector.getParent());
        Files.writeString(vector, "globalThis.chatHistoryWireFixture={payload:'" + Base64.getEncoder().encodeToString(packet.copyPayload())
                + "',clear:'" + Base64.getEncoder().encodeToString(original)
                + "',livePayload:'" + Base64.getEncoder().encodeToString(richPacket.copyPayload())
                + "',liveClear:'" + Base64.getEncoder().encodeToString(richJson.getBytes(StandardCharsets.UTF_8)) + "'};", StandardCharsets.UTF_8);
    }

    private static ChatHistoryStore.Entry entry(int index, UUID viewer, UUID peer, ChatChannel channel, String stream) {
        String text = index + " 今天一起完成建筑，Meet at " + index * 12 + ", 64, -300 🌍";
        var conversation = switch (channel) {
            case PUBLIC -> ChatConversation.minecraftPublic();
            case STAFF -> ChatConversation.minecraftStaff();
            case PRIVATE -> ChatConversation.minecraftPrivate(peer, viewer);
        };
        var submission = new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Web(peer), ChatSender.minecraft(peer, "Alex"),
                conversation, text, ChatReferences.empty(), Instant.ofEpochMilli(1_800_000_000_000L + index * 1000), ChatProcessingContext.external());
        var message = new ChatMessage(submission, ChatContent.plain(text), Set.of());
        return WebChatHistory.user(ChatDelivery.managed(message, channel == ChatChannel.PRIVATE ? ChatSender.minecraft(viewer, "Viewer") : null, null), stream, index + 1);
    }
}
