package org.encinet.mik.feature.community.chat.model;

import java.util.Objects;
import java.util.UUID;

/** Source of a message displayed in the game. */
public sealed interface ChatOrigin permits ChatOrigin.Player,
        ChatOrigin.Social {

    sealed interface Player extends ChatOrigin permits Minecraft, Web {
        UUID playerId();
    }

    record Minecraft(UUID playerId) implements Player {
        public Minecraft { playerId = Objects.requireNonNull(playerId, "playerId"); }
    }

    record Web(UUID playerId) implements Player {
        public Web { playerId = Objects.requireNonNull(playerId, "playerId"); }
    }

    record Social(String platformName, int sourceColor) implements ChatOrigin {
        public Social {
            platformName = Objects.requireNonNull(platformName, "platformName");
            if (platformName.isBlank()) throw new IllegalArgumentException("platformName is blank");
            if (sourceColor < 0 || sourceColor > 0xFFFFFF) {
                throw new IllegalArgumentException("sourceColor is outside RGB range");
            }
        }

    }

}
