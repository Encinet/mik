package org.encinet.mik.module.social.platform.matrix.render;

import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatConversation;
import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatMessageId;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatOrigin;
import org.encinet.mik.module.chat.model.ChatProcessingContext;
import org.encinet.mik.module.chat.model.ChatReferences;
import org.encinet.mik.module.chat.model.ChatSender;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.chat.pipeline.ChatProcessor;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.identity.ExternalIdentityKey;
import org.encinet.mik.module.social.chat.SocialChatMentionResolution;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixChatRendererTest {

    @Test
    void rendersSemanticLinksAsMatrixCustomHtml() {
        ChatContent content = new ChatContent(List.of(
                new ChatNode.Text("查看 ", ChatStyle.EMPTY),
                new ChatNode.Link("[Minecraft Wiki: 钻石]", URI.create(
                        "https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3"),
                        new ChatStyle(0x4EA5FF, null,
                                true, false, true, false))));

        MatrixChatRenderer.RenderedChat rendered =
                MatrixChatRenderer.render(message("ignored", content), 4_000);

        assertEquals("Steve: 查看 [Minecraft Wiki: 钻石]", rendered.plainText());
        assertTrue(rendered.html().contains("<strong>Steve</strong>: 查看 "));
        assertTrue(rendered.html().contains(
                "<a href=\"https://zh.minecraft.wiki/w/%E9%92%BB%E7%9F%B3\">"));
        assertTrue(rendered.html().contains("<u>"));
        assertTrue(rendered.html().contains("<font color=\"#4EA5FF\">"));
    }

    @Test
    void plainTextNodesCannotInjectLinks() {
        ChatContent content = ChatContent.plain("javascript:alert(1)");

        String html = MatrixChatRenderer.render(
                message("javascript:alert(1)", content), 4_000).html();

        assertFalse(html.contains("href="));
        assertTrue(html.contains("javascript:alert(1)"));
    }

    @Test
    void rendersSafeMailtoAndNormalizedMatrixLinks() {
        ChatContent content = new ChatContent(List.of(
                new ChatNode.Link("[Email: admin@example.org]",
                        URI.create("mailto:admin@example.org"), ChatStyle.EMPTY),
                new ChatNode.Text(" ", ChatStyle.EMPTY),
                new ChatNode.Link("[Matrix: @alice:example.org]",
                        URI.create("https://matrix.to/#/%40alice%3Aexample.org"),
                        ChatStyle.EMPTY)));

        String html = MatrixChatRenderer.render(
                message("ignored", content), 4_000).html();

        assertTrue(html.contains("href=\"mailto:admin@example.org\""));
        assertTrue(html.contains(
                "href=\"https://matrix.to/#/%40alice%3Aexample.org\""));
    }

    @Test
    void rendersGradientLinksAsAnUnderlinelessSolidColorFallback() {
        ChatContent content = new ChatContent(List.of(new ChatNode.Link(
                "[Example: gradient]", URI.create("https://example.org"),
                new ChatStyle(0x55D6FF, 0xC084FC,
                        false, false, false, false))));

        String html = MatrixChatRenderer.render(
                message("ignored", content), 4_000).html();

        assertTrue(html.contains("<font color=\"#55D6FF\">"));
        assertTrue(html.contains("href=\"https://example.org\""));
        assertFalse(html.contains("<u>"));
    }

    @Test
    void preservesTheCompleteUrlFromTheGenericModifier() {
        String token = "abcdef0123456789".repeat(6);
        String url = "https://example.org/download/archive?utm_source=matrix"
                + "&token=" + token + "&fbclid=opaque&part=2#checksum";
        String cleaned = "https://example.org/download/archive?token="
                + token + "&part=2#checksum";
        ChatContent content = new ChatProcessor().process(
                url, ChatProcessingContext.external());

        MatrixChatRenderer.RenderedChat rendered = MatrixChatRenderer.render(
                message(url, content), 4_000);

        assertEquals("Steve: [" + cleaned + "]", rendered.plainText());
        assertTrue(rendered.html().contains("href=\""
                + cleaned.replace("&", "&amp;") + "\""));
        assertFalse(rendered.html().contains("utm_source"));
        assertFalse(rendered.html().contains("fbclid"));
        assertFalse(rendered.html().contains("..."));
    }

    @Test
    void aLengthLimitedLinkStillTargetsTheCompleteUrl() {
        String url = "https://example.org/download?token=" + "a".repeat(160);
        ChatContent content = new ChatContent(List.of(new ChatNode.Link(
                "[" + url + "]", URI.create(url), ChatStyle.EMPTY)));

        MatrixChatRenderer.RenderedChat rendered = MatrixChatRenderer.render(
                message(url, content), 80);

        assertTrue(rendered.plainText().endsWith("…"));
        assertTrue(rendered.html().contains("href=\"" + url + "\""));
        assertTrue(rendered.html().endsWith("</a>…"));
    }

    @Test
    void usesBoundMinecraftNameAndKeepsItemFallbacksPortable() {
        UUID playerId = UUID.randomUUID();
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.external("matrix", "$event"),
                new ChatOrigin.Social("matrix", "$event"),
                new ChatSender("Steve", java.util.Optional.of(playerId),
                        java.util.Optional.empty(), "Steve", "[VIP] ", ""),
                new ChatConversation(ChatConversation.Kind.SOCIAL,
                        "matrix:room"), "item", ChatReferences.empty(),
                Instant.EPOCH, ChatProcessingContext.external());
        ChatMessage message = new ChatMessage(submission,
                new ChatContent(List.of(new ChatNode.Item(
                        "[Diamond x2]", java.util.Optional.empty(),
                        ChatStyle.EMPTY))), Set.of());

        MatrixChatRenderer.RenderedChat rendered =
                MatrixChatRenderer.render(message, 4_000);

        assertEquals("Steve: [Diamond x2]", rendered.plainText());
        assertFalse(rendered.html().contains("[VIP]"));
    }

    @Test
    void minecraftMentionShowsGameNameAndEveryJoinedMatrixAccount() {
        UUID target = UUID.randomUUID();
        ChatContent content = new ChatContent(List.of(
                new ChatNode.Text("hello ", ChatStyle.EMPTY),
                new ChatNode.PlayerMention(target, "Alex", ChatStyle.EMPTY)));
        ExternalIdentity first = matrix("@alex:one.example", "Alex One");
        ExternalIdentity second = matrix("@alex:two.example", "Alex Two");

        MatrixChatRenderer.RenderedChat rendered = MatrixChatRenderer.render(
                message("Alex@", content),
                new SocialChatMentionResolution(Map.of(
                        target, List.of(first, second))), 4_000);

        assertEquals("Steve: hello @Alex(Alex One, Alex Two)",
                rendered.plainText());
        assertEquals(Set.of("@alex:one.example", "@alex:two.example"),
                rendered.mentionedUserIds());
        assertTrue(rendered.html().contains(
                "https://matrix.to/#/@alex:one.example"));
        assertTrue(rendered.html().contains(">Alex One</a>"));
    }

    @Test
    void socialMentionUsesDestinationNicknameBeforeTheGameName() {
        UUID target = UUID.randomUUID();
        ChatNode.PlayerMention mention = new ChatNode.PlayerMention(
                target, "Alex", Optional.of(matrix(
                "@source:source.example", "Source Nick")), ChatStyle.EMPTY);
        ExternalIdentity destination = matrix(
                "@target:destination.example", "Target Nick");

        MatrixChatRenderer.RenderedChat rendered = MatrixChatRenderer.render(
                message("ignored", new ChatContent(List.of(mention))),
                new SocialChatMentionResolution(Map.of(
                        target, List.of(destination))), 4_000);

        assertEquals("Steve: @Target Nick(Alex)", rendered.plainText());
        assertEquals(Set.of("@target:destination.example"),
                rendered.mentionedUserIds());
    }

    @Test
    void unresolvedAndTruncatedMentionsNeverCreateNativeNotifications() {
        UUID target = UUID.randomUUID();
        ChatContent content = new ChatContent(List.of(
                new ChatNode.PlayerMention(target, "VeryLongPlayerName",
                        ChatStyle.EMPTY)));

        MatrixChatRenderer.RenderedChat unresolved = MatrixChatRenderer.render(
                message("ignored", content), 4_000);
        MatrixChatRenderer.RenderedChat truncated = MatrixChatRenderer.render(
                message("ignored", content),
                new SocialChatMentionResolution(Map.of(target, List.of(
                        matrix("@long:example.org", "Long Matrix Nick")))), 16);

        assertEquals("Steve: @VeryLongPlayerName", unresolved.plainText());
        assertTrue(unresolved.mentionedUserIds().isEmpty());
        assertTrue(truncated.mentionedUserIds().isEmpty());
        assertTrue(truncated.plainText().endsWith("…"));
    }

    private static ExternalIdentity matrix(String userId, String displayName) {
        return new ExternalIdentity(new ExternalIdentityKey(
                "matrix", userId.substring(userId.indexOf(':') + 1), "",
                userId), displayName);
    }

    private ChatMessage message(String source, ChatContent content) {
        UUID playerId = UUID.randomUUID();
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(), new ChatOrigin.Minecraft(playerId),
                ChatSender.minecraft(playerId, "Steve"),
                ChatConversation.minecraftPublic(), source,
                ChatReferences.empty(), Instant.EPOCH,
                ChatProcessingContext.external());
        return new ChatMessage(submission, content, Set.of());
    }
}
