package org.encinet.mik.module.social.chat;

import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialChatOutboundPolicyTest {

    @Test
    void alwaysRoutesEveryNonBlankMessageUnchanged() {
        SocialChatOutboundPolicy policy = SocialChatOutboundPolicy.always();

        assertEquals("hello", policy.select("hello").orElseThrow());
        assertEquals("#hello", policy.select("#hello").orElseThrow());
        assertTrue(policy.select("   ").isEmpty());
    }

    @Test
    void prefixRoutesOnlyMatchingMessagesAndCanStripTheTrigger() {
        SocialChatOutboundPolicy stripped =
                SocialChatOutboundPolicy.prefixed("#", true);
        SocialChatOutboundPolicy preserved =
                SocialChatOutboundPolicy.prefixed("@matrix", false);

        assertEquals("你好", stripped.select("#你好").orElseThrow());
        assertEquals("你好", stripped.select("#  你好").orElseThrow());
        assertTrue(stripped.select("你好").isEmpty());
        assertTrue(stripped.select("#").isEmpty());
        assertEquals("@matrix hello",
                preserved.select("@matrix hello").orElseThrow());
    }

    @Test
    void prefixValidationIsSharedByEveryAdapter() {
        assertThrows(IllegalArgumentException.class,
                () -> SocialChatOutboundPolicy.prefixed("", true));
        assertThrows(IllegalArgumentException.class,
                () -> SocialChatOutboundPolicy.prefixed("two words", true));
    }

    @Test
    void richTextCanStripAPrefixWithoutDroppingLinkMetadata() {
        ChatContent rich = new ChatContent(List.of(
                new ChatNode.Text("# ", ChatStyle.EMPTY),
                new ChatNode.Link("[example]", URI.create(
                        "https://example.com/path"),
                        new ChatStyle(null, false, false, true, false))));

        ChatContent selected = rich.withoutLeadingTrigger("#");

        assertEquals("[example]", selected.plainText());
        assertEquals("https://example.com/path",
                ((ChatNode.Link) selected.nodes().getFirst()).target().toString());
    }

    @Test
    void sharedContentRejectsUnsafeLinks() {
        for (String unsafe : List.of(
                "javascript:alert(1)",
                "file:///etc/passwd",
                "https://user:secret@example.com/path",
                "https://example.com\\@evil.test/path")) {
            assertThrows(IllegalArgumentException.class, () ->
                    new ChatNode.Link("link", URI.create(unsafe),
                            ChatStyle.EMPTY), unsafe);
        }
        ChatNode.Link unicode = new ChatNode.Link(
                "wiki", URI.create("https://zh.minecraft.wiki/w/钻石"),
                ChatStyle.EMPTY);
        assertEquals("https://zh.minecraft.wiki/w/钻石",
                unicode.target().toString());
        assertFalse(unicode.target().toString().isBlank());
    }
}
