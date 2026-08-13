package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.net.URI;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BilibiliModifier implements ChatModifier {

    private static final Pattern VIDEO_URL_PATTERN = Pattern.compile(
            "(?i)(https?://)?(?:www\\.|m\\.)?bilibili\\.com/video/(bv[0-9A-Za-z]{10}|av\\d+)[^\\s<]*"
    );
    private static final Pattern BV_PATTERN = Pattern.compile("(?i)bv[0-9A-Za-z]{10}");
    private static final Pattern AV_PATTERN = Pattern.compile("(?i)av\\d+");

    @Override
    public int priority() {
        return 30;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.URL);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        ChatReplacement url = findVideoUrl(text, fromIndex, context);
        ChatReplacement bv = findBv(text, fromIndex, context);
        ChatReplacement av = findAv(text, fromIndex, context);
        return earliest(url, bv, av);
    }

    private ChatReplacement findVideoUrl(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = VIDEO_URL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String token = matcher.group();
        int linkLength = urlEnd(token);
        String link = token.substring(0, linkLength);
        String label = matcher.group(2);
        String url = ChatUrlSupport.normalizedHttpUrl(link);
        int queryIndex = url.indexOf('?');
        if (queryIndex >= 0) {
            url = url.substring(0, queryIndex);
        }
        return new ChatReplacement(matcher.start(), matcher.start() + linkLength,
                link(label, url));
    }

    private ChatReplacement findBv(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = BV_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String label = matcher.group();
        String url = "https://www.bilibili.com/video/" + label;
        return new ChatReplacement(matcher.start(), matcher.end(),
                link(label, url));
    }

    private ChatReplacement findAv(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = AV_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String label = matcher.group();
        String url = "https://www.bilibili.com/video/" + label;
        return new ChatReplacement(matcher.start(), matcher.end(),
                link(label, url));
    }

    private ChatNode.Link link(String label, String url) {
        return new ChatNode.Link(label, URI.create(url),
                ChatSemanticStyles.BILIBILI);
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

    private int urlEnd(String token) {
        return ChatUrlSupport.visibleUrlEnd(token);
    }
}
