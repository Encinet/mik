package org.encinet.mik.module.communication.tip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TipRendererTest {

    private static final List<Set<TextColor>> HARMONIOUS_PALETTES = List.of(
            colors(0x91D7E3, 0xCAD3F5, 0x8BD5CA, 0x7DC4E4, 0x8AADF4, 0xB7BDF8),
            colors(0xA6DA95, 0xCAD3F5, 0x8BD5CA, 0x91D7E3, 0xB7D89A, 0xEED49F),
            colors(0xC6A0F6, 0xCAD3F5, 0xB7BDF8, 0xF5BDE6, 0xF0C6C6, 0xEE99A0),
            colors(0xF5A97F, 0xCAD3F5, 0xEED49F, 0xF0C6C6, 0xEE99A0, 0xF5BDE6));

    @Test
    void rendersSemanticMarkupWithMultipleColorsAndCommandInteraction() {
        TipTemplate template = new TipMarkupParser().parse(
                "Use <command>/spawn</command> to return to <place>Spawn</place>.");
        Component rendered = new TipRenderer().render(
                "TIP", "Server tip", template, new Random(7));

        assertEquals("TIP | Use /spawn to return to Spawn.",
                PlainTextComponentSerializer.plainText().serialize(rendered));
        Set<TextColor> colors = new HashSet<>();
        collectColors(rendered, colors);
        assertTrue(colors.size() >= 3);
        assertTrue(hasSuggestedCommand(rendered));
    }

    @Test
    void eachDeliveryKeepsAllRandomColorsWithinOneCuratedPalette() {
        TipTemplate template = new TipMarkupParser().parse(
                "Use <command>/spawn</command>, press <key>F</key> at "
                        + "<place>Spawn</place>, then wait <value>3s</value>.");

        for (int seed = 0; seed < 256; seed++) {
            Component rendered = new TipRenderer().render(
                    "TIP", "Server tip", template, new Random(seed));
            Set<TextColor> colors = new HashSet<>();
            collectColors(rendered, colors);
            colors.remove(NamedTextColor.DARK_GRAY);

            assertTrue(HARMONIOUS_PALETTES.stream().anyMatch(it -> it.containsAll(colors)),
                    () -> "render mixed unrelated palette colors: " + colors);
        }
    }

    private static void collectColors(Component component, Set<TextColor> colors) {
        if (component.color() != null) colors.add(component.color());
        component.children().forEach(child -> collectColors(child, colors));
    }

    private static boolean hasSuggestedCommand(Component component) {
        ClickEvent event = component.clickEvent();
        if (event != null && event.action() == ClickEvent.Action.SUGGEST_COMMAND) {
            return true;
        }
        return component.children().stream().anyMatch(TipRendererTest::hasSuggestedCommand);
    }

    private static Set<TextColor> colors(int... values) {
        Set<TextColor> result = new HashSet<>();
        for (int value : values) result.add(TextColor.color(value));
        return Set.copyOf(result);
    }
}
