package org.encinet.mik.module.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageFormatterTest {

    @Test
    void channelMarkerPrecedesTheCompleteSenderIdentity() {
        Component privateBody = ChatMessageFormatter.privateBody(
                Component.text("[Bedrock] A"),
                Component.text("B"));
        Component prefix = ChatMessageFormatter.channelPrefix(
                Component.text("[MSG] "),
                privateBody);

        assertEquals("[MSG] [Bedrock] A -> B",
                PlainTextComponentSerializer.plainText().serialize(prefix));
    }

    @Test
    void repeatSuffixUsesTheExpectedTextColorAndClickAction() {
        Component suffix = ChatMessageFormatter.repeatSuffix("/mikrepeat token");

        assertEquals(" [+1]", PlainTextComponentSerializer.plainText().serialize(suffix));
        assertEquals(NamedTextColor.GRAY, suffix.color());
        assertEquals(ClickEvent.runCommand("/mikrepeat token"), suffix.clickEvent());
        assertEquals(Component.empty(), ChatMessageFormatter.repeatSuffix(null));
    }

}
