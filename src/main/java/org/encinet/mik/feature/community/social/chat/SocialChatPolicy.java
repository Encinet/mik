package org.encinet.mik.feature.community.social.chat;

import java.util.Objects;
import java.util.Optional;
import java.util.Locale;
import java.util.Map;

/** Message selection for either direction of a platform chat route. */
public record SocialChatPolicy(
        Mode mode,
        String prefix,
        boolean stripPrefix
) {
    private static final int MAXIMUM_PREFIX_LENGTH = 16;

    public SocialChatPolicy {
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

    public static SocialChatPolicy always() {
        return new SocialChatPolicy(Mode.ALWAYS, "", false);
    }

    public static SocialChatPolicy prefixed(String prefix, boolean stripPrefix) {
        return new SocialChatPolicy(Mode.PREFIX, prefix, stripPrefix);
    }

    /** Parses one optional inbound/outbound section; omitted directions forward all text. */
    public static SocialChatPolicy fromConfig(Object configured, String field) {
        if (configured == null) {
            return always();
        }
        if (!(configured instanceof Map<?, ?> settings)) {
            throw new IllegalArgumentException(field + " must be a map");
        }
        Object configuredMode = settings.get("mode");
        if (configuredMode != null && !(configuredMode instanceof String)) {
            throw new IllegalArgumentException(field + ".mode must be always or prefix");
        }
        String modeName = configuredMode == null ? "" : ((String) configuredMode).strip();
        Mode mode;
        try {
            mode = Mode.valueOf((modeName.isEmpty() ? "always" : modeName).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(field + ".mode must be always or prefix", error);
        }
        if (mode == Mode.ALWAYS) {
            return always();
        }
        Object prefix = settings.get("prefix");
        Object stripPrefix = settings.get("strip-prefix");
        if (!(prefix instanceof String) || (stripPrefix != null && !(stripPrefix instanceof Boolean))) {
            throw new IllegalArgumentException(field + " requires a string prefix and boolean strip-prefix");
        }
        return prefixed((String) prefix, stripPrefix == null || (boolean) stripPrefix);
    }

    /** Returns the selected body, or empty when the route does not match. */
    public Optional<String> select(String message) {
        return selectRange(message).map(Selection::body);
    }

    /** Retains source offsets so stripping a trigger can also relocate trusted mentions. */
    public Optional<Selection> selectRange(String message) {
        String source = Objects.requireNonNullElse(message, "");
        String body = source.strip();
        if (body.isEmpty()) {
            return Optional.empty();
        }
        int start = source.indexOf(body);
        int end = start + body.length();
        if (mode == Mode.ALWAYS) {
            return Optional.of(new Selection(body, start, end));
        }
        if (!body.startsWith(prefix)) {
            return Optional.empty();
        }
        String selected = stripPrefix
                ? body.substring(prefix.length()).stripLeading()
                : body;
        return selected.isBlank() ? Optional.empty()
                : Optional.of(new Selection(selected, end - selected.length(), end));
    }

    public record Selection(String body, int start, int end) { }

    public enum Mode {
        ALWAYS,
        PREFIX
    }
}
