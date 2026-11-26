package org.encinet.mik.feature.community.chat.parse;

import org.encinet.mik.feature.community.chat.model.ChatNode;

import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

record ChatReplacement(
        int start,
        int end,
        ChatNode node
) {
    public ChatReplacement {
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("chat replacement range is invalid");
        }
        Objects.requireNonNull(node, "node");
    }

    /** Merges forward searches, breaking ties in input order and retaining future matches. */
    public static IntFunction<ChatReplacement> mergeSearches(List<IntFunction<ChatReplacement>> searches) {
        var inputs = List.copyOf(searches);
        ChatReplacement[] next = new ChatReplacement[inputs.size()];
        boolean[] exhausted = new boolean[inputs.size()];
        return from -> {
            ChatReplacement first = null;
            for (int i = 0; i < inputs.size(); i++) {
                if (exhausted[i]) continue;
                ChatReplacement candidate = next[i];
                if (candidate == null || candidate.start() < from) {
                    candidate = inputs.get(i).apply(from);
                    next[i] = candidate;
                    exhausted[i] = candidate == null;
                }
                if (candidate != null && (first == null || candidate.start() < first.start())) {
                    first = candidate;
                }
            }
            return first;
        };
    }
}
