package org.encinet.mik.module.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatMessageFormatterTest {

    @Test
    void repeatSuffixUsesTheExpectedTextColorAndClickAction() {
        Component suffix = ChatMessageFormatter.repeatSuffix("/mikrepeat token");

        assertEquals(" [+1]", PlainTextComponentSerializer.plainText().serialize(suffix));
        assertEquals(NamedTextColor.GRAY, suffix.color());
        assertEquals(ClickEvent.runCommand("/mikrepeat token"), suffix.clickEvent());
        assertEquals(Component.empty(), ChatMessageFormatter.repeatSuffix(null));
    }

}
