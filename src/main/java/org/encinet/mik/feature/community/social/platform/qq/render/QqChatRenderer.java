package org.encinet.mik.feature.community.social.platform.qq.render;

import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.social.chat.SocialChatMentionResolution;
import org.encinet.mik.feature.community.social.platform.qq.message.QqOutgoingMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Renders platform-neutral chat as bounded QQ text or Markdown with native mentions. */
public final class QqChatRenderer {
    private QqChatRenderer() {
    }

    public static QqOutgoingMessage render(ChatMessage message, int maximumLength) {
        return render(message, SocialChatMentionResolution.empty(), maximumLength);
    }

    public static QqOutgoingMessage render(
            ChatMessage message,
            SocialChatMentionResolution mentions,
            int maximumLength
    ) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(mentions, "mentions");
        if (maximumLength < 16) {
            throw new IllegalArgumentException("maximumLength must be at least 16");
        }

        String plainPrefix = message.submission().sender().displayName() + ": ";
        List<RenderedNode> nodes = message.content().nodes().stream()
                .map(node -> renderNode(node, mentions)).toList();
        boolean markdown = QqText.containsInteractionSyntax(plainPrefix + message.content().plainText())
                || nodes.stream().anyMatch(RenderedNode::formatted);
        var format = markdown ? QqOutgoingMessage.Format.MARKDOWN : QqOutgoingMessage.Format.TEXT;
        String prefix = markdown ? QqText.markdownLiteral(plainPrefix) : plainPrefix;
        int fullLength = prefix.length()
                + nodes.stream().mapToInt(node -> node.content(markdown).length()).sum();
        if (fullLength <= maximumLength) {
            StringBuilder complete = new StringBuilder(prefix);
            nodes.forEach(node -> complete.append(node.content(markdown)));
            return new QqOutgoingMessage(format, complete.toString());
        }

        int contentLimit = maximumLength - 1;
        StringBuilder result = new StringBuilder(maximumLength);
        if (!appendText(result, plainPrefix, contentLimit, markdown)) {
            return new QqOutgoingMessage(format, result.append('…').toString());
        }
        for (RenderedNode node : nodes) {
            int remaining = contentLimit - result.length();
            if (remaining <= 0) {
                break;
            }
            if (node.content(markdown).length() <= remaining) {
                result.append(node.content(markdown));
                continue;
            }
            if (node.atomic()) break;
            appendText(result, node.plain(), remaining, markdown);
            break;
        }
        result.append('…');
        return new QqOutgoingMessage(format, result.toString());
    }

    private static RenderedNode renderNode(
            ChatNode node,
            SocialChatMentionResolution mentions
    ) {
        if (node instanceof ChatNode.PlayerMention mention) {
            List<ExternalIdentity> targets = mentions.targetsFor(mention.playerId())
                    .stream().filter(QqChatRenderer::hasSafeMentionToken).toList();
            if (!targets.isEmpty()) {
                return renderPlayerMention(mention, targets);
            }
        }
        String plain = node.visibleText();
        String markdown = QqText.markdownLiteral(plain);
        if (node instanceof ChatNode.Link link) {
            markdown = "[" + markdown + "](" + markdownLinkTarget(
                    link.target().toASCIIString()) + ")";
        }
        return new RenderedNode(plain, applyStyle(markdown, node.style()),
                node instanceof ChatNode.Placeholder || node instanceof ChatNode.PlayerMention
                        || node instanceof ChatNode.ExternalMention || node instanceof ChatNode.BroadcastMention,
                node instanceof ChatNode.Link || hasStyle(node.style()));
    }

    private static RenderedNode renderPlayerMention(
            ChatNode.PlayerMention mention,
            List<ExternalIdentity> targets
    ) {
        List<String> tokens = targets.stream()
                .map(target -> "<qqbot-at-user id=\"" + target.key().subject() + "\" />")
                .toList();
        String markdown;
        if (mention.sourceIdentity().isEmpty()) {
            markdown = QqText.markdownLiteral(
                    "@" + mention.playerName() + "(")
                    + String.join(", ", tokens)
                    + QqText.markdownLiteral(")");
        } else {
            List<String> markdownMentions = new ArrayList<>();
            for (int index = 0; index < targets.size(); index++) {
                markdownMentions.add(tokens.get(index)
                        + QqText.markdownLiteral(
                        "(" + mention.playerName() + ")"));
            }
            markdown = String.join(", ", markdownMentions);
        }
        return new RenderedNode(SocialChatMentionResolution.playerMentionText(mention, targets),
                applyStyle(markdown, mention.style()), true, true);
    }

    private static boolean hasSafeMentionToken(ExternalIdentity identity) {
        String subject = identity.key().subject();
        return identity.key().platform().equals("qq") && subject.length() <= 256
                && subject.matches("[A-Za-z0-9_-]+");
    }

    private static String markdownLinkTarget(String target) {
        return target.replace("\\", "%5C")
                .replace("[", "%5B")
                .replace("]", "%5D")
                .replace("(", "%28")
                .replace(")", "%29");
    }

    private static boolean hasStyle(ChatStyle style) {
        // QQ's __text__ means bold, so Minecraft underline has no equivalent.
        return style.bold() || style.italic() || style.strikethrough();
    }

    private static String applyStyle(String markdown, ChatStyle style) {
        int start = 0;
        int end = markdown.length();
        while (start < end && Character.isWhitespace(markdown.charAt(start))) start++;
        while (end > start && Character.isWhitespace(markdown.charAt(end - 1))) end--;
        if (start == end) return markdown;
        String rendered = markdown.substring(start, end);
        if (style.strikethrough()) {
            rendered = "~~" + rendered + "~~";
        }
        if (style.italic()) {
            rendered = "*" + rendered + "*";
        }
        if (style.bold()) {
            rendered = "**" + rendered + "**";
        }
        return markdown.substring(0, start) + rendered + markdown.substring(end);
    }

    private static boolean appendText(
            StringBuilder target,
            String value,
            int maximumAdditionalLength,
            boolean markdown
    ) {
        int endLength = target.length() + maximumAdditionalLength;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            String escaped = markdown ? QqText.markdownLiteral(character) : character;
            if (target.length() + escaped.length() > endLength) {
                return false;
            }
            target.append(escaped);
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private record RenderedNode(String plain, String markdown, boolean atomic, boolean formatted) {
        String content(boolean useMarkdown) { return useMarkdown ? markdown : plain; }
    }
}
