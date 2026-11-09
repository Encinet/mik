package org.encinet.mik.feature.player.profile;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

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
