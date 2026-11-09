package org.encinet.mik.shared.menu.ui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiTextTest {
    @Test void multilinePreviewsKeepTheirStylesAndFitBothLineAndCharacterLimits() {
        Component original = Component.text("one\ntwo\nthree\nfour", NamedTextColor.AQUA).decorate(TextDecoration.BOLD);
        Component preview = UiText.abbreviate(original, 100, 2);
        assertEquals("one\ntwo…", PlainTextComponentSerializer.plainText().serialize(preview));
        assertEquals(NamedTextColor.AQUA, preview.children().getFirst().color());
        assertEquals(TextDecoration.State.TRUE, preview.children().getFirst().decoration(TextDecoration.BOLD));
        assertSame(original, UiText.abbreviate(original, 100, 4));
        assertEquals("one\n…", PlainTextComponentSerializer.plainText().serialize(UiText.abbreviate(original, 5, 4)));
    }

    @Test void compactLabelsPreserveInheritedAndChildStylesAndNeverSplitSupplementaryCharacters() {
        Component original = Component.text().color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(Component.text("AB"))
                .append(Component.text("😀中文Tail", NamedTextColor.BLUE).decoration(TextDecoration.BOLD, false)).build();
        Component preview = UiText.abbreviate(original, 6);
        assertEquals("AB😀中文…", PlainTextComponentSerializer.plainText().serialize(preview));
        var first = (TextComponent) preview.children().get(0);
        var second = (TextComponent) preview.children().get(1);
        assertEquals(NamedTextColor.RED, first.color());
        assertEquals(TextDecoration.State.TRUE, first.decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.BLUE, second.color());
        assertEquals(TextDecoration.State.FALSE, second.decoration(TextDecoration.BOLD));
        assertSame(original, UiText.abbreviate(original, 100));
    }
}
