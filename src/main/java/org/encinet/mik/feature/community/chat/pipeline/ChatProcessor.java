package org.encinet.mik.feature.community.chat.pipeline;

import org.encinet.mik.feature.community.chat.model.ChatPlaceholder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatEffect;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatReferences;
import org.encinet.mik.feature.community.chat.model.ChatStyle;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.modifier.AllMentionModifier;
import org.encinet.mik.feature.community.chat.modifier.BilibiliModifier;
import org.encinet.mik.feature.community.chat.modifier.ChatModifier;
import org.encinet.mik.feature.community.chat.modifier.ChatReplacement;
import org.encinet.mik.feature.community.chat.modifier.ChatReplacementSpacing;
import org.encinet.mik.feature.community.chat.modifier.EmailModifier;
import org.encinet.mik.feature.community.chat.modifier.GitHubModifier;
import org.encinet.mik.feature.community.chat.modifier.InventorySlotModifier;
import org.encinet.mik.feature.community.chat.modifier.ItemModifier;
import org.encinet.mik.feature.community.chat.modifier.MatrixLinkModifier;
import org.encinet.mik.feature.community.chat.modifier.MinecraftWikiModifier;
import org.encinet.mik.feature.community.chat.modifier.MojiraModifier;
import org.encinet.mik.feature.community.chat.modifier.PlayerMentionModifier;
import org.encinet.mik.feature.community.chat.modifier.UrlModifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** The single semantic parsing and effect-derivation pipeline for every origin. */
public final class ChatProcessor {
    private static final ChatStyle TRUSTED_MENTION_STYLE = new ChatStyle(
            0xFFD166, null, false, false, false, false);
    private static final MiniMessage SAFE_MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()))
            .build();

    private final List<ChatModifier> modifiers;
    private final AdventureComponentImporter componentImporter;

    public ChatProcessor() {
        this(List.of(
                new GitHubModifier(),
                new MinecraftWikiModifier(),
                new BilibiliModifier(),
                new MojiraModifier(),
                new MatrixLinkModifier(),
                new EmailModifier(),
                new AllMentionModifier(),
                new PlayerMentionModifier(),
                new ItemModifier(),
                new InventorySlotModifier(),
                new UrlModifier()), new AdventureComponentImporter());
    }

    ChatProcessor(
            List<? extends ChatModifier> modifiers,
            AdventureComponentImporter componentImporter
    ) {
        this.modifiers = modifiers.stream()
                .sorted(Comparator.comparingInt(ChatModifier::priority))
                .map(modifier -> (ChatModifier) modifier)
                .toList();
        this.componentImporter = java.util.Objects.requireNonNull(
                componentImporter, "componentImporter");
    }

    public ChatMessage process(ChatSubmission submission) {
        ChatContent content = process(
                submission.sourceText(), submission.processingContext(),
                submission.references());
        return new ChatMessage(submission, content, effects(content));
    }

    public ChatContent process(String source, ChatProcessingContext context) {
        return process(source, context, ChatReferences.empty());
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
        return new ChatMessage(original.submission(), content, effects(content));
    }

    private ChatContent process(
            String source,
            ChatProcessingContext context,
            ChatReferences references
    ) {
        if (source == null || source.isEmpty()) {
            return new ChatContent(List.of());
        }
        ChatReplacement[] candidates = new ChatReplacement[modifiers.size()];
        for (int index = 0; index < modifiers.size(); index++) {
            ChatModifier modifier = modifiers.get(index);
            if (modifier.supports(context)) {
                candidates[index] = modifier.find(source, 0, context);
            }
        }
        List<ChatNode> nodes = new ArrayList<>();
        int cursor = 0;
        boolean previousReplacementPaddedAfter = false;
        while (cursor < source.length()) {
            ChatReplacement replacement = nextReplacement(
                    source, cursor, context, references, candidates);
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
        try {
            Component parsed = SAFE_MINI_MESSAGE.deserialize(text);
            return componentImporter.importComponent(parsed).nodes();
        } catch (RuntimeException ignored) {
            return List.of(new ChatNode.Text(text, ChatStyle.EMPTY));
        }
    }

    private ChatReplacement nextReplacement(
            String text,
            int fromIndex,
            ChatProcessingContext context,
            ChatReferences references,
            ChatReplacement[] candidates
    ) {
        ChatReplacement trusted = trustedReplacement(
                text, fromIndex, references.mentions());
        for (var span : references.placeholders()) {
            if (span.start() >= fromIndex && span.end() <= text.length()
                    && (trusted == null || span.start() < trusted.start())) {
                trusted = new ChatReplacement(span.start(), span.end(),
                        new ChatNode.Placeholder(span.kind(), ChatStyle.EMPTY));
                break;
            }
        }
        ChatReplacement best = trusted;
        int bestPriority = Integer.MAX_VALUE;
        for (int index = 0; index < modifiers.size(); index++) {
            ChatModifier modifier = modifiers.get(index);
            ChatReplacement replacement = candidates[index];
            // Future matches and exhausted searches stay valid as the cursor advances.
            if (replacement != null && replacement.start() < fromIndex) {
                replacement = modifier.find(text, fromIndex, context);
                candidates[index] = replacement;
            }
            if (replacement != null
                    && !overlapsTrustedMention(replacement, references.mentions())
                    && !overlapsPlaceholder(replacement, references.placeholders())
                    && (best == null
                    || replacement.start() < best.start()
                    || replacement.start() == best.start()
                    && trusted == null
                    && modifier.priority() < bestPriority)) {
                best = replacement;
                bestPriority = modifier.priority();
            }
        }
        return best;
    }

    private ChatReplacement trustedReplacement(
            String text,
            int fromIndex,
            List<ChatReferences.Mention> mentions
    ) {
        for (ChatReferences.Mention mention : mentions) {
            if (mention.start() < fromIndex || mention.end() > text.length()) {
                continue;
            }
            ChatNode node = mention.minecraftTarget()
                    .<ChatNode>map(target -> new ChatNode.PlayerMention(
                            target.playerId(), target.playerName(),
                            java.util.Optional.of(mention.externalIdentity()),
                            TRUSTED_MENTION_STYLE))
                    .orElseGet(() -> new ChatNode.ExternalMention(
                            mention.externalIdentity(), TRUSTED_MENTION_STYLE));
            return new ChatReplacement(mention.start(), mention.end(), node);
        }
        return null;
    }

    private boolean overlapsTrustedMention(
            ChatReplacement replacement,
            List<ChatReferences.Mention> mentions
    ) {
        for (ChatReferences.Mention mention : mentions) {
            if (replacement.start() < mention.end()
                    && mention.start() < replacement.end()) {
                return true;
            }
        }
        return false;
    }

    private boolean overlapsPlaceholder(ChatReplacement replacement,
                                        List<ChatPlaceholder.Span> spans) {
        return spans.stream().anyMatch(span -> replacement.start() < span.end() && span.start() < replacement.end());
    }

    public static Set<ChatEffect> effects(ChatContent content) {
        LinkedHashSet<UUID> players = new LinkedHashSet<>();
        boolean broadcast = false;
        for (ChatNode node : content.nodes()) {
            if (node instanceof ChatNode.PlayerMention mention) {
                players.add(mention.playerId());
            } else if (node instanceof ChatNode.BroadcastMention) {
                broadcast = true;
            }
        }
        return players.isEmpty() && !broadcast
                ? Set.of() : Set.of(new ChatEffect.Mentions(players, broadcast));
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
