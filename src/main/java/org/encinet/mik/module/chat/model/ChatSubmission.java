package org.encinet.mik.module.chat.model;

import java.time.Instant;
import java.util.Objects;

/** Immutable raw input accepted from Minecraft, a social adapter or another plugin. */
public record ChatSubmission(
        ChatMessageId id,
        ChatOrigin origin,
        ChatSender sender,
        ChatConversation conversation,
        String sourceText,
        ChatReferences references,
        Instant receivedAt,
        ChatProcessingContext processingContext
) {
    private static final int MAXIMUM_SOURCE_LENGTH = 4_000;

    public ChatSubmission {
        id = Objects.requireNonNull(id, "id");
        origin = Objects.requireNonNull(origin, "origin");
        sender = Objects.requireNonNull(sender, "sender");
        conversation = Objects.requireNonNull(conversation, "conversation");
        sourceText = normalize(sourceText);
        references = Objects.requireNonNullElseGet(references, ChatReferences::empty);
        receivedAt = Objects.requireNonNullElseGet(receivedAt, Instant::now);
        processingContext = Objects.requireNonNullElseGet(
                processingContext, ChatProcessingContext::external);
    }

    private static String normalize(String value) {
        String source = Objects.requireNonNull(value, "sourceText");
        StringBuilder normalized = new StringBuilder(
                Math.min(source.length(), MAXIMUM_SOURCE_LENGTH));
        for (int index = 0; index < source.length(); ) {
            int codePoint = source.codePointAt(index);
            if (isLineBreak(codePoint)) {
                normalized.append(' ');
            } else if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException("chat source text is invalid");
            } else {
                normalized.appendCodePoint(codePoint);
            }
            if (normalized.length() > MAXIMUM_SOURCE_LENGTH) {
                throw new IllegalArgumentException("chat source text is invalid");
            }
            index += Character.charCount(codePoint);
        }
        String checked = normalized.toString().strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException("chat source text is invalid");
        }
        return checked;
    }

    private static boolean isLineBreak(int codePoint) {
        return codePoint == '\n' || codePoint == '\r';
    }
}
