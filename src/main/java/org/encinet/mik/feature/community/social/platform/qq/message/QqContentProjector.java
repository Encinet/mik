package org.encinet.mik.feature.community.social.platform.qq.message;

import org.encinet.mik.feature.community.identity.ExternalIdentity;
import org.encinet.mik.feature.community.chat.model.ChatPlaceholder;
import org.encinet.mik.feature.community.social.api.SocialMessageReferences;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.encinet.mik.feature.community.social.platform.qq.message.QqMessageContent.*;

/** One bounded text projection for every QQ content node and every bridge destination. */
public final class QqContentProjector {
    private QqContentProjector() { }

    public static Projection project(QqMessageContent message, Map<String, MentionTarget> mentions) {
        Buffer primary = new Buffer(2_000);
        List<SocialMessageReferences.MentionSpan> spans = new ArrayList<>();
        if (message.type() != Type.HISTORY) inline(primary, message.text(), mentions, spans);
        String ownText = primary.finish();

        Buffer additional = new Buffer(1_999);
        if (message.type().isCollection()) additional.marker(label(message.type()));
        if (message.type() != Type.HISTORY) elements(additional, message);
        if (ownText.isBlank() && additional.isEmpty() && message.type() != Type.TEXT) {
            additional.marker(label(message.type()));
        }
        additional.truncated |= message.truncated();
        String details = additional.finish();
        String body = details.isBlank() ? ownText : ownText.isBlank() ? details : ownText + " " + details;
        List<ChatPlaceholder.Span> placeholders = new ArrayList<>(primary.placeholders);
        int offset = ownText.length() + (ownText.isBlank() || details.isBlank() ? 0 : 1);
        additional.placeholders.forEach(span -> placeholders.add(span.shifted(offset)));
        return new Projection(body, message.type().allowsCommands() && primary.placeholders.isEmpty() ? ownText : "",
                spans, placeholders);
    }

    private static void inline(Buffer output, List<Inline> text, Map<String, MentionTarget> mentions,
                               List<SocialMessageReferences.MentionSpan> spans) {
        for (Inline part : text) {
            switch (part) {
                case Text plain -> output.text(plain.value());
                case Placeholder placeholder -> output.placeholder(label(placeholder.kind()));
                case Mention mention -> {
                    MentionTarget target = mentions.get(mention.id());
                    if (target == null) {
                        output.atom("@" + mention.id());
                    } else if (target.hidden()) {
                        output.space();
                    } else {
                        int start = output.atom("@" + target.label());
                        if (start >= 0) target.identity().ifPresent(identity -> spans.add(
                                new SocialMessageReferences.MentionSpan(start, output.length(), identity)));
                    }
                }
            }
        }
    }

    private static void elements(Buffer output, QqMessageContent message) {
        for (Element element : message.elements()) {
            switch (element) {
                case Media media -> {
                    output.marker(label(media.kind()));
                    output.word(media.transcription(), 512);
                }
                case Card card -> {
                    output.marker(label(card.kind()));
                    card.captions().forEach(caption -> output.word(caption, 256));
                }
                case Nested nested -> {
                    QqMessageContent child = nested.message();
                    if (child.type() == Type.HISTORY) {
                        output.marker(ChatPlaceholder.HISTORY);
                        continue;
                    }
                    if (child.type().isCollection()) output.marker(label(child.type()));
                    else output.marker(message.type() == Type.REPLY || child.type() == Type.REPLY ? "↪" : "💬");
                    if (!nested.author().isBlank()) {
                        output.word(nested.author(), 64);
                        output.text(":");
                    }
                    output.space();
                    output.limited(512, () -> inline(output, child.text(), Map.of(), new ArrayList<>()));
                    elements(output, child);
                    if (child.text().isEmpty() && child.elements().isEmpty()
                            && (child.type() == Type.CARD || child.type() == Type.UNKNOWN)) {
                        output.marker(label(child.type()));
                    }
                }
            }
        }
    }

    private static ChatPlaceholder label(Type type) {
        return switch (type) {
            case CARD -> ChatPlaceholder.CARD;
            case COMPOSITE -> ChatPlaceholder.MULTIPLE;
            case HISTORY -> ChatPlaceholder.HISTORY;
            case REPLY -> ChatPlaceholder.REPLY;
            case TEXT, UNKNOWN -> ChatPlaceholder.MESSAGE;
        };
    }

    private static ChatPlaceholder label(Kind kind) {
        return switch (kind) {
            case FACE -> ChatPlaceholder.FACE;
            case IMAGE -> ChatPlaceholder.IMAGE;
            case VIDEO -> ChatPlaceholder.VIDEO;
            case AUDIO -> ChatPlaceholder.AUDIO;
            case FILE -> ChatPlaceholder.FILE;
            case CARD -> ChatPlaceholder.CARD;
            case LOCATION -> ChatPlaceholder.LOCATION;
            case CONTACT -> ChatPlaceholder.CONTACT;
            case MUSIC -> ChatPlaceholder.MUSIC;
        };
    }

    public record MentionTarget(String label, Optional<ExternalIdentity> identity, boolean hidden) { }

    public record Projection(String body, String commandBody, List<SocialMessageReferences.MentionSpan> mentions, List<ChatPlaceholder.Span> placeholders) {
        public Projection { mentions = List.copyOf(mentions); placeholders = List.copyOf(placeholders); }
    }

    /** Reserves one character for truncation; atoms and identity spans are never cut. */
    private static final class Buffer {
        private final StringBuilder value = new StringBuilder();
        private final List<ChatPlaceholder.Span> placeholders = new ArrayList<>();
        private int capacity;
        private boolean pendingSpace;
        private boolean truncated;

        Buffer(int maximumLength) { capacity = maximumLength - 1; }

        void text(String text) {
            for (int index = 0; index < text.length();) {
                int point = text.codePointAt(index);
                index += Character.charCount(point);
                if (Character.isISOControl(point) || Character.isWhitespace(point)
                        || Character.getType(point) == Character.FORMAT) {
                    space();
                    continue;
                }
                int needed = Character.charCount(point) + (pendingSpace ? 1 : 0);
                if (value.length() + needed > capacity) { truncated = true; return; }
                flushSpace();
                value.appendCodePoint(point);
            }
        }

        int atom(String atom) {
            if (value.length() + atom.length() + (pendingSpace ? 1 : 0) > capacity) {
                truncated = true;
                return -1;
            }
            flushSpace();
            int start = value.length();
            value.append(atom);
            return start;
        }

        void word(String text) { space(); text(text); }
        void placeholder(ChatPlaceholder kind) {
            int start = atom(kind.fallback());
            if (start >= 0) placeholders.add(new ChatPlaceholder.Span(start, length(), kind));
        }
        void marker(ChatPlaceholder kind) { space(); placeholder(kind); }
        void marker(String label) { space(); atom(label); }
        void word(String text, int maximumLength) {
            Buffer field = new Buffer(maximumLength);
            field.text(text);
            word(field.finish());
        }
        void space() { pendingSpace = !value.isEmpty(); }
        boolean isEmpty() { return value.isEmpty(); }
        int length() { return value.length(); }
        String finish() { return truncated ? value + "…" : value.toString(); }

        void limited(int maximumLength, Runnable render) {
            int previous = capacity;
            capacity = Math.min(capacity, value.length() + maximumLength);
            try { render.run(); }
            finally { capacity = previous; }
        }

        private void flushSpace() {
            if (pendingSpace) value.append(' ');
            pendingSpace = false;
        }
    }
}
