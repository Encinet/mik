package org.encinet.mik.module.menu.runtime;


import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FloatingMenuTextTest {

    @Test
    void absentRootColorBecomesExplicitWhite() {
        Component styled = FloatingMenuText.withDefaultWhite(
                Component.text("Menu").append(Component.text(" detail")));

        assertEquals(NamedTextColor.WHITE, styled.color());
        assertEquals(NamedTextColor.WHITE,
                styled.children().getFirst().style().colorIfAbsent(styled.color()).color());
    }

    @Test
    void intentionalRootAndChildColorsRemainUntouched() {
        Component source = Component.text("Warning", NamedTextColor.RED)
                .append(Component.text(" detail", NamedTextColor.GRAY));
        Component styled = FloatingMenuText.withDefaultWhite(source);

        assertEquals(NamedTextColor.RED, styled.color());
        assertEquals(NamedTextColor.GRAY, styled.children().getFirst().color());
    }

    @Test
    void lowContrastColorsAreBrightenedForWorldTextIncludingNestedLines() {
        Component source = Component.text("Title", NamedTextColor.DARK_PURPLE)
                .append(Component.text(" detail", NamedTextColor.DARK_GRAY));
        Component styled = FloatingMenuText.readableOnDark(source);

        assertEquals(NamedTextColor.LIGHT_PURPLE, styled.color());
        assertEquals(NamedTextColor.GRAY, styled.children().getFirst().color());
        Component inherited = FloatingMenuText.readableOnDark(
                Component.text("Parent", NamedTextColor.AQUA)
                        .append(Component.text(" child")));
        assertEquals(null, inherited.children().getFirst().color());

        Component actions = FloatingMenuText.readableOnDark(
                Component.text("Delete", NamedTextColor.RED)
                        .append(Component.text(" music", NamedTextColor.BLUE)));
        assertEquals(TextColor.color(0xFFAAAA), actions.color());
        assertEquals(TextColor.color(0xBBBBFF), actions.children().getFirst().color());
    }
}
