package org.encinet.mik.module.chat;

import java.util.UUID;

public record ChatChannelState(ChatChannel channel, UUID targetId, String targetName) {

    public static ChatChannelState publicChannel() {
        return new ChatChannelState(ChatChannel.PUBLIC, null, null);
    }

    public static ChatChannelState staff() {
        return new ChatChannelState(ChatChannel.STAFF, null, null);
    }

    public static ChatChannelState privateChannel(UUID targetId, String targetName) {
        return new ChatChannelState(ChatChannel.PRIVATE, targetId, targetName);
    }
}
