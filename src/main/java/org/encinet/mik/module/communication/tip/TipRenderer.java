package org.encinet.mik.module.communication.tip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Assigns a fresh readable palette to semantic tip markup for each delivery. */
public final class TipRenderer {

    /**
     * Hand-tuned pastel groups for dark chat backgrounds. A delivery chooses one complete
     * group instead of mixing unrelated random hues, so its label and semantic accents
     * always belong to the same visual family.
     */
    private static final List<Palette> PALETTES = List.of(
            new Palette(color(0x91D7E3), color(0xCAD3F5), List.of(
                    color(0x8BD5CA), color(0x7DC4E4), color(0x8AADF4), color(0xB7BDF8))),
            new Palette(color(0xA6DA95), color(0xCAD3F5), List.of(
                    color(0x8BD5CA), color(0x91D7E3), color(0xB7D89A), color(0xEED49F))),
            new Palette(color(0xC6A0F6), color(0xCAD3F5), List.of(
                    color(0xB7BDF8), color(0xF5BDE6), color(0xF0C6C6), color(0xEE99A0))),
            new Palette(color(0xF5A97F), color(0xCAD3F5), List.of(
                    color(0xEED49F), color(0xF0C6C6), color(0xEE99A0), color(0xF5BDE6))));

    public Component render(String label, String hover,
                            TipTemplate template, RandomGenerator random) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("Tip label is blank");
        if (hover == null || hover.isBlank()) throw new IllegalArgumentException("Tip hover is blank");
        if (template == null || random == null) throw new NullPointerException();

        Palette palette = PALETTES.get(random.nextInt(PALETTES.size()));
        List<TextColor> accents = shuffledAccents(palette.accents(), random);
        Map<String, TextColor> semanticColors = semanticColors(template, accents);
        Component body = renderNodes(template.nodes(), palette.base(), semanticColors);
        return Component.text()
                .append(Component.text(label, palette.label(), TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.GRAY))))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(body)
                .build();
    }

    private static Component renderNodes(List<TipTemplate.Node> nodes,
                                         TextColor inherited,
                                         Map<String, TextColor> colors) {
        Component result = Component.empty();
        for (TipTemplate.Node node : nodes) {
            Component rendered = switch (node) {
                case TipTemplate.TextNode text -> Component.text(text.text(), inherited);
                case TipTemplate.ElementNode element -> renderElement(element, colors);
            };
            result = result.append(rendered);
        }
        return result;
    }

    private static Component renderElement(TipTemplate.ElementNode element,
                                           Map<String, TextColor> colors) {
        TextColor color = colors.getOrDefault(element.semantic(), NamedTextColor.WHITE);
        Component rendered = renderNodes(element.children(), color, colors);
        if (switch (element.semantic()) {
            case "command", "code", "key", "strong" -> true;
            default -> false;
        }) {
            rendered = rendered.decorate(TextDecoration.BOLD);
        }
        if (element.semantic().equals("emphasis") || element.semantic().equals("mark")) {
            rendered = rendered.decorate(TextDecoration.UNDERLINED);
        }
        String plain = TipTemplate.plainText(element.children()).strip();
        if (element.semantic().equals("command") && plain.startsWith("/")) {
            rendered = rendered.clickEvent(ClickEvent.suggestCommand(plain));
        } else if (element.semantic().equals("site")) {
            String url = safeSite(plain);
            if (url != null) rendered = rendered.clickEvent(ClickEvent.openUrl(url));
        }
        return rendered;
    }

    private static Map<String, TextColor> semanticColors(
            TipTemplate template, List<TextColor> palette) {
        LinkedHashSet<String> semantics = new LinkedHashSet<>();
        collectSemantics(template.nodes(), semantics);
        Map<String, TextColor> result = new LinkedHashMap<>();
        int offset = 0;
        for (String semantic : semantics) {
            result.put(semantic, palette.get(offset++ % palette.size()));
        }
        return Map.copyOf(result);
    }

    private static void collectSemantics(List<TipTemplate.Node> nodes, java.util.Set<String> target) {
        for (TipTemplate.Node node : nodes) {
            if (node instanceof TipTemplate.ElementNode element) {
                target.add(element.semantic());
                collectSemantics(element.children(), target);
            }
        }
    }

    private static List<TextColor> shuffledAccents(
            List<TextColor> accents, RandomGenerator random) {
        List<TextColor> result = new ArrayList<>(accents);
        for (int index = result.size() - 1; index > 0; index--) {
            int swap = random.nextInt(index + 1);
            TextColor value = result.get(index);
            result.set(index, result.get(swap));
            result.set(swap, value);
        }
        return result;
    }

    private static TextColor color(int value) {
        return TextColor.color(value);
    }

    private static String safeSite(String input) {
        if (input == null || input.isBlank() || input.indexOf(' ') >= 0) return null;
        String value = input.contains("://") ? input : "https://" + input;
        try {
            URI uri = URI.create(value);
            if ((uri.getScheme().equalsIgnoreCase("https")
                    || uri.getScheme().equalsIgnoreCase("http"))
                    && uri.getHost() != null) {
                return uri.toString();
            }
        } catch (IllegalArgumentException ignored) {
            // A malformed site remains colored text without a click action.
        }
        return null;
    }

    private record Palette(TextColor label, TextColor base, List<TextColor> accents) {
        private Palette {
            accents = List.copyOf(accents);
            if (accents.isEmpty()) throw new IllegalArgumentException("Palette has no accents");
        }
    }
}
