package org.encinet.mik.module.chat.modifier;

import java.net.URI;
import java.util.Set;

/** Shared URL normalization and punctuation rules for semantic enrichers. */
final class ChatUrlSupport {
    private static final Set<String> TRACKING_PARAMETERS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term",
            "utm_content", "utm_id", "utm_name", "utm_cid", "utm_reader",
            "utm_social", "gclid", "gclsrc", "dclid", "wbraid", "gbraid",
            "fbclid", "igshid", "igsh", "msclkid", "mc_cid", "mc_eid",
            "ttclid", "twclid", "yclid", "spm", "scm", "_hsenc", "_hsmi",
            "hsctatracking", "mkt_tok", "vero_id", "vero_conv", "s_cid",
            "oly_anon_id", "oly_enc_id"
    );

    private ChatUrlSupport() {
    }

    static CanonicalHttpLink canonicalHttpLink(String token) {
        String displayText = stripTrackingParameters(token);
        return new CanonicalHttpLink(
                displayText, URI.create(normalizedHttpUrl(displayText)));
    }

    static String normalizedHttpUrl(String token) {
        return hasHttpScheme(token) ? token : "https://" + token;
    }

    static boolean hasHttpScheme(String token) {
        return token.regionMatches(true, 0, "http://", 0, 7)
                || token.regionMatches(true, 0, "https://", 0, 8);
    }

    static int visibleUrlEnd(String token) {
        int end = token.length();
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
        if (queryStart < 0) {
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

    record CanonicalHttpLink(String displayText, URI target) {
    }
}
