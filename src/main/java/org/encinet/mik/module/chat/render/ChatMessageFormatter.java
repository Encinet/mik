package org.encinet.mik.module.chat.render;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.chat.ChatDisplayRenderer;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.util.NameMetaRenderer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class ChatMessageFormatter {

    private static final ZoneId CHAT_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss zzzz");

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private LuckPerms luckPerms;

    public ChatMessageFormatter(JavaPlugin plugin, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
    }

    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found; chat prefix and suffix rendering will use defaults.");
            luckPerms = null;
            return;
        }
        luckPerms = provider.getProvider();
    }

    public Component publicMessage(Player sender, Audience viewer, Component message, String copyText) {
        return channelMessage(ChannelMarker.empty(), sender, viewer, playerBody(sender), message, copyText);
    }

    public Component staffMessage(Player sender, Audience viewer, Component message, String copyText) {
        return channelMessage(ChannelMarker.text("STAFF", NamedTextColor.GOLD), sender, viewer,
                playerBody(sender), message, copyText);
    }

    public Component privateMessage(Player sender, Player target, Audience viewer, Component message, String copyText) {
        return channelMessage(ChannelMarker.text(privateLabel(viewer), NamedTextColor.LIGHT_PURPLE), sender, viewer,
                privateBody(sender, ChatDisplayRenderer.playerName(target)), message, copyText);
    }

    public Component privatePreview(Player sender, String targetName, Component message, String copyText) {
        Component target = Component.text(targetName == null ? "?" : targetName, NamedTextColor.WHITE);
        return channelMessage(ChannelMarker.text(privateLabel(sender), NamedTextColor.LIGHT_PURPLE), sender, sender,
                privateBody(sender, target), message, copyText);
    }

    private Component channelMessage(ChannelMarker marker, Player sender, Audience viewer,
                                     Component body, Component message, String copyText) {
        return Component.text()
                .append(marker.component())
                .append(body)
                .append(Component.text(" »", NamedTextColor.GOLD))
                .append(timeHoveredMessage(message, copyText, copyHint(viewer)))
                .build();
    }

    private Component playerBody(Player sender) {
        return Component.text()
                .append(metaComponent(sender, true))
                .append(ChatDisplayRenderer.playerName(sender))
                .append(metaComponent(sender, false))
                .build();
    }

    private Component privateBody(Player sender, Component target) {
        return Component.text()
                .append(ChatDisplayRenderer.playerName(sender))
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

    private Component metaComponent(Player player, boolean prefix) {
        CachedMetaData metaData = cachedMetaData(player);
        if (metaData == null) {
            return Component.empty();
        }
        String raw = prefix ? metaData.getPrefix() : metaData.getSuffix();
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        try {
            return NameMetaRenderer.deserialize(player, raw);
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Failed to parse LuckPerms " + (prefix ? "prefix" : "suffix")
                    + " for " + player.getName() + ": " + e.getMessage());
            return NameMetaRenderer.fallback(player, raw);
        }
    }

    private CachedMetaData cachedMetaData(Player player) {
        if (luckPerms == null) {
            return null;
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user == null ? null : user.getCachedData().getMetaData();
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
