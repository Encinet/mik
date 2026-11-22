package org.encinet.mik.feature.community.ai.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

final class AiWebLinks {
    private static final Pattern PUBLIC_SUFFIX = Pattern.compile("[a-z]{2,63}|xn--[a-z0-9-]{1,59}");
    private static final Set<String> PRIVATE_SUFFIXES = Set.of(
            "localhost", "local", "internal", "localdomain", "home", "lan", "onion", "invalid");

    private AiWebLinks() {
    }

    static Component render(Component label, String text, String url, boolean nativeInteraction) {
        if (!isPublic(url)) return label;
        return nativeInteraction ? label.color(NamedTextColor.AQUA).decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                : text.equals(url) ? label : label.append(Component.text(" · " + url, NamedTextColor.WHITE));
    }

    private static boolean isPublic(String url) {
        if (url.length() > 512 || !url.startsWith("https://")
                || url.chars().anyMatch(character -> character <= 32 || character >= 127 || character == '\\')) {
            return false;
        }
        try {
            var uri = new URI(url);
            var host = uri.getHost();
            if (host == null || !host.equals(uri.getRawAuthority())) return false;
            int dot = host.lastIndexOf('.');
            if (dot < 1) return false;
            var suffix = host.substring(dot + 1);
            return PUBLIC_SUFFIX.matcher(suffix).matches() && !PRIVATE_SUFFIXES.contains(suffix);
        } catch (URISyntaxException ignored) {
            return false;
        }
    }
}
