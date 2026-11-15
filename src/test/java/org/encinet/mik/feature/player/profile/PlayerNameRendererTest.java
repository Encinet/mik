package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerNameRendererTest {

    @Test
    void usernameColorsUseTheLuckPermsMiniMessageValue() {
        assertEquals(NamedTextColor.YELLOW, PlayerNameTagRenderer.parseUsernameColor("<yellow>"));
        assertEquals(NamedTextColor.WHITE, PlayerNameTagRenderer.parseUsernameColor("<white>"));
        assertEquals(TextColor.color(0x55AAFF), PlayerNameTagRenderer.parseUsernameColor("<#55AAFF>"));
        for (String invalid : new String[]{"", "yellow", "<unknown>", "<red>visible text", "<gradient:red:blue>"}) {
            assertEquals(NamedTextColor.WHITE, PlayerNameTagRenderer.parseUsernameColor(invalid));
        }
    }

    @Test
    void usernameColorCanRequestRainbowWithoutAnimating() {
        assertEquals(java.util.Optional.of(new PlayerNameTagRenderer.UsernameStyle(NamedTextColor.WHITE, true)),
                PlayerNameTagRenderer.parseUsernameStyle("<rainbow>"));
        assertEquals(java.util.Optional.of(new PlayerNameTagRenderer.UsernameStyle(NamedTextColor.YELLOW, false)),
                PlayerNameTagRenderer.parseUsernameStyle("<yellow>"));
        assertEquals(java.util.Optional.empty(),
                PlayerNameTagRenderer.parseUsernameStyle("<gradient:red:blue>"));
    }

    @Test
    void rainbowColorsEachUnicodeCodePointAndKeepsNameInteractions() {
        var click = ClickEvent.suggestCommand("/tell Alice ");
        Component name = Component.text("A😀BCDEFG").clickEvent(click);
        Component colored = PlayerNameRenderer.withUsernameStyle(
                name, NamedTextColor.WHITE, true);

        assertEquals(click, colored.clickEvent());
        assertEquals("A😀BCDEFG", PlainTextComponentSerializer.plainText().serialize(colored));
        assertEquals(List.of(
                NamedTextColor.RED,
                NamedTextColor.GOLD,
                NamedTextColor.YELLOW,
                NamedTextColor.GREEN,
                NamedTextColor.AQUA,
                NamedTextColor.BLUE,
                NamedTextColor.LIGHT_PURPLE,
                NamedTextColor.RED
        ), colored.children().stream().map(Component::color).toList());
    }

    @Test
    void accountColorAppliesToNestedNamesWithoutRemovingTheirInteractions() {
        var click = ClickEvent.suggestCommand("/tell Alice ");
        Component name = Component.text().append(Component.text("Alice", NamedTextColor.RED)).build()
                .clickEvent(click);
        Component colored = PlayerNameRenderer.withColor(name, NamedTextColor.YELLOW);
        assertEquals(NamedTextColor.YELLOW, colored.color());
        assertEquals(NamedTextColor.YELLOW, colored.children().getFirst().color());
        assertEquals(click, colored.clickEvent());
    }

    @Test
    void clickingPlayerNameSuggestsTellCommand() {
        Component name = PlayerNameRenderer.clickablePlayerName(Component.text("TestPlayer"), "TestPlayer");

        assertEquals(ClickEvent.suggestCommand("/tell TestPlayer "), name.clickEvent());
    }
}
