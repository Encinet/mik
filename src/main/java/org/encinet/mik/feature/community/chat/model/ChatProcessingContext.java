package org.encinet.mik.feature.community.chat.model;

import org.encinet.mik.feature.community.chat.mention.PlayerMentions;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable capabilities and snapshots available to semantic modifiers. */
public record ChatProcessingContext(
        Set<ChatCapability> capabilities,
        PlayerMentions mentions,
        Optional<ChatItemSnapshot> mainHand,
        Map<Integer, ChatItemSnapshot> inventory
) {
    public ChatProcessingContext {
        capabilities = Set.copyOf(Objects.requireNonNullElse(capabilities, Set.of()));
        mentions = Objects.requireNonNullElseGet(mentions, PlayerMentions::empty);
        mainHand = mainHand == null ? Optional.empty() : mainHand;
        inventory = Map.copyOf(Objects.requireNonNullElse(inventory, Map.of()));
    }

    /** Text-only player input has no inventory, formatting or broadcast privileges. */
    public static ChatProcessingContext playerText(PlayerMentions mentions) {
        return new ChatProcessingContext(Set.of(ChatCapability.URL, ChatCapability.PLAYER_MENTION),
                mentions, Optional.empty(), Map.of());
    }

    public static ChatProcessingContext external() {
        return new ChatProcessingContext(Set.of(ChatCapability.URL),
                PlayerMentions.empty(), Optional.empty(), Map.of());
    }
}
