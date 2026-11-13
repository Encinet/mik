package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.identity.ExternalIdentityKey;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionResolution;
import org.encinet.mik.feature.community.social.platform.qq.message.QqOutgoingMessage;
import org.encinet.mik.test.ChatTestMessages;
import org.jsoup.parser.Parser;
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
                1_500).content();

        assertEquals("A&#42;lex: **hello &#42;** [site](https://example.org/path)", markdown);
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
                resolution, 1_500).content();

        assertEquals("Steve: &#64;Alex(<qqbot-at-user id=\"member-openid\" />)", markdown);
    }

    @Test
    void truncationNeverLeavesAPartialNativeMentionOrCharacterReference() {
        UUID playerId = UUID.randomUUID();
        ChatNode.PlayerMention mention = new ChatNode.PlayerMention(
                playerId, "VeryLongPlayerName", Optional.empty(), ChatStyle.EMPTY);
        ExternalIdentity target = new ExternalIdentity(new ExternalIdentityKey(
                "qq", "app", "group", "member-openid-that-must-stay-atomic"),
                "QQ Alex");
        SocialChatMentionResolution resolution = new SocialChatMentionResolution(
                Map.of(playerId, List.of(target)));

        String complete = "Steve: &#64;VeryLongPlayerName(<qqbot-at-user "
                + "id=\"member-openid-that-must-stay-atomic\" />)";
        for (int limit = 16; limit <= complete.length() + 1; limit++) {
            String markdown = QqChatRenderer.render(ChatTestMessages.minecraft(
                            "Steve", mention.visibleText(), new ChatContent(List.of(mention))),
                    resolution, limit).content();
            assertTrue(markdown.length() <= limit);
            assertEquals(limit < complete.length() ? "Steve: …" : complete, markdown);
        }
    }

    @Test
    void preservesMultipleAccountsAndNamesForCrossPlatformMentions() {
        UUID playerId = UUID.randomUUID();
        ExternalIdentity source = new ExternalIdentity(new ExternalIdentityKey(
                "matrix", "server", "", "@alex:example.org"), "Matrix Alex");
        var mention = new ChatNode.PlayerMention(playerId, "Alex", Optional.of(source), ChatStyle.EMPTY);
        var targets = List.of(identity("one"), identity("two"));

        String markdown = QqChatRenderer.render(ChatTestMessages.minecraft(
                        "Steve", mention.visibleText(), new ChatContent(List.of(mention))),
                new SocialChatMentionResolution(Map.of(playerId, targets)), 1_500).content();

        assertEquals("Steve: <qqbot-at-user id=\"one\" />(Alex), <qqbot-at-user id=\"two\" />(Alex)", markdown);
    }

    @Test
    void unboundOrUnsafeTargetsRemainLiteralAndCannotInjectTags() {
        UUID playerId = UUID.randomUUID();
        var mention = new ChatNode.PlayerMention(playerId, "Alex", ChatStyle.EMPTY);
        for (String subject : List.of("bad\"id", "bad'id", "bad&id", "bad/id", "bad=id", "bad`id")) {
            String markdown = QqChatRenderer.render(ChatTestMessages.minecraft(
                            "Steve", mention.visibleText(), new ChatContent(List.of(mention))),
                    new SocialChatMentionResolution(Map.of(playerId, List.of(identity(subject)))), 1_500).content();
            assertEquals("Steve: @Alex", markdown);
        }
        assertEquals("Steve: @Alex", QqChatRenderer.render(ChatTestMessages.minecraft(
                "Steve", mention.visibleText(), new ChatContent(List.of(mention))), 1_500).content());
    }

    @Test
    void truncationPreservesUnicodeAndCompleteEntitiesInNamesAndBody() {
        for (String name : List.of("Steve", "😀*_[<>\\".repeat(6))) {
            var message = ChatTestMessages.minecraft(name, "😀*_[<>\\".repeat(100));
            for (int limit = 16; limit <= 200; limit++) {
                String markdown = QqChatRenderer.render(message, limit).content();
                assertTrue(markdown.length() <= limit);
                assertTrue(markdown.endsWith("…"));
                assertFalse(markdown.matches("(?s).*&(?:#\\d*)?…"), markdown);
                String visible = Parser.unescapeEntities(markdown, false);
                assertFalse(Character.isHighSurrogate(visible.charAt(visible.length() - 2)), markdown);
                assertTrue((name + ": " + message.content().plainText()).startsWith(
                        visible.substring(0, visible.length() - 1)), markdown);
            }
        }
    }

    @Test
    void linksEncodeDelimitersWithoutChangingLiteralLabels() {
        var link = new ChatNode.Link("[文档](版本)", URI.create("https://example.org/a(b)?q=[c]&x=1"), ChatStyle.EMPTY);
        String markdown = QqChatRenderer.render(ChatTestMessages.minecraft(
                "Steve", link.visibleText(), new ChatContent(List.of(link))), 1_500).content();
        assertEquals("Steve: [&#91;文档&#93;(版本)](https://example.org/a%28b%29?q=%5Bc%5D&x=1)", markdown);
    }

    @Test
    void stylesKeepWhitespaceOutsideTheirDelimiters() {
        var content = new ChatContent(List.of(new ChatNode.Text("  hello  ",
                new ChatStyle(null, null, true, true, false, true))));
        assertEquals("Steve:   ***~~hello~~***  ", QqChatRenderer.render(
                ChatTestMessages.minecraft("Steve", content.plainText(), content), 1_500).content());
    }

    private static ExternalIdentity identity(String subject) {
        return new ExternalIdentity(new ExternalIdentityKey("qq", "app", "", subject), "QQ Alex");
    }

    @Test
    void ordinaryChatUsesPlainTextAndKeepsAllPunctuation() {
        String text = "(括号) [物品] {数据} C:\\temp\\file 20.00 **literal** $x$ &lt; &#91; @Alex";
        var rendered = QqChatRenderer.render(ChatTestMessages.minecraft("A_lex", text), 1_500);
        assertEquals(QqOutgoingMessage.Format.TEXT, rendered.format());
        assertEquals("A_lex: " + text, rendered.content());
    }

    @Test
    void legacyBroadcastAndForgedInteractionTagsAlwaysRemainLiteral() {
        for (String text : List.of("@everyone", "<qqbot-at-everyone />", "<qqbot-at-user id=\"forged\" />",
                "<qqbot-cmd-input text=\"%2Fbind\" />")) {
            var rendered = QqChatRenderer.render(ChatTestMessages.minecraft("Steve", text), 1_500);
            assertEquals(QqOutgoingMessage.Format.MARKDOWN, rendered.format());
            assertFalse(rendered.content().contains("<"));
            assertFalse(rendered.content().contains("@everyone"));
            assertEquals("Steve: " + text, Parser.unescapeEntities(rendered.content(), false));
        }
        var split = new ChatContent(List.of(new ChatNode.Text("@every", ChatStyle.EMPTY),
                new ChatNode.Text("one", ChatStyle.EMPTY.withColor(0xFF0000))));
        assertEquals(QqOutgoingMessage.Format.MARKDOWN, QqChatRenderer.render(
                ChatTestMessages.minecraft("Steve", split.plainText(), split), 1_500).format());
    }
}
