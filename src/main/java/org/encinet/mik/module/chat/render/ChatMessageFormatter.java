package org.encinet.mik.module.chat.render;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.encinet.mik.module.chat.ChatDisplayRenderer;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.player.identity.PlayerIdentityComponent;
import org.encinet.mik.module.player.identity.PlayerIdentityRenderer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

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
                identity.component(), message, copyText, repeatCommand);
    }

    public Component staffMessage(Player sender, Audience viewer, Component message, String copyText, String repeatCommand) {
        PlayerIdentityComponent identity = identity(sender, viewer);
        return channelMessage(ChannelMarker.text("STAFF", NamedTextColor.GOLD), viewer,
                identity.component(), message, copyText, repeatCommand);
    }

    public Component privateMessage(Player sender, Player target, Audience viewer, Component message, String copyText,
                                    String repeatCommand) {
        PlayerIdentityComponent identity = identity(sender, viewer);
        return channelMessage(ChannelMarker.text(privateLabel(viewer), NamedTextColor.LIGHT_PURPLE), viewer,
                privateBody(identity.component(), ChatDisplayRenderer.playerName(target)),
                message, copyText, repeatCommand);
    }

    public Component privatePreview(Player sender, String targetName, Component message, String copyText) {
        String username = targetName == null ? "?" : targetName;
        Component target = ChatDisplayRenderer.clickablePlayerName(
                Component.text(username, NamedTextColor.WHITE), username);
        PlayerIdentityComponent identity = identity(sender, sender);
        return channelMessage(ChannelMarker.text(privateLabel(sender), NamedTextColor.LIGHT_PURPLE), sender,
                privateBody(identity.component(), target), message, copyText, null);
    }

    private Component channelMessage(ChannelMarker marker, Audience viewer,
                                     Component body, Component message, String copyText, String repeatCommand) {
        return Component.text()
                .append(channelPrefix(marker.component(), body))
                .append(Component.text(" »", NamedTextColor.GOLD))
                .append(timeHoveredMessage(message, copyText, copyHint(viewer)))
                .append(repeatSuffix(repeatCommand))
                .build();
    }

    static Component repeatSuffix(String repeatCommand) {
        return repeatCommand == null ? Component.empty()
                : Component.text(" [+1]", NamedTextColor.GRAY).clickEvent(ClickEvent.runCommand(repeatCommand));
    }

    private PlayerIdentityComponent identity(Player sender, Audience viewer) {
        return playerIdentities.render(sender, viewer, ChatDisplayRenderer.playerName(sender));
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

    private Component timeHoveredMessage(Component message, String copyText, String copyHint) {
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

        Component component() {
            return label == null || label.isEmpty()
                    ? Component.empty()
                    : Component.text("[" + label + "] ", color);
        }
    }
}
