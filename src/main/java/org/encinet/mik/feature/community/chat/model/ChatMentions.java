package org.encinet.mik.feature.community.chat.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Targets of live notifications, before intersecting with the actual game recipients. */
public record ChatMentions(Set<UUID> players, boolean broadcast) {
    public ChatMentions {
        players = Set.copyOf(players);
    }

    public static ChatMentions fromContent(ChatContent content) {
        var players = new HashSet<UUID>();
        boolean broadcast = false;
        for (ChatNode node : content.nodes()) {
            if (node instanceof ChatNode.Reference reference) {
                switch (reference.target()) {
                    case ChatNode.Reference.Person person -> players.add(person.playerId());
                    case ChatNode.Reference.Audience _ -> broadcast = true;
                    case ChatNode.Reference.Link _ -> { }
                }
            }
        }
        return new ChatMentions(players, broadcast);
    }

    public boolean includes(UUID id) { return broadcast || players.contains(id); }
}
