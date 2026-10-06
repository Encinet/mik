package org.encinet.mik.module.social.platform.qq.render;

import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.social.chat.SocialChatMentionResolution;
import org.encinet.mik.test.ChatTestMessages;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QqChatRendererTest {
    @Test
    void rendersEscapedStyledChatAndLinks() {
        ChatContent content = new ChatContent(List.of(
                new ChatNode.Text("hello * ", new ChatStyle(
                        null, null, true, false, false, false)),
                new ChatNode.Link("site", URI.create("https://example.org/path"),
                        ChatStyle.EMPTY)));

        String markdown = QqChatRenderer.render(
                ChatTestMessages.minecraft("A*lex", content.plainText(), content),
                1_500).markdown();

        assertEquals("A\\*lex: **hello \\* **[site](https://example.org/path)", markdown);
    }

    @Test
    void rendersResolvedBindingsAsNativeMentions() {
        UUID playerId = UUID.randomUUID();
        ChatNode.PlayerMention mention = new ChatNode.PlayerMention(
                playerId, "Alex", Optional.empty(), ChatStyle.EMPTY);
        ExternalIdentity target = new ExternalIdentity(new ExternalIdentityKey(
                "qq", "app", "group", "member-openid"), "QQ Alex");
        SocialChatMentionResolution resolution = new SocialChatMentionResolution(
                Map.of(playerId, List.of(target)));

        String markdown = QqChatRenderer.render(ChatTestMessages.minecraft(
                        "Steve", mention.visibleText(), new ChatContent(List.of(mention))),
                resolution, 1_500).markdown();

        assertEquals("Steve: @Alex\\(<@!member-openid>\\)", markdown);
    }

    @Test
    void truncationNeverLeavesAPartialNativeMentionOrMarkdownEscape() {
        UUID playerId = UUID.randomUUID();
        ChatNode.PlayerMention mention = new ChatNode.PlayerMention(
                playerId, "VeryLongPlayerName", Optional.empty(), ChatStyle.EMPTY);
        ExternalIdentity target = new ExternalIdentity(new ExternalIdentityKey(
                "qq", "app", "group", "member-openid-that-must-stay-atomic"),
                "QQ Alex");
        SocialChatMentionResolution resolution = new SocialChatMentionResolution(
                Map.of(playerId, List.of(target)));

        String markdown = QqChatRenderer.render(ChatTestMessages.minecraft(
                        "Steve", mention.visibleText(), new ChatContent(List.of(mention))),
                resolution, 24).markdown();

        assertTrue(markdown.length() <= 24);
        assertTrue(markdown.endsWith("…"));
        assertFalse(markdown.contains("<@!"));
        assertFalse(markdown.substring(0, markdown.length() - 1).endsWith("\\"));
    }
}
