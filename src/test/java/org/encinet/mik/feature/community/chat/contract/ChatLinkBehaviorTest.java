package org.encinet.mik.feature.community.chat.contract;

import com.google.gson.Gson;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.encinet.mik.feature.community.chat.input.AdventureComponentImporter;
import org.encinet.mik.feature.community.chat.input.AuthorizedChatMarkup;
import org.encinet.mik.feature.community.chat.link.ChatResourceLinks;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.parse.ChatProcessor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatLinkBehaviorTest {
    private final ChatProcessor processor = new ChatProcessor(new AuthorizedChatMarkup()::parse);
    private final AdventureComponentImporter importer = new AdventureComponentImporter();

    @Test
    void sharedResourceRules() throws Exception {
        try (var reader = Files.newBufferedReader(Path.of("shared/chat/link-cases.json"))) {
            for (Case example : new Gson().fromJson(reader, Case[].class)) {
                var links = processor.process(example.text(), ChatProcessingContext.linksOnly()).nodes().stream()
                        .filter(node -> node instanceof ChatNode.Reference ref && ref.target() instanceof ChatNode.Reference.Link)
                        .map(ChatNode.Reference.class::cast).toList();
                if (example.target() == null) {
                    assertTrue(links.isEmpty(), example.text());
                    continue;
                }
                assertEquals(1, links.size(), example.text());
                var reference = links.getFirst();
                var link = assertInstanceOf(ChatNode.Reference.Link.class, reference.target());
                assertEquals(example.label(), reference.visibleText(), example.text());
                assertEquals(example.target(), link.uri().toString(), example.text());
                assertEquals(example.resource(), ChatResourceLinks.resource(link.uri()).name(), example.text());
                assertTrue(link.automatic(), example.text());
            }
        }
    }

    @Test
    void nativeAnchorKeepsItsLabelAndStyles() {
        var nativeAnchor = Component.text("@").append(Component.text("Alice").decorate(TextDecoration.BOLD))
                .clickEvent(ClickEvent.openUrl("https://minecraft.wiki/w/Diamond"));
        var content = importer.importComponent(nativeAnchor);
        var reference = assertInstanceOf(ChatNode.Reference.class, content.nodes().getFirst());
        var link = assertInstanceOf(ChatNode.Reference.Link.class, reference.target());
        assertEquals("@Alice", reference.visibleText());
        assertEquals(ChatResourceLinks.Resource.MINECRAFT_WIKI, ChatResourceLinks.resource(link.uri()));
        assertEquals(2, reference.runs().size());
        assertTrue(reference.runs().getLast().style().bold());
        assertFalse(link.automatic());
        assertTrue(org.encinet.mik.feature.community.chat.model.ChatMentions.fromContent(content).players().isEmpty());
    }

    private record Case(String text, String label, String target, String resource) {}
}
