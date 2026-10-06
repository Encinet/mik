package org.encinet.mik.module.social.platform.qq.render;

import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.social.chat.SocialChatMentionResolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Renders platform-neutral chat as bounded QQ Markdown with native mentions. */
public final class QqChatRenderer {
    private QqChatRenderer() {
    }

    public static RenderedChat render(ChatMessage message, int maximumLength) {
        return render(message, SocialChatMentionResolution.empty(), maximumLength);
    }

    public static RenderedChat render(
            ChatMessage message,
            SocialChatMentionResolution mentions,
            int maximumLength
    ) {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(mentions, "mentions");
        if (maximumLength < 16) {
            throw new IllegalArgumentException("maximumLength must be at least 16");
        }

        String prefix = QqMarkdownRenderer.escape(
                message.submission().sender().displayName()) + ": ";
        List<RenderedNode> nodes = message.content().nodes().stream()
                .map(node -> renderNode(node, mentions)).toList();
        int fullLength = prefix.length()
                + nodes.stream().mapToInt(node -> node.markdown().length()).sum();
        if (fullLength <= maximumLength) {
            StringBuilder complete = new StringBuilder(prefix);
            nodes.forEach(node -> complete.append(node.markdown()));
            return new RenderedChat(complete.toString());
        }

        int contentLimit = maximumLength - 1;
        StringBuilder markdown = new StringBuilder(maximumLength);
        appendEscapedPrefix(markdown, prefix, contentLimit);
        for (RenderedNode node : nodes) {
            int remaining = contentLimit - markdown.length();
            if (remaining <= 0) {
                break;
            }
            if (node.markdown().length() <= remaining) {
                markdown.append(node.markdown());
                continue;
            }
            appendEscaped(markdown, node.plain(), remaining);
            break;
        }
        trimTrailingEscape(markdown);
        markdown.append('…');
        return new RenderedChat(markdown.toString());
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
        String markdown = QqMarkdownRenderer.escape(plain);
        if (node instanceof ChatNode.Link link) {
            markdown = "[" + markdown + "](" + markdownLinkTarget(
                    link.target().toASCIIString()) + ")";
        }
        return new RenderedNode(plain, applyStyle(markdown, node.style()));
    }

    private static RenderedNode renderPlayerMention(
            ChatNode.PlayerMention mention,
            List<ExternalIdentity> targets
    ) {
        List<String> names = targets.stream()
                .map(QqChatRenderer::externalName).toList();
        List<String> tokens = targets.stream()
                .map(target -> "<@!" + target.key().subject() + ">")
                .toList();
        String plain;
        String markdown;
        if (mention.sourceIdentity().isEmpty()) {
            plain = "@" + mention.playerName() + "(" + String.join(", ", names) + ")";
            markdown = QqMarkdownRenderer.escape(
                    "@" + mention.playerName() + "(")
                    + String.join(", ", tokens)
                    + QqMarkdownRenderer.escape(")");
        } else {
            List<String> plainMentions = new ArrayList<>();
            List<String> markdownMentions = new ArrayList<>();
            for (int index = 0; index < targets.size(); index++) {
                plainMentions.add("@" + names.get(index)
                        + "(" + mention.playerName() + ")");
                markdownMentions.add(tokens.get(index)
                        + QqMarkdownRenderer.escape(
                        "(" + mention.playerName() + ")"));
            }
            plain = String.join(", ", plainMentions);
            markdown = String.join(", ", markdownMentions);
        }
        return new RenderedNode(plain, applyStyle(markdown, mention.style()));
    }

    private static boolean hasSafeMentionToken(ExternalIdentity identity) {
        String subject = identity.key().subject();
        return !subject.isBlank() && subject.length() <= 256
                && subject.codePoints().noneMatch(character ->
                Character.isWhitespace(character) || Character.isISOControl(character)
                        || character == '<' || character == '>');
    }

    private static String externalName(ExternalIdentity identity) {
        return identity.displayName().isBlank()
                ? identity.key().subject() : identity.displayName();
    }

    private static String markdownLinkTarget(String target) {
        return target.replace("\\", "%5C")
                .replace("(", "%28")
                .replace(")", "%29");
    }

    private static String applyStyle(String markdown, ChatStyle style) {
        String rendered = markdown;
        if (style.strikethrough()) {
            rendered = "~~" + rendered + "~~";
        }
        if (style.italic()) {
            rendered = "*" + rendered + "*";
        }
        if (style.bold()) {
            rendered = "**" + rendered + "**";
        }
        return rendered;
    }

    private static void appendEscapedPrefix(
            StringBuilder target,
            String escapedPrefix,
            int limit
    ) {
        int end = Math.min(escapedPrefix.length(), limit);
        if (end > 0 && end < escapedPrefix.length()
                && Character.isHighSurrogate(escapedPrefix.charAt(end - 1))) {
            end--;
        }
        target.append(escapedPrefix, 0, end);
        trimTrailingEscape(target);
    }

    private static void appendEscaped(
            StringBuilder target,
            String value,
            int maximumAdditionalLength
    ) {
        int endLength = target.length() + maximumAdditionalLength;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            String escaped = QqMarkdownRenderer.escape(character);
            if (target.length() + escaped.length() > endLength) {
                break;
            }
            target.append(escaped);
            offset += Character.charCount(codePoint);
        }
    }

    private static void trimTrailingEscape(StringBuilder value) {
        int backslashes = 0;
        for (int index = value.length() - 1;
             index >= 0 && value.charAt(index) == '\\'; index--) {
            backslashes++;
        }
        if ((backslashes & 1) == 1) {
            value.setLength(value.length() - 1);
        }
    }

    private record RenderedNode(String plain, String markdown) {
    }

    public record RenderedChat(String markdown) {
        public RenderedChat {
            markdown = Objects.requireNonNull(markdown, "markdown");
        }
    }
}
