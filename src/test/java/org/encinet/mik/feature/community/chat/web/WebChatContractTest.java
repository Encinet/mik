package org.encinet.mik.feature.community.chat.web;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.encinet.mik.feature.community.chat.model.*;
import org.encinet.mik.feature.community.chat.pipeline.ChatProcessor;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WebChatContractTest {
    @Test void projectsEveryUserSourceAndExportsActualBrowserFrames() throws Exception {
        UUID player = UUID.fromString("11111111-1111-4111-8111-111111111111");
        var identity = new ExternalIdentity(new ExternalIdentityKey("matrix", "https://example.org", "room", "@guest:example.org"), "Guest");
        JsonArray frames = new JsonArray();
        List<ChatOrigin> origins = List.of(new ChatOrigin.Minecraft(player), new ChatOrigin.Web(player),
                new ChatOrigin.Social("matrix", "Matrix", 0x55FFFF, "room", "event"));
        for (ChatOrigin origin : origins) {
            for (boolean bound : List.of(false, true)) {
                if (origin instanceof ChatOrigin.Player && !bound) continue;
                ChatSender sender = origin instanceof ChatOrigin.Social
                        ? new ChatSender("Guest".repeat(25), bound ? Optional.of(player) : Optional.empty(), Optional.of(identity), bound ? "Alice" : "", "", "", 0xFFFFFF)
                        : ChatSender.minecraft(player, "Alice");
                ChatSubmission submission = new ChatSubmission(origin instanceof ChatOrigin.Social social
                        ? ChatMessageId.external(social.platformId(), social.conversationId(), "event".repeat(100)) : ChatMessageId.random(),
                        origin, sender, ChatConversation.minecraftPublic(), "<script>safe text</script> https://example.org", ChatReferences.empty(), Instant.now(), ChatProcessingContext.external());
                JsonObject payload = new WebChatMessage(new ChatProcessor().process(submission), null, null).payload();
                payload.addProperty("mentioned", false); payload.addProperty("sequence", frames.size() + 1);
                payload.addProperty("streamId", "44444444-4444-4444-8444-444444444444");
                assertEquals("user", payload.get("kind").getAsString());
                assertFalse(payload.has("senderUuid"));
                assertEquals(bound, payload.getAsJsonObject("sender").has("playerUuid"));
                assertFalse(payload.getAsJsonObject("sender").get("id").getAsString().contains(identity.key().subject()));
                JsonObject event = new JsonObject(); event.addProperty("type", "message"); event.add("message", payload); frames.add(event);
            }
        }
        Path fixture = Path.of("build/test-fixtures/chat-messages.json");
        Files.createDirectories(fixture.getParent()); Files.writeString(fixture, new Gson().toJson(frames));
        assertEquals(4, frames.size());
    }
}
