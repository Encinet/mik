package org.encinet.mik.feature.community.chat.model;

import org.encinet.mik.shared.i18n.Message;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Semantic descriptions supplied by a transport, never inferred from user text. */
public enum ChatPlaceholder {
    AUDIO, CARD, CONTACT, EMOTE, FACE, FILE, HISTORY, IMAGE, LOCATION,
    MESSAGE, MULTIPLE, MUSIC, NOTICE, REPLY, STICKER, VIDEO;

    public Message message() {
        return switch (this) {
            case AUDIO -> Message.CHAT_CONTENT_AUDIO;
            case CARD -> Message.CHAT_CONTENT_CARD;
            case CONTACT -> Message.CHAT_CONTENT_CONTACT;
            case EMOTE -> Message.CHAT_CONTENT_EMOTE;
            case FACE -> Message.CHAT_CONTENT_FACE;
            case FILE -> Message.CHAT_CONTENT_FILE;
            case HISTORY -> Message.CHAT_CONTENT_HISTORY;
            case IMAGE -> Message.CHAT_CONTENT_IMAGE;
            case LOCATION -> Message.CHAT_CONTENT_LOCATION;
            case MESSAGE -> Message.CHAT_CONTENT_MESSAGE;
            case MULTIPLE -> Message.CHAT_CONTENT_MULTIPLE;
            case MUSIC -> Message.CHAT_CONTENT_MUSIC;
            case NOTICE -> Message.CHAT_CONTENT_NOTICE;
            case REPLY -> Message.CHAT_CONTENT_REPLY;
            case STICKER -> Message.CHAT_CONTENT_STICKER;
            case VIDEO -> Message.CHAT_CONTENT_VIDEO;
        };
    }

    /** Stable source fallback for copies, logs and clients without localization. */
    public String fallback() {
        return "[" + name().toLowerCase(Locale.ROOT) + "]";
    }

    public record Span(int start, int end, ChatPlaceholder kind) {
        public Span {
            if (start < 0 || end <= start) throw new IllegalArgumentException("Invalid content span");
            kind = Objects.requireNonNull(kind, "kind");
        }

        public Span shifted(int offset) {
            return new Span(start + offset, end + offset, kind);
        }
    }

    public static List<Span> validate(List<Span> spans, int maximumEnd) {
        List<Span> checked = List.copyOf(spans);
        if (checked.size() > 1_024) throw new IllegalArgumentException("Too many content spans");
        int end = 0;
        for (Span span : checked) {
            if (span.start() < end || span.end() > maximumEnd) {
                throw new IllegalArgumentException("Content spans must be bounded, ordered and non-overlapping");
            }
            end = span.end();
        }
        return checked;
    }
}
