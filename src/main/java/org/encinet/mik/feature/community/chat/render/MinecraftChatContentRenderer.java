package org.encinet.mik.feature.community.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.net.URI;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.encinet.mik.feature.player.profile.PlayerNameRenderer;
import org.encinet.mik.feature.community.chat.link.ChatResourceLinks;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatItemSnapshot;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatStyle;

/** Renders semantic content into Minecraft Adventure interactions. */
public final class MinecraftChatContentRenderer {
    private static final Key PLAYER_ENTITY_TYPE = Key.key("minecraft", "player");
    private static final ChatStyle PLAYER_MENTION = new ChatStyle(0xFFD166, null, false, false, false, false);
    private static final ChatStyle BROADCAST_MENTION = new ChatStyle(0xFFAA00, null, true, false, false, false);

    public Component render(ChatContent content, Context context) {
        return render(content, context, (_, _) -> {});
    }

    public Component render(ChatContent content, Context context, BiConsumer<Component, ChatNode> renderedNodes) {
        TextComponent.Builder result = Component.text();
        for (ChatNode node : content.nodes()) {
            Component rendered = render(node, context);
            renderedNodes.accept(rendered, node);
            result.append(rendered);
        }
        return result.build().colorIfAbsent(NamedTextColor.WHITE);
    }

    private Component render(ChatNode node, Context context) {
        Component rendered;
        if (node instanceof ChatNode.Reference reference) {
            rendered = renderReference(reference, context);
        } else if (node instanceof ChatNode.PendingMention mention) {
            rendered = styledRuns(mention.runs(), ChatStyle.EMPTY);
        } else if (node instanceof ChatNode.Item item) {
            rendered = styled(item.fallback(), item.style());
            if (item.item().isPresent()) {
                rendered = rendered.hoverEvent(context.items().apply(item.item().orElseThrow()));
            } else if (!context.emptyItemHover().isBlank()) {
                rendered = rendered.hoverEvent(HoverEvent.showText(Component.text(
                        context.emptyItemHover(), NamedTextColor.GRAY)));
            }
        } else {
            rendered = styled(node.visibleText(), node.style());
        }
        return rendered;
    }

    private Component renderReference(ChatNode.Reference reference, Context context) {
        return switch (reference.target()) {
            case ChatNode.Reference.Link link -> {
                URI uri = link.uri();
                ClickEvent<?> click = "mailto".equalsIgnoreCase(uri.getScheme())
                        ? ClickEvent.copyToClipboard(uri.getRawSchemeSpecificPart())
                        : ClickEvent.openUrl(uri.toString());
                yield linkLabel(reference, link).clickEvent(click)
                        .hoverEvent(HoverEvent.showText(Component.text(uri.toString(), NamedTextColor.GRAY)));
            }
            case ChatNode.Reference.Person person -> PlayerNameRenderer.clickablePlayerName(
                    styledRuns(reference.runs(), PLAYER_MENTION)
                            .hoverEvent(HoverEvent.showEntity(PLAYER_ENTITY_TYPE, person.playerId(),
                                    Component.text(person.playerName()))), person.playerName());
            case ChatNode.Reference.Audience _ -> {
                var label = styledRuns(reference.runs(), BROADCAST_MENTION);
                yield context.broadcastMentionHover().isBlank() ? label : label.hoverEvent(HoverEvent.showText(
                        Component.text(context.broadcastMentionHover(), NamedTextColor.GRAY)));
            }
        };
    }

    private Component styledRuns(java.util.List<ChatNode.Text> runs, ChatStyle base) {
        if (runs.size() == 1) return styled(runs.getFirst().text(), combined(runs.getFirst().style(), base));
        var result = Component.text();
        for (var run : runs) result.append(styled(run.text(), combined(run.style(), base)));
        return result.build();
    }

    private Component linkLabel(ChatNode.Reference reference, ChatNode.Reference.Link link) {
        boolean decorate = link.automatic();
        ChatStyle palette = decorate ? automaticStyle(link.uri()) : ChatStyle.EMPTY;
        if (reference.runs().size() == 1) {
            ChatNode.Text run = reference.runs().getFirst();
            String label = decorate ? "[" + run.text() + "]" : run.text();
            return styled(label, combined(run.style(), palette));
        }
        TextComponent.Builder result = Component.text();
        if (decorate) result.append(styled("[", palette));
        for (ChatNode.Text run : reference.runs()) {
            result.append(styled(run.text(), combined(run.style(), palette)));
        }
        if (decorate) result.append(styled("]", palette));
        return result.build();
    }

    private static ChatStyle combined(ChatStyle style, ChatStyle base) {
        return new ChatStyle(style.color() == null ? base.color() : style.color(),
                style.color() == null ? base.gradientEndColor() : style.gradientEndColor(),
                style.bold() || base.bold(), style.italic() || base.italic(),
                style.underlined() || base.underlined(), style.strikethrough() || base.strikethrough());
    }

    private static ChatStyle automaticStyle(URI uri) {
        return switch (ChatResourceLinks.resource(uri)) {
            case BILIBILI -> gradient(0x00AEEC, 0xFB7299);
            case GITHUB -> gradient(0x58A6FF, 0xA371F7);
            case MINECRAFT_WIKI -> gradient(0x55FF55, 0xFFAA00);
            case MOJIRA -> gradient(0xFF5555, 0xFFAA00);
            case MATRIX -> gradient(0xFFFFFF, 0xAAAAAA);
            case GENERIC -> "mailto".equalsIgnoreCase(uri.getScheme())
                    ? gradient(0x55FFFF, 0x5555FF) : gradient(0x4EA5FF, 0x79C0FF);
        };
    }

    private static ChatStyle gradient(int start, int end) {
        return new ChatStyle(start, end, false, false, false, false);
    }

    private Component styled(String text, ChatStyle style) {
        Component result;
        if (style.gradientEndColor() != null) {
            TextComponent.Builder gradient = Component.text();
            int[] points = text.codePoints().toArray();
            TextColor start = TextColor.color(style.color());
            TextColor end = TextColor.color(style.gradientEndColor());
            double step = points.length <= 1 ? 0 : 1.0 / (points.length - 1);
            for (int index = 0; index < points.length; index++) {
                gradient.append(Component.text(new String(points, index, 1),
                        TextColor.lerp((float) (index * step), start, end)));
            }
            result = gradient.build();
        } else {
            result = Component.text(text);
        }
        if (style.color() != null && style.gradientEndColor() == null) {
            result = result.color(TextColor.color(style.color()));
        }
        result = result.decoration(TextDecoration.BOLD, style.bold())
                .decoration(TextDecoration.ITALIC, style.italic())
                .decoration(TextDecoration.UNDERLINED, style.underlined())
                .decoration(TextDecoration.STRIKETHROUGH, style.strikethrough());
        return result;
    }

    public record Context(String emptyItemHover, String broadcastMentionHover,
                          Function<ChatItemSnapshot, HoverEvent<HoverEvent.ShowItem>> items) {
        public Context {
            emptyItemHover = java.util.Objects.requireNonNullElse(emptyItemHover, "");
            broadcastMentionHover = java.util.Objects.requireNonNullElse(
                    broadcastMentionHover, "");
        }

        public static Context empty() {
            return new Context("", "", _ -> null);
        }
    }
}
