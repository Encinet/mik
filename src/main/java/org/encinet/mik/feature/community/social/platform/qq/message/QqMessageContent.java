package org.encinet.mik.feature.community.social.platform.qq.message;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** Decoded QQ content, independent of Gateway events, identities and display formatting. */
public record QqMessageContent(
        Type type,
        List<Inline> text,
        List<Element> elements,
        boolean truncated
) {
    public QqMessageContent {
        text = List.copyOf(text);
        elements = List.copyOf(elements);
    }

    public enum Type {
        TEXT, CARD, COMPOSITE, HISTORY, REPLY, UNKNOWN;

        public boolean allowsCommands() { return this == TEXT || this == REPLY; }

        public boolean isCollection() { return this == COMPOSITE || this == HISTORY; }
    }

    public enum Kind { FACE, IMAGE, VIDEO, AUDIO, FILE, CARD, LOCATION, CONTACT, MUSIC }

    public sealed interface Inline permits Text, Mention, Placeholder { }
    public record Text(String value) implements Inline { }
    public record Mention(String id) implements Inline { }
    public record Placeholder(Kind kind) implements Inline { }

    public sealed interface Element permits Media, Card, Nested { }

    public record Media(Kind kind, String filename, long width, long height, long size,
                        Optional<URI> url, String transcription) implements Element { }

    public record Card(Kind kind, List<String> captions, Optional<URI> target,
                       Optional<URI> preview) implements Element {
        public Card { captions = List.copyOf(captions); }
    }

    public record Nested(String author, QqMessageContent message) implements Element { }
}
