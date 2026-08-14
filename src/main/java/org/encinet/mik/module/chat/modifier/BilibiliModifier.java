package org.encinet.mik.module.chat.modifier;

import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.net.URI;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BilibiliModifier implements ChatModifier {

    private static final String VIDEO_ID = "(?:bv[0-9a-z]{10}|av[0-9]+)";
    private static final Pattern VIDEO_URL_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9_@.-])(?:https?://)?"
                    + "(?:www\\.|m\\.)?bilibili\\.com"
                    + "(?![a-z0-9_@.-]|:[0-9])/video/(" + VIDEO_ID + ")"
                    + "(?![a-z0-9])(?:[/?#][^\\s<>]*)?"
    );
    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9_@./-])" + VIDEO_ID
                    + "(?![a-z0-9_./-])"
    );

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
        return earliest(
                findVideoUrl(text, fromIndex),
                findVideoId(text, fromIndex));
    }

    private ChatReplacement findVideoUrl(
            String text, int fromIndex
    ) {
        Matcher matcher = VIDEO_URL_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String token = matcher.group();
        int linkLength = ChatUrlSupport.visibleUrlEnd(token);
        String videoId = normalizedVideoId(matcher.group(1));
        return new ChatReplacement(matcher.start(), matcher.start() + linkLength,
                videoLink(videoId));
    }

    private ChatReplacement findVideoId(
            String text, int fromIndex
    ) {
        Matcher matcher = VIDEO_ID_PATTERN.matcher(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String videoId = normalizedVideoId(matcher.group());
        return new ChatReplacement(matcher.start(), matcher.end(),
                videoLink(videoId));
    }

    private String normalizedVideoId(String videoId) {
        String prefix = videoId.regionMatches(true, 0, "bv", 0, 2)
                ? "BV" : "av";
        return prefix + videoId.substring(2);
    }

    private ChatNode.Link videoLink(String videoId) {
        return ChatLinkPresentation.serviceLink(
                "Bilibili", videoId,
                URI.create("https://www.bilibili.com/video/" + videoId),
                ChatLinkPalette.BILIBILI);
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
}
