package org.encinet.mik.module.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.encinet.mik.Mik;
import org.encinet.mik.module.chat.modifier.AllMentionModifier;
import org.encinet.mik.module.chat.modifier.BilibiliModifier;
import org.encinet.mik.module.chat.modifier.ChatModifier;
import org.encinet.mik.module.chat.modifier.ChatModifierContext;
import org.encinet.mik.module.chat.modifier.ChatReplacement;
import org.encinet.mik.module.chat.modifier.ItemModifier;
import org.encinet.mik.module.chat.modifier.PlayerMentionModifier;
import org.encinet.mik.module.chat.modifier.UrlModifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class ChatMessageParser {

    private static final String MEMBER_PERMISSION = "group." + Mik.GROUP_MEMBER;
    private static final MiniMessage MEMBER_MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()
            ))
            .build();

    private final List<ChatModifier> modifiers = List.of(
            new BilibiliModifier(),
            new UrlModifier(),
            new ItemModifier(),
            new AllMentionModifier(),
            new PlayerMentionModifier()
    );

    Component parse(Player sender, Component originalMessage, Collection<Player> mentionablePlayers,
                    String itemEmptyHover, String allHover, String bilibiliHover) {
        return parse(sender, PlainTextComponentSerializer.plainText().serialize(originalMessage),
                mentionablePlayers, itemEmptyHover, allHover, bilibiliHover);
    }

    Component parse(Player sender, String plainMessage, Collection<Player> mentionablePlayers,
                    String itemEmptyHover, String allHover, String bilibiliHover) {
        if (plainMessage == null || plainMessage.isEmpty()) {
            return Component.empty();
        }

        List<Player> players = new ArrayList<>(mentionablePlayers);
        players.sort(Comparator.comparingInt((Player p) -> p.getName().length()).reversed());
        ChatModifierContext context = new ChatModifierContext(sender, players, itemEmptyHover, allHover, bilibiliHover);
        TextComponent.Builder builder = Component.text();
        boolean allowMiniMessage = sender.hasPermission(MEMBER_PERMISSION);

        int cursor = 0;
        while (cursor < plainMessage.length()) {
            ChatReplacement replacement = nextReplacement(plainMessage, cursor, context);
            if (replacement == null) {
                builder.append(renderTextSegment(plainMessage.substring(cursor), allowMiniMessage));
                break;
            }
            if (replacement.start() > cursor) {
                builder.append(renderTextSegment(plainMessage.substring(cursor, replacement.start()), allowMiniMessage));
            }
            builder.append(replacement.component());
            cursor = replacement.end();
        }

        return builder.build().colorIfAbsent(NamedTextColor.WHITE);
    }

    private Component renderTextSegment(String text, boolean allowMiniMessage) {
        if (!allowMiniMessage || text.isEmpty()) {
            return Component.text(text);
        }
        try {
            return MEMBER_MINI_MESSAGE.deserialize(text);
        } catch (RuntimeException ignored) {
            return Component.text(text);
        }
    }

    private ChatReplacement nextReplacement(String text, int fromIndex, ChatModifierContext context) {
        ChatReplacement best = null;
        int priority = 0;
        int bestPriority = Integer.MAX_VALUE;
        for (ChatModifier modifier : modifiers) {
            ChatReplacement replacement = modifier.find(text, fromIndex, context);
            if (replacement != null && (best == null
                    || replacement.start() < best.start()
                    || (replacement.start() == best.start() && priority < bestPriority))) {
                best = replacement;
                bestPriority = priority;
            }
            priority += 1;
        }
        return best;
    }
}
