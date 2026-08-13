package org.encinet.mik.module.chat.model;

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
                    && previous.style().equals(current.style())) {
                checked.set(checked.size() - 1, new ChatNode.Text(
                        previous.text() + current.text(), previous.style()));
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

    public String plainText() {
        StringBuilder result = new StringBuilder();
        nodes.forEach(node -> result.append(node.visibleText()));
        return result.toString();
    }

    /** Removes an already matched route trigger and following visible padding. */
    public ChatContent withoutLeadingTrigger(String trigger) {
        String expected = Objects.requireNonNull(trigger, "trigger");
        String visible = plainText();
        if (!visible.startsWith(expected)) {
            return this;
        }
        int removed = expected.length();
        while (removed < visible.length()
                && Character.isWhitespace(visible.charAt(removed))) {
            removed++;
        }
        List<ChatNode> remaining = new ArrayList<>();
        int skip = removed;
        for (ChatNode node : nodes) {
            String text = node.visibleText();
            if (skip >= text.length()) {
                skip -= text.length();
                continue;
            }
            remaining.add(skip == 0 ? node : sliced(node, skip));
            skip = 0;
        }
        return new ChatContent(remaining);
    }

    private static ChatNode sliced(ChatNode node, int fromIndex) {
        String remaining = node.visibleText().substring(fromIndex);
        if (node instanceof ChatNode.Text text) {
            return new ChatNode.Text(remaining, text.style());
        }
        if (node instanceof ChatNode.Link link) {
            return new ChatNode.Link(remaining, link.target(), link.style());
        }
        // Route triggers are required to be literal text. Preserve safety if a
        // third-party importer nevertheless placed one inside a semantic node.
        return new ChatNode.Text(remaining, node.style());
    }
}
