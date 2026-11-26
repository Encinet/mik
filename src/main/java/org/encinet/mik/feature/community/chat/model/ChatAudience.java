package org.encinet.mik.feature.community.chat.model;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Logical membership; callers supply current admission and staff facts. */
public record ChatAudience(ChatChannel channel, Set<UUID> participants) {
    public static final ChatAudience PUBLIC = new ChatAudience(ChatChannel.PUBLIC, Set.of());
    public static final ChatAudience STAFF = new ChatAudience(ChatChannel.STAFF, Set.of());

    public ChatAudience {
        Objects.requireNonNull(channel, "channel");
        participants = Set.copyOf(participants);
        if (participants.size() != (channel == ChatChannel.PRIVATE ? 2 : 0))
            throw new IllegalArgumentException("Private audiences require exactly two distinct players");
    }

    public static ChatAudience of(ChatChannel channel, UUID sender, UUID target) {
        if ((channel == ChatChannel.PRIVATE) != (target != null))
            throw new IllegalArgumentException("Private chat requires its target");
        return switch (channel) {
            case PUBLIC -> PUBLIC;
            case STAFF -> STAFF;
            case PRIVATE -> {
                if (sender == null) throw new IllegalArgumentException("Private chat requires its author");
                yield new ChatAudience(channel, Set.of(sender, target));
            }
        };
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
