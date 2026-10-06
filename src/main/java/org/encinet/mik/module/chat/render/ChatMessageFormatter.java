package org.encinet.mik.module.chat.render;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.encinet.mik.module.chat.ChatDisplayRenderer;
import org.encinet.mik.module.chat.model.ChatSender;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.player.identity.PlayerIdentityComponent;
import org.encinet.mik.module.player.identity.PlayerIdentityRenderer;
import org.encinet.mik.module.player.identity.PlayerNameTag;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class ChatMessageFormatter {

    private static final ZoneId CHAT_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss zzzz");

    private final LanguageService languageService;
    private final PlayerIdentityRenderer playerIdentities;

    public ChatMessageFormatter(LanguageService languageService,
                                PlayerIdentityRenderer playerIdentities) {
        this.languageService = languageService;
        this.playerIdentities = playerIdentities;
    }

    public Component publicMessage(Player sender, Audience viewer, Component message, String copyText, String repeatCommand) {
        PlayerIdentityComponent identity = identity(sender, viewer);
        return channelMessage(ChannelMarker.empty(), viewer,
                identity.component(), standardSeparator(), message, copyText, repeatCommand);
    }

    /** Renders external public chat through the same interactive path as player chat. */
    public Component externalPublicMessage(
            String platformName,
            Component senderIdentity,
            Audience viewer,
            Component message,
            String copyText
    ) {
        return externalPublicMessage(platformName, senderIdentity, message, copyText,
                copyHint(viewer));
    }

    /** Resolves the linked Minecraft identity and renders its platform details for one viewer. */
    public Component externalPublicMessage(
            String platformName,
            ChatSender sender,
            Audience viewer,
            Component message,
            String copyText
    ) {
        return externalPublicMessage(platformName,
                externalSenderIdentity(platformName, sender, viewer),
                viewer, message, copyText);
    }

    private Component externalSenderIdentity(
            String platformName, ChatSender sender, Audience viewer
    ) {
        Player onlinePlayer = sender.minecraftId().map(Bukkit::getPlayer).orElse(null);
        if (sender.minecraftId().isPresent()) {
            UUID playerId = sender.minecraftId().orElseThrow();
            String visiblePlayerName = onlinePlayer == null
                    ? sender.minecraftName() : onlinePlayer.getName();
            OfflinePlayer identityPlayer = onlinePlayer == null
                    ? Bukkit.getOfflinePlayer(playerId) : onlinePlayer;
            Component baseName = onlinePlayer == null
                    ? ChatDisplayRenderer.clickablePlayerName(
                            Component.text(visiblePlayerName, NamedTextColor.WHITE),
                            visiblePlayerName)
                    : ChatDisplayRenderer.playerName(onlinePlayer);
            Component details = externalSenderDetails(platformName, sender,
                    visiblePlayerName, viewer);
            return externalPlayerIdentity(identityPlayer, viewer, baseName,
                    new PlayerNameTag(sender.prefix(), sender.suffix()), details);
        }
        return Component.text(sender.displayName(), NamedTextColor.WHITE)
                .hoverEvent(HoverEvent.showText(
                        externalSenderDetails(platformName, sender, null, viewer)));
    }

    private Component externalSenderDetails(
            String platformName, ChatSender sender,
            String visiblePlayerName, Audience viewer
    ) {
        TextComponent.Builder details = Component.text()
                .append(Component.text(platformName, NamedTextColor.AQUA))
                .append(Component.newline())
                .append(detailLine(viewer, Message.CHAT_SOCIAL_NAME_LABEL,
                        sender.externalIdentity()
                                .map(ExternalIdentity::displayName)
                                .filter(value -> !value.isBlank())
                                .orElse(sender.displayName()),
                        NamedTextColor.WHITE));
        sender.externalIdentity().map(ExternalIdentity::key).ifPresent(key -> details
                .append(Component.newline())
                .append(detailLine(viewer, Message.CHAT_SOCIAL_ACCOUNT_LABEL,
                        key.subject(), NamedTextColor.GRAY)));
        details.append(Component.newline()).append(Component.newline());
        if (sender.minecraftId().isPresent()) {
            details.append(detailLine(viewer,
                            Message.CHAT_SOCIAL_BOUND_PLAYER_LABEL,
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

    static Component externalPublicMessage(
            String platformName,
            Component senderIdentity,
            Component message,
            String copyText,
            String copyHint
    ) {
        return channelMessage(ChannelMarker.platform(platformName).component(),
                senderIdentity,
                standardSeparator(),
                message, copyText, copyHint, null);
    }

    /** Reuses the live public-chat identity, including badges, tags and name actions. */
    public Component playerIdentity(Player player, Audience viewer) {
        return playerIdentities.render(player, viewer,
                ChatDisplayRenderer.playerName(player),
                playerNameHover(player, viewer)).component();
    }

    /** Reuses a resolved identity snapshot even when the linked player is offline. */
    public Component playerIdentity(
            OfflinePlayer player,
            Audience viewer,
            Component baseName,
            PlayerNameTag nameTag
    ) {
        return playerIdentities.renderSnapshot(
                player, viewer, baseName, nameTag).component();
    }

    /** Replaces only the visible player's name hover for an external identity. */
    public Component externalPlayerIdentity(
            OfflinePlayer player,
            Audience viewer,
            Component baseName,
            PlayerNameTag nameTag,
            Component externalDetails
    ) {
        return playerIdentities.renderSnapshot(
                player, viewer, baseName, nameTag,
                HoverEvent.showText(externalDetails)).component();
    }

    public Component staffMessage(Player sender, Audience viewer, Component message, String copyText, String repeatCommand) {
        PlayerIdentityComponent identity = identity(sender, viewer);
        return channelMessage(ChannelMarker.text("STAFF", NamedTextColor.GOLD), viewer,
                identity.component(), standardSeparator(), message, copyText, repeatCommand);
    }

    public Component privateMessage(Player sender, Player target, Audience viewer, Component message, String copyText,
                                    String repeatCommand) {
        PlayerIdentityComponent identity = identity(sender, viewer);
        return channelMessage(ChannelMarker.text(privateLabel(viewer), NamedTextColor.LIGHT_PURPLE), viewer,
                privateBody(identity.component(), playerName(target, viewer)),
                standardSeparator(), message, copyText, repeatCommand);
    }

    public Component privatePreview(Player sender, String targetName, Component message, String copyText) {
        String username = targetName == null ? "?" : targetName;
        Component target = ChatDisplayRenderer.clickablePlayerName(
                Component.text(username, NamedTextColor.WHITE), username);
        PlayerIdentityComponent identity = identity(sender, sender);
        return channelMessage(ChannelMarker.text(privateLabel(sender), NamedTextColor.LIGHT_PURPLE), sender,
                privateBody(identity.component(), target), standardSeparator(),
                message, copyText, null);
    }

    private Component channelMessage(ChannelMarker marker, Audience viewer,
                                     Component body, Component separator, Component message,
                                     String copyText, String repeatCommand) {
        return channelMessage(marker.component(), body, separator, message,
                copyText, copyHint(viewer), repeatCommand);
    }

    static Component channelMessage(
            Component marker,
            Component body,
            Component separator,
            Component message,
            String copyText,
            String copyHint,
            String repeatCommand
    ) {
        return Component.text()
                .append(channelPrefix(marker, body))
                .append(separator)
                .append(timeHoveredMessage(message, copyText, copyHint))
                .append(repeatSuffix(repeatCommand))
                .build();
    }

    private static Component standardSeparator() {
        return Component.text(" »", NamedTextColor.GOLD);
    }

    static Component repeatSuffix(String repeatCommand) {
        return repeatCommand == null ? Component.empty()
                : Component.text(" [+1]", NamedTextColor.GRAY).clickEvent(ClickEvent.runCommand(repeatCommand));
    }

    private PlayerIdentityComponent identity(Player sender, Audience viewer) {
        return playerIdentities.render(sender, viewer,
                ChatDisplayRenderer.playerName(sender),
                playerNameHover(sender, viewer));
    }

    private Component playerName(Player subject, Audience viewer) {
        return ChatDisplayRenderer.playerName(subject)
                .hoverEvent(playerNameHover(subject, viewer));
    }

    private HoverEvent<Component> playerNameHover(
            Player subject,
            Audience viewer
    ) {
        Component details = Component.text()
                .append(Component.text(localized(viewer,
                                Message.SOCIAL_TITLE_PLAYER_PROFILE),
                        NamedTextColor.AQUA))
                .append(Component.newline())
                .append(detailLine(viewer, Message.SOCIAL_PROFILE_PLAYER_LABEL,
                        subject.getName(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(detailLine(viewer, Message.SOCIAL_PROFILE_UUID_LABEL,
                        subject.getUniqueId().toString(), NamedTextColor.DARK_GRAY))
                .append(Component.newline()).append(Component.newline())
                .append(Component.text(localized(viewer,
                                Message.CHAT_PLAYER_HOVER),
                        NamedTextColor.YELLOW))
                .build();
        return HoverEvent.showText(details);
    }

    private Component detailLine(
            Audience viewer,
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

    private String localized(Audience viewer, Message message) {
        return viewer instanceof Player player
                ? languageService.t(player, message)
                : languageService.t(Language.DEFAULT, message);
    }

    static Component channelPrefix(Component marker, Component body) {
        return Component.text()
                .append(marker)
                .append(body)
                .build();
    }

    static Component privateBody(Component senderIdentity, Component target) {
        return Component.text()
                .append(senderIdentity)
                .append(Component.text(" -> ", NamedTextColor.DARK_GRAY))
                .append(target)
                .build();
    }

    private static Component timeHoveredMessage(Component message, String copyText, String copyHint) {
        Component hover = Component.text(ZonedDateTime.now(CHAT_TIME_ZONE).format(CHAT_TIME_FORMAT), NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text(copyHint, NamedTextColor.YELLOW));
        return Component.space()
                .append(message.colorIfAbsent(NamedTextColor.WHITE))
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.copyToClipboard(copyText));
    }

    private String privateLabel(Audience viewer) {
        if (viewer instanceof Player player) {
            return languageService.t(player, Message.CHAT_PRIVATE_LABEL);
        }
        return languageService.t(Language.DEFAULT, Message.CHAT_PRIVATE_LABEL);
    }

    private String copyHint(Audience viewer) {
        if (viewer instanceof Player player) {
            return languageService.t(player, Message.CHAT_COPY_HOVER);
        }
        return languageService.t(Language.DEFAULT, Message.CHAT_COPY_HOVER);
    }

    private record ChannelMarker(String label, NamedTextColor color) {
        static ChannelMarker empty() {
            return new ChannelMarker(null, NamedTextColor.WHITE);
        }

        static ChannelMarker text(String label, NamedTextColor color) {
            return new ChannelMarker(label, color);
        }

        static ChannelMarker platform(String label) {
            return new ChannelMarker(label, NamedTextColor.AQUA);
        }

        Component component() {
            return label == null || label.isEmpty()
                    ? Component.empty()
                    : Component.text("[" + label + "] ", color);
        }
    }
}
