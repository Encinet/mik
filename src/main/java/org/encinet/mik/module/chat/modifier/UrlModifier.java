package org.encinet.mik.module.chat.modifier;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UrlModifier implements ChatModifier {

    private static final String DOMAIN_LABEL = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
    private static final String TOP_LEVEL_DOMAIN = "(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})";
    private static final String DOMAIN = "(?:" + DOMAIN_LABEL + "\\.)+" + TOP_LEVEL_DOMAIN;
    private static final String BARE_WWW_DOMAIN = "www\\.(?:" + DOMAIN_LABEL + "\\.)*" + TOP_LEVEL_DOMAIN;
    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    private static final String IPV4 = IPV4_OCTET + "(?:\\." + IPV4_OCTET + "){3}";
    private static final String IPV6 = "\\[(?=[0-9a-f:.]*:)[0-9a-f:.]+\\]";
    private static final String HOST = "(?:" + DOMAIN + "|" + IPV4 + "|" + IPV6 + "|localhost)";
    private static final String PORT = "(?::[0-9]{1,5})?";
    private static final String RESOURCE = "(?:[/?#][^\\s<]*)?";
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(https?://" + HOST + PORT + RESOURCE + "|" + BARE_WWW_DOMAIN + PORT + RESOURCE + ")"
    );

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
