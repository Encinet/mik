package org.encinet.mik.module.chat.modifier;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlModifier implements ChatModifier {

    // Unicode 字母数字：覆盖中文、日文、韩文、阿拉伯文等所有 Unicode 字母/数字类别
    // \p{L}  = Unicode Letter（含 CJK、假名、谚文等）
    // \p{N}  = Unicode Number
    // \p{M}  = Mark（组合音标，如泰文元音符号）
    private static final String U_ALNUM = "[\\p{L}\\p{N}\\p{M}]";

    // 域名 label：ASCII 字母数字 + 连字符，或纯 Unicode 字符序列
    // ASCII label：[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?
    // Unicode label：一个或多个 Unicode 字母/数字（CJK 域名不含连字符）
    private static final String DOMAIN_LABEL =
            "(?:[a-z0-9](?:[a-z0-9\\-]{0,61}[a-z0-9])?|" + U_ALNUM + "+)";

    // 顶级域名：ASCII TLD 或 Unicode TLD（.中国 .日本 .한국 .مصر 等）
    private static final String TOP_LEVEL_DOMAIN =
            "(?:[a-z]{2,63}|xn--[a-z0-9\\-]{2,59}|" + U_ALNUM + "{2,})";

    private static final String DOMAIN = "(?:" + DOMAIN_LABEL + "\\.)+" + TOP_LEVEL_DOMAIN;

    // bare www. 前缀域名（无协议头）
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

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(https?://" + HOST + PORT + RESOURCE
                    + "|" + BARE_WWW_DOMAIN + PORT + RESOURCE + ")",
            Pattern.UNICODE_CHARACTER_CLASS  // 让 \p{L} 等正确匹配 Unicode，Java 8u20+ 默认也可，显式更安全
    );

    private static final ThreadLocal<Matcher> MATCHER_CACHE =
            ThreadLocal.withInitial(() -> URL_PATTERN.matcher(""));

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "utm_id", "utm_name", "utm_cid", "utm_reader", "utm_social",
            "gclid", "gclsrc", "dclid", "wbraid", "gbraid",
            "fbclid", "igshid", "igsh",
            "msclkid",
            "mc_cid", "mc_eid",
            "ttclid", "twclid", "yclid",
            "spm", "scm",
            "_hsenc", "_hsmi", "hsctatracking",
            "mkt_tok", "vero_id", "vero_conv", "s_cid",
            "oly_anon_id", "oly_enc_id"
    );

    @Override
    public ChatReplacement find(String text, int fromIndex, ChatModifierContext context) {
        Matcher matcher = MATCHER_CACHE.get();
        matcher.reset(text);
        if (!matcher.find(fromIndex)) {
            return null;
        }
        String token = matcher.group();
        int linkLength = urlEnd(token);
        String link = token.substring(0, linkLength);
        String cleanedLink = stripTrackingParams(link);
        String url = normalizedUrl(cleanedLink);
        return new ChatReplacement(
                matcher.start(), matcher.start() + linkLength,
                ChatRenderUtil.link(ChatRenderUtil.compactLinkLabel(cleanedLink), url)
        );
    }

    private String normalizedUrl(String token) {
        if (token.regionMatches(true, 0, "http://", 0, 7)
                || token.regionMatches(true, 0, "https://", 0, 8)) {
            return token;
        }
        return "https://" + token;
    }

    private int urlEnd(String token) {
        int end = token.length();
        // 先去除 ASCII 标点
        while (end > 0 && isTrailingUrlPunctuation(token.charAt(end - 1))) {
            end--;
        }
        // 再去除 Unicode 标点，需要按码点倒退
        while (end > 0) {
            int cp = token.codePointBefore(end);
            if (!isTrailingUnicodePunctuation(cp)) break;
            end -= Character.charCount(cp);
        }
        return Math.max(end, 1);
    }

    private boolean isTrailingUrlPunctuation(char c) {
        return c == '.' || c == ',' || c == '!' || c == '?' || c == ':' || c == ';';
    }

    private boolean isTrailingUnicodePunctuation(int cp) {
        return cp == '。' || cp == '，' || cp == '！' || cp == '？'
                || cp == '；' || cp == '：' || cp == '、' || cp == '…'
                || cp == '》' || cp == '』'
                || cp == '）';
    }

    private String stripTrackingParams(String url) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0) return url;

        int hashIdx = url.indexOf('#', queryStart + 1);
        int queryEnd = hashIdx >= 0 ? hashIdx : url.length();
        if (queryStart + 1 >= queryEnd) return url;

        String query = url.substring(queryStart + 1, queryEnd);
        int qLen = query.length();

        StringBuilder filtered = null;
        boolean removedAny = false;
        int segStart = 0;
        int eqPos = -1;

        for (int i = 0; i <= qLen; i++) {
            char c = i < qLen ? query.charAt(i) : '&';
            if (c == '=' && eqPos < 0) {
                eqPos = i;
            } else if (c == '&') {
                if (i > segStart) {
                    int keyEnd = eqPos >= 0 ? eqPos : i;
                    if (isTrackingParam(query, segStart, keyEnd)) {
                        removedAny = true;
                    } else {
                        if (filtered == null) filtered = new StringBuilder(qLen);
                        if (!filtered.isEmpty()) filtered.append('&');
                        filtered.append(query, segStart, i);
                    }
                }
                segStart = i + 1;
                eqPos = -1;
            }
        }

        if (!removedAny) return url;

        StringBuilder result = new StringBuilder(url.length());
        result.append(url, 0, queryStart);
        if (filtered != null && !filtered.isEmpty()) {
            result.append('?').append(filtered);
        }
        if (hashIdx >= 0) result.append(url, hashIdx, url.length());
        return result.toString();
    }

    private boolean isTrackingParam(String query, int start, int end) {
        int len = end - start;
        if (len < 3 || len > 16) return false;
        char first = Character.toLowerCase(query.charAt(start));
        return switch (first) {
            case 'u', 'g', 'f', 'i', 'm', 't', 's', '_', 'w', 'd', 'o', 'v', 'h', 'b' ->
                    TRACKING_PARAMS.contains(toLowerSubstring(query, start, end));
            default -> false;
        };
    }

    private String toLowerSubstring(String s, int start, int end) {
        for (int i = start; i < end; i++) {
            if (Character.isUpperCase(s.charAt(i))) {
                StringBuilder sb = new StringBuilder(end - start);
                sb.append(s, start, i);
                for (int j = i; j < end; j++) {
                    sb.append(Character.toLowerCase(s.charAt(j)));
                }
                return sb.toString();
            }
        }
        return s.substring(start, end);
    }
}