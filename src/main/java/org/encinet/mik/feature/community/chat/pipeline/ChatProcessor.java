package org.encinet.mik.feature.community.chat.pipeline;

import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatEffect;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.modifier.AllMentionModifier;
import org.encinet.mik.feature.community.chat.modifier.ChatModifier;
import org.encinet.mik.feature.community.chat.modifier.ChatReplacement;
import org.encinet.mik.feature.community.chat.modifier.ChatReplacementSpacing;
import org.encinet.mik.feature.community.chat.modifier.EmailModifier;
import org.encinet.mik.feature.community.chat.modifier.InventorySlotModifier;
import org.encinet.mik.feature.community.chat.modifier.ItemModifier;
import org.encinet.mik.feature.community.chat.modifier.PlayerMentionModifier;
import org.encinet.mik.feature.community.chat.modifier.UrlModifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Local game input parsing and rendering effects. */
public final class ChatProcessor {
    private static final System.Logger LOGGER = System.getLogger(ChatProcessor.class.getName());
    private final List<ChatModifier> modifiers;
    private final Function<String, List<ChatNode>> authorizedText;

    public ChatProcessor(Function<String, List<ChatNode>> authorizedText) {
        this(List.of(
                new EmailModifier(),
                new AllMentionModifier(),
                new PlayerMentionModifier(),
                new ItemModifier(),
                new InventorySlotModifier(),
                new UrlModifier()), authorizedText);
    }

    ChatProcessor(
            List<? extends ChatModifier> modifiers,
            Function<String, List<ChatNode>> authorizedText
    ) {
        this.modifiers = modifiers.stream()
                .sorted(Comparator.comparingInt(ChatModifier::priority))
                .map(modifier -> (ChatModifier) modifier)
                .toList();
        this.authorizedText = java.util.Objects.requireNonNull(authorizedText, "authorizedText");
    }

    public ChatMessage process(ChatSubmission submission) {
        ChatContent content = process(submission.sourceText(), submission.processingContext());
        return new ChatMessage(submission, content, ChatEffect.fromContent(content));
    }

    /** Retain unchanged semantics and derive effects from edited content using the original capabilities. */
    public ChatMessage acceptFinalContent(ChatMessage original, ChatContent finalContent) {
        if (original.content().equals(finalContent)) return original;
        ChatProcessingContext context = original.submission().processingContext();
        Set<ChatCapability> mentionCapabilities = new LinkedHashSet<>(context.capabilities());
        mentionCapabilities.retainAll(Set.of(ChatCapability.PLAYER_MENTION, ChatCapability.BROADCAST_MENTION));
        ChatProcessingContext mentionContext = new ChatProcessingContext(
                mentionCapabilities, context.mentions(), java.util.Optional.empty(), java.util.Map.of());
        List<ChatNode> nodes = new ArrayList<>();
        for (ChatNode node : finalContent.nodes()) {
            if (node instanceof ChatNode.Text text) {
                // Imported link labels are never reparsed as links or mentions.
                for (ChatNode parsed : process(text.text(), mentionContext).nodes()) {
                    nodes.add(parsed instanceof ChatNode.Text plain
                            ? new ChatNode.Text(plain.text(), text.style()) : parsed);
                }
            } else {
                nodes.add(node);
            }
        }
        ChatContent content = new ChatContent(nodes);
        return new ChatMessage(original.submission(), content, ChatEffect.fromContent(content));
    }

    public ChatContent process(
            String source,
            ChatProcessingContext context
    ) {
        if (source == null || source.isEmpty()) {
            return new ChatContent(List.of());
        }
        ChatReplacement[] candidates = new ChatReplacement[modifiers.size()];
        for (int index = 0; index < modifiers.size(); index++) {
            ChatModifier modifier = modifiers.get(index);
            candidates[index] = findReplacement(modifier, source, 0, context);
        }
        List<ChatNode> nodes = new ArrayList<>();
        int cursor = 0;
        boolean previousReplacementPaddedAfter = false;
        while (cursor < source.length()) {
            ChatReplacement replacement = nextReplacement(
                    source, cursor, context, candidates);
            if (replacement == null) {
                nodes.addAll(renderTextSegment(source.substring(cursor), context));
                break;
            }
            if (replacement.start() > cursor) {
                nodes.addAll(renderTextSegment(
                        source.substring(cursor, replacement.start()), context));
                previousReplacementPaddedAfter = false;
            }
            if (!previousReplacementPaddedAfter
                    && needsPaddingBefore(source, replacement)) {
                nodes.add(new ChatNode.Text(" ", ChatStyle.EMPTY));
            }
            nodes.addAll(replacement.nodes());
            previousReplacementPaddedAfter = needsPaddingAfter(source, replacement);
            if (previousReplacementPaddedAfter) {
                nodes.add(new ChatNode.Text(" ", ChatStyle.EMPTY));
            }
            cursor = replacement.end();
        }
        return new ChatContent(nodes);
    }

    private List<ChatNode> renderTextSegment(
            String text,
            ChatProcessingContext context
    ) {
        if (text.isEmpty()) {
            return List.of();
        }
        if (!context.capabilities().contains(ChatCapability.MINI_MESSAGE)) {
            return List.of(new ChatNode.Text(text, ChatStyle.EMPTY));
        }
        return authorizedText.apply(text);
    }

    private ChatReplacement nextReplacement(
            String text,
            int fromIndex,
            ChatProcessingContext context,
            ChatReplacement[] candidates
    ) {
        ChatReplacement best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (int index = 0; index < modifiers.size(); index++) {
            ChatModifier modifier = modifiers.get(index);
            ChatReplacement replacement = candidates[index];
            // Future matches and exhausted searches stay valid as the cursor advances.
            if (replacement != null && replacement.start() < fromIndex) {
                replacement = findReplacement(modifier, text, fromIndex, context);
                candidates[index] = replacement;
            }
            if (replacement != null
                    && (best == null
                    || replacement.start() < best.start()
                    || replacement.start() == best.start()
                    && modifier.priority() < bestPriority)) {
                best = replacement;
                bestPriority = modifier.priority();
            }
        }
        return best;
    }

    private ChatReplacement findReplacement(
            ChatModifier modifier,
            String text,
            int fromIndex,
            ChatProcessingContext context
    ) {
        try {
            if (!modifier.supports(context)) {
                return null;
            }
            ChatReplacement replacement = modifier.find(text, fromIndex, context);
            if (replacement != null && (replacement.start() < fromIndex
                    || replacement.end() > text.length())) {
                throw new IllegalArgumentException("Chat modifier returned an invalid replacement range");
            }
            return replacement;
        } catch (RuntimeException error) {
            // A null candidate disables this modifier for the rest of this message.
            LOGGER.log(System.Logger.Level.WARNING,
                    "Chat modifier " + modifier.getClass().getName()
                            + " failed; skipping its remaining replacements for this message", error);
            return null;
        }
    }

    private boolean needsPaddingBefore(
            String text,
            ChatReplacement replacement
    ) {
        return replacement.spacing() == ChatReplacementSpacing.PAD_WHEN_JOINED
                && replacement.start() > 0
                && !Character.isWhitespace(text.charAt(replacement.start() - 1));
    }

    private boolean needsPaddingAfter(
            String text,
            ChatReplacement replacement
    ) {
        return replacement.spacing() == ChatReplacementSpacing.PAD_WHEN_JOINED
                && replacement.end() < text.length()
                && !Character.isWhitespace(text.charAt(replacement.end()));
    }
}
