package org.encinet.mik.module.chat.pipeline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Safe compatibility import for MiniMessage and final Paper chat components. */
public final class AdventureComponentImporter {
    public ChatContent importComponent(Component component) {
        List<ChatNode> nodes = new ArrayList<>();
        append(component, ResolvedStyle.empty(), nodes);
        return new ChatContent(nodes);
    }

    private void append(
            Component component,
            ResolvedStyle inherited,
            List<ChatNode> nodes
    ) {
        ResolvedStyle style = inherited.resolve(component);
        String ownText = component instanceof TextComponent text
                ? text.content()
                : PlainTextComponentSerializer.plainText().serialize(
                component.children(List.of()));
        if (!ownText.isEmpty()) {
            nodes.add(style.node(ownText));
        }
        component.children().forEach(child -> append(child, style, nodes));
    }

    private record ResolvedStyle(
            ChatStyle style,
            String link
    ) {
        static ResolvedStyle empty() {
            return new ResolvedStyle(ChatStyle.EMPTY, "");
        }

        ResolvedStyle resolve(Component component) {
            TextColor componentColor = component.color();
            ClickEvent<?> click = component.clickEvent();
            String resolvedLink = click == null
                    ? link : click.action() == ClickEvent.Action.OPEN_URL
                    && click.payload() instanceof ClickEvent.Payload.Text text
                    ? text.value() : "";
            Integer resolvedColor = style.color();
            if (componentColor != null) {
                resolvedColor = componentColor.value();
            }
            return new ResolvedStyle(new ChatStyle(
                    resolvedColor,
                    decoration(component, TextDecoration.BOLD, style.bold()),
                    decoration(component, TextDecoration.ITALIC, style.italic()),
                    decoration(component, TextDecoration.UNDERLINED,
                            style.underlined()),
                    decoration(component, TextDecoration.STRIKETHROUGH,
                            style.strikethrough())), resolvedLink);
        }

        ChatNode node(String text) {
            if (!link.isEmpty()) {
                try {
                    return new ChatNode.Link(text, URI.create(link), style);
                } catch (IllegalArgumentException ignored) {
                    // Unsafe or malformed actions are represented only by visible text.
                }
            }
            return new ChatNode.Text(text, style);
        }

        private static boolean decoration(
                Component component,
                TextDecoration decoration,
                boolean inherited
        ) {
            return switch (component.decoration(decoration)) {
                case TRUE -> true;
                case FALSE -> false;
                case NOT_SET -> inherited;
            };
        }
    }
}
