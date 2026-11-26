package org.encinet.mik.feature.community.chat.bridge;

import org.bukkit.plugin.Plugin;
import org.encinet.mik.protocol.ChatBody;
import org.encinet.mik.protocol.ChatDestination;
import org.encinet.mik.protocol.ChatEvent;
import org.encinet.mik.protocol.ChatIdentity;
import org.encinet.mik.protocol.ChatPart;
import org.encinet.mik.protocol.ChatPlayer;
import org.encinet.mik.protocol.ChatRecord;
import org.encinet.mik.protocol.ChatTextRun;
import org.encinet.mik.protocol.ChatUserRecord;
import org.encinet.mik.protocol.PaperChatDelivery;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperChatBridgeTest {
    @Test void badDecodeAndFailedDisplayDoNotDiscardOtherMessagesInTheBatch() {
        var plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var seen = new ArrayList<String>();
        var first = wire("first", "one", UUID.randomUUID().toString());
        var invalid = wire("decode-error", "not delivered", "invalid UUID");
        var failed = wire("display-error", "not delivered", UUID.randomUUID().toString());
        var last = wire("last", "two", UUID.randomUUID().toString());

        try (var bridge = new PaperChatBridge(plugin, null, delivery -> {
            if (delivery.message().id().value().equals("display-error")) throw new IllegalStateException();
            seen.add(delivery.message().content().plainText());
        }, (_, _) -> { })) {
            bridge.deliverBatch(bridge.decodeBatch(List.of(first, invalid, failed, last)));
        }

        assertEquals(List.of("one", "two"), seen);
    }

    private static PaperChatDelivery wire(String id, String text, String author) {
        var body = ChatBody.newBuilder().addParts(ChatPart.newBuilder().setText(ChatTextRun.newBuilder().setText(text)));
        var user = ChatUserRecord.newBuilder()
                .setAuthor(ChatIdentity.newBuilder().setPlayer(ChatPlayer.newBuilder().setId(author).setName("Reader")))
                .setDestination(ChatDestination.newBuilder().setRoom(ChatDestination.Room.PUBLIC)).setBody(body);
        var record = ChatRecord.newBuilder().setPlatform("web").setUser(user);
        return PaperChatDelivery.newBuilder().setEvent(ChatEvent.newBuilder().setMessageId(id).setRecord(record)).build();
    }
}
