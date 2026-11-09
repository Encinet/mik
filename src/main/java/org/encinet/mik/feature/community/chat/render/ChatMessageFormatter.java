package org.encinet.mik.feature.community.chat.render;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.encinet.mik.feature.player.profile.PlayerNameRenderer;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.shared.util.TextBadge;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.profile.PlayerIdentityComponent;
import org.encinet.mik.feature.player.profile.PlayerSource;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.feature.player.profile.PlayerNameTag;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Function;

public final class ChatMessageFormatter {

    private static final ZoneId CHAT_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss zzzz");

    private final LanguageService languageService;
    private final PlayerNameTagRenderer nameTags;
    private final PlayerSourceRenderer sources;
    private final Function<UUID, Player> onlinePlayers;
    private final MinecraftChatContentRenderer contentRenderer = new MinecraftChatContentRenderer();

    public ChatMessageFormatter(LanguageService languageService,
                                PlayerNameTagRenderer nameTags,
                                PlayerSourceRenderer sources,
                                Function<UUID, Player> onlinePlayers) {
        this.languageService = languageService;
        this.nameTags = nameTags;
        this.sources = sources;
        this.onlinePlayers = java.util.Objects.requireNonNull(onlinePlayers, "onlinePlayers");
    }

    /** Every origin keeps its source, audience and identity through the same game presentation. */
    public Component message(ChatMessage message, ChatSender target, Audience viewer, Key repeatIdentifier) {
        return message(message, target, viewer, content(message.content(), viewer), repeatIdentifier);
    }

    /** Paper may supply a final body edited by another plugin without changing trusted header metadata. */
    public Component message(ChatMessage message, ChatSender target, Audience viewer,
                             Component body, Key repeatIdentifier) {
        var submission = message.submission();
        ChatSender sender = submission.sender();
        PlayerIdentityComponent identity = senderIdentity(sender,
                name -> submission.origin() instanceof ChatOrigin.Social social
                        ? externalSenderDetails(social.platformName(), sender, name, viewer)
                        : sender.minecraftId().map(id -> playerDetails(id, name, viewer))
                                .orElse(Component.empty()));
        ChatChannel channel = submission.conversation().channel();
        Component marker = channelMarker(channel, viewer);
        return channelMessage(marker,
                channelBody(channel, identity.chat(source(submission.origin(), viewer)),
                        target == null ? null : playerIdentity(target, viewer)),
                standardSeparator(), body, submission.sourceText(), localized(viewer, Message.CHAT_COPY_HOVER), repeatIdentifier);
    }

    public Component content(ChatContent content, Audience viewer) {
        return contentRenderer.render(content, new MinecraftChatContentRenderer.Context(
                localized(viewer, Message.CHAT_ITEM_EMPTY_HOVER),
                localized(viewer, Message.CHAT_MENTION_ALL_HOVER)));
    }

    private PlayerSource source(ChatOrigin origin, Audience viewer) {
        return switch (origin) {
            case ChatOrigin.Web _ -> sources.web(viewer);
            case ChatOrigin.Social social -> new PlayerSource(social.platformName());
            case ChatOrigin.Minecraft game -> {
                Player subject = onlinePlayers.apply(game.playerId());
                yield subject == null ? PlayerSource.NONE : sources.game(subject, viewer);
            }
            case ChatOrigin.Plugin _ -> PlayerSource.NONE;
        };
    }

    private PlayerIdentityComponent senderIdentity(
            ChatSender sender, Function<String, Component> details
    ) {
        Player onlinePlayer = sender.minecraftId().map(onlinePlayers).orElse(null);
        if (sender.minecraftId().isPresent()) {
            UUID playerId = sender.minecraftId().orElseThrow();
            String visiblePlayerName = onlinePlayer == null
                    ? sender.minecraftName() : onlinePlayer.getName();
            OfflinePlayer identityPlayer = onlinePlayer == null
                    ? Bukkit.getOfflinePlayer(playerId) : onlinePlayer;
            Component baseName = onlinePlayer == null
                    ? PlayerNameRenderer.clickablePlayerName(
                            Component.text(visiblePlayerName),
                            visiblePlayerName)
                    : PlayerNameRenderer.playerName(onlinePlayer);
            Component name = baseName.hoverEvent(HoverEvent.showText(details.apply(visiblePlayerName)));
            return nameTags.render(identityPlayer, name, new PlayerNameTag(
                    sender.prefix(), sender.suffix(), TextColor.color(sender.usernameColor())));
        }
        return new PlayerIdentityComponent(Component.text(sender.displayName(), TextColor.color(sender.usernameColor()))
                .hoverEvent(HoverEvent.showText(details.apply(null))));
    }

    private Component externalSenderDetails(
            String platformName, ChatSender sender,
            String visiblePlayerName, Audience viewer
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

    private Component playerIdentity(ChatSender sender, Audience viewer) {
        UUID playerId = sender.minecraftId().orElseThrow();
        return senderIdentity(sender, name -> playerDetails(playerId, name, viewer)).component();
    }

    private Component channelMarker(ChatChannel channel, Audience viewer) {
        return switch (channel) {
            case PUBLIC -> Component.empty();
            case STAFF -> TextBadge.prefix(localized(viewer, Message.CHAT_STAFF_LABEL), NamedTextColor.GOLD);
            case PRIVATE -> TextBadge.prefix(localized(viewer, Message.CHAT_PRIVATE_LABEL), NamedTextColor.LIGHT_PURPLE);
        };
    }

    static Component channelBody(ChatChannel channel, Component sender, Component target) {
        return channel == ChatChannel.PRIVATE ? privateBody(sender, java.util.Objects.requireNonNull(target, "target")) : sender;
    }

    static Component channelMessage(
            Component marker,
            Component body,
            Component separator,
            Component message,
            String copyText,
            String copyHint,
            Key repeatIdentifier
    ) {
        return Component.text()
                .append(channelPrefix(marker, body))
                .append(separator)
                .append(timeHoveredMessage(message, copyText, copyHint))
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

    private Component playerDetails(UUID id, String name, Audience viewer) {
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

}
