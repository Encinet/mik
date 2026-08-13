package org.encinet.mik.module.social.management;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.social.runtime.SocialPlatformAdmin;
import org.encinet.mik.module.social.runtime.SocialPlatformSnapshot;
import org.encinet.mik.module.social.runtime.SocialPlatformState;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Thin Bukkit adapter for the complete {@code /social} management tree. */
public final class SocialManagementCommandRegistrar {
    private static final String ADMIN_PERMISSION = "group." + Mik.GROUP_MANAGER;
    private static final String PLATFORM_ARGUMENT = "platform";

    private final SocialPlatformAdmin platforms;
    private final LanguageService languages;

    public SocialManagementCommandRegistrar(
            SocialPlatformAdmin platforms,
            LanguageService languages
    ) {
        this.platforms = Objects.requireNonNull(platforms, "platforms");
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    public void register(LifecycleEventManager<Plugin> lifecycleManager) {
        Objects.requireNonNull(lifecycleManager, "lifecycleManager")
                .registerEventHandler(LifecycleEvents.COMMANDS, event ->
                        event.registrar().register(Commands.literal("social")
                                        .requires(source -> source.getSender()
                                                .hasPermission(ADMIN_PERMISSION))
                                        .executes(context -> showPlatforms(
                                                context.getSource().getSender()))
                                        .then(Commands.literal("help")
                                                .executes(context -> help(
                                                        context.getSource().getSender())))
                                        .then(Commands.literal("platforms")
                                                .executes(context -> showPlatforms(
                                                        context.getSource().getSender())))
                                        .then(Commands.literal("status")
                                                .executes(context -> statusAll(
                                                        context.getSource().getSender()))
                                                .then(platformArgument(context -> status(
                                                        context.getSource().getSender(),
                                                        platformId(context)))))
                                        .then(Commands.literal("reload")
                                                .executes(context -> reloadAll(
                                                        context.getSource().getSender()))
                                                .then(platformArgument(context -> reload(
                                                        context.getSource().getSender(),
                                                        platformId(context)))))
                                        .then(Commands.literal("conversations")
                                                .then(platformArgument(context -> conversations(
                                                        context.getSource().getSender(),
                                                        platformId(context)))))
                                        .build(),
                                languages.t(Language.DEFAULT,
                                        Message.SOCIAL_COMMAND_DESCRIPTION)));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> platformArgument(
            Command<CommandSourceStack> command
    ) {
        return Commands.argument(PLATFORM_ARGUMENT, StringArgumentType.word())
                .suggests((context, builder) -> {
                    matchingPlatformIds(platforms.platforms().stream()
                                    .map(value -> value.descriptor().id()).toList(),
                            builder.getRemaining()).forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(command);
    }

    private int showPlatforms(CommandSender sender) {
        sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_TITLE,
                NamedTextColor.AQUA));
        for (SocialPlatformSnapshot platform : platforms.platforms()) {
            sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_PLATFORM,
                    color(platform.state()), platform.descriptor().displayName(),
                    platform.descriptor().id(), stateName(sender, platform.state())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandSender sender) {
        sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_TITLE,
                NamedTextColor.AQUA));
        sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_HELP,
                NamedTextColor.WHITE));
        return Command.SINGLE_SUCCESS;
    }

    private int statusAll(CommandSender sender) {
        platforms.platforms().forEach(value -> sendStatus(sender, value));
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandSender sender, String platformId) {
        SocialPlatformSnapshot snapshot = platforms.status(platformId).orElse(null);
        if (snapshot == null) {
            return unknown(sender, platformId);
        }
        sendStatus(sender, snapshot);
        return Command.SINGLE_SUCCESS;
    }

    private int reloadAll(CommandSender sender) {
        platforms.reloadAll();
        sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_RELOADED_ALL,
                NamedTextColor.GREEN));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandSender sender, String platformId) {
        if (!platforms.reload(platformId)) {
            return unknown(sender, platformId);
        }
        sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_RELOADED,
                NamedTextColor.GREEN, platformId));
        return Command.SINGLE_SUCCESS;
    }

    private int conversations(CommandSender sender, String platformId) {
        if (platforms.status(platformId).isEmpty()) {
            return unknown(sender, platformId);
        }
        Collection<String> conversations = platforms.conversations(platformId);
        if (conversations.isEmpty()) {
            sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_CONVERSATIONS_EMPTY,
                    NamedTextColor.YELLOW, platformId));
            return Command.SINGLE_SUCCESS;
        }
        sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_CONVERSATIONS_TITLE,
                NamedTextColor.AQUA, platformId));
        conversations.forEach(id -> sender.sendMessage(
                Component.text("- " + id, NamedTextColor.WHITE)));
        return Command.SINGLE_SUCCESS;
    }

    private void sendStatus(CommandSender sender, SocialPlatformSnapshot status) {
        sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_STATUS, color(status.state()),
                status.descriptor().displayName(), stateName(sender, status.state()),
                status.generation(), status.observedConversations()));
        sender.sendMessage(Component.text("in " + status.inboundAccepted()
                + " / retry " + status.inboundBackpressured()
                + " · out " + status.outboundAccepted()
                + " / pressure " + status.outboundBackpressured()
                + " · failures " + status.deliveryFailures(),
                status.deliveryFailures() == 0
                        ? NamedTextColor.DARK_GRAY : NamedTextColor.YELLOW));
        if (!status.endpoint().isBlank()) {
            sender.sendMessage(Component.text(status.endpoint(), NamedTextColor.GRAY));
        }
        if (!status.lastError().isBlank()) {
            sender.sendMessage(Component.text(status.lastError(), NamedTextColor.RED));
        }
    }

    private int unknown(CommandSender sender, String platformId) {
        sender.sendMessage(text(sender, Message.SOCIAL_ADMIN_UNKNOWN,
                NamedTextColor.RED, platformId));
        return 0;
    }

    private String stateName(CommandSender sender, SocialPlatformState state) {
        Message message = switch (state) {
            case DISABLED -> Message.SOCIAL_STATE_DISABLED;
            case STOPPED -> Message.SOCIAL_STATE_STOPPED;
            case STARTING -> Message.SOCIAL_STATE_STARTING;
            case READY -> Message.SOCIAL_STATE_READY;
            case FAILED -> Message.SOCIAL_STATE_FAILED;
        };
        return languages.t(language(sender), message);
    }

    private Component text(
            CommandSender sender,
            Message message,
            NamedTextColor color,
            Object... arguments
    ) {
        return Component.text(languages.t(language(sender), message, arguments), color);
    }

    private Language language(CommandSender sender) {
        return sender instanceof Player player
                ? languages.language(player) : Language.DEFAULT;
    }

    private static NamedTextColor color(SocialPlatformState state) {
        return switch (state) {
            case READY -> NamedTextColor.GREEN;
            case FAILED -> NamedTextColor.RED;
            case DISABLED, STOPPED -> NamedTextColor.YELLOW;
            case STARTING -> NamedTextColor.AQUA;
        };
    }

    static List<String> matchingPlatformIds(
            Collection<String> platformIds,
            String remaining
    ) {
        String prefix = Objects.requireNonNull(remaining, "remaining")
                .toLowerCase(Locale.ROOT);
        return Objects.requireNonNull(platformIds, "platformIds").stream()
                .map(id -> Objects.requireNonNull(id, "platformId"))
                .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct().sorted().toList();
    }

    private static String platformId(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, PLATFORM_ARGUMENT);
    }
}
