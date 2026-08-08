package org.encinet.mik.module.chat.modifier;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MinecraftWikiModifier implements ChatModifier {

    private static final int MAX_LABEL_LENGTH = 48;
    private static final String LABEL_PREFIX = "Minecraft Wiki";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?iu)(?<![a-z0-9_@.-])(?:https?://)?"
                    + "(?:(?:www|[a-z]{2,8}(?:-[a-z0-9]{2,8})?)\\.)?minecraft\\.wiki"
                    + "(?![\\p{L}\\p{N}_@.-]|:[0-9])(?:[/?#][^\\s<>]*)?"
    );

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }

        String token = matcher.group();
        int linkLength = urlEnd(token);
        String link = token.substring(0, linkLength);
        return new ChatReplacement(
                matcher.start(),
                matcher.start() + linkLength,
                ChatRenderUtil.link(labelFor(link), normalizedUrl(link))
        );
    }

    private String labelFor(String link) {
        String pageTitle = pageTitle(link);
        String label = pageTitle == null || pageTitle.isBlank()
                ? LABEL_PREFIX
                : LABEL_PREFIX + ": " + pageTitle;
        if (label.codePointCount(0, label.length()) > MAX_LABEL_LENGTH) {
            int end = label.offsetByCodePoints(0, MAX_LABEL_LENGTH - 3);
            label = label.substring(0, end) + "...";
        }
        return "[" + label + "]";
    }

    private String pageTitle(String link) {
        int hostStart = link.indexOf("://");
        hostStart = hostStart >= 0 ? hostStart + 3 : 0;

        int pathStart = link.indexOf('/', hostStart);
        if (pathStart < 0) {
            return readableTitle(queryPageTitle(link), false);
        }

        int pathEnd = firstIndexOf(link, pathStart, '?', '#');
        String path = link.substring(pathStart, pathEnd);
        String rawTitle = null;
        if (path.regionMatches(true, 0, "/w/", 0, 3) && path.length() > 3) {
            rawTitle = path.substring(3);
        } else if (path.regionMatches(true, 0, "/wiki/", 0, 6) && path.length() > 6) {
            rawTitle = path.substring(6);
        }

        if (rawTitle == null || "index.php".equalsIgnoreCase(rawTitle)) {
            return readableTitle(queryPageTitle(link), false);
        }
        return readableTitle(rawTitle, true);
    }

    private String queryPageTitle(String link) {
        int queryStart = link.indexOf('?');
        if (queryStart < 0) {
            return null;
        }

        int queryEnd = link.indexOf('#', queryStart + 1);
        if (queryEnd < 0) {
            queryEnd = link.length();
        }
        for (String parameter : link.substring(queryStart + 1, queryEnd).split("&")) {
            int equals = parameter.indexOf('=');
            if (equals > 0 && "title".equalsIgnoreCase(parameter.substring(0, equals))) {
                return parameter.substring(equals + 1);
            }
        }
        return null;
    }

    private String readableTitle(String rawTitle, boolean preservePlus) {
        if (rawTitle == null || rawTitle.isEmpty()) {
            return null;
        }
        try {
            // URLDecoder treats '+' as a form-space; in a URL path it is a literal plus.
            String encodedTitle = preservePlus ? rawTitle.replace("+", "%2B") : rawTitle;
            return URLDecoder.decode(encodedTitle, StandardCharsets.UTF_8)
                    .replace('_', ' ');
        } catch (IllegalArgumentException ignored) {
            String fallbackTitle = preservePlus ? rawTitle : rawTitle.replace('+', ' ');
            return fallbackTitle.replace('_', ' ');
        }
    }

    private int firstIndexOf(String value, int fromIndex, char first, char second) {
        int firstIndex = value.indexOf(first, fromIndex);
        int secondIndex = value.indexOf(second, fromIndex);
        if (firstIndex < 0) {
            return secondIndex < 0 ? value.length() : secondIndex;
        }
        return secondIndex < 0 ? firstIndex : Math.min(firstIndex, secondIndex);
    }

    private String normalizedUrl(String link) {
        String lowerCaseLink = link.toLowerCase(Locale.ROOT);
        if (lowerCaseLink.startsWith("http://") || lowerCaseLink.startsWith("https://")) {
            return link;
        }
        return "https://" + link;
    }

    private int urlEnd(String token) {
        int end = token.length();
        while (end > 0) {
            int codePoint = token.codePointBefore(end);
            if (!isTrailingPunctuation(codePoint) && !isUnmatchedClosing(token, end, codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return Math.max(end, 1);
    }

    private boolean isTrailingPunctuation(int codePoint) {
        return switch (codePoint) {
            case '.', ',', '!', '?', ':', ';', '\'', '"',
                    '。', '，', '！', '？', '：', '；', '、', '…' -> true;
            default -> false;
        };
    }

    private boolean isUnmatchedClosing(String token, int end, int closing) {
        int opening = switch (closing) {
            case ')' -> '(';
            case ']' -> '[';
            case '}' -> '{';
            case '）' -> '（';
            case '】' -> '【';
            case '》' -> '《';
            case '」' -> '「';
            case '』' -> '『';
            default -> -1;
        };
        if (opening < 0) {
            return false;
        }

        int balance = 0;
        for (int i = 0; i < end; ) {
            int codePoint = token.codePointAt(i);
            if (codePoint == opening) {
                balance++;
            } else if (codePoint == closing) {
                balance--;
            }
            i += Character.charCount(codePoint);
        }
        return balance < 0;
    }
}
