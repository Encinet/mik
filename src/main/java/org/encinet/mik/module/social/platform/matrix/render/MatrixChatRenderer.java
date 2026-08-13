package org.encinet.mik.module.social.platform.matrix.render;

import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.social.chat.SocialChatMentionResolution;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Renders shared chat spans into Matrix plain fallback plus restricted custom HTML. */
public final class MatrixChatRenderer {
    private static final int MAXIMUM_HTML_EXPANSION = 8;

    private MatrixChatRenderer() {
    }

    public static RenderedChat render(
            ChatMessage message,
            int maximumLength
    ) {
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
        String senderName = message.submission().sender().displayName();
        String prefix = senderName + ": ";
        int available = Math.max(0, maximumLength - prefix.length());
        List<RenderedNode> nodes = message.content().nodes().stream()
                .map(node -> renderNode(node, mentions)).toList();
        int visibleLength = nodes.stream().mapToInt(node -> node.plain().length()).sum();
        boolean truncated = visibleLength > available;
        int bodyLimit = truncated ? Math.max(0, available - 1) : available;

        StringBuilder plain = new StringBuilder(prefix);
        StringBuilder html = new StringBuilder("<strong>")
                .append(MatrixHtmlRenderer.escape(senderName))
                .append("</strong>: ");
        LinkedHashSet<String> mentionedUserIds = new LinkedHashSet<>();
        int remaining = bodyLimit;
        for (RenderedNode node : nodes) {
            if (remaining <= 0) {
                break;
            }
            boolean complete = node.plain().length() <= remaining;
            String text = limit(node.plain(), remaining);
            if (text.isEmpty()) {
                continue;
            }
            plain.append(text);
            if (complete) {
                html.append(node.html());
                mentionedUserIds.addAll(node.mentionedUserIds());
            } else {
                html.append(MatrixHtmlRenderer.escape(text));
            }
            remaining -= text.length();
        }
        if (truncated) {
            plain.append('…');
            html.append('…');
        }
        String plainText = plain.toString();
        int maximumHtmlLength = Math.max(
                1_024, maximumLength * MAXIMUM_HTML_EXPANSION);
        String formatted = html.length() <= maximumHtmlLength
                ? html.toString() : MatrixHtmlRenderer.escape(plainText);
        if (html.length() > maximumHtmlLength) {
            mentionedUserIds.clear();
        }
        return new RenderedChat(plainText, formatted, mentionedUserIds);
    }

    private static RenderedNode renderNode(
            ChatNode node,
            SocialChatMentionResolution resolution
    ) {
        if (node instanceof ChatNode.PlayerMention mention) {
            List<ExternalIdentity> targets = resolution.targetsFor(
                    mention.playerId());
            if (!targets.isEmpty()) {
                return renderPlayerMention(mention, targets);
            }
        }
        String text = node.visibleText();
        String rendered = MatrixHtmlRenderer.escape(text);
        if (node instanceof ChatNode.Link link) {
            rendered = "<a href=\"" + MatrixHtmlRenderer.escape(
                    link.target().toString()) + "\">" + rendered + "</a>";
        }
        return new RenderedNode(text, applyStyle(rendered, node.style()), Set.of());
    }

    private static RenderedNode renderPlayerMention(
            ChatNode.PlayerMention mention,
            List<ExternalIdentity> targets
    ) {
        List<String> userIds = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ExternalIdentity target : targets) {
            userIds.add(target.key().subject());
            names.add(externalName(target));
        }
        String plain;
        String html;
        if (mention.sourceIdentity().isEmpty()) {
            plain = "@" + mention.playerName() + "(" + String.join(", ", names) + ")";
            List<String> linkedNames = new ArrayList<>();
            for (int index = 0; index < targets.size(); index++) {
                linkedNames.add(matrixLink(targets.get(index), names.get(index)));
            }
            html = MatrixHtmlRenderer.escape("@" + mention.playerName() + "(")
                    + String.join(", ", linkedNames) + ")";
        } else {
            List<String> plainMentions = new ArrayList<>();
            List<String> htmlMentions = new ArrayList<>();
            for (int index = 0; index < targets.size(); index++) {
                String label = "@" + names.get(index)
                        + "(" + mention.playerName() + ")";
                plainMentions.add(label);
                htmlMentions.add("<a href=\"" + matrixUri(
                        targets.get(index).key().subject()) + "\">"
                        + MatrixHtmlRenderer.escape(label) + "</a>");
            }
            plain = String.join(", ", plainMentions);
            html = String.join(", ", htmlMentions);
        }
        return new RenderedNode(plain, applyStyle(html, mention.style()),
                Set.copyOf(userIds));
    }

    private static String matrixLink(ExternalIdentity identity, String label) {
        return "<a href=\"" + matrixUri(identity.key().subject()) + "\">"
                + MatrixHtmlRenderer.escape(label) + "</a>";
    }

    private static String matrixUri(String userId) {
        return MatrixHtmlRenderer.escape("https://matrix.to/#/" + userId);
    }

    private static String externalName(ExternalIdentity identity) {
        return identity.displayName().isBlank()
                ? identity.key().subject() : identity.displayName();
    }

    private static String applyStyle(String rendered, ChatStyle style) {
        if (style.color() != null) {
            rendered = "<font color=\"#%06X\">%s</font>"
                    .formatted(style.color(), rendered);
        }
        if (style.strikethrough()) {
            rendered = "<del>" + rendered + "</del>";
        }
        if (style.underlined()) {
            rendered = "<u>" + rendered + "</u>";
        }
        if (style.italic()) {
            rendered = "<em>" + rendered + "</em>";
        }
        if (style.bold()) {
            rendered = "<strong>" + rendered + "</strong>";
        }
        return rendered;
    }

    private static String limit(String value, int maximumLength) {
        if (value.length() <= maximumLength) {
            return value;
        }
        int end = maximumLength;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private record RenderedNode(
            String plain,
            String html,
            Set<String> mentionedUserIds
    ) {
    }

    public record RenderedChat(
            String plainText,
            String html,
            Set<String> mentionedUserIds
    ) {
        public RenderedChat {
            plainText = Objects.requireNonNull(plainText, "plainText");
            html = Objects.requireNonNull(html, "html");
            mentionedUserIds = Set.copyOf(Objects.requireNonNull(
                    mentionedUserIds, "mentionedUserIds"));
        }

        public RenderedChat(String plainText, String html) {
            this(plainText, html, Set.of());
        }
    }
}
