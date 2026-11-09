package org.encinet.mik.feature.community.chat.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Logical membership; adapters supply their current admission and staff facts. */
public record ChatAudience(ChatChannel channel, Set<UUID> participants) {
    public ChatAudience {
        Objects.requireNonNull(channel, "channel");
        participants = Set.copyOf(participants);
        if (channel == ChatChannel.PRIVATE ? participants.isEmpty() || participants.size() > 2 : !participants.isEmpty())
            throw new IllegalArgumentException("Only private audiences have one or two participants");
    }

    public static ChatAudience of(ChatMessage message, ChatSender target) {
        ChatSubmission submission = Objects.requireNonNull(message, "message").submission();
        ChatChannel channel = submission.conversation().channel();
        if ((channel == ChatChannel.PRIVATE) != (target != null))
            throw new IllegalArgumentException("Private chat requires its target");
        if (target == null) return new ChatAudience(channel, Set.of());
        UUID senderId = submission.sender().minecraftId().orElseThrow();
        UUID targetId = target.minecraftId().orElseThrow();
        if (!submission.conversation().equals(ChatConversation.minecraftPrivate(senderId, targetId)))
            throw new IllegalArgumentException("Private target does not match its conversation");
        return new ChatAudience(channel, Set.copyOf(List.of(senderId, targetId)));
    }

    public boolean includes(UUID viewer, boolean staff) {
        Objects.requireNonNull(viewer, "viewer");
        return switch (channel) {
            case PUBLIC -> true;
            case STAFF -> staff;
            case PRIVATE -> participants.contains(viewer);
        };
    }
}
