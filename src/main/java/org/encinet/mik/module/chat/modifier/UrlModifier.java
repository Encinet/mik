package org.encinet.mik.module.chat.modifier;

import com.google.common.net.InternetDomainName;
import org.encinet.mik.module.chat.model.ChatCapability;
import org.encinet.mik.module.chat.model.ChatProcessingContext;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlModifier implements ChatModifier {

    // Unicode 字母数字：覆盖中文、日文、韩文、阿拉伯文等所有 Unicode 字母/数字类别
    // \p{L}  = Unicode Letter（含 CJK、假名、谚文等）
    // \p{N}  = Unicode Number
    // \p{M}  = Mark（组合音标，如泰文元音符号）
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

    private static final String DOMAIN = "(?:" + DOMAIN_LABEL + "\\.)+" + TOP_LEVEL_DOMAIN;
    private static final String BARE_WWW_DOMAIN =
            "www\\.(?:" + DOMAIN_LABEL + "\\.)*" + TOP_LEVEL_DOMAIN;

    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final String IPV4 = IPV4_OCTET + "(?:\\." + IPV4_OCTET + "){3}";
    private static final String IPV6 = "\\[(?=[0-9a-f:.]*:)[0-9a-f:.]+\\]";
    private static final String HOST = "(?:" + DOMAIN + "|" + IPV4 + "|" + IPV6 + "|localhost)";
    private static final String PORT = "(?::[0-9]{1,5})?";

    // path/query/fragment：允许非 ASCII Unicode 字符（中文路径、日文参数等）
    // 排除空白和 < > 即可；百分号编码和原始 Unicode 都接受
    private static final String RESOURCE = "(?:[/?#][^\\s<>]*)?";
    private static final String ASCII_HOST_END =
            "(?![a-z0-9_@\\-]|\\." + U_ALNUM + "|:[0-9])";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://" + HOST + PORT + RESOURCE
                    // ASCII 裸域名允许紧邻中文文本，例如“查看google.com”。
                    + "|(?<![a-z0-9_@.\\-])" + ASCII_DOMAIN + PORT + ASCII_HOST_END + RESOURCE
                    // 保留 www. 开头的 Unicode 裸域名支持，同时用边界排除邮箱。
                    + "|(?<![a-z0-9_@.\\-])" + BARE_WWW_DOMAIN + PORT + ASCII_HOST_END + RESOURCE + ")",
            Pattern.UNICODE_CHARACTER_CLASS  // 让 \p{L} 等正确匹配 Unicode，Java 8u20+ 默认也可，显式更安全
    );

    private static final ThreadLocal<Matcher> MATCHER_CACHE =
            ThreadLocal.withInitial(() -> URL_PATTERN.matcher(""));

    @Override
    public int priority() {
        return 1_000;
    }

    @Override
    public Set<ChatCapability> requiredCapabilities() {
        return Set.of(ChatCapability.URL);
    }

    @Override
    public ChatReplacement find(
            String text, int fromIndex, ChatProcessingContext context
    ) {
        Matcher matcher = MATCHER_CACHE.get();
        matcher.reset(text);
        int searchIndex = fromIndex;
        while (matcher.find(searchIndex)) {
            String token = matcher.group();
            int linkLength = urlEnd(token);
            String link = token.substring(0, linkLength);
            if (!hasHttpScheme(link) && !hasRegistrableDomain(link)) {
                // 当前候选可能只是文件名（如 config.yaml）；继续寻找后面的真正链接。
                searchIndex = matcher.start() + 1;
                continue;
            }
            ChatUrlSupport.CanonicalHttpLink canonical =
                    ChatUrlSupport.canonicalHttpLink(link);
            return new ChatReplacement(
                    matcher.start(), matcher.start() + linkLength,
                    ChatLinkPresentation.urlLink(canonical,
                            ChatLinkPalette.GENERIC)
            );
        }
        return null;
    }

    private boolean hasHttpScheme(String token) {
        return ChatUrlSupport.hasHttpScheme(token);
    }

    private boolean hasRegistrableDomain(String token) {
        String host = bareHost(token);
        try {
            return InternetDomainName.from(host).isUnderRegistrySuffix();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private String bareHost(String token) {
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

    private int urlEnd(String token) {
        return ChatUrlSupport.visibleUrlEnd(token);
    }

}
