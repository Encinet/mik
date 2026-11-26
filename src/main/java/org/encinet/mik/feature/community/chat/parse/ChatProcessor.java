package org.encinet.mik.feature.community.chat.parse;

import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

/** Interprets input with capabilities supplied by its entry point. */
public final class ChatProcessor {
    private static final System.Logger LOGGER = System.getLogger(ChatProcessor.class.getName());
    private final List<ChatModifier> modifiers;
    private final Function<String, List<ChatNode>> authorizedText;

    public ChatProcessor(Function<String, List<ChatNode>> authorizedText) {
        this(List.of(new ReferenceModifier(), new ItemModifier(), new InventorySlotModifier()), authorizedText);
    }

    ChatProcessor(
            List<? extends ChatModifier> modifiers,
            Function<String, List<ChatNode>> authorizedText
    ) {
        this.modifiers = List.copyOf(modifiers);
        this.authorizedText = Objects.requireNonNull(authorizedText, "authorizedText");
    }

    public ChatContent process(String source, ChatProcessingContext context) {
        return source == null ? new ChatContent(List.of()) : process(ChatContent.plain(source), context);
    }

    public ChatContent process(ChatContent input, ChatProcessingContext context) {
        List<ChatNode> output = new ArrayList<>();
        List<ChatNode.Text> adjacent = new ArrayList<>();
        for (ChatNode node : input.nodes()) {
            if (node instanceof ChatNode.Text text && !text.verbatim()) {
                adjacent.add(text);
            } else {
                interpret(adjacent, context, output);
                adjacent.clear();
                output.add(node);
            }
        }
        interpret(adjacent, context, output);
        return new ChatContent(output);
    }

    private void interpret(List<ChatNode.Text> runs, ChatProcessingContext context, List<ChatNode> nodes) {
        if (runs.isEmpty()) return;
        if (runs.size() == 1 && runs.getFirst().style().equals(ChatStyle.EMPTY)
                && context.capabilities().contains(ChatCapability.MINI_MESSAGE)) {
            runs = authorizedText.apply(runs.getFirst().text()).stream()
                    .filter(ChatNode.Text.class::isInstance).map(ChatNode.Text.class::cast).toList();
        }
        if (runs.isEmpty()) return;
        StringBuilder input = new StringBuilder();
        runs.forEach(run -> input.append(run.text()));
        String source = input.toString();
        var search = ChatReplacement.mergeSearches(modifiers.stream()
                .map(modifier -> openSearch(modifier, source, context)).toList());
        int cursor = 0;
        while (cursor < source.length()) {
            ChatReplacement replacement = search.apply(cursor);
            if (replacement == null) {
                nodes.addAll(slice(runs, cursor, source.length()));
                break;
            }
            if (replacement.start() > cursor) {
                nodes.addAll(slice(runs, cursor, replacement.start()));
            }
            ChatNode node = replacement.node();
            nodes.add(switch (node) {
                case ChatNode.Reference reference -> styledReference(reference, runs, source, replacement);
                case ChatNode.PendingMention mention -> new ChatNode.PendingMention(
                        slice(runs, replacement.start(), replacement.end()), mention.playerName());
                default -> node;
            });
            cursor = replacement.end();
        }
    }

    private static ChatNode.Reference styledReference(ChatNode.Reference reference, List<ChatNode.Text> runs,
                                                      String source, ChatReplacement replacement) {
        List<ChatNode.Text> label = slice(runs, replacement.start(), replacement.end());
        String visible = reference.visibleText();
        if (!visible.equals(source.substring(replacement.start(), replacement.end()))) {
            label = List.of(new ChatNode.Text(visible, label.getFirst().style()));
        }
        return new ChatNode.Reference(label, reference.target());
    }

    private static List<ChatNode.Text> slice(List<ChatNode.Text> runs, int start, int end) {
        List<ChatNode.Text> result = new ArrayList<>();
        int offset = 0;
        for (ChatNode.Text run : runs) {
            int next = offset + run.text().length();
            int first = Math.max(start, offset);
            int last = Math.min(end, next);
            if (first < last) {
                result.add(new ChatNode.Text(run.text().substring(first - offset, last - offset), run.style()));
            }
            if (next >= end) break;
            offset = next;
        }
        return result;
    }

    private static IntFunction<ChatReplacement> openSearch(
            ChatModifier modifier, String text, ChatProcessingContext context
    ) {
        try {
            if (!modifier.supports(context)) return _ -> null;
            var search = modifier.search(text, context);
            return from -> {
                try {
                    ChatReplacement replacement = search.apply(from);
                    if (replacement != null && (replacement.start() < from || replacement.end() > text.length())) {
                        throw new IllegalArgumentException("Chat modifier returned an invalid replacement range");
                    }
                    return replacement;
                } catch (RuntimeException error) {
                    logFailure(modifier, error);
                    return null;
                }
            };
        } catch (RuntimeException error) {
            logFailure(modifier, error);
            return _ -> null;
        }
    }

    private static void logFailure(ChatModifier modifier, RuntimeException error) {
        LOGGER.log(System.Logger.Level.WARNING,
                "Chat modifier " + modifier.getClass().getName()
                        + " failed; skipping its remaining replacements for this message", error);
    }
}
