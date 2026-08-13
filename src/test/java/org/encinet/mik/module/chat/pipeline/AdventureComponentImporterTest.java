package org.encinet.mik.module.chat.pipeline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatNode;
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
        ChatNode.Link link = (ChatNode.Link) content.nodes().getLast();
        assertEquals("https://github.com/encinet/mik",
                link.target().toString());
        assertTrue(link.style().underlined());
        assertEquals(NamedTextColor.GRAY.value(), link.style().color());
    }

    @Test
    void doesNotExportMinecraftOnlyClickActions() {
        ChatContent content = importer.importComponent(Component.text("command")
                .clickEvent(ClickEvent.runCommand("/op Alice")));

        assertFalse(content.nodes().getFirst() instanceof ChatNode.Link);
        assertEquals("command", content.plainText());
    }
}
