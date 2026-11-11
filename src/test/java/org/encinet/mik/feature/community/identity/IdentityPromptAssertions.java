package org.encinet.mik.feature.community.identity;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies visible emphasis after inheriting parent styles, including click actions. */
final class IdentityPromptAssertions {
    private IdentityPromptAssertions() { }

    static void assertCodeEmphasis(Component prompt, int expectedCodes) {
        var emphasized = runs(prompt).stream()
                .filter(run -> run.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE)
                .toList();
        assertEquals(expectedCodes, emphasized.size());
        for (var run : emphasized) {
            assertTrue(run.content().matches("[0-9]{6}"), run.content());
            assertEquals(NamedTextColor.GOLD, run.color());
        }
    }

    static List<TextComponent> runs(Component component) {
        return runs(component, Style.empty()).toList();
    }

    private static Stream<TextComponent> runs(Component component, Style inherited) {
        Style effective = component.style().merge(inherited, Style.Merge.Strategy.IF_ABSENT_ON_TARGET);
        Stream<TextComponent> current = component instanceof TextComponent text && !text.content().isEmpty()
                ? Stream.of(text.style(effective)) : Stream.empty();
        return Stream.concat(current, component.children().stream().flatMap(child -> runs(child, effective)));
    }
}
