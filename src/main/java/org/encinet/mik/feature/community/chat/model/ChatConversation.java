package org.encinet.mik.feature.community.chat.model;

import java.util.Objects;

/** Logical conversation and audience scope independent of its message source. */
public record ChatConversation(ChatChannel channel, String id) {
    public ChatConversation {
        channel = Objects.requireNonNull(channel, "channel");
        id = Objects.requireNonNullElse(id, "").strip();
        if (id.length() > 512 || id.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("chat conversation ID is invalid");
        }
    }

    public static ChatConversation minecraftPublic() {
        return new ChatConversation(ChatChannel.PUBLIC, "minecraft:public");
    }

    public static ChatConversation minecraftStaff() {
        return new ChatConversation(ChatChannel.STAFF, "minecraft:staff");
    }

    public static ChatConversation minecraftPrivate(
            java.util.UUID firstPlayer,
            java.util.UUID secondPlayer
    ) {
        java.util.UUID first = Objects.requireNonNull(firstPlayer, "firstPlayer");
        java.util.UUID second = Objects.requireNonNull(secondPlayer, "secondPlayer");
        String pair = first.compareTo(second) <= 0
                ? first + ":" + second : second + ":" + first;
        return new ChatConversation(ChatChannel.PRIVATE, "minecraft:private:" + pair);
    }
}
