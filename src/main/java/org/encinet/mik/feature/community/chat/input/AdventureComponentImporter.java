package org.encinet.mik.feature.community.chat.input;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Imports the visible Adventure text and the ranges of validated native links. */
public final class AdventureComponentImporter {
    public ChatContent importComponent(Component component) {
        return importComponent(component, Map.of(), false);
    }

    public ChatContent importFinal(Component component, Map<Component, ChatNode> renderedNodes) {
        return importComponent(component, renderedNodes, true);
    }

    private ChatContent importComponent(Component component, Map<Component, ChatNode> renderedNodes, boolean verbatim) {
        Collector collector = new Collector();
        append(component, ResolvedStyle.EMPTY, renderedNodes, collector, verbatim);
        collector.flushLink();
        return new ChatContent(collector.nodes);
    }

    private void append(Component component, ResolvedStyle inherited,
                        Map<Component, ChatNode> renderedNodes, Collector collector, boolean verbatim) {
        ChatNode original = inherited.canReuse() ? renderedNodes.get(component) : null;
        if (original != null) {
            collector.flushLink();
            collector.nodes.add(original);
            return;
        }
        ResolvedStyle resolved = inherited.resolve(component);
        String ownText = component instanceof TextComponent text ? text.content()
                : PlainTextComponentSerializer.plainText().serialize(component.children(List.of()));
        if (!ownText.isEmpty()) {
            collector.text(new ChatNode.Text(ownText, resolved.style, verbatim || resolved.action != null), resolved.action);
        }
        component.children().forEach(child -> append(child, resolved, renderedNodes, collector, verbatim));
    }

    // The same parent click event applies to all of its styled children.
    private record Action(URI link) {
        static Action from(ClickEvent<?> event) {
            if (event.action() == ClickEvent.Action.OPEN_URL
                    && event.payload() instanceof ClickEvent.Payload.Text text) {
                try {
                    return new Action(URI.create(text.value()));
                } catch (IllegalArgumentException ignored) {
                    return new Action(null);
                }
            }
            return new Action(null);
        }
    }

    private static final class Collector {
        private final List<ChatNode> nodes = new ArrayList<>();
        private final List<ChatNode.Text> runs = new ArrayList<>();
        private Action action;

        void text(ChatNode.Text text, Action next) {
            if (next != action) flushLink();
            if (next == null || next.link() == null) {
                nodes.add(text);
                return;
            }
            action = next;
            if (!runs.isEmpty() && runs.getLast().style().equals(text.style())) {
                ChatNode.Text previous = runs.removeLast();
                runs.add(new ChatNode.Text(previous.text() + text.text(), text.style(), true));
            } else {
                runs.add(text);
            }
        }

        void flushLink() {
            if (runs.isEmpty()) return;
            try {
                nodes.add(new ChatNode.Reference(runs, new ChatNode.Reference.Link(action.link(), false)));
            } catch (IllegalArgumentException ignored) {
                nodes.addAll(runs);
            }
            runs.clear();
            action = null;
        }
    }

    private record ResolvedStyle(ChatStyle style, Action action, boolean extraStyle) {
        private static final ResolvedStyle EMPTY = new ResolvedStyle(ChatStyle.EMPTY, null, false);

        boolean canReuse() {
            return action == null && !extraStyle && (style.equals(ChatStyle.EMPTY)
                    || style.equals(ChatStyle.EMPTY.withColor(NamedTextColor.WHITE.value())));
        }

        ResolvedStyle resolve(Component component) {
            var color = component.color();
            var click = component.clickEvent();
            return new ResolvedStyle(new ChatStyle(
                    color == null ? style.color() : Integer.valueOf(color.value()), null,
                    decoration(component, TextDecoration.BOLD, style.bold()),
                    decoration(component, TextDecoration.ITALIC, style.italic()),
                    decoration(component, TextDecoration.UNDERLINED, style.underlined()),
                    decoration(component, TextDecoration.STRIKETHROUGH, style.strikethrough())),
                    click == null ? action : Action.from(click),
                    extraStyle || component.hoverEvent() != null || component.font() != null
                            || component.insertion() != null
                            || component.decoration(TextDecoration.OBFUSCATED) != TextDecoration.State.NOT_SET);
        }

        private static boolean decoration(Component component, TextDecoration decoration, boolean inherited) {
            return switch (component.decoration(decoration)) {
                case TRUE -> true;
                case FALSE -> false;
                case NOT_SET -> inherited;
            };
        }
    }
}
