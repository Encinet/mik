package org.encinet.mik.feature.community.chat.parse;

import com.google.common.net.InternetDomainName;
import org.encinet.mik.feature.community.chat.link.ChatResourceLinks;
import org.encinet.mik.feature.community.chat.link.ChatUrls;
import org.encinet.mik.feature.community.chat.mention.MentionBoundaries;
import org.encinet.mik.feature.community.chat.model.ChatCapability;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatStyle;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves references in ordinary game chat; labels of existing references are never rescanned. */
final class ReferenceModifier implements ChatModifier {
    private static final String U_ALNUM = "[\\p{L}\\p{N}\\p{M}]";

    private static final String ASCII_DOMAIN_LABEL =
            "[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?";
    private static final String ASCII_TOP_LEVEL_DOMAIN =
            "(?:[a-z]{2,63}|xn--[a-z0-9\\-]{2,59})";
    private static final String ASCII_DOMAIN =
            "(?:" + ASCII_DOMAIN_LABEL + "\\.)+" + ASCII_TOP_LEVEL_DOMAIN;

    // 域名 label：ASCII 字母数字 + 连字符，或纯 Unicode 字符序列
    // ASCII label：[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?
    // Unicode label：一个或多个 Unicode 字母/数字（CJK 域名不含连字符）
    private static final String DOMAIN_LABEL =
            "(?:[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?|" + U_ALNUM + "+)";

    // 顶级域名：ASCII TLD 或 Unicode TLD（.中国 .日本 .한국 .مصر 等）
    private static final String TOP_LEVEL_DOMAIN =
            "(?:[a-z]{2,63}|xn--[a-z0-9\\-]{2,59}|" + U_ALNUM + "{2,})";

    private static final String BARE_WWW_DOMAIN =
            "www\\.(?:" + DOMAIN_LABEL + "\\.)*" + TOP_LEVEL_DOMAIN;

    private static final String PORT = "(?::[0-9]{1,5})?";

