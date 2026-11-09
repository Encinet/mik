package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiTextMetricsTest {
    @Test void completeTextAtTheLineLimitIsPreservedAndOverflowIncludesItsEllipsisInTheBudget() {
        Component full = Component.text(String.join("\n", java.util.Collections.nCopies(8, "园".repeat(12))));
        assertSame(full, UiTextMetrics.fitLines(full, 112, 8));
        for (String body : List.of("长公告".repeat(500), "a_long_unbroken_word".repeat(100),
                "Words are wrapped at spaces. ".repeat(100), "第一行\n\n".repeat(40), "😀🌱\n".repeat(50))) {
            Component fitted = UiTextMetrics.fitLines(Component.text(body), 112, 8);
            var extent = UiTextMetrics.measure(fitted, 112);
            assertTrue(extent.lines() <= 8, body);
            assertTrue(extent.widthPixels() <= 112, body);
            String plain = PlainTextComponentSerializer.plainText().serialize(fitted);
            assertTrue(plain.endsWith("…"));
            assertTrue(body.startsWith(plain.substring(0, plain.length() - 1)));
        }
    }

    @Test void previewAccountsForGlyphWidthsAndInheritedBoldWithoutLosingStyles() {
        var narrow = Component.text("i".repeat(100));
        assertSame(narrow, UiTextMetrics.fitLines(narrow, 112, 2));
        var wide = Component.text("庭".repeat(100), NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
        var fitted = UiTextMetrics.fitLines(wide, 112, 2);
        assertNotEquals(wide, fitted);
        assertEquals(NamedTextColor.AQUA, fitted.children().getFirst().color());
        assertEquals(TextDecoration.State.TRUE, fitted.children().getFirst().decoration(TextDecoration.BOLD));
        assertEquals(2, UiTextMetrics.measure(fitted, 112).lines());
    }
}
