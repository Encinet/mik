package org.encinet.mik.feature.community.chat.link;

import org.encinet.mik.feature.community.chat.model.ChatNode;

import java.net.URI;
import java.util.Set;

/** Shared URL normalization and punctuation rules for semantic enrichers. */
public final class ChatUrls {
    private static final Set<String> TRACKING_PARAMETERS = ChatResourceLinks.trackingParameters();

    private ChatUrls() {
    }

    public static CanonicalHttpLink canonicalHttpLink(String token) {
        String displayText = stripTrackingParameters(token);
        URI target = httpTarget(displayText);
        return target == null ? null : new CanonicalHttpLink(displayText, target);
    }

    /** Invalid candidates remain plain text instead of interrupting chat processing. */
    static URI httpTarget(String token) {
        String url = hasHttpScheme(token) ? token : "https://" + token;
        int fragmentStart = url.indexOf('#');
        if (fragmentStart >= 0 && url.indexOf('#', fragmentStart + 1) >= 0) {
            // Only the first '#' is a delimiter; subsequent ones are fragment data.
            url = url.substring(0, fragmentStart + 1)
                    + url.substring(fragmentStart + 1).replace("#", "%23");
        }
        try {
            return ChatNode.Reference.Link.canonicalUri(URI.create(url));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean hasHttpScheme(String token) {
        return token.regionMatches(true, 0, "http://", 0, 7)
                || token.regionMatches(true, 0, "https://", 0, 8);
    }

    public static boolean insideUrl(String text, int index) {
        int start = index;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))
                && "（【《「『，。！？、；：<>\"".indexOf(text.charAt(start - 1)) < 0) start--;
        return text.substring(start, index).contains("://");
    }

    public static int visibleUrlEnd(String token) {
        int end = token.length();
        for (int i = 0; i < end; i++) {
            if ("，。！？、；：".indexOf(token.charAt(i)) >= 0) {
                end = i;
                break;
            }
        }
        while (end > 0) {
            int codePoint = token.codePointBefore(end);
            if (!isTrailingPunctuation(codePoint)
                    && !isUnmatchedClosing(token, end, codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return Math.max(end, 1);
    }

    private static String stripTrackingParameters(String url) {
        int queryStart = url.indexOf('?');
        int firstFragment = url.indexOf('#');
        if (queryStart < 0 || firstFragment >= 0 && firstFragment < queryStart) {
            return url;
        }

        int fragmentStart = url.indexOf('#', queryStart + 1);
        int queryEnd = fragmentStart >= 0 ? fragmentStart : url.length();
        if (queryStart + 1 >= queryEnd) {
            return url;
        }

        String query = url.substring(queryStart + 1, queryEnd);
        StringBuilder filtered = new StringBuilder(query.length());
        boolean removedAny = false;
        int parameterStart = 0;
        for (int index = 0; index <= query.length(); index++) {
            if (index < query.length() && query.charAt(index) != '&') {
                continue;
            }
            if (index > parameterStart) {
                String parameter = query.substring(parameterStart, index);
                int equals = parameter.indexOf('=');
                String name = equals >= 0
                        ? parameter.substring(0, equals) : parameter;
                if (TRACKING_PARAMETERS.contains(name.toLowerCase(
                        java.util.Locale.ROOT))) {
                    removedAny = true;
                } else {
                    if (!filtered.isEmpty()) {
                        filtered.append('&');
                    }
                    filtered.append(parameter);
                }
            }
            parameterStart = index + 1;
        }
        if (!removedAny) {
            return url;
        }

        StringBuilder result = new StringBuilder(url.length());
        result.append(url, 0, queryStart);
        if (!filtered.isEmpty()) {
            result.append('?').append(filtered);
        }
        if (fragmentStart >= 0) {
            result.append(url, fragmentStart, url.length());
        }
        return result.toString();
    }

    private static boolean isTrailingPunctuation(int codePoint) {
        return switch (codePoint) {
            case '.', ',', '!', '?', ':', ';', '\'', '"',
                    '。', '，', '！', '？', '；', '：', '、', '…' -> true;
            default -> false;
        };
    }

    private static boolean isUnmatchedClosing(
            String token,
            int end,
            int closing
    ) {
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
        for (int index = 0; index < end; ) {
            int codePoint = token.codePointAt(index);
            if (codePoint == opening) {
                balance++;
            } else if (codePoint == closing) {
                balance--;
            }
            index += Character.charCount(codePoint);
        }
        return balance < 0;
    }

    public record CanonicalHttpLink(String displayText, URI target) {
    }
}
