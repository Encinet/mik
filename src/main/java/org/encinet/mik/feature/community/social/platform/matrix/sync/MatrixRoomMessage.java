package org.encinet.mik.feature.community.social.platform.matrix.sync;

import java.util.List;
import org.encinet.mik.feature.community.chat.model.ChatPlaceholder;
import java.util.Optional;

/** Authenticated fields after optional device decryption and shared text projection. */
public record MatrixRoomMessage(
        String eventId,
        String roomId,
        MatrixMember sender,
        String body,
        String commandBody,
        List<MatrixMention> mentions,
        Optional<MatrixMember> repliedAuthor,
        Optional<String> threadRootEventId,
        List<ChatPlaceholder.Span> placeholders
) {
    public MatrixRoomMessage {
        mentions = List.copyOf(mentions);
        placeholders = ChatPlaceholder.validate(placeholders, body.length());
        repliedAuthor = repliedAuthor == null ? Optional.empty() : repliedAuthor;
        threadRootEventId = threadRootEventId == null
                ? Optional.empty() : threadRootEventId;
    }

    public MatrixRoomMessage(String eventId, String roomId, MatrixMember sender, String body, String commandBody,
                             List<MatrixMention> mentions, Optional<MatrixMember> repliedAuthor,
                             Optional<String> threadRootEventId) {
        this(eventId, roomId, sender, body, commandBody, mentions, repliedAuthor, threadRootEventId, List.of());
    }

    public record MatrixMember(String userId, String displayName) {
        public MatrixMember {
            displayName = displayName == null || displayName.isBlank()
                    ? userId : displayName;
        }
    }

    /** Exact visible range authenticated by the event's m.mentions metadata. */
    public record MatrixMention(MatrixMember member, int start, int end) {
        public MatrixMention {
            if (start < 0 || end <= start) {
                throw new IllegalArgumentException("invalid Matrix mention range");
            }
        }
    }
}
