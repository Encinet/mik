package org.encinet.mik.module.chat.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Source and forwarding trace used to prevent bridge loops. */
public sealed interface ChatOrigin permits ChatOrigin.Minecraft,
        ChatOrigin.Social, ChatOrigin.Plugin {

    Set<String> visitedPlatforms();

    record Minecraft(UUID playerId) implements ChatOrigin {
        public Minecraft {
            playerId = Objects.requireNonNull(playerId, "playerId");
        }

        @Override
        public Set<String> visitedPlatforms() {
            return Set.of();
        }
    }

    record Social(String platformId, String eventId) implements ChatOrigin {
        public Social {
            platformId = requireId(platformId, "platformId");
            eventId = requireId(eventId, "eventId");
        }

        @Override
        public Set<String> visitedPlatforms() {
            return Set.of(platformId);
        }
    }

    record Plugin(String pluginId, Set<String> visitedPlatforms)
            implements ChatOrigin {
        public Plugin {
            pluginId = requireId(pluginId, "pluginId");
            visitedPlatforms = Set.copyOf(Objects.requireNonNullElse(
                    visitedPlatforms, Set.of()));
        }
    }

    private static String requireId(String value, String field) {
        String checked = Objects.requireNonNull(value, field).strip();
        if (checked.isEmpty() || checked.length() > 512
                || checked.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return checked;
    }
}
