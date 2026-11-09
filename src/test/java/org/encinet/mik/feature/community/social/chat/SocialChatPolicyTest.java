package org.encinet.mik.feature.community.social.chat;

import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialChatPolicyTest {

    @Test
    void alwaysRoutesEveryNonBlankMessageUnchanged() {
        SocialChatPolicy policy = SocialChatPolicy.always();

        assertEquals("hello", policy.select("hello").orElseThrow());
        assertEquals("#hello", policy.select("#hello").orElseThrow());
        assertTrue(policy.select("   ").isEmpty());
    }

    @Test
    void prefixRoutesOnlyMatchingMessagesAndCanStripTheTrigger() {
        SocialChatPolicy stripped =
                SocialChatPolicy.prefixed("#", true);
        SocialChatPolicy preserved =
                SocialChatPolicy.prefixed("@matrix", false);

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
                () -> SocialChatPolicy.prefixed("", true));
        assertThrows(IllegalArgumentException.class,
                () -> SocialChatPolicy.prefixed("two words", true));
    }

    @Test
    void directionalConfigurationDefaultsAndValidationAreShared() {
        assertEquals(SocialChatPolicy.always(), SocialChatPolicy.fromConfig(null, "inbound"));
        assertEquals(SocialChatPolicy.prefixed("#", true), SocialChatPolicy.fromConfig(
                Map.of("mode", "prefix", "prefix", "#"), "inbound"));
        assertEquals(SocialChatPolicy.prefixed("~", false), SocialChatPolicy.fromConfig(
                Map.of("mode", "PREFIX", "prefix", "~", "strip-prefix", false), "outbound"));
        for (Object invalid : List.of("prefix", Map.of("mode", "sometimes"),
                Map.of("mode", 1), Map.of("mode", "prefix"),
                Map.of("mode", "prefix", "prefix", "#", "strip-prefix", "true"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> SocialChatPolicy.fromConfig(invalid, "inbound"));
        }
    }

    @Test
    void selectedRangesRetainExactUtf16OffsetsForMentionRelocation() {
        SocialChatPolicy.Selection selected = SocialChatPolicy.prefixed("😀", true)
                .selectRange("  😀\t @Alex hi  ").orElseThrow();
        assertEquals("@Alex hi", selected.body());
        assertEquals(6, selected.start());
        assertEquals(14, selected.end());
        assertEquals(new SocialChatPolicy.Selection("# @Alex", 2, 9),
                SocialChatPolicy.prefixed("#", false).selectRange("  # @Alex ").orElseThrow());
    }

    @Test
    void richTextCanStripAPrefixWithoutDroppingLinkMetadata() {
        ChatContent rich = new ChatContent(List.of(
                new ChatNode.Text("# ", ChatStyle.EMPTY),
                new ChatNode.Link("[example]", URI.create(
                        "https://example.com/path"),
                        new ChatStyle(null, null, false, false, true, false))));

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
