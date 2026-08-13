package org.encinet.mik.module.social.platform.matrix.sync;

import java.util.List;
import java.util.Optional;

/** Authenticated fields from one unencrypted Matrix room message. */
public record MatrixRoomMessage(
        String eventId,
        String roomId,
        MatrixMember sender,
        String body,
        List<MatrixMention> mentions,
        Optional<MatrixMember> repliedAuthor,
        Optional<String> threadRootEventId
) {
    public MatrixRoomMessage {
        mentions = List.copyOf(mentions);
        repliedAuthor = repliedAuthor == null ? Optional.empty() : repliedAuthor;
        threadRootEventId = threadRootEventId == null
                ? Optional.empty() : threadRootEventId;
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
