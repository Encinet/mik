package org.encinet.mik.module.chat.modifier;

/** Shared URL normalization and punctuation rules for semantic enrichers. */
final class ChatUrlSupport {
    private ChatUrlSupport() {
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
}
