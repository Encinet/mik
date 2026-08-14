package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Recognizes Matrix identifiers, matrix.to permalinks, and matrix: URIs. */
public final class MatrixLinkModifier implements ChatModifier {
    private static final String SERVER_NAME =
            "(?:[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?|"
                    + "\\[[0-9a-f:.]+])(?::[0-9]{1,5})?";
    private static final Pattern MATRIX_TO_PATTERN = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_@.-])(?:https?://)?matrix\\.to"
                    + "(?![\\p{L}\\p{N}_.-])/#/[^\\s<>]+"
    );
    private static final Pattern MATRIX_URI_PATTERN = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])matrix:[^\\s<>]+"
    );
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_@#!$./:-])(?:"
                    + "@[a-z0-9._=+/-]+:" + SERVER_NAME
                    + "|#[^\\s:#<>]+:" + SERVER_NAME
                    + "|![^\\s:!<>]+:" + SERVER_NAME + ")"
                    + "(?![\\p{L}\\p{N}_-]|\\.[\\p{L}\\p{N}]|:[0-9])"
    );

    @Override
    public int priority() {
        return 44;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.URL);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        return earliest(
                findMatrixTo(text, fromIndex),
                findMatrixUri(text, fromIndex),
                findIdentifier(text, fromIndex));
    }

    private ChatReplacement findMatrixTo(String text, int fromIndex) {
        Matcher matcher = MATRIX_TO_PATTERN.matcher(text);
        int searchIndex = fromIndex;
        while (matcher.find(searchIndex)) {
            int end = matcher.start() + ChatUrlSupport.visibleUrlEnd(matcher.group());
            ParsedMatrixLink parsed = parseMatrixTo(text.substring(matcher.start(), end));
            if (parsed != null) {
                return replacement(matcher.start(), end, parsed);
            }
            searchIndex = matcher.start() + 1;
        }
        return null;
    }

    private ChatReplacement findMatrixUri(String text, int fromIndex) {
        Matcher matcher = MATRIX_URI_PATTERN.matcher(text);
        int searchIndex = fromIndex;
        while (matcher.find(searchIndex)) {
            int end = matcher.start() + ChatUrlSupport.visibleUrlEnd(matcher.group());
            ParsedMatrixLink parsed = parseMatrixUri(text.substring(matcher.start(), end));
            if (parsed != null) {
                return replacement(matcher.start(), end, parsed);
            }
            searchIndex = matcher.start() + 1;
        }
        return null;
    }

    private ChatReplacement findIdentifier(String text, int fromIndex) {
        Matcher matcher = IDENTIFIER_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String identifier = matcher.group();
        ParsedMatrixLink parsed = parsed(identifier, null, null);
        return parsed == null ? null
                : replacement(matcher.start(), matcher.end(), parsed);
    }

    private ChatReplacement replacement(
            int start, int end, ParsedMatrixLink parsed
    ) {
        return new ChatReplacement(start, end,
                ChatLinkPresentation.serviceLink(
                        "Matrix", labelDetail(parsed), URI.create(parsed.url()),
                        ChatLinkPalette.MATRIX));
    }

    private ParsedMatrixLink parseMatrixTo(String token) {
        int schemeEnd = token.indexOf("://");
        String normalized = "https://" + (schemeEnd >= 0
                ? token.substring(schemeEnd + 3) : token);
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (!"matrix.to".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1
                || !"/".equals(uri.getPath())) {
            return null;
        }
        String fragment = uri.getRawFragment();
        if (fragment == null || !fragment.startsWith("/")) {
            return null;
        }
        return parseMatrixToFragment(fragment.substring(1));
    }

    private ParsedMatrixLink parseMatrixToFragment(String fragment) {
        int queryStart = fragment.indexOf('?');
        String path = queryStart < 0 ? fragment : fragment.substring(0, queryStart);
        String query = queryStart < 0 ? null : fragment.substring(queryStart + 1);
        String[] components = path.split("/", -1);
        if (components.length < 1 || components.length > 2) {
            return null;
        }
        String identifier = decode(components[0]);
        String event = components.length == 2 ? decode(components[1]) : null;
        return parsed(identifier, event, query);
    }

    private ParsedMatrixLink parseMatrixUri(String token) {
        String schemeSpecific = token.substring("matrix:".length());
        if (schemeSpecific.startsWith("//")) {
            return null;
        }
        int fragmentStart = schemeSpecific.indexOf('#');
        if (fragmentStart >= 0) {
            schemeSpecific = schemeSpecific.substring(0, fragmentStart);
        }
        int queryStart = schemeSpecific.indexOf('?');
        String path = queryStart < 0
                ? schemeSpecific : schemeSpecific.substring(0, queryStart);
        String query = queryStart < 0
                ? null : schemeSpecific.substring(queryStart + 1);
        String[] components = path.split("/", -1);
        if (components.length != 2 && components.length != 4) {
            return null;
        }

        String decodedIdentifier = decode(components[1]);
        if (decodedIdentifier == null) {
            return null;
        }
        String type = components[0].toLowerCase(Locale.ROOT);
        String identifier = switch (type) {
            case "u", "user" -> "@" + decodedIdentifier;
            case "r", "room" -> "#" + decodedIdentifier;
            case "roomid" -> "!" + decodedIdentifier;
            default -> null;
        };
        if (identifier == null) {
            return null;
        }
        String event = null;
        if (components.length == 4) {
            String eventType = components[2].toLowerCase(Locale.ROOT);
            if (!("e".equals(eventType) || "event".equals(eventType))) {
                return null;
            }
            String decodedEvent = decode(components[3]);
            if (decodedEvent == null) {
                return null;
            }
            event = "$" + decodedEvent;
        }
        return parsed(identifier, event, query);
    }

    private ParsedMatrixLink parsed(
            String identifier, String event, String rawQuery
    ) {
        if (!isIdentifier(identifier) || event != null
                && (!identifier.startsWith("!") && !identifier.startsWith("#")
                || !isEventId(event)) || !isSafeQuery(rawQuery)) {
            return null;
        }
        StringBuilder url = new StringBuilder("https://matrix.to/#/")
                .append(encodeComponent(identifier));
        if (event != null) {
            url.append('/').append(encodeComponent(event));
        }
        if (rawQuery != null && !rawQuery.isEmpty()) {
            url.append('?').append(rawQuery);
        }
        try {
            URI.create(url.toString());
            return new ParsedMatrixLink(identifier, event, url.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean isIdentifier(String value) {
        if (!isOpaqueIdentifier(value) || value.length() < 2) {
            return false;
        }
        return switch (value.charAt(0)) {
            case '@' -> value.matches("@[a-z0-9._=+/-]+:" + SERVER_NAME);
            case '#' -> hasServerName(value.substring(1));
            case '!' -> true;
            default -> false;
        };
    }

    private boolean hasServerName(String valueWithoutSigil) {
        for (int colon = valueWithoutSigil.indexOf(':'); colon > 0;
             colon = valueWithoutSigil.indexOf(':', colon + 1)) {
            if (valueWithoutSigil.substring(colon + 1).matches(SERVER_NAME)) {
                return true;
            }
        }
        return false;
    }

    private boolean isEventId(String value) {
        return value != null && value.startsWith("$")
                && value.length() > 1 && isOpaqueIdentifier(value);
    }

    private boolean isOpaqueIdentifier(String value) {
        return value != null
                && value.getBytes(StandardCharsets.UTF_8).length <= 255
                && value.codePoints().noneMatch(Character::isISOControl)
                && value.codePoints().noneMatch(Character::isWhitespace);
    }

    private boolean isSafeQuery(String rawQuery) {
        return rawQuery == null || rawQuery.codePoints().noneMatch(
                codePoint -> Character.isISOControl(codePoint)
                        || Character.isWhitespace(codePoint));
    }

    private String labelDetail(ParsedMatrixLink parsed) {
        return parsed.event() == null
                ? parsed.identifier() : parsed.identifier() + " / " + parsed.event();
    }

    private String decode(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return URLDecoder.decode(value.replace("+", "%2B"),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String encodeComponent(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte valueByte : bytes) {
            int valueInt = Byte.toUnsignedInt(valueByte);
            if (valueInt >= 'a' && valueInt <= 'z'
                    || valueInt >= 'A' && valueInt <= 'Z'
                    || valueInt >= '0' && valueInt <= '9'
                    || valueInt == '-' || valueInt == '.'
                    || valueInt == '_' || valueInt == '~') {
                encoded.append((char) valueInt);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(
                        Character.forDigit(valueInt >>> 4, 16)));
                encoded.append(Character.toUpperCase(
                        Character.forDigit(valueInt & 0xF, 16)));
            }
        }
        return encoded.toString();
    }

    private ChatReplacement earliest(ChatReplacement... replacements) {
        ChatReplacement best = null;
        for (ChatReplacement replacement : replacements) {
            if (replacement != null && (best == null
                    || replacement.start() < best.start())) {
                best = replacement;
            }
        }
        return best;
    }

    private record ParsedMatrixLink(String identifier, String event, String url) {
    }
}
