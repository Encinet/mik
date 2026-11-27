package org.encinet.mik.feature.community.chat.input;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdventureComponentImporterTest {
    private final AdventureComponentImporter importer =
            new AdventureComponentImporter();

    @Test
    void importsFinalPaperLinksAndInheritedFormatting() {
        Component rendered = Component.text("see ", NamedTextColor.WHITE)
                .append(Component.text("[GitHub: encinet/mik]",
                                NamedTextColor.GRAY, TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(
                                "https://github.com/encinet/mik")));

        ChatContent content = importer.importComponent(rendered);

        assertEquals("see [GitHub: encinet/mik]", content.plainText());
        ChatNode.Reference link = (ChatNode.Reference) content.nodes().getLast();
        assertEquals("https://github.com/encinet/mik",
                ((ChatNode.Reference.Link) link.target()).uri().toString());
        assertTrue(link.runs().getFirst().style().underlined());
        assertEquals(NamedTextColor.GRAY.value(), link.runs().getFirst().style().color());
    }

    @Test
    void doesNotExportMinecraftOnlyClickActions() {
        ChatContent content = importer.importComponent(Component.text("command")
                .clickEvent(ClickEvent.runCommand("/op Alice")));

        assertFalse(content.nodes().getFirst() instanceof ChatNode.Reference);
        assertEquals("command", content.plainText());
    }
}
