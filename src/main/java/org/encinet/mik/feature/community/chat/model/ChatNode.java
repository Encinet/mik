package org.encinet.mik.feature.community.chat.model;

import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


/** One platform-neutral semantic run in processed chat content. */
public sealed interface ChatNode permits ChatNode.Text, ChatNode.Reference,
        ChatNode.PendingMention, ChatNode.Item, ChatNode.Placeholder {

    String visibleText();

    ChatStyle style();

    record Placeholder(ChatPlaceholder kind, String text, ChatStyle style) implements ChatNode {
        public Placeholder {
            kind = Objects.requireNonNull(kind, "kind");
            text = safeText(text);
            style = Objects.requireNonNullElse(style, ChatStyle.EMPTY);
        }

        @Override
        public String visibleText() { return text; }

        public Placeholder(ChatPlaceholder kind, ChatStyle style) {
            this(kind, kind.fallback(), style);
        }
    }

    record Text(String text, ChatStyle style, boolean verbatim) implements ChatNode {
        public Text(String text, ChatStyle style) {
            this(text, style, false);
        }

        public Text {
            text = safeText(text);
            style = Objects.requireNonNullElse(style, ChatStyle.EMPTY);
        }

        @Override
        public String visibleText() {
            return text;
        }
    }

    record Reference(List<Text> runs, Target target) implements ChatNode {
        public Reference {
            runs = checkedLabel(runs);
            Objects.requireNonNull(target, "target");
        }

        public Reference(String label, ChatStyle style, Target target) {
            this(List.of(new Text(label, style)), target);
        }

        @Override public String visibleText() { return labelText(runs); }
        @Override public ChatStyle style() { return ChatStyle.EMPTY; }

        public sealed interface Target permits Link, Person, Audience { }

        public record Link(URI uri, boolean automatic) implements Target {
            public Link {
                uri = canonicalUri(uri);
            }

            public static URI canonicalUri(URI target) {
                String ascii = safeLinkUri(target).toASCIIString();
                if (ascii.length() > 2048) throw new IllegalArgumentException("chat link is too long");
                return URI.create(ascii);
            }
        }

        public record Person(UUID playerId, String playerName) implements Target {
            public Person {
                Objects.requireNonNull(playerId, "playerId");
                playerName = safeText(playerName);
                if (playerName.isBlank() || playerName.length() > 64) {
                    throw new IllegalArgumentException("playerName is invalid");
                }
            }
        }

        public enum Audience implements Target { INSTANCE }
    }

    /** A recognized lookup, rendered as plain text until mikd admits a target. */
    record PendingMention(List<Text> runs, String playerName) implements ChatNode {
        public PendingMention {
            runs = checkedLabel(runs);
            Objects.requireNonNull(playerName, "playerName");
        }

        @Override public String visibleText() { return labelText(runs); }
        @Override public ChatStyle style() { return ChatStyle.EMPTY; }
    }

    record Item(
            String fallback,
            Optional<ChatItemSnapshot> item,
            ChatStyle style
    ) implements ChatNode {
        public Item {
            fallback = safeText(fallback);
            if (fallback.isBlank() || fallback.length() > 1_024) {
                throw new IllegalArgumentException("item fallback is invalid");
            }
            item = item == null ? Optional.empty() : item;
            style = Objects.requireNonNullElse(style, ChatStyle.EMPTY);
        }

        public static Item snapshot(ChatItemSnapshot item, ChatStyle style) {
            ChatItemSnapshot checked = Objects.requireNonNull(item, "item");
            return new Item(checked.fallbackText(), Optional.of(checked), style);
        }

        @Override
        public String visibleText() {
            return fallback;
        }
    }

    private static String safeText(String value) {
        String source = Objects.requireNonNull(value, "text");
        if (source.codePoints().noneMatch(Character::isISOControl)) {
            return source;
        }
        StringBuilder checked = new StringBuilder(source.length());
        source.codePoints().forEach(codePoint -> {
            if (codePoint == '\n' || codePoint == '\r') {
                checked.append(' ');
            } else if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException(
                        "chat text contains control characters");
            } else {
                checked.appendCodePoint(codePoint);
            }
        });
        return checked.toString();
    }

    private static List<Text> checkedLabel(List<Text> runs) {
        var checked = List.copyOf(runs);
        if (checked.isEmpty() || checked.size() > 256 || checked.stream().anyMatch(run -> run.text().isEmpty())) {
            throw new IllegalArgumentException("chat interaction needs a label");
        }
        return checked;
    }

    private static String labelText(List<Text> runs) {
        var label = new StringBuilder();
        runs.forEach(run -> label.append(run.text()));
        return label.toString();
    }

    private static URI safeLinkUri(URI value) {
        URI uri = Objects.requireNonNull(value, "target");
        String scheme = uri.getScheme();
        if ("mailto".equalsIgnoreCase(scheme)) {
            return safeMailtoUri(uri);
        }
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isBlank() || uri.getUserInfo() != null
                || authority.indexOf('@') >= 0 || authority.indexOf('\\') >= 0
                || !("https".equalsIgnoreCase(scheme)
                || "http".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(
                    "chat link must be a safe HTTP(S) or mailto URI");
        }
        String host = uri.getHost();
        if (host == null) {
            int colon = authority.lastIndexOf(':');
            host = IDN.toASCII(colon < 0 ? authority : authority.substring(0, colon), IDN.USE_STD3_ASCII_RULES);
            uri = URI.create(scheme + "://" + host + (colon < 0 ? "" : authority.substring(colon))
                    + uri.toString().substring(scheme.length() + 3 + authority.length()));
        }
        if (uri.getHost() == null || uri.getPort() > 65535) {
            throw new IllegalArgumentException("chat link has an invalid host or port");
        }
        String canonical = scheme.toLowerCase(Locale.ROOT) + "://" + host.toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port >= 0 && !(port == 80 && "http".equalsIgnoreCase(scheme))
                && !(port == 443 && "https".equalsIgnoreCase(scheme))) canonical += ":" + port;
        canonical += uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) canonical += "?" + uri.getRawQuery();
        if (uri.getRawFragment() != null) canonical += "#" + uri.getRawFragment();
        return URI.create(canonical);
    }

    private static URI safeMailtoUri(URI uri) {
        String address = uri.getRawSchemeSpecificPart();
        String decodedAddress;
        try {
            decodedAddress = address == null ? "" : URLDecoder.decode(
                    address.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            decodedAddress = "";
        }
        if (!uri.isOpaque() || uri.getRawFragment() != null
                || decodedAddress.isBlank() || !decodedAddress.matches(
                "(?i)[a-z0-9!#$&'*+/=?^_`{|}~.-]+@[a-z0-9]"
                        + "(?:[a-z0-9.-]{0,251}[a-z0-9])?")) {
            throw new IllegalArgumentException(
                    "chat mailto link must contain only one safe email address");
        }
        return uri;
    }
}
