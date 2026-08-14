package org.encinet.mik.module.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftChatContentRendererTest {

    @Test
    void rendersSemanticLinksAsAdventureOpenUrlInteractions() {
        ChatContent content = new ChatContent(List.of(
                new ChatNode.Text("see ", ChatStyle.EMPTY),
                new ChatNode.Link("[wiki]", URI.create(
                        "https://zh.minecraft.wiki/w/钻石"),
                        new ChatStyle(0x4EA5FF, null,
                                false, false, true, false))));

        Component rendered = new MinecraftChatContentRenderer().render(
                content, MinecraftChatContentRenderer.Context.empty());

        assertEquals("see [wiki]",
                PlainTextComponentSerializer.plainText().serialize(rendered));
        Component link = findOpenUrl(rendered);
        assertNotNull(link);
        assertEquals(ClickEvent.openUrl("https://zh.minecraft.wiki/w/钻石"),
                link.clickEvent());
        assertNotNull(link.hoverEvent());
    }

    @Test
    void emptyItemRetainsAStableFallbackAndLocalizedHover() {
        ChatContent content = new ChatContent(List.of(new ChatNode.Item(
                "[empty]", Optional.empty(), ChatStyle.EMPTY)));

        Component rendered = new MinecraftChatContentRenderer().render(content,
                new MinecraftChatContentRenderer.Context(
                        "No item", "Mention everyone"));

        assertEquals("[empty]",
                PlainTextComponentSerializer.plainText().serialize(rendered));
        assertNotNull(rendered.children().getFirst().hoverEvent());
    }

    @Test
    void safelyDegradesMailtoLinksToClipboardInteractions() {
        ChatContent content = new ChatContent(List.of(new ChatNode.Link(
                "[Email: admin@example.org]",
                URI.create("mailto:admin@example.org"), ChatStyle.EMPTY)));

        Component rendered = new MinecraftChatContentRenderer().render(
                content, MinecraftChatContentRenderer.Context.empty());

        Component link = findClickAction(rendered,
                ClickEvent.Action.COPY_TO_CLIPBOARD);
        assertNotNull(link);
        assertEquals(ClickEvent.copyToClipboard("admin@example.org"),
                link.clickEvent());
    }

    @Test
    void delegatesLinkGradientsToMiniMessageWithoutUnderlining() {
        ChatContent content = new ChatContent(List.of(new ChatNode.Link(
                "[Example: gradient]", URI.create("https://example.org"),
                new ChatStyle(0x55D6FF, 0xC084FC,
                        false, false, false, false))));

        Component rendered = new MinecraftChatContentRenderer().render(
                content, MinecraftChatContentRenderer.Context.empty());

        Component link = findOpenUrl(rendered);
        assertNotNull(link);
        assertEquals(TextDecoration.State.FALSE,
                link.decoration(TextDecoration.UNDERLINED));
        Set<TextColor> colors = new HashSet<>();
        collectColors(link, colors);
        assertTrue(colors.size() > 1);
        assertTrue(colors.contains(TextColor.color(0x55D6FF)));
        assertTrue(colors.contains(TextColor.color(0xC084FC)));
    }

    private Component findOpenUrl(Component component) {
        return findClickAction(component, ClickEvent.Action.OPEN_URL);
    }

    private Component findClickAction(
            Component component, ClickEvent.Action action
    ) {
        if (component.clickEvent() != null
                && component.clickEvent().action() == action) {
            return component;
        }
        for (Component child : component.children()) {
            Component found = findClickAction(child, action);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void collectColors(Component component, Set<TextColor> colors) {
        if (component.color() != null) {
            colors.add(component.color());
        }
        component.children().forEach(child -> collectColors(child, colors));
    }
}
