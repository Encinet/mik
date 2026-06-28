package org.encinet.mik.module.chat.modifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlModifier implements ChatModifier {

    private static final Pattern URL_PATTERN = Pattern.compile("(?i)(https?://[^\\s<]+|www\\.[^\\s<]+)");

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String token = matcher.group();
        int linkLength = urlEnd(token);
        String link = token.substring(0, linkLength);
        String url = normalizedUrl(link);
        return new ChatReplacement(matcher.start(), matcher.start() + linkLength,
                ChatRenderUtil.link(ChatRenderUtil.compactLinkLabel(link), url));
    }

    private String normalizedUrl(String token) {
        if (token.regionMatches(true, 0, "http://", 0, "http://".length())
                || token.regionMatches(true, 0, "https://", 0, "https://".length())) {
            return token;
        }
        return "https://" + token;
    }

    private int urlEnd(String token) {
        int end = token.length();
        while (end > 0 && isTrailingUrlPunctuation(token.charAt(end - 1))) {
            end--;
        }
        return Math.max(end, 1);
    }

    private boolean isTrailingUrlPunctuation(char c) {
        return c == '.' || c == ',' || c == '!' || c == '?' || c == ':' || c == ';';
    }
}
