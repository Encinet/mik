package org.encinet.mik.feature.community.chat.render;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatItemSnapshot;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.shared.util.TextBadge;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.PlayerIdentityComponent;
import org.encinet.mik.feature.player.profile.PlayerSource;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;

import java.time.ZoneId;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ChatMessageFormatter {
    public record PlayerView(String name, Component interactiveName, PlayerNameTagRenderer.Style style, boolean bedrock) {
        PlayerIdentityComponent withDetails(Component details) {
            return style.render(interactiveName.hoverEvent(HoverEvent.showText(details)));
        }
    }

    public record Presentation(PlayerView author, PlayerView recipient,
                               Function<ChatItemSnapshot, HoverEvent<HoverEvent.ShowItem>> items) {
        public TextColor authorColor() {
            return author == null ? NamedTextColor.WHITE : author.style().usernameColor();
        }
    }

    private static final int PRIVATE_IDENTITY_MAX_WIDTH = 240;

    private static final ZoneId CHAT_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss zzzz");

    private final LanguageService languageService;
    private final PlayerSourceRenderer sources;
    private final MinecraftChatContentRenderer contentRenderer = new MinecraftChatContentRenderer();

    public ChatMessageFormatter(LanguageService languageService, PlayerSourceRenderer sources) {
        this.languageService = languageService;
        this.sources = sources;
    }

    public Component message(ChatMessage message, Presentation presentation, Language viewer, Key repeatIdentifier, String original) {
        var content = message.content().localized(key -> localized(viewer, key));
        return message(message, presentation, viewer, render(content, presentation, viewer, (_, _) -> {}), repeatIdentifier,
                original == null ? content.plainText() : original);
    }

    /** The native event may supply a body edited by another plugin. */
    public Component message(ChatMessage message, Presentation presentation, Language viewer,
            Component body, Key repeatIdentifier, String copyText) {
        ChatSender sender = message.sender();
        PlayerView author = presentation.author();
        PlayerSource source = switch (message.origin()) {
            case ChatOrigin.Player.WEB -> sources.web(viewer);
            case ChatOrigin.Social social -> socialSource(social.platform());
            case ChatOrigin.Player.MINECRAFT -> sources.game(author.bedrock(), viewer);
        };
        Component details = message.origin() instanceof ChatOrigin.Social
                ? externalSenderDetails(source.label(), sender, author == null ? "" : author.name(), viewer)
                : playerDetails(sender.minecraftId().orElseThrow(), author.name(), viewer);
        PlayerIdentityComponent identity = author == null
                ? new PlayerIdentityComponent(Component.text(sender.displayName(), NamedTextColor.WHITE)
                        .hoverEvent(HoverEvent.showText(details)))
                : author.withDetails(details);
        Component marker = channelMarker(message.channel(), viewer);
        PlayerView recipient = presentation.recipient();
        Component header = message.channel() == ChatChannel.PRIVATE
                ? privateIdentityBody(identity, source,
                        recipient.withDetails(playerDetails(message.targetId(), recipient.name(), viewer)))
                : identity.chat(source);
        return channelMessage(marker,
                header,
                standardSeparator(), body, copyText,
                localized(viewer, Message.CHAT_COPY_HOVER), repeatIdentifier, message.createdAt());
    }

    public Component content(ChatContent content, Presentation presentation, Language viewer, BiConsumer<Component, ChatNode> renderedNodes) {
        return render(content.localized(message -> localized(viewer, message)), presentation, viewer, renderedNodes);
    }

    private Component render(ChatContent content, Presentation presentation, Language viewer, BiConsumer<Component, ChatNode> renderedNodes) {
        return contentRenderer.render(content, new MinecraftChatContentRenderer.Context(
                localized(viewer, Message.CHAT_ITEM_EMPTY_HOVER),
                localized(viewer, Message.CHAT_MENTION_ALL_HOVER), presentation.items()), renderedNodes);
    }

    private static PlayerSource socialSource(String platform) {
        return switch (platform) {
            case "qq" -> new PlayerSource("QQ", TextColor.color(0x12B7F5));
            case "matrix" -> new PlayerSource("Matrix", TextColor.color(0x0DBD8B));
            default -> new PlayerSource(platform, NamedTextColor.WHITE);
        };
    }

    private Component externalSenderDetails(
            String platformName, ChatSender sender,
            String visiblePlayerName, Language viewer
    ) {
        TextComponent.Builder details = Component.text()
                .append(Component.text(platformName, NamedTextColor.AQUA));
        sender.externalIdentity().ifPresent(identity -> details
                .append(Component.newline())
                .append(detailLine(viewer, Message.CHAT_SOCIAL_NAME_LABEL,
                        identity.displayName().isBlank() ? sender.displayName() : identity.displayName(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(detailLine(viewer, Message.CHAT_SOCIAL_ACCOUNT_LABEL,
                        identity.key().subject(), NamedTextColor.GRAY)));
        details.append(Component.newline()).append(Component.newline());
        if (sender.minecraftId().isPresent()) {
            details.append(detailLine(viewer,
                            sender.externalIdentity().isPresent() ? Message.CHAT_SOCIAL_BOUND_PLAYER_LABEL : Message.SOCIAL_PROFILE_PLAYER_LABEL,
                            visiblePlayerName, NamedTextColor.WHITE))
                    .append(Component.newline())
                    .append(detailLine(viewer, Message.SOCIAL_PROFILE_UUID_LABEL,
                            sender.minecraftId().orElseThrow().toString(),
                            NamedTextColor.DARK_GRAY));
        } else {
            details.append(Component.text(localized(
                    viewer, Message.CHAT_SOCIAL_UNBOUND), NamedTextColor.YELLOW));
        }
        return details.build();
    }

    private Component channelMarker(ChatChannel channel, Language viewer) {
        return switch (channel) {
            case PUBLIC -> Component.empty();
            case STAFF -> TextBadge.prefix(localized(viewer, Message.CHAT_STAFF_LABEL), NamedTextColor.GOLD);
            case PRIVATE -> TextBadge.prefix(localized(viewer, Message.CHAT_PRIVATE_LABEL), NamedTextColor.LIGHT_PURPLE);
        };
    }

    static Component channelMessage(
            Component marker,
            Component body,
            Component separator,
            Component message,
            String copyText,
            String copyHint,
            Key repeatIdentifier,
            Instant createdAt
    ) {
        return Component.text()
                .append(channelPrefix(marker, body))
                .append(separator)
                .append(timeHoveredMessage(message, copyText, copyHint, createdAt))
                .append(repeatSuffix(repeatIdentifier))
                .build();
    }

    private static Component standardSeparator() {
        return Component.text(" »", NamedTextColor.GOLD);
    }

    static Component repeatSuffix(Key repeatIdentifier) {
        return repeatIdentifier == null ? Component.empty()
                : Component.text(" [+1]", NamedTextColor.GRAY).clickEvent(ClickEvent.custom(repeatIdentifier));
    }

    private Component playerDetails(UUID id, String name, Language viewer) {
        return Component.text()
                .append(Component.text(localized(viewer,
                                Message.SOCIAL_TITLE_PLAYER_PROFILE),
                        NamedTextColor.AQUA))
                .append(Component.newline())
                .append(detailLine(viewer, Message.SOCIAL_PROFILE_PLAYER_LABEL,
                        name, NamedTextColor.WHITE))
                .append(Component.newline())
                .append(detailLine(viewer, Message.SOCIAL_PROFILE_UUID_LABEL,
                        id.toString(), NamedTextColor.DARK_GRAY))
                .append(Component.newline()).append(Component.newline())
                .append(Component.text(localized(viewer,
                                Message.CHAT_PLAYER_HOVER),
                        NamedTextColor.YELLOW))
                .build();
    }

    private Component detailLine(
            Language viewer,
            Message label,
            String value,
            NamedTextColor valueColor
    ) {
        return Component.text()
                .append(Component.text(localized(viewer, label) + ": ",
                        NamedTextColor.GRAY))
                .append(Component.text(value, valueColor))
                .build();
    }

    private String localized(Language viewer, Message message) {
        return languageService.t(viewer, message);
    }

    static Component channelPrefix(Component marker, Component body) {
        return Component.text()
                .append(marker)
                .append(body)
                .build();
    }

    static Component privateIdentityBody(
            PlayerIdentityComponent sender, PlayerSource source, PlayerIdentityComponent target
    ) {
        Component full = privateBody(sender.chat(source), target.component());
        String visible = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(full);
        if (org.encinet.mik.shared.menu.ui.UiFontMetrics.width(visible) <= PRIVATE_IDENTITY_MAX_WIDTH) {
            return full;
        }
        return privateBody(new PlayerIdentityComponent(sender.undecorated()).chat(source), target.undecorated());
    }

    static Component privateBody(Component senderIdentity, Component target) {
        return Component.text()
                .append(senderIdentity)
                .append(Component.text(" -> ", NamedTextColor.DARK_GRAY))
                .append(target)
                .build();
    }

    private static Component timeHoveredMessage(Component message, String copyText, String copyHint, Instant createdAt) {
        Component hover = Component.text(createdAt.atZone(CHAT_TIME_ZONE).format(CHAT_TIME_FORMAT), NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text(copyHint, NamedTextColor.YELLOW));
        return Component.space()
                .append(message.colorIfAbsent(NamedTextColor.WHITE))
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.copyToClipboard(copyText));
    }

}
