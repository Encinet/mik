package org.encinet.mik.module.menu;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure conversion from the client-independent menu definition to a Bedrock form model. */
final class BedrockMenuTranslator {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    Menu translate(FloatingMenuDefinition definition, ActionLabels labels) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(labels, "labels");

        String inferredHeadingId = null;
        String title = definition.titleVisible() && hasText(definition.title())
                ? serialize(definition.title()) : "";
        if (title.isEmpty()) {
            for (FloatingMenuDefinition.Entry entry : definition.entries().values()) {
                if (entry.role() != FloatingMenuNodeRole.INFORMATION || !isHeading(entry)) continue;
                title = firstLine(serialize(entry.label()));
                inferredHeadingId = entry.id();
                break;
            }
        }

        List<String> content = new ArrayList<>();
        for (FloatingMenuDecoration decoration : definition.decorations().values()) {
            if (decoration.content() instanceof FloatingMenuDecoration.Text text
                    && hasText(text.text())) {
                content.add(serialize(text.text()));
            }
        }

        List<Option> options = new ArrayList<>();
        for (FloatingMenuDefinition.Entry entry : definition.entries().values()) {
            List<FloatingMenuInteraction> interactions = ordered(entry.triggers());
            if (interactions.isEmpty()) {
                if (!entry.id().equals(inferredHeadingId) && hasText(entry.label())) {
                    content.add(serialize(entry.label()));
                }
                continue;
            }
            String label = serialize(entry.label());
            if (entry.selected()) label = "§a✓ §r" + label;
            String disabledReason = entry.disabledReason() == null
                    ? "" : serialize(entry.disabledReason());
            if (!entry.enabled() && !disabledReason.isEmpty()) {
                label = label + "\n§c" + disabledReason;
            }
            options.add(new Option(entry.id(), label, interactions,
                    entry.enabled(), disabledReason));
        }
        for (FloatingMenuInteraction interaction : FloatingMenuInteraction.values()) {
            if (definition.triggers().containsKey(interaction)) {
                options.add(new Option(null, labels.label(interaction),
                        List.of(interaction), true, ""));
            }
        }

        if (title.isEmpty()) {
            if (!content.isEmpty()) title = firstLine(content.getFirst());
            else if (!options.isEmpty()) title = firstLine(options.getFirst().label());
        }
        return new Menu(title, String.join("\n\n", content), options);
    }

    private static boolean isHeading(FloatingMenuDefinition.Entry entry) {
        String id = entry.id().toLowerCase(java.util.Locale.ROOT);
        String region = entry.region().toLowerCase(java.util.Locale.ROOT);
        return id.contains("title") || id.contains("heading")
                || region.contains("title") || region.contains("heading");
    }

    private static List<FloatingMenuInteraction> ordered(
            Map<FloatingMenuInteraction, FloatingMenuAction> triggers) {
        List<FloatingMenuInteraction> interactions = new ArrayList<>();
        for (FloatingMenuInteraction interaction : FloatingMenuInteraction.values()) {
            if (triggers.containsKey(interaction)) interactions.add(interaction);
        }
        return List.copyOf(interactions);
    }

    private static boolean hasText(Component component) {
        return !PLAIN.serialize(component).isBlank();
    }

    private static String serialize(Component component) {
        return LEGACY.serialize(component).strip();
    }

    static String firstLine(String value) {
        int newline = value.indexOf('\n');
        return (newline < 0 ? value : value.substring(0, newline)).strip();
    }

    record Menu(String title, String content, List<Option> options) {
        Menu {
            title = Objects.requireNonNull(title, "title");
            content = Objects.requireNonNull(content, "content");
            options = List.copyOf(Objects.requireNonNull(options, "options"));
        }
    }

    record Option(String elementId, String label,
                  List<FloatingMenuInteraction> interactions,
                  boolean enabled, String disabledReason) {
        Option {
            label = Objects.requireNonNull(label, "label");
            interactions = List.copyOf(Objects.requireNonNull(interactions, "interactions"));
            if (interactions.isEmpty()) {
                throw new IllegalArgumentException("A native form option requires an interaction");
            }
            disabledReason = Objects.requireNonNull(disabledReason, "disabledReason");
        }
    }

    static final class ActionLabels {
        private final Map<FloatingMenuInteraction, String> values;

        ActionLabels(String primary, String secondary, String hotkey,
                     String scrollUp, String scrollDown) {
            EnumMap<FloatingMenuInteraction, String> labels =
                    new EnumMap<>(FloatingMenuInteraction.class);
            labels.put(FloatingMenuInteraction.PRIMARY, primary);
            labels.put(FloatingMenuInteraction.SECONDARY, secondary);
            labels.put(FloatingMenuInteraction.HOTKEY, hotkey);
            labels.put(FloatingMenuInteraction.SCROLL_UP, scrollUp);
            labels.put(FloatingMenuInteraction.SCROLL_DOWN, scrollDown);
            labels.replaceAll((key, value) -> Objects.requireNonNull(value, key.name()));
            values = Map.copyOf(labels);
        }

        String label(FloatingMenuInteraction interaction) {
            return values.get(Objects.requireNonNull(interaction, "interaction"));
        }
    }
}