    // path/query/fragment：允许非 ASCII Unicode 字符（中文路径、日文参数等）
    // 与显式地址使用相同的句读边界；百分号编码和原始 Unicode 路径都接受。
    private static final String RESOURCE = "(?:[/?#][^\\s<>\"，。！？、；：]*)?";
    private static final String ASCII_HOST_END =
            "(?![a-z0-9_@\\-]|\\." + U_ALNUM + "|:[0-9])";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:(?<![a-z0-9+.-])" + ChatResourceLinks.absoluteLinks()
                    // ASCII 裸域名允许紧邻中文文本，例如“查看google.com”。
                    + "|(?<![a-z0-9_@.:\\-])" + ASCII_DOMAIN + PORT + ASCII_HOST_END + RESOURCE
                    // 保留 www. 开头的 Unicode 裸域名支持，同时用边界排除邮箱。
                    + "|(?<![a-z0-9_@.:\\-])" + BARE_WWW_DOMAIN + PORT + ASCII_HOST_END + RESOURCE + ")",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private static final String LOCAL_ATOM =
            "[a-z0-9!#$&'*+/=?^_`{|}~-]+";
    private static final String LOCAL_PART =
            LOCAL_ATOM + "(?:\\." + LOCAL_ATOM + ")*";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9!#$&'*+/=?^_`{|}~.-])"
                    + "(?:mailto:)?(" + LOCAL_PART + "@" + ASCII_DOMAIN + ")"
                    + "(?![a-z0-9_-]|\\.[a-z0-9])"
    );

    private static final Pattern ALL_PATTERN = Pattern.compile("(?iu)@all");
    private static final Pattern ACCOUNT_MENTION = Pattern.compile("@[ \t]*([A-Za-z0-9_]{1,16})");

    @Override
    public boolean supports(ChatProcessingContext context) {
        var capabilities = context.capabilities();
        return capabilities.contains(ChatCapability.LINK) || capabilities.contains(ChatCapability.PLAYER_MENTION)
                || capabilities.contains(ChatCapability.BROADCAST_MENTION);
    }

    @Override
    public IntFunction<ChatReplacement> search(String text, ChatProcessingContext context) {
        var capabilities = context.capabilities();
        List<IntFunction<ChatReplacement>> searches = new ArrayList<>();
        // At the same offset, addresses take precedence over mentions.
        if (capabilities.contains(ChatCapability.LINK)) {
            Matcher email = EMAIL_PATTERN.matcher(text);
            Matcher url = URL_PATTERN.matcher(text);
            searches.add(from -> findEmail(text, email, from));
            searches.add(from -> findUrl(text, url, from));
            searches.add(from -> {
                var shortLink = ChatResourceLinks.shortLink(text, from);
                return shortLink == null ? null
                        : new ChatReplacement(shortLink.start(), shortLink.end(), shortLink.link());
            });
        }
        List<IntFunction<ChatReplacement>> mentions = new ArrayList<>();
        if (capabilities.contains(ChatCapability.BROADCAST_MENTION)) {
            Matcher audience = ALL_PATTERN.matcher(text);
            mentions.add(from -> findAudience(text, audience, from));
        }
        if (capabilities.contains(ChatCapability.PLAYER_MENTION)) {
            mentions.add(from -> {
                var match = context.mentions().find(text, from);
                return match == null ? null : reference(text, match.start(), match.end(),
                        new ChatNode.Reference.Person(match.target().id(), match.target().name()));
            });
        }
        if (capabilities.contains(ChatCapability.BOUND_PLAYER_MENTION)) {
            Matcher accounts = ACCOUNT_MENTION.matcher(text);
            mentions.add(from -> findAccount(text, accounts, from, context));
        }
        if (!mentions.isEmpty()) {
            var search = ChatReplacement.mergeSearches(mentions);
            searches.add(from -> {
                ChatReplacement match = search.apply(from);
                while (match != null && ChatUrls.insideUrl(text, match.start())) {
                    match = search.apply(match.start() + 1);
                }
                return match;
            });
        }
        return ChatReplacement.mergeSearches(searches);
    }

    private static ChatReplacement findUrl(String text, Matcher matcher, int fromIndex) {
        int searchIndex = fromIndex;
        while (matcher.find(searchIndex)) {
            if (ChatUrls.insideUrl(text, matcher.start())) {
                searchIndex = matcher.end();
                continue;
            }
            String token = matcher.group();
            int linkLength = ChatUrls.visibleUrlEnd(token);
            String link = token.substring(0, linkLength);
            ChatUrls.CanonicalHttpLink canonical = ChatUrls.canonicalHttpLink(link);
            if (canonical == null) {
                searchIndex = matcher.end();
                continue;
            }
            if (!ChatUrls.hasHttpScheme(link) && !ChatResourceLinks.isKnown(canonical.target())
                    && !hasRegistrableDomain(link)) {
                // 当前候选可能只是文件名（如 config.yaml）；继续寻找后面的真正链接。
                searchIndex = matcher.start() + 1;
                continue;
            }
            return new ChatReplacement(
                    matcher.start(), matcher.start() + linkLength,
                    ChatResourceLinks.automatic(canonical.displayText(), canonical.target())
            );
        }
        return null;
    }

    private static boolean hasRegistrableDomain(String token) {
        String host = bareHost(token);
        try {
            return InternetDomainName.from(host).isUnderRegistrySuffix();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String bareHost(String token) {
        int end = token.length();
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == ':' || c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        return token.substring(0, end);
    }

    private static ChatReplacement findEmail(String text, Matcher matcher, int fromIndex) {
        for (boolean found = matcher.find(fromIndex); found; found = matcher.find()) {
            if (ChatUrls.insideUrl(text, matcher.start())) continue;
            String address = matcher.group(1);
            URI uri = mailto(address);
            if (uri == null) continue;
            return new ChatReplacement(matcher.start(), matcher.end(),
                    ChatResourceLinks.generated("Email: " + address, uri));
        }
        return null;
    }

    private static URI mailto(String address) {
        try {
            return ChatNode.Reference.Link.canonicalUri(new URI("mailto", address, null));
        } catch (URISyntaxException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ChatReplacement findAudience(String text, Matcher matcher, int fromIndex) {
        for (boolean found = matcher.find(fromIndex); found; found = matcher.find()) {
            if (MentionBoundaries.isStart(text, matcher.start()) && MentionBoundaries.isEnd(text, matcher.end())) {
                return reference(text, matcher.start(), matcher.end(), ChatNode.Reference.Audience.INSTANCE);
            }
        }
        return null;
    }

    private static ChatReplacement reference(String text, int start, int end, ChatNode.Reference.Target target) {
        return new ChatReplacement(start, end,
                new ChatNode.Reference(text.substring(start, end), ChatStyle.EMPTY, target));
    }

    private static ChatReplacement findAccount(String text, Matcher matcher, int fromIndex, ChatProcessingContext context) {
        for (boolean found = matcher.find(fromIndex); found; found = matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (MentionBoundaries.isStart(text, start) && MentionBoundaries.isEnd(text, end)
                    && !context.mentions().knowsName(matcher.group(1))) {
                return new ChatReplacement(start, end, new ChatNode.PendingMention(
                        List.of(new ChatNode.Text(matcher.group(), ChatStyle.EMPTY)), matcher.group(1)));
            }
        }
        return null;
    }
}
