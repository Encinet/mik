package org.encinet.mik.module.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.encinet.mik.module.chat.ChatDisplayRenderer;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatNode;
import org.encinet.mik.module.chat.model.ChatStyle;

/** Renders semantic content into Minecraft Adventure interactions. */
public final class MinecraftChatContentRenderer {
    private static final Key PLAYER_ENTITY_TYPE = Key.key("minecraft", "player");
    public Component render(ChatContent content, Context context) {
        TextComponent.Builder result = Component.text();
        for (ChatNode node : content.nodes()) {
            result.append(render(node, context));
        }
        return result.build().colorIfAbsent(NamedTextColor.WHITE);
    }

    private Component render(ChatNode node, Context context) {
        Component rendered;
        if (node instanceof ChatNode.Link link) {
            String target = link.target().toString();
            ClickEvent clickEvent = "mailto".equalsIgnoreCase(
                    link.target().getScheme())
                    ? ClickEvent.copyToClipboard(
                    link.target().getRawSchemeSpecificPart())
                    : ClickEvent.openUrl(target);
            rendered = styled(link.label(), link.style())
                    .clickEvent(clickEvent)
                    .hoverEvent(HoverEvent.showText(Component.text(
                            target, NamedTextColor.GRAY)));
        } else if (node instanceof ChatNode.PlayerMention mention) {
            Component name = styled(mention.playerName(), mention.style())
                    .hoverEvent(HoverEvent.showEntity(
                            PLAYER_ENTITY_TYPE, mention.playerId(),
                            Component.text(mention.playerName())));
            Component clickableName = ChatDisplayRenderer.clickablePlayerName(
                    name, mention.playerName());
            if (mention.sourceIdentity().isPresent()) {
                var identity = mention.sourceIdentity().orElseThrow();
                String externalName = identity.displayName().isBlank()
                        ? identity.key().subject() : identity.displayName();
                Component external = styled("@" + externalName, mention.style())
                        .hoverEvent(HoverEvent.showText(Component.text(
                                identity.key().platform() + ": "
                                        + identity.key().subject(),
                                NamedTextColor.GRAY)));
                rendered = external.append(styled("(", mention.style()))
                        .append(clickableName)
                        .append(styled(")", mention.style()));
            } else {
                rendered = styled("@", mention.style()).append(clickableName);
            }
        } else if (node instanceof ChatNode.ExternalMention mention) {
            var identity = mention.identity();
            rendered = styled(mention.visibleText(), mention.style())
                    .hoverEvent(HoverEvent.showText(Component.text(
                            identity.key().platform() + ": "
                                    + identity.key().subject(),
                            NamedTextColor.GRAY)));
        } else if (node instanceof ChatNode.BroadcastMention mention) {
            rendered = styled(mention.label(), mention.style());
            if (!context.broadcastMentionHover().isBlank()) {
                rendered = rendered.hoverEvent(HoverEvent.showText(Component.text(
                        context.broadcastMentionHover(), NamedTextColor.GRAY)));
            }
        } else if (node instanceof ChatNode.Item item) {
            rendered = styled(item.fallback(), item.style());
            if (item.item().isPresent()) {
                try {
                    ItemStack snapshot = ItemStack.deserializeBytes(
                            item.item().orElseThrow().minecraftData());
                    rendered = rendered.hoverEvent(snapshot.asHoverEvent());
                } catch (RuntimeException ignored) {
                    // The stable visible fallback is retained if data is incompatible.
                }
            } else if (!context.emptyItemHover().isBlank()) {
                rendered = rendered.hoverEvent(HoverEvent.showText(Component.text(
                        context.emptyItemHover(), NamedTextColor.GRAY)));
            }
        } else {
            rendered = styled(node.visibleText(), node.style());
        }
        return rendered;
    }

    private Component styled(String text, ChatStyle style) {
        Component result = Component.text(text);
        if (style.color() != null) {
            result = result.color(TextColor.color(style.color()));
        }
        result = result.decoration(TextDecoration.BOLD, style.bold())
                .decoration(TextDecoration.ITALIC, style.italic())
                .decoration(TextDecoration.UNDERLINED, style.underlined())
                .decoration(TextDecoration.STRIKETHROUGH, style.strikethrough());
        return result;
    }

    public record Context(String emptyItemHover, String broadcastMentionHover) {
        public Context {
            emptyItemHover = java.util.Objects.requireNonNullElse(emptyItemHover, "");
            broadcastMentionHover = java.util.Objects.requireNonNullElse(
                    broadcastMentionHover, "");
        }

        public static Context empty() {
            return new Context("", "");
        }
    }
}
