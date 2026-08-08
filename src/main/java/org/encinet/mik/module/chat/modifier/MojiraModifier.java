package org.encinet.mik.module.chat.modifier;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MojiraModifier implements ChatModifier {

    private static final String CANONICAL_BASE_URL = "https://mojira.dev/";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://|(?<![A-Z0-9_@.-]))"
                    + "(?:mojira\\.dev/"
                    + "|bugs-legacy\\.mojang\\.com/browse/"
                    + "|bugs\\.mojang\\.com/browse/"
                    + "|report\\.bugs\\.mojang\\.com/servicedesk/customer/portal/2/"
                    + "|mojira\\.atlassian\\.net/browse/)"
                    + "(MC-[0-9]+)(?![A-Z0-9_-])(?:[?#][^\\s<>]*)?"
    );
    private static final Pattern ISSUE_KEY_PATTERN = Pattern.compile(
            "(?i)(?<![A-Z0-9_-])MC-[0-9]+(?![A-Z0-9_-])"
    );

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        ChatReplacement url = findUrl(text, fromIndex);
        ChatReplacement issueKey = findIssueKey(text, fromIndex);
        if (url == null) {
            return issueKey;
        }
        if (issueKey == null) {
            return url;
        }
        return url.start() <= issueKey.start() ? url : issueKey;
    }

    private ChatReplacement findUrl(String text, int fromIndex) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }

        String issueKey = normalizedIssueKey(matcher.group(1));
        int linkLength = urlEnd(matcher.group());
        return replacement(matcher.start(), matcher.start() + linkLength, issueKey);
    }

    private ChatReplacement findIssueKey(String text, int fromIndex) {
        Matcher matcher = ISSUE_KEY_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }

        String issueKey = normalizedIssueKey(matcher.group());
        return replacement(matcher.start(), matcher.end(), issueKey);
    }

    private ChatReplacement replacement(int start, int end, String issueKey) {
        String url = CANONICAL_BASE_URL + issueKey;
        return new ChatReplacement(start, end, ChatRenderUtil.link(issueKey, url));
    }

    private String normalizedIssueKey(String issueKey) {
        return issueKey.toUpperCase(Locale.ROOT);
    }

    private int urlEnd(String token) {
        int end = token.length();
        while (end > 0 && isTrailingPunctuation(token.codePointBefore(end))) {
            end -= Character.charCount(token.codePointBefore(end));
        }
        return Math.max(end, 1);
    }

    private boolean isTrailingPunctuation(int codePoint) {
        return switch (codePoint) {
            case '.', ',', '!', '?', ':', ';', '\'', '"', ')', ']', '}',
                    '。', '，', '！', '？', '：', '；', '、', '…', '）', '】', '》', '』' -> true;
            default -> false;
        };
    }
}
