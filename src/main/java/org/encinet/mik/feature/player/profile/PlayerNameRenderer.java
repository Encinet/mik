package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Builds the interactive player name shared by chat and identity previews. */
public final class PlayerNameRenderer {

    private static final TextColor[] RAINBOW_COLORS = {
            NamedTextColor.RED,
            NamedTextColor.GOLD,
            NamedTextColor.YELLOW,
            NamedTextColor.GREEN,
            NamedTextColor.AQUA,
            NamedTextColor.BLUE,
            NamedTextColor.LIGHT_PURPLE
    };

    private PlayerNameRenderer() {
    }

    public static Component playerName(Player player) {
        return clickablePlayerName(
                player.displayName(),
                player.getName());
    }

    public static Component clickablePlayerName(Component name, String username) {
        return name.clickEvent(ClickEvent.suggestCommand("/tell " + username + " "));
    }

    /** Apply the effective account color without changing name interactions or decorations. */
    public static Component withColor(Component name, TextColor color) {
        return name.color(color).children(name.children().stream()
                .map(child -> withColor(child, color)).toList());
    }

    public static Component withUsernameStyle(
            Component name, TextColor color, boolean rainbow
    ) {
        return rainbow ? withRainbow(name, new int[1]) : withColor(name, color);
    }

    private static Component withRainbow(Component component, int[] position) {
        List<Component> children = new ArrayList<>();
        if (component instanceof TextComponent text) {
            String content = text.content();
            for (int offset = 0; offset < content.length();) {
                int codePoint = content.codePointAt(offset);
                children.add(Component.text(
                        new String(Character.toChars(codePoint)),
                        RAINBOW_COLORS[position[0]++ % RAINBOW_COLORS.length]));
                offset += Character.charCount(codePoint);
            }
        }
        component.children().stream()
                .map(child -> withRainbow(child, position))
                .forEach(children::add);
        return component instanceof TextComponent
                ? Component.empty().style(component.style()).children(children)
                : component.children(children);
    }
}
