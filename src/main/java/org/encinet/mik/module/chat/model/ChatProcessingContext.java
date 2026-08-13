package org.encinet.mik.module.chat.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Immutable capabilities and snapshots available to semantic modifiers. */
public record ChatProcessingContext(
        Set<ChatCapability> capabilities,
        List<PlayerReference> mentionablePlayers,
        Optional<ChatItemSnapshot> mainHand,
        Map<Integer, ChatItemSnapshot> inventory
) {
    public ChatProcessingContext {
        capabilities = Set.copyOf(Objects.requireNonNullElse(capabilities, Set.of()));
        mentionablePlayers = List.copyOf(Objects.requireNonNullElse(
                mentionablePlayers, List.of()));
        mainHand = mainHand == null ? Optional.empty() : mainHand;
        inventory = Map.copyOf(Objects.requireNonNullElse(inventory, Map.of()));
    }

    public static ChatProcessingContext external() {
        return new ChatProcessingContext(Set.of(ChatCapability.URL),
                List.of(), Optional.empty(), Map.of());
    }

    public record PlayerReference(UUID id, String name) {
        public PlayerReference {
            id = Objects.requireNonNull(id, "id");
            name = Objects.requireNonNull(name, "name").strip();
            if (name.isEmpty() || name.length() > 64
                    || name.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("mentionable player name is invalid");
            }
        }
    }
}
