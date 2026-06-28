package org.encinet.mik.module.chat.modifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BilibiliModifier implements ChatModifier {

    private static final Pattern BV_PATTERN = Pattern.compile("(?i)bv[0-9A-Za-z]{10}");
    private static final Pattern AV_PATTERN = Pattern.compile("(?i)av\\d+");

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        ChatReplacement bv = findBv(text, fromIndex, context);
        ChatReplacement av = findAv(text, fromIndex, context);
        if (bv == null) return av;
        if (av == null) return bv;
        return bv.start() <= av.start() ? bv : av;
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
}
