package org.encinet.mik.feature.community.chat.bridge;

import java.util.Objects;

/** One destination platform's outbound moderation failure; contains no original message body. */
public record SocialChatFailure(String platformId, String platformName, String reason, boolean unavailable) {
    public SocialChatFailure {
        Objects.requireNonNull(platformId, "platformId");
        Objects.requireNonNull(platformName, "platformName");
        Objects.requireNonNull(reason, "reason");
    }
}
