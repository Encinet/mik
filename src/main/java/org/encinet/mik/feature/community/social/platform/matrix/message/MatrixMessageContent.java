package org.encinet.mik.feature.community.social.platform.matrix.message;

import java.util.Optional;
import java.util.Set;

/** Protocol content independent of /sync state, identity lookup and display policy. */
public record MatrixMessageContent(
        Kind kind, String body, Optional<String> html, String filename,
        Set<String> mentionedUserIds, Relation relation
) {
    public MatrixMessageContent {
        mentionedUserIds = Set.copyOf(mentionedUserIds);
    }

    public enum Kind { TEXT, NOTICE, EMOTE, IMAGE, AUDIO, VIDEO, FILE, LOCATION, STICKER, UNKNOWN }

    /** Matrix uses body as a filename unless a distinct filename identifies it as a caption. */
    public boolean bodyIsFilename() {
        return switch (kind) {
            case IMAGE, AUDIO, VIDEO, FILE -> filename.isBlank() || filename.equals(body);
            default -> false;
        };
    }

    public record Relation(
            Optional<String> replyEventId,
            Optional<String> threadRootEventId,
            boolean threadFallback,
            boolean replacement
    ) {
        public Optional<String> explicitReplyEventId() {
            return threadFallback ? Optional.empty() : replyEventId;
        }
    }
}
