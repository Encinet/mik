package org.encinet.mik.feature.community.chat.web;

import org.encinet.mik.feature.community.chat.history.ChatHistoryStore;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.FluentTestSupport;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WebChatHistoryTest {
    @Test void aHistoryResponseGroupsEqualScopesAndSeparatesRestrictedMessagesAndMetadata() throws Exception {
        UUID viewer = UUID.randomUUID();
        var rows = new java.util.ArrayList<ChatHistoryStore.Row>();
        var ids = new java.util.ArrayList<String>();
        for (int i = 1; i <= 3; i++) {
            var message = ChatServerMessage.publicMessage(new ChatServerMessage.RichText(ChatContent.plain("message " + i + " 界".repeat(800))), null, true);
            var entry = WebChatHistory.server(message, Optional.empty());
            rows.addFirst(new ChatHistoryStore.Row(i, entry.payload(), System.currentTimeMillis() + 60_000,
                    i == 1 ? "restricted:1" : "public"));
            ids.add(entry.id());
        }
        var parts = WebChatHistory.responseParts(new ChatHistoryStore.SyncPage(new ChatHistoryStore.Page(rows, false, 1, UUID.randomUUID().toString(), 3), "a".repeat(91), _ -> "b".repeat(91), false),
                ChatChannel.PUBLIC, "0123456789abcdef", new WebChatHistory.Viewer(viewer, Set.of(), Map.of()));
        assertTrue(parts.stream().anyMatch(part -> part.contains(ids.get(1)) && part.contains(ids.get(2))));
        for (String part : parts) if (part.contains(ids.getFirst())) {
            assertFalse(part.contains(ids.get(1)));
            assertFalse(part.contains(ids.get(2)));
            assertFalse(part.contains("0123456789abcdef"));
        }
        var packet = WebChatPacket.history(parts, true);
        assertEquals(WebChatPacket.COMPRESSED_APPLICATION, packet.kind());
        String json = String.join("", parts);
        assertEquals(json, WebChatPacketTest.expand(packet));
        var response = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        assertEquals("0123456789abcdef", response.get("requestId").getAsString());
        assertEquals(3, response.getAsJsonArray("messages").size());
        for (int index = 0; index < ids.size(); index++)
            assertEquals(ids.get(index), response.getAsJsonArray("messages").get(index).getAsJsonObject().get("id").getAsString());
    }

    @Test void queuedSnapshotsRetainVisibleItemsWithoutKeepingInventoriesOrMinecraftBinaryData() {
        UUID sender = UUID.randomUUID();
        var item = new ChatItemSnapshot("minecraft:stone", "Stone", 3, new byte[1_048_576]);
        var context = new ChatProcessingContext(Set.of(ChatCapability.ITEM), null, Optional.of(item), Map.of(0, item));
        var source = new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Web(sender), ChatSender.minecraft(sender, "Sender"),
                ChatConversation.minecraftPublic(), "[item]", ChatReferences.empty(), Instant.now(), context);
        var content = new ChatContent(List.of(ChatNode.Item.snapshot(item, ChatStyle.EMPTY)));
        var original = new ChatMessage(source, content, Set.of());
        var snapshot = WebChatHistory.snapshot(ChatDelivery.managed(original, null, "0123456789abcdef"));
        assertTrue(snapshot.message().submission().processingContext().inventory().isEmpty());
        assertTrue(snapshot.message().submission().processingContext().mainHand().isEmpty());
        assertTrue(((ChatNode.Item) snapshot.message().content().nodes().getFirst()).item().isEmpty());
        assertEquals(content.plainText(), snapshot.message().content().plainText());
        assertNull(snapshot.requestId());
        var server = WebChatHistory.snapshot(ChatServerMessage.publicMessage(new ChatServerMessage.RichText(content), null, true));
        var richText = (ChatServerMessage.RichText) server.content();
        assertTrue(((ChatNode.Item) richText.content().nodes().getFirst()).item().isEmpty());
        assertEquals(content.plainText(), richText.content().plainText());
        assertEquals(1_048_576, ((ChatNode.Item) original.content().nodes().getFirst()).item().orElseThrow().minecraftData().length);
    }

    @Test void paginatesLocalizedBytesWithoutSkippingRows(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var nodes = java.util.stream.IntStream.range(0, 256)
                .mapToObj(_ -> (ChatNode) new ChatNode.Placeholder(ChatPlaceholder.REPLY, ChatStyle.EMPTY)).toList();
        ChatContent content = new ChatContent(nodes);
        var languages = FluentTestSupport.languages();
        java.util.function.Function<org.encinet.mik.shared.i18n.Message, String> text = key -> languages.t(Language.TH_TH, key);
        try (var store = new ChatHistoryStore(temp.resolve("localized.db"))) {
            for (int i = 1; i <= 40; i++) {
                var message = ChatServerMessage.publicMessage(new ChatServerMessage.RichText(content), null, true);
                store.append(WebChatHistory.server(message, Optional.empty())).join();
            }
            UUID viewer = UUID.randomUUID();
            var first = store.read(new ChatHistoryStore.Query(viewer, false, ChatChannel.PUBLIC, null, Long.MAX_VALUE)).join();
            int expanded = 0;
            for (var row : first.rows()) expanded += WebChatHistory.present(row, viewer, _ -> true, text).orElseThrow()
                    .toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            assertTrue(expanded > 128 * 1024, "Regression requires localization to exceed the transport limit");
            Set<String> ids = new java.util.HashSet<>();
            long before = Long.MAX_VALUE;
            for (int attempt = 0; attempt < 40; attempt++) {
                var page = store.read(new ChatHistoryStore.Query(viewer, false, ChatChannel.PUBLIC, null, before)).join();
                var presented = WebChatHistory.presentPage(page, viewer, _ -> true, text);
                assertTrue(presented.messages().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= ChatHistoryStore.PAGE_BYTES);
                for (var message : presented.messages()) assertTrue(ids.add(message.getAsJsonObject().get("id").getAsString()));
                if (!presented.hasMore()) break;
                assertTrue(presented.nextBefore() < before);
                before = presented.nextBefore();
            }
            assertEquals(40, ids.size());
        }
    }

    @Test void oversizedLocalizedPresentationFallsBackToPlainTextWithoutLosingText() {
        ChatStyle style = new ChatStyle(0x123456, 0x654321, true, true, true, true);
        var nodes = java.util.stream.IntStream.range(0, 256)
                .mapToObj(_ -> (ChatNode) new ChatNode.Placeholder(ChatPlaceholder.REPLY, style)).toList();
        var message = ChatServerMessage.publicMessage(new ChatServerMessage.RichText(new ChatContent(nodes)), null, true);
        var entry = WebChatHistory.server(message, Optional.empty());
        String translated = "&".repeat(62);
        var restored = WebChatHistory.present(new ChatHistoryStore.Row(1, entry.payload()), UUID.randomUUID(),
                _ -> true, _ -> translated).orElseThrow();
        var parts = restored.getAsJsonArray("content");
        assertEquals(1, parts.size());
        assertTrue(parts.get(0).getAsJsonObject().get("text").getAsString().startsWith(translated.repeat(255)));
        assertTrue(restored.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length < ChatHistoryStore.PAGE_BYTES);
    }

    @Test void restoresSafeContentWithoutRequestIdsAndLocalizesOnlyTypedPlaceholders() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        ChatContent content = new ChatContent(List.of(new ChatNode.Placeholder(ChatPlaceholder.IMAGE, ChatStyle.EMPTY),
                new ChatNode.Text(" [image] ", ChatStyle.EMPTY),
                new ChatNode.Link("link", URI.create("https://example.org"), new ChatStyle(0x123456, null, true, false, false, false))));
        ChatSubmission submission = new ChatSubmission(ChatMessageId.random(), new ChatOrigin.Web(alice),
                ChatSender.minecraft(alice, "Alice"), ChatConversation.minecraftPublic(), content.plainText(), ChatReferences.empty(),
                Instant.now(), ChatProcessingContext.external());
        ChatMessage message = new ChatMessage(submission, content, Set.of(new ChatEffect.Mentions(Set.of(bob), false)));
        var entry = WebChatHistory.user(ChatDelivery.managed(message, null, "secret-request-id"));
        var languages = FluentTestSupport.languages();
        var restored = WebChatHistory.present(new ChatHistoryStore.Row(10, entry.payload()), bob, _ -> true,
                key -> languages.t(Language.DE_DE, key)).orElseThrow();
        assertFalse(entry.payload().contains("secret-request-id"));
        assertFalse(restored.has("requestId"));
        assertTrue(restored.get("mentioned").getAsBoolean());
        assertTrue(restored.get("content").toString().contains("[Bild]"));
        assertTrue(restored.get("content").toString().contains(" [image] "));
        assertTrue(restored.get("content").toString().contains("https://example.org"));
        assertFalse(restored.toString().contains("placeholder"));
        assertEquals(7, UUID.fromString(restored.get("order").getAsString()).version());
    }

    @Test void reconstructsPrivateServerMessagesForEachParticipantAndHonorsCurrentVisibility() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        var message = new ChatServerMessage(ChatMessageId.random(), ChatConversation.minecraftPrivate(alice, bob),
                new ChatServerMessage.RichText(ChatContent.plain("private notice")), Optional.empty(),
                Map.of(alice, "Alice", bob, "Bob"), true, Instant.now());
        var entry = WebChatHistory.server(message, Optional.empty());
        var row = new ChatHistoryStore.Row(1, entry.payload());
        for (UUID viewer : List.of(alice, bob)) {
            var restored = WebChatHistory.present(row, viewer, _ -> true, Enum::name).orElseThrow();
            assertEquals(viewer.toString(), restored.get("recipientUuid").getAsString());
            assertEquals((viewer.equals(alice) ? bob : alice).toString(), restored.get("targetUuid").getAsString());
        }
        assertTrue(WebChatHistory.present(row, alice, id -> !id.equals(bob), Enum::name).isEmpty());
        assertTrue(WebChatHistory.present(row, UUID.randomUUID(), _ -> true, Enum::name).isEmpty());
    }
}
