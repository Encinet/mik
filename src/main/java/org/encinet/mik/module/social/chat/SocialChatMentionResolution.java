package org.encinet.mik.module.social.chat;

import org.encinet.mik.module.identity.ExternalIdentity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Conversation-specific native identities that are safe to notify. */
public record SocialChatMentionResolution(
        Map<UUID, List<ExternalIdentity>> targets
) {
    public SocialChatMentionResolution {
        Map<UUID, List<ExternalIdentity>> copy = new LinkedHashMap<>();
        Objects.requireNonNullElse(targets,
                        Map.<UUID, List<ExternalIdentity>>of())
                .forEach((playerId, identities) -> {
                    LinkedHashMap<Object, ExternalIdentity> unique =
                            new LinkedHashMap<>();
                    Objects.requireNonNull(identities, "identities")
                            .forEach(identity -> unique.putIfAbsent(
                                    Objects.requireNonNull(identity, "identity").key(),
                                    identity));
                    if (!unique.isEmpty()) {
                        copy.put(Objects.requireNonNull(playerId, "playerId"),
                                List.copyOf(unique.values()));
                    }
                });
        targets = Map.copyOf(copy);
    }

    public static SocialChatMentionResolution empty() {
        return new SocialChatMentionResolution(Map.of());
    }

    public List<ExternalIdentity> targetsFor(UUID playerId) {
        return targets.getOrDefault(playerId, List.of());
    }
}
