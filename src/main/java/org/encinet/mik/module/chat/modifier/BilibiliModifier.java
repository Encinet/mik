package org.encinet.mik.module.chat.modifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BilibiliModifier implements ChatModifier {

    private static final Pattern VIDEO_URL_PATTERN = Pattern.compile(
            "(?i)(https?://)?(?:www\\.|m\\.)?bilibili\\.com/video/(bv[0-9A-Za-z]{10}|av\\d+)[^\\s<]*"
    );
    private static final Pattern BV_PATTERN = Pattern.compile("(?i)bv[0-9A-Za-z]{10}");
    private static final Pattern AV_PATTERN = Pattern.compile("(?i)av\\d+");

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        ChatReplacement url = findVideoUrl(text, fromIndex, context);
        ChatReplacement bv = findBv(text, fromIndex, context);
        ChatReplacement av = findAv(text, fromIndex, context);
        return earliest(url, bv, av);
    }

    private ChatReplacement findVideoUrl(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = VIDEO_URL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String token = matcher.group();
        int linkLength = urlEnd(token);
        String link = token.substring(0, linkLength);
        String label = matcher.group(2);
        String url = normalizedUrl(link);
        return new ChatReplacement(matcher.start(), matcher.start() + linkLength,
                ChatRenderUtil.bilibili(label, url, context.bilibiliHover()));
    }

    private ChatReplacement findBv(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = BV_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String label = matcher.group();
        String url = "https://www.bilibili.com/video/" + label;
        return new ChatReplacement(matcher.start(), matcher.end(),
                ChatRenderUtil.bilibili(label, url, context.bilibiliHover()));
    }

    private ChatReplacement findAv(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = AV_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String label = matcher.group();
        String url = "https://www.bilibili.com/video/" + label;
        return new ChatReplacement(matcher.start(), matcher.end(),
                ChatRenderUtil.bilibili(label, url, context.bilibiliHover()));
    }

    private ChatReplacement earliest(ChatReplacement... replacements) {
        ChatReplacement best = null;
        for (ChatReplacement replacement : replacements) {
            if (replacement != null && (best == null || replacement.start() < best.start())) {
                best = replacement;
            }
        }
        return best;
    }

    private String normalizedUrl(String token) {
        if (!(token.regionMatches(true, 0, "http://", 0, 7) ||
                token.regionMatches(true, 0, "https://", 0, 8))) {
            token = "https://" + token;
        }
        int queryIndex = token.indexOf('?');
        if (queryIndex >= 0) {
            token = token.substring(0, queryIndex);
        }
        return token;
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
