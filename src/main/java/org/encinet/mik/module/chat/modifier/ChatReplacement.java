package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatNode;

import java.util.List;
import java.util.Objects;

public record ChatReplacement(
        int start,
        int end,
        List<ChatNode> nodes,
        ChatReplacementSpacing spacing
) {
    public ChatReplacement {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("chat replacement range is invalid");
        }
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("chat replacement nodes are empty");
        }
        spacing = Objects.requireNonNull(spacing, "spacing");
    }

    public ChatReplacement(int start, int end, ChatNode node) {
        this(start, end, List.of(node), ChatReplacementSpacing.PRESERVE);
    }

    public static ChatReplacement padded(int start, int end, ChatNode node) {
        return new ChatReplacement(start, end, List.of(node),
                ChatReplacementSpacing.PAD_WHEN_JOINED);
    }
}
