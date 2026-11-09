package org.encinet.mik.feature.community.chat.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Trusted server output, with no fabricated player sender or player-text capabilities. */
public record ChatServerMessage(
        ChatMessageId id,
        ChatConversation conversation,
        Body content,
        Optional<Subject> subject,
        Map<UUID, String> participants,
        boolean countsAsUnread,
        Instant createdAt
) {
    public ChatServerMessage {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversation, "conversation");
        Objects.requireNonNull(content, "content");
        subject = Objects.requireNonNull(subject, "subject");
        Map<UUID, String> identities = new LinkedHashMap<>();
        Objects.requireNonNull(participants, "participants").forEach((player, name) -> {
            Subject identity = new Subject(player, name);
            identities.put(identity.playerUuid(), identity.name());
        });
        participants = Map.copyOf(identities);
        if (conversation.channel() == ChatChannel.PRIVATE) {
            if (participants.size() != 2) throw new IllegalArgumentException("Private server output requires two participants");
            var ids = participants.keySet().stream().toList();
            if (!conversation.equals(ChatConversation.minecraftPrivate(ids.get(0), ids.get(1))))
                throw new IllegalArgumentException("Private participants must match their conversation");
        } else if (!participants.isEmpty() || !conversation.equals(conversation.channel() == ChatChannel.PUBLIC
                ? ChatConversation.minecraftPublic() : ChatConversation.minecraftStaff())) {
            throw new IllegalArgumentException("Server output requires a canonical conversation");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        if (createdAt.toEpochMilli() < 0 || createdAt.toEpochMilli() > 8_640_000_000_000_000L)
            throw new IllegalArgumentException("Invalid server message time");
    }

    public static ChatServerMessage publicMessage(Body content, Subject subject, boolean countsAsUnread) {
        return new ChatServerMessage(ChatMessageId.random(), ChatConversation.minecraftPublic(), content,
                Optional.ofNullable(subject), Map.of(), countsAsUnread, Instant.now());
    }

    public ChatAudience audience() { return new ChatAudience(conversation.channel(), participants.keySet()); }

    public record Subject(UUID playerUuid, String name) {
        public Subject {
            Objects.requireNonNull(playerUuid, "playerUuid");
            name = line(name, 64);
        }
    }

    public sealed interface Body permits RichText, Translation { }

    public record RichText(ChatContent content) implements Body {
        public RichText {
            Objects.requireNonNull(content, "content");
            if (content.plainText().isBlank()) throw new IllegalArgumentException("Server output cannot be blank");
        }
    }

    /** Supported website templates are explicit; ordinary output uses RichText. */
    public enum Template {
        GAME_JOINED("gameJoined"), GAME_LEFT("gameLeft"), WEB_JOINED("webJoined"), WEB_LEFT("webLeft");
        private final String key;
        Template(String key) { this.key = key; }
        public String key() { return key; }
    }

    public record Translation(Template template, Map<String, String> values) implements Body {
        public Translation {
            Objects.requireNonNull(template, "template");
            Map<String, String> checked = new LinkedHashMap<>(Objects.requireNonNull(values, "values"));
            if (!checked.keySet().equals(java.util.Set.of("name")))
                throw new IllegalArgumentException("Player templates require the name argument");
            checked.replaceAll((_, value) -> line(value, 64));
            values = Map.copyOf(checked);
        }
    }

    private static String line(String value, int maximum) {
        String checked = Objects.requireNonNull(value, "value").strip();
        if (checked.isEmpty() || checked.length() > maximum || checked.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid server message value");
        return checked;
    }
}
