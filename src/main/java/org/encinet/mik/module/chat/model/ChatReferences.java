package org.encinet.mik.module.chat.model;

import org.encinet.mik.module.identity.ExternalIdentity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reply, thread and structured mention references retained across transports. */
public record ChatReferences(
        Optional<Reference> replyTo,
        Optional<Reference> threadRoot,
        List<Mention> mentions
) {
    public ChatReferences {
        replyTo = replyTo == null ? Optional.empty() : replyTo;
        threadRoot = threadRoot == null ? Optional.empty() : threadRoot;
        mentions = List.copyOf(Objects.requireNonNullElse(mentions, List.of()));
        if (mentions.size() > 64) {
            throw new IllegalArgumentException("too many chat references");
        }
        int previousEnd = -1;
        for (Mention mention : mentions) {
            Objects.requireNonNull(mention, "mention");
            if (mention.start() < previousEnd) {
                throw new IllegalArgumentException(
                        "chat mention references must be ordered and non-overlapping");
            }
            previousEnd = mention.end();
        }
    }

    public static ChatReferences empty() {
        return new ChatReferences(Optional.empty(), Optional.empty(), List.of());
    }

    public record Mention(
            int start,
            int end,
            ExternalIdentity externalIdentity,
            Optional<MinecraftTarget> minecraftTarget
    ) {
        public Mention {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException(
                        "chat mention reference range is invalid");
            }
            externalIdentity = Objects.requireNonNull(
                    externalIdentity, "externalIdentity");
            minecraftTarget = minecraftTarget == null
                    ? Optional.empty() : minecraftTarget;
        }

        public Mention(int start, int end, ExternalIdentity externalIdentity) {
            this(start, end, externalIdentity, Optional.empty());
        }

        public Mention withMinecraftTarget(UUID playerId, String playerName) {
            return new Mention(start, end, externalIdentity,
                    Optional.of(new MinecraftTarget(playerId, playerName)));
        }
    }

    public record MinecraftTarget(UUID playerId, String playerName) {
        public MinecraftTarget {
            playerId = Objects.requireNonNull(playerId, "playerId");
            playerName = Reference.requireLine(playerName, "playerName", 64);
        }
    }

    public record Reference(String id, String displayName) {
        public Reference {
            id = requireLine(id, "id", 512);
            displayName = requireLine(displayName, "displayName", 128);
        }

        static String requireLine(
                String value, String field, int maximumLength
        ) {
            String checked = Objects.requireNonNull(value, field).strip();
            if (checked.isEmpty() || checked.length() > maximumLength
                    || checked.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("chat reference " + field + " is invalid");
            }
            return checked;
        }
    }
}
