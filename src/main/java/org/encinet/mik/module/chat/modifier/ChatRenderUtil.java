package org.encinet.mik.module.chat.modifier;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.util.PlayerDisplay;

final class ChatRenderUtil {

    private static final int MAX_LINK_LABEL_LENGTH = 36;
    private static final TextColor ITEM_BRACKET = TextColor.color(0x7D8590);
    private static final TextColor ITEM_NAME = TextColor.color(0x8BD450);
    private static final TextColor ITEM_COUNT = TextColor.color(0x6EE7B7);
    private static final TextColor LINK = TextColor.color(0x4EA5FF);
    private static final TextColor BILIBILI = TextColor.color(0xFF6FAE);
    private static final TextColor MENTION_AT = TextColor.color(0xF59E0B);
    private static final TextColor MENTION_NAME = TextColor.color(0xFFD166);
    private static final TextColor ALL_MENTION = TextColor.color(0xFFAA00);

    private ChatRenderUtil() {
    }

    static Component item(Player sender, String itemEmptyHover) {
        ItemStack item = sender.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || item.isEmpty()) {
            return Component.text("[empty]", NamedTextColor.GRAY, TextDecoration.ITALIC)
                    .hoverEvent(HoverEvent.showText(Component.text(itemEmptyHover, NamedTextColor.GRAY)));
        }
        ItemStack displayItem = item.clone();
        return Component.text("[", ITEM_BRACKET)
                .append(displayItem.effectiveName().colorIfAbsent(ITEM_NAME))
                .append(Component.text(" x" + displayItem.getAmount(), ITEM_COUNT))
                .append(Component.text("]", ITEM_BRACKET))
                .decoration(TextDecoration.UNDERLINED, false)
                .hoverEvent(displayItem.asHoverEvent());
    }

    static Component link(String label, String url) {
        return Component.text(label, LINK, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text(url, NamedTextColor.GRAY)));
    }

    static Component bilibili(String label, String url, String hoverPrefix) {
        return Component.text(label, BILIBILI, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(url))
                .hoverEvent(HoverEvent.showText(Component.text()
                        .append(Component.text(hoverPrefix, BILIBILI))
                        .append(Component.newline())
                        .append(Component.text(url, LINK))
                        .build()));
    }

    static Component mention(Player player) {
        return Component.text("@", MENTION_AT)
                .append(PlayerDisplay.name(player, MENTION_NAME));
    }

    static Component allMention(String hover) {
        return Component.text("@all", ALL_MENTION, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY)));
    }

    static String compactLinkLabel(String url) {
        String label = url.length() <= MAX_LINK_LABEL_LENGTH
                ? url
                : url.substring(0, MAX_LINK_LABEL_LENGTH - 4) + "....";
        return "[" + label + "]";
    }
}
