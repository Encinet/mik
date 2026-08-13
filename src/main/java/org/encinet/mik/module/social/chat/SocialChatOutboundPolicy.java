package org.encinet.mik.module.social.chat;

import java.util.Objects;
import java.util.Optional;

/** Shared selection policy for one Minecraft-to-platform chat route. */
public record SocialChatOutboundPolicy(
        Mode mode,
        String prefix,
        boolean stripPrefix
) {
    private static final int MAXIMUM_PREFIX_LENGTH = 16;

    public SocialChatOutboundPolicy {
        mode = Objects.requireNonNull(mode, "mode");
        prefix = Objects.requireNonNullElse(prefix, "");
        if (mode == Mode.ALWAYS) {
            prefix = "";
            stripPrefix = false;
        } else {
            if (prefix.isBlank() || prefix.length() > MAXIMUM_PREFIX_LENGTH
                    || prefix.codePoints().anyMatch(character ->
                    Character.isISOControl(character) || Character.isWhitespace(character))) {
                throw new IllegalArgumentException(
                        "PREFIX chat routes require a 1-16 character non-whitespace prefix");
            }
        }
    }

    public static SocialChatOutboundPolicy always() {
        return new SocialChatOutboundPolicy(Mode.ALWAYS, "", false);
    }

    public static SocialChatOutboundPolicy prefixed(String prefix, boolean stripPrefix) {
        return new SocialChatOutboundPolicy(Mode.PREFIX, prefix, stripPrefix);
    }

    /** Returns this route's outgoing body, or empty when the route does not match. */
    public Optional<String> select(String message) {
        String body = Objects.requireNonNullElse(message, "").strip();
        if (body.isEmpty()) {
            return Optional.empty();
        }
        if (mode == Mode.ALWAYS) {
            return Optional.of(body);
        }
        if (!body.startsWith(prefix)) {
            return Optional.empty();
        }
        String selected = stripPrefix
                ? body.substring(prefix.length()).stripLeading()
                : body;
        return selected.isBlank() ? Optional.empty() : Optional.of(selected);
    }

    public enum Mode {
        ALWAYS,
        PREFIX
    }
}
