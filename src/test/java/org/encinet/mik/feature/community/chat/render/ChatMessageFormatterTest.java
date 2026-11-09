package org.encinet.mik.feature.community.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.player.profile.PlayerIdentityComponent;
import org.encinet.mik.feature.player.profile.PlayerSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMessageFormatterTest {

    @Test
    void channelMarkerPrecedesTheCompleteSenderIdentity() {
        Component privateBody = ChatMessageFormatter.privateBody(
                new PlayerIdentityComponent(Component.text("[Member] A"))
                        .chat(new PlayerSource("Bedrock")),
                Component.text("B"));
        Component prefix = ChatMessageFormatter.channelPrefix(
                Component.text("[MSG] "),
                privateBody);

        assertEquals("[MSG] [Bedrock] [Member] A -> B",
                PlainTextComponentSerializer.plainText().serialize(prefix));
    }

    @Test
    void repeatSuffixUsesTheExpectedTextColorAndClickAction() {
        Key identifier = Key.key("mik", "chat/repeat/00000000-0000-0000-0000-000000000001");
        Component suffix = ChatMessageFormatter.repeatSuffix(identifier);

        assertEquals(" [+1]", PlainTextComponentSerializer.plainText().serialize(suffix));
        assertEquals(NamedTextColor.GRAY, suffix.color());
        assertEquals(ClickEvent.custom(identifier), suffix.clickEvent());
        assertEquals(Component.empty(), ChatMessageFormatter.repeatSuffix(null));
    }

    @Test
    void externalChatUsesTheInteractivePublicMessagePipeline() {
        Component prefix = Component.text("[VIP] ", NamedTextColor.GOLD);
        Component name = Component.text("Steve", NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Matrix identity details")));
        Component suffix = Component.text(" [Member]", NamedTextColor.GRAY);
        Component sender = new PlayerIdentityComponent(Component.text().append(prefix).append(name).append(suffix).build())
                .chat(new PlayerSource("Matrix"));
        Component rendered = ChatMessageFormatter.channelMessage(
                Component.empty(),
                sender, Component.text(" »", NamedTextColor.GOLD),
                Component.text("你好"), "你好", "点击复制", null);

        assertEquals("[Matrix] [VIP] Steve [Member] » 你好",
                PlainTextComponentSerializer.plainText().serialize(rendered));
        Component interactiveBody = findWithClickEvent(rendered);
        assertNotNull(interactiveBody);
        assertEquals(ClickEvent.copyToClipboard("你好"),
                interactiveBody.clickEvent());
        assertNotNull(interactiveBody.hoverEvent());
        assertEquals(HoverEvent.Action.SHOW_TEXT,
                interactiveBody.hoverEvent().action());
        assertTrue(PlainTextComponentSerializer.plainText().serialize(
                        (Component) interactiveBody.hoverEvent().value())
                .contains("点击复制"));
        assertEquals(NamedTextColor.GOLD,
                findByText(rendered, " »").color());
        assertEquals(name.hoverEvent(), findByText(rendered, "Steve").hoverEvent());
        assertEquals(null, findByText(rendered, "[VIP] ").hoverEvent());
        assertEquals(null, findByText(rendered, " [Member]").hoverEvent());
    }

    private static Component findWithClickEvent(Component component) {
        if (component.clickEvent() != null) {
            return component;
        }
        for (Component child : component.children()) {
            Component found = findWithClickEvent(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Component findByText(Component component, String text) {
        if (PlainTextComponentSerializer.plainText().serialize(component).equals(text)) {
            return component;
        }
        for (Component child : component.children()) {
            Component found = findByText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

}
