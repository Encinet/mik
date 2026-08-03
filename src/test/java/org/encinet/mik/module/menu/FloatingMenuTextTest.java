package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
}
