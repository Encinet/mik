package org.encinet.mik.module.chat.pipeline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatEffect;
import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatProcessingContext;
import org.encinet.mik.module.chat.model.ChatReferences;
import org.encinet.mik.module.chat.model.ChatStyle;
import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.chat.modifier.AllMentionModifier;
import org.encinet.mik.module.chat.modifier.BilibiliModifier;
import org.encinet.mik.module.chat.modifier.ChatModifier;
import org.encinet.mik.module.chat.modifier.ChatReplacement;
import org.encinet.mik.module.chat.modifier.ChatReplacementSpacing;
import org.encinet.mik.module.chat.modifier.EmailModifier;
import org.encinet.mik.module.chat.modifier.GitHubModifier;
import org.encinet.mik.module.chat.modifier.InventorySlotModifier;
import org.encinet.mik.module.chat.modifier.ItemModifier;
import org.encinet.mik.module.chat.modifier.MatrixLinkModifier;
import org.encinet.mik.module.chat.modifier.MinecraftWikiModifier;
import org.encinet.mik.module.chat.modifier.MojiraModifier;
import org.encinet.mik.module.chat.modifier.PlayerMentionModifier;
import org.encinet.mik.module.chat.modifier.UrlModifier;

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

    private ChatContent process(
            String source,
            ChatProcessingContext context,
            ChatReferences references
    ) {
        if (source == null || source.isEmpty()) {
            return new ChatContent(List.of());
        }
        List<ChatNode> nodes = new ArrayList<>();
        int cursor = 0;
        boolean previousReplacementPaddedAfter = false;
        while (cursor < source.length()) {
            ChatReplacement replacement = nextReplacement(
                    source, cursor, context, references.mentions());
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
            List<ChatReferences.Mention> trustedMentions
    ) {
        ChatReplacement trusted = trustedReplacement(
                text, fromIndex, trustedMentions);
        ChatReplacement best = trusted;
        int bestPriority = Integer.MAX_VALUE;
        for (ChatModifier modifier : modifiers) {
            if (!modifier.supports(context)) {
                continue;
            }
            ChatReplacement replacement = modifier.find(text, fromIndex, context);
            if (replacement != null
                    && !overlapsTrustedMention(replacement, trustedMentions)
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

    private Set<ChatEffect> effects(ChatContent content) {
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
