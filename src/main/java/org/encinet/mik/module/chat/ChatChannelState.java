package org.encinet.mik.module.chat;

import java.util.UUID;

record ChatChannelState(ChatChannel channel, UUID targetId, String targetName) {

    static ChatChannelState publicChannel() {
        return new ChatChannelState(ChatChannel.PUBLIC, null, null);
    }

    static ChatChannelState staff() {
        return new ChatChannelState(ChatChannel.STAFF, null, null);
    }

    static ChatChannelState privateChannel(UUID targetId, String targetName) {
        return new ChatChannelState(ChatChannel.PRIVATE, targetId, targetName);
    }
}
