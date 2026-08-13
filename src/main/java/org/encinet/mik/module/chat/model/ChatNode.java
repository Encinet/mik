package org.encinet.mik.module.chat.model;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.encinet.mik.module.identity.ExternalIdentity;

/** One platform-neutral semantic run in processed chat content. */
public sealed interface ChatNode permits ChatNode.Text, ChatNode.Link,
        ChatNode.PlayerMention, ChatNode.ExternalMention,
        ChatNode.BroadcastMention, ChatNode.Item {

    String visibleText();

    ChatStyle style();

    record Text(String text, ChatStyle style) implements ChatNode {
        public Text {
            text = safeText(text);
            style = Objects.requireNonNullElse(style, ChatStyle.EMPTY);
        }

        @Override
        public String visibleText() {
            return text;
        }
    }

    record Link(String label, URI target, ChatStyle style) implements ChatNode {
        public Link {
            label = safeText(label);
            target = safeLinkUri(target);
            style = Objects.requireNonNullElse(style, ChatStyle.EMPTY);
        }

        @Override
        public String visibleText() {
            return label;
        }
    }

    record PlayerMention(
            UUID playerId,
            String playerName,
            Optional<ExternalIdentity> sourceIdentity,
            ChatStyle style
    ) implements ChatNode {
        public PlayerMention {
            playerId = Objects.requireNonNull(playerId, "playerId");
            playerName = safeText(playerName);
            if (playerName.isBlank() || playerName.length() > 64) {
                throw new IllegalArgumentException("playerName is invalid");
            }
            sourceIdentity = sourceIdentity == null
                    ? Optional.empty() : sourceIdentity;
            style = Objects.requireNonNullElse(style, ChatStyle.EMPTY);
        }

        public PlayerMention(UUID playerId, String playerName, ChatStyle style) {
            this(playerId, playerName, Optional.empty(), style);
        }

        @Override
        public String visibleText() {
            return sourceIdentity.map(identity -> "@" + externalName(identity)
                            + "(" + playerName + ")")
                    .orElseGet(() -> "@" + playerName);
        }
    }

    record ExternalMention(
            ExternalIdentity identity,
            ChatStyle style
    ) implements ChatNode {
        public ExternalMention {
            identity = Objects.requireNonNull(identity, "identity");
            style = Objects.requireNonNullElse(style, ChatStyle.EMPTY);
        }

        @Override
        public String visibleText() {
            return "@" + externalName(identity);
        }
    }

    record BroadcastMention(String label, ChatStyle style) implements ChatNode {
        public BroadcastMention {
            label = safeText(label);
            if (label.isBlank() || label.length() > 64) {
                throw new IllegalArgumentException("broadcast mention label is invalid");
            }
            style = Objects.requireNonNullElse(style, ChatStyle.EMPTY);
        }

        @Override
        public String visibleText() {
            return label;
        }
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

    private static String externalName(ExternalIdentity identity) {
        return identity.displayName().isBlank()
                ? identity.key().subject() : identity.displayName();
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
        return uri;
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
