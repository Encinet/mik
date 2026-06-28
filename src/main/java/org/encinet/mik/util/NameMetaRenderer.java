package org.encinet.mik.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameMetaRenderer {

    private static final String SUPPORTED_PAPI_PREFIX = "player_";
    private static final Pattern PAPI_PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");

    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset(),
                    StandardTags.hoverEvent(),
                    StandardTags.insertion(),
                    StandardTags.font(),
                    StandardTags.keybind(),
                    StandardTags.translatable(),
                    StandardTags.newline(),
                    StandardTags.score()
            ))
            .build();

    private NameMetaRenderer() {
    }

    public static Component deserialize(Player player, String raw) {
        return deserialize(applyPlayerPlaceholders(player, raw));
    }

    public static Component deserialize(String raw) {
        return MINI_MESSAGE.deserialize(stripVisibleNewlineTags(raw));
    }

    public static Component fallback(Player player, String raw) {
        return fallback(applyPlayerPlaceholders(player, raw));
    }

    public static Component fallback(String raw) {
        return Component.text(stripVisibleNewlineTags(raw));
    }

    public static String findUnsupportedPlaceholder(String raw) {
        Matcher matcher = PAPI_PLACEHOLDER_PATTERN.matcher(raw);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!isSupportedPlaceholder(placeholder)) {
                return placeholder;
            }
        }
        return null;
    }

    public static String applyPlayerPlaceholders(Player player, String raw) {
        Plugin placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            return raw;
        }
        try {
            MaskedPlaceholders masked = maskUnsupportedPlaceholders(raw);
            Class<?> placeholderApiClass = placeholderApi.getClass().getClassLoader()
                    .loadClass("me.clip.placeholderapi.PlaceholderAPI");
            Method setPlaceholders = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            Object parsed = setPlaceholders.invoke(null, player, masked.text());
            return parsed instanceof String value ? masked.restore(value) : raw;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return raw;
        }
    }

    private static MaskedPlaceholders maskUnsupportedPlaceholders(String raw) {
        Matcher matcher = PAPI_PLACEHOLDER_PATTERN.matcher(raw);
        StringBuilder masked = new StringBuilder();
        Map<String, String> replacements = new LinkedHashMap<>();
        int index = 0;
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (isSupportedPlaceholder(placeholder)) {
                matcher.appendReplacement(masked, Matcher.quoteReplacement(matcher.group()));
            } else {
                String token = "\uE000mik_papi_" + index++ + "\uE000";
                replacements.put(token, matcher.group());
                matcher.appendReplacement(masked, Matcher.quoteReplacement(token));
            }
        }
        matcher.appendTail(masked);
        return new MaskedPlaceholders(masked.toString(), replacements);
    }

    private static boolean isSupportedPlaceholder(String placeholder) {
        return placeholder.toLowerCase(Locale.ROOT).startsWith(SUPPORTED_PAPI_PREFIX);
    }

    private static String stripVisibleNewlineTags(String raw) {
        StringBuilder stripped = new StringBuilder(raw.length());
        int index = 0;
        while (index < raw.length()) {
            int tagStart = raw.indexOf('<', index);
            if (tagStart < 0) {
                stripped.append(raw, index, raw.length());
                break;
            }
            stripped.append(raw, index, tagStart);
            if (isEscaped(raw, tagStart)) {
                stripped.append('<');
                index = tagStart + 1;
                continue;
            }
            int tagEnd = findTagEnd(raw, tagStart + 1);
            if (tagEnd < 0) {
                stripped.append(raw, tagStart, raw.length());
                break;
            }
            String tag = raw.substring(tagStart, tagEnd + 1);
            stripped.append(isNewlineTag(tag) ? " " : tag);
            index = tagEnd + 1;
        }
        return stripped.toString();
    }

    private static boolean isNewlineTag(String tag) {
        String content = tag.substring(1, tag.length() - 1).trim().toLowerCase(Locale.ROOT);
        return content.equals("newline") || content.equals("newline/")
                || content.equals("br") || content.equals("br/");
    }

    private static int findTagEnd(String raw, int start) {
        char quote = 0;
        for (int i = start; i < raw.length(); i++) {
            char current = raw.charAt(i);
            if ((current == '\'' || current == '"') && !isEscaped(raw, i)) {
                quote = quote == 0 ? current : (quote == current ? 0 : quote);
            } else if (current == '>' && quote == 0 && !isEscaped(raw, i)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isEscaped(String raw, int index) {
        int slashes = 0;
        for (int i = index - 1; i >= 0 && raw.charAt(i) == '\\'; i--) {
            slashes++;
        }
        return slashes % 2 == 1;
    }

    private record MaskedPlaceholders(String text, Map<String, String> replacements) {
        String restore(String parsedText) {
            String restored = parsedText;
            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                restored = restored.replace(entry.getKey(), entry.getValue());
            }
            return restored;
        }
    }
}
