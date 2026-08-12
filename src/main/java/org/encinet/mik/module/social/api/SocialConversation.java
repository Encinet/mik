package org.encinet.mik.module.social.api;

import java.util.Objects;

/** An opaque platform conversation identifier. */
public record SocialConversation(String id, Type type) {

    public SocialConversation {
        id = Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("conversation id must not be blank");
        }
        type = Objects.requireNonNull(type, "type");
    }

    public enum Type {
        GROUP,
        DIRECT,
        CHANNEL
    }
}
