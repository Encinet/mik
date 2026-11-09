package org.encinet.mik.feature.community.chat.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Source and forwarding trace used to prevent bridge loops. */
public sealed interface ChatOrigin permits ChatOrigin.Player,
        ChatOrigin.Social, ChatOrigin.Plugin {

    Set<String> visitedPlatforms();

    sealed interface Player extends ChatOrigin permits Minecraft, Web {
        UUID playerId();
        @Override default Set<String> visitedPlatforms() { return Set.of(); }
    }

    record Minecraft(UUID playerId) implements Player {
        public Minecraft { playerId = Objects.requireNonNull(playerId, "playerId"); }
    }

    record Web(UUID playerId) implements Player {
        public Web { playerId = Objects.requireNonNull(playerId, "playerId"); }
    }

    record Social(String platformId, String platformName, int sourceColor,
                  String conversationId, String eventId) implements ChatOrigin {
        public Social {
            platformId = requireId(platformId, "platformId");
            platformName = requireId(platformName, "platformName");
            if (sourceColor < 0 || sourceColor > 0xFFFFFF) {
                throw new IllegalArgumentException("sourceColor is outside RGB range");
            }
            conversationId = requireId(conversationId, "conversationId");
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
