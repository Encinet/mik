package org.encinet.mik.module.chat.model;

import java.util.Objects;

/** Logical source conversation independent of any transport. */
public record ChatConversation(Kind kind, String id) {
    public ChatConversation {
        kind = Objects.requireNonNull(kind, "kind");
        id = Objects.requireNonNullElse(id, "").strip();
        if (id.length() > 512 || id.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("chat conversation ID is invalid");
        }
    }

    public static ChatConversation minecraftPublic() {
        return new ChatConversation(Kind.PUBLIC, "minecraft:public");
    }

    public static ChatConversation minecraftStaff() {
        return new ChatConversation(Kind.STAFF, "minecraft:staff");
    }

    public static ChatConversation minecraftPrivate(
            java.util.UUID firstPlayer,
            java.util.UUID secondPlayer
    ) {
        java.util.UUID first = Objects.requireNonNull(firstPlayer, "firstPlayer");
        java.util.UUID second = Objects.requireNonNull(secondPlayer, "secondPlayer");
        String pair = first.compareTo(second) <= 0
                ? first + ":" + second : second + ":" + first;
        return new ChatConversation(Kind.PRIVATE, "minecraft:private:" + pair);
    }

    public enum Kind {
        PUBLIC,
        STAFF,
        PRIVATE,
        SOCIAL
    }
}
