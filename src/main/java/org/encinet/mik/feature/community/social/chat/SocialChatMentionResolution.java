package org.encinet.mik.feature.community.social.chat;

import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.chat.model.ChatNode;

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

    /** Shared visible wording for moderation and both platform renderers. */
    public static String playerMentionText(ChatNode.PlayerMention mention,
                                           List<ExternalIdentity> identities) {
        if (identities.isEmpty()) return mention.visibleText();
        List<String> names = identities.stream().map(identity -> identity.displayName().isBlank()
                ? identity.key().subject() : identity.displayName()).toList();
        return mention.sourceIdentity().isEmpty()
                ? "@" + mention.playerName() + "(" + String.join(", ", names) + ")"
                : String.join(", ", names.stream()
                .map(name -> "@" + name + "(" + mention.playerName() + ")").toList());
    }
}
