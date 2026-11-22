package org.encinet.mik.feature.community.ai.chat;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.encinet.mik.protocol.AssistantPart;
import org.encinet.mik.protocol.AssistantSpan.Style;

import java.util.function.Predicate;
import java.util.regex.Pattern;

final class AiAnswerText {
    private static final int MAX_ITEM_NAME_LENGTH = 64;
    private static final Pattern ITEM_ID = Pattern.compile("minecraft:[a-z0-9._/\\-]{1,96}");

    private AiAnswerText() {
    }

    static Component render(AssistantPart result, boolean nativeInteraction, String prefix) {
        return render(result, nativeInteraction, prefix,
                key -> RegistryAccess.registryAccess().getRegistry(RegistryKey.ITEM).get(key) != null);
    }

    static Component render(AssistantPart result, boolean nativeInteraction, String prefix,
                            Predicate<NamespacedKey> registeredItem) {
        var body = Component.text();
        int length = 0;
        for (var span : result.getSpansList()) {
            String text = span.getText();
            length += text.codePointCount(0, text.length());
            if (text.isEmpty()) continue;
            var word = Component.text(withLinePrefix(text, prefix));
            var styles = span.getStylesList();
            if (styles.contains(Style.CODE)) word = word.color(NamedTextColor.GREEN);
            else if (styles.contains(Style.BOLD)) word = word.color(NamedTextColor.GOLD);
            if (styles.contains(Style.BOLD)) word = word.decorate(TextDecoration.BOLD);
            if (styles.contains(Style.ITALIC)) word = word.decorate(TextDecoration.ITALIC);
            if (styles.contains(Style.STRIKETHROUGH)) word = word.decorate(TextDecoration.STRIKETHROUGH);
            body.append(word);
        }
        if (nativeInteraction && length > 0 && length <= MAX_ITEM_NAME_LENGTH) {
            var preview = itemPreview(result.getItemId(), registeredItem);
            if (preview != null) body.decorate(TextDecoration.UNDERLINED).hoverEvent(preview);
        }
        return body.build();
    }

    static String withLinePrefix(String text, String prefix) {
        return prefix.isEmpty() ? text : text.replace("\n", "\n" + prefix);
    }

    private static HoverEvent<HoverEvent.ShowItem> itemPreview(String id, Predicate<NamespacedKey> registeredItem) {
        if (id.isEmpty() || !ITEM_ID.matcher(id).matches() || id.equals("minecraft:air")) return null;
        var key = NamespacedKey.fromString(id);
        // A native preview of the default item: never accept model-provided data or use an inventory stack.
        return key != null && registeredItem.test(key) ? HoverEvent.showItem(key, 1) : null;
    }
}
