package org.encinet.mik.feature.community.chat.model;

import org.encinet.mik.shared.i18n.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable platform-neutral semantic chat content. */
public record ChatContent(List<ChatNode> nodes) {
    private static final int MAXIMUM_VISIBLE_LENGTH = 16_000;

    public ChatContent {
        List<ChatNode> checked = new ArrayList<>();
        int visibleLength = 0;
        for (ChatNode node : Objects.requireNonNull(nodes, "nodes")) {
            ChatNode value = Objects.requireNonNull(node, "node");
            if (value.visibleText().isEmpty()) {
                continue;
            }
            visibleLength += value.visibleText().length();
            if (visibleLength > MAXIMUM_VISIBLE_LENGTH) {
                throw new IllegalArgumentException("chat content is too long");
            }
            if (!checked.isEmpty()
                    && checked.getLast() instanceof ChatNode.Text previous
                    && value instanceof ChatNode.Text current
                    && previous.style().equals(current.style())
                    && previous.verbatim() == current.verbatim()) {
                checked.set(checked.size() - 1, new ChatNode.Text(
                        previous.text() + current.text(), previous.style(), previous.verbatim()));
            } else {
                checked.add(value);
            }
        }
        nodes = List.copyOf(checked);
    }

    public static ChatContent plain(String text) {
        return new ChatContent(List.of(new ChatNode.Text(
                Objects.requireNonNull(text, "text"), ChatStyle.EMPTY)));
    }

    /** Resolve only transport-authored descriptions; literal user text remains untouched. */
    public ChatContent localized(java.util.function.Function<Message, String> text) {
        if (nodes.stream().noneMatch(ChatNode.Placeholder.class::isInstance)) return this;
        int remaining = MAXIMUM_VISIBLE_LENGTH - plainText().length();
        var translated = new ArrayList<ChatNode>(nodes.size());
        for (ChatNode node : nodes) {
            if (node instanceof ChatNode.Placeholder placeholder) {
                String label = text.apply(placeholder.kind().message());
                if (label != null && label.length() - placeholder.text().length() <= remaining) {
                    remaining -= label.length() - placeholder.text().length();
                    node = new ChatNode.Placeholder(placeholder.kind(), label, placeholder.style());
                }
            }
            translated.add(node);
        }
        return new ChatContent(translated);
    }

    public String plainText() {
        StringBuilder result = new StringBuilder();
        nodes.forEach(node -> result.append(node.visibleText()));
        return result.toString();
    }

}
