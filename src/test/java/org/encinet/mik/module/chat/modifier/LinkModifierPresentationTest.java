package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkModifierPresentationTest {

    @Test
    void brandPalettesKeepTheirPlatformColors() {
        assertPalette(ChatLinkPalette.GENERIC, 0x4EA5FF, 0x79C0FF);
        assertPalette(ChatLinkPalette.GITHUB, 0x58A6FF, 0xA371F7);
        assertPalette(ChatLinkPalette.MINECRAFT_WIKI, 0x55FF55, 0xFFAA00);
        assertPalette(ChatLinkPalette.BILIBILI, 0x00AEEC, 0xFB7299);
        assertPalette(ChatLinkPalette.MOJIRA, 0xFF5555, 0xFFAA00);
        assertPalette(ChatLinkPalette.MATRIX, 0xFFFFFF, 0xAAAAAA);
        assertPalette(ChatLinkPalette.EMAIL, 0x55FFFF, 0x5555FF);
    }

    @Test
    void everyLinkModifierUsesItsBrandPaletteWithoutUnderlining() {
        Map<ChatLinkPalette, ChatNode.Link> links = new EnumMap<>(
                ChatLinkPalette.class);
        links.put(ChatLinkPalette.GITHUB, link(new GitHubModifier().find(
                "github.com/encinet/mik", 0, null)));
        links.put(ChatLinkPalette.MINECRAFT_WIKI,
                link(new MinecraftWikiModifier().find(
                        "minecraft.wiki/w/Diamond", 0, null)));
        links.put(ChatLinkPalette.BILIBILI, link(new BilibiliModifier().find(
                "BV1GJ411x7h7", 0, null)));
        links.put(ChatLinkPalette.MOJIRA, link(new MojiraModifier().find(
                "MC-4", 0, null)));
        links.put(ChatLinkPalette.MATRIX, link(new MatrixLinkModifier().find(
                "@alice:example.org", 0, null)));
        links.put(ChatLinkPalette.EMAIL, link(new EmailModifier().find(
                "admin@example.org", 0, null)));
        links.put(ChatLinkPalette.GENERIC, link(new UrlModifier().find(
                "example.org/path", 0, null)));

        for (Map.Entry<ChatLinkPalette, ChatNode.Link> entry : links.entrySet()) {
            ChatNode.Link link = entry.getValue();
            assertTrue(link.label().startsWith("["), link.label());
            assertTrue(link.label().endsWith("]"), link.label());
            assertEquals(entry.getKey().style(), link.style(), link.label());
            assertFalse(link.style().underlined(), link.label());
        }
        assertEquals(ChatLinkPalette.values().length,
                links.values().stream().map(ChatNode.Link::style).distinct().count());
    }

    private ChatNode.Link link(ChatReplacement replacement) {
        return (ChatNode.Link) replacement.nodes().getFirst();
    }

    private void assertPalette(ChatLinkPalette palette, int start, int end) {
        ChatStyle style = palette.style();
        assertEquals(start, style.color());
        assertEquals(end, style.gradientEndColor());
    }
}
