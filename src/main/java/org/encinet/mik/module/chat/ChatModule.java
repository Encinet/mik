package org.encinet.mik.module.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.chat.delay.ChatDelayScheduler;
import org.encinet.mik.module.chat.menu.ChatSettingsMenu;
import org.encinet.mik.module.chat.mention.MentionService;
import org.encinet.mik.module.chat.model.ChatContent;
import org.encinet.mik.module.chat.model.ChatConversation;
import org.encinet.mik.module.chat.model.ChatMessage;
import org.encinet.mik.module.chat.model.ChatMessageId;
import org.encinet.mik.module.chat.model.ChatOrigin;
import org.encinet.mik.module.chat.model.ChatProcessingContext;
import org.encinet.mik.module.chat.model.ChatReferences;
import org.encinet.mik.module.chat.model.ChatSender;
import org.encinet.mik.module.chat.model.ChatSubmission;
import org.encinet.mik.module.chat.pipeline.AdventureComponentImporter;
import org.encinet.mik.module.chat.pipeline.BukkitChatContextFactory;
import org.encinet.mik.module.chat.pipeline.ChatProcessor;
import org.encinet.mik.module.chat.render.ChatMessageFormatter;
import org.encinet.mik.module.chat.render.MinecraftChatContentRenderer;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.identity.ExternalIdentity;
import org.encinet.mik.module.player.identity.PlayerIdentityRenderer;
import org.encinet.mik.module.player.identity.PlayerNameTag;
import org.encinet.mik.module.social.api.SocialPlatformDescriptor;
import org.encinet.mik.module.social.chat.SocialChatGameSink;
import org.encinet.mik.module.social.chat.SocialChatPublisher;
import org.encinet.mik.module.identity.IdentityBindingManager;
import org.encinet.mik.module.social.chat.SocialChatPublishReport;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

public class ChatModule implements Listener, SocialChatGameSink {

    private static final String STAFF_PERMISSION = "group." + Mik.GROUP_HELPER;
    private static final String REPEAT_COMMAND = "mikrepeat";

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final MentionService mentionService;
    private final ChatSettingsStore settingsStore;
    private final ChatProcessor chatProcessor = new ChatProcessor();
    private final MinecraftChatContentRenderer contentRenderer =
            new MinecraftChatContentRenderer();
    private final AdventureComponentImporter componentImporter =
            new AdventureComponentImporter();
    private final BukkitChatContextFactory contextFactory;
    private final ChatSettingsMenu settingsMenu;
    private final ChatMessageFormatter formatter;
    private final ChatDelayScheduler delayScheduler;
    private final ChatRepeatTracker repeatTracker = new ChatRepeatTracker();
    private final ChatRepeatActionStore repeatActionStore = new ChatRepeatActionStore();
    private final SocialChatPublisher socialChat;
    private final Map<AsyncChatEvent, PendingChatTransaction> pendingTransactions =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<UUID, ChatChannelState> channelStates =
            new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastPrivatePartner = new ConcurrentHashMap<>();

    public ChatModule(JavaPlugin plugin, MentionService mentionService, LanguageService languageService,
                      ChatSettingsStore settingsStore,
                      PlayerIdentityRenderer playerIdentities) {
        this(plugin, mentionService, languageService, settingsStore, playerIdentities,
                ignored -> SocialChatPublishReport.empty());
    }

    public ChatModule(JavaPlugin plugin, MentionService mentionService, LanguageService languageService,
                      ChatSettingsStore settingsStore,
                      PlayerIdentityRenderer playerIdentities,
                      SocialChatPublisher socialChat) {
        this(plugin, mentionService, languageService, settingsStore,
                new ChatMessageFormatter(languageService, playerIdentities), socialChat, null);
    }

    public ChatModule(JavaPlugin plugin, MentionService mentionService,
                      LanguageService languageService,
                      ChatSettingsStore settingsStore,
                      PlayerIdentityRenderer playerIdentities,
                      SocialChatPublisher socialChat,
                      IdentityBindingManager identityBindings) {
        this(plugin, mentionService, languageService, settingsStore,
                new ChatMessageFormatter(languageService, playerIdentities), socialChat,
                identityBindings);
    }

    public ChatModule(JavaPlugin plugin, MentionService mentionService,
                      LanguageService languageService,
                      ChatSettingsStore settingsStore,
                      ChatMessageFormatter formatter,
                      SocialChatPublisher socialChat) {
        this(plugin, mentionService, languageService, settingsStore,
                formatter, socialChat, null);
    }

    public ChatModule(JavaPlugin plugin, MentionService mentionService,
                      LanguageService languageService,
                      ChatSettingsStore settingsStore,
                      ChatMessageFormatter formatter,
                      SocialChatPublisher socialChat,
                      IdentityBindingManager identityBindings) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.mentionService = mentionService;
        this.settingsStore = settingsStore;
        this.contextFactory = new BukkitChatContextFactory(plugin, identityBindings);
        this.socialChat = java.util.Objects.requireNonNull(socialChat, "socialChat");
        this.settingsMenu = new ChatSettingsMenu(languageService, mentionService, settingsStore);
        this.formatter = java.util.Objects.requireNonNull(formatter, "formatter");
        this.delayScheduler = new ChatDelayScheduler(plugin, languageService, settingsStore,
                this::sendDelayedMessage, this::sendDelayedPreview);
    }

    public void enable() {
        contextFactory.enable();
        for (Player player : Bukkit.getOnlinePlayers()) {
            settingsStore.get(player.getUniqueId());
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ChatModule enabled");
    }

    public void disable() {
        contextFactory.close();
        delayScheduler.cancelAll();
        pendingTransactions.clear();
        channelStates.clear();
        lastPrivatePartner.clear();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();
            commands.register(Commands.literal("staff")
                    .requires(source -> source.getSender().hasPermission(STAFF_PERMISSION))
                    .executes(ctx -> {
                        Player player = requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            toggleStaff(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    sendStaffCommand(player, StringArgumentType.getString(ctx, "message"));
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_STAFF_COMMAND_DESCRIPTION), List.of("staffchat"));

            commands.register(Commands.literal("public")
                    .executes(ctx -> {
                        Player player = requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            switchPublic(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    sendTemporaryPublicCommand(player, StringArgumentType.getString(ctx, "message"));
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_PUBLIC_COMMAND_DESCRIPTION), List.of("global"));

            commands.register(Commands.literal("msg")
                    .executes(ctx -> {
                        Player player = requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            switchPrivateOrShowUsage(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("player", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                Bukkit.getOnlinePlayers().stream()
                                        .map(Player::getName)
                                        .filter(name -> name.toLowerCase(Locale.ROOT)
                                                .startsWith(builder.getRemaining().toLowerCase(Locale.ROOT)))
                                        .forEach(builder::suggest);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    switchPrivate(player, StringArgumentType.getString(ctx, "player"));
                                }
                                return Command.SINGLE_SUCCESS;
                            })
                            .then(Commands.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> {
                                        Player player = requirePlayer(ctx.getSource().getSender());
                                        if (player != null) {
                                            sendTemporaryPrivateCommand(player,
                                                    StringArgumentType.getString(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "message"));
                                        }
                                        return Command.SINGLE_SUCCESS;
                                    })))
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_MSG_COMMAND_DESCRIPTION), List.of("tell", "w", "whisper"));

            commands.register(Commands.literal("r")
                    .executes(ctx -> {
                        Player player = requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            switchReply(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    sendReplyCommand(player, StringArgumentType.getString(ctx, "message"));
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_REPLY_COMMAND_DESCRIPTION), List.of("reply"));

            commands.register(Commands.literal("cancel")
                    .executes(ctx -> {
                        Player player = requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            cancelDelayedMessages(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .build(), languageService.t(Language.DEFAULT, Message.CHAT_DELAY_CANCEL_COMMAND_DESCRIPTION), List.of("c"));

            commands.register(Commands.literal(REPEAT_COMMAND)
                    .then(Commands.argument("token", StringArgumentType.word())
                            .executes(ctx -> {
                                Player player = requirePlayer(ctx.getSource().getSender());
                                if (player != null) {
                                    repeatMessage(player, StringArgumentType.getString(ctx, "token"));
                                }
                                return Command.SINGLE_SUCCESS;
                            }))
                    .build(), "Repeat a chat message", List.of());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatRoute(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        ChatChannelState state = channelStates.getOrDefault(sender.getUniqueId(), ChatChannelState.publicChannel());
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (delayScheduler.queue(sender, plainMessage, state)) {
            event.setCancelled(true);
            return;
        }

        switch (state.channel()) {
            case PUBLIC -> routePublic(event, sender);
            case STAFF -> routeStaff(event, sender);
            case PRIVATE -> routePrivate(event, sender, state);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatCommit(AsyncChatEvent event) {
        PendingChatTransaction transaction = pendingTransactions.remove(event);
        if (transaction != null && !event.isCancelled()) {
            commit(event.getPlayer(), transaction, event.message());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        settingsStore.get(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        channelStates.remove(playerId);
        lastPrivatePartner.remove(playerId);
        settingsStore.forget(playerId);
        delayScheduler.cancel(playerId);
        repeatTracker.forget(playerId);
        repeatActionStore.forgetPrivateActions(playerId);
        contextFactory.forget(playerId);
        clearPrivateChannelsTargeting(event.getPlayer());
    }

    public void openSettingsMenu(Player player) {
        settingsMenu.open(player);
    }

    public List<Component> settingsSummary(Player player) {
        return settingsMenu.summary(player);
    }

    @Override
    public void display(
            SocialPlatformDescriptor platform,
            ChatSubmission submission
    ) {
        java.util.Objects.requireNonNull(platform, "platform");
        java.util.Objects.requireNonNull(submission, "submission");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                "External chat must enter ChatModule on the primary thread");
        }
        ChatMessage message = chatProcessor.process(submission);
        plugin.getServer().getConsoleSender().sendMessage(
                externalPublicMessage(platform, message,
                        plugin.getServer().getConsoleSender()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(externalPublicMessage(
                    platform, message, player));
        }
        mentionService.notifyEffects(
                message.submission().sender().minecraftId(),
                Component.text(message.submission().sender().displayName(),
                        NamedTextColor.WHITE),
                message.submission().sender().displayName(),
                message.effects(), Set.copyOf(Bukkit.getOnlinePlayers()));
        publishSocialChat(message);
    }

    private Component externalPublicMessage(
            SocialPlatformDescriptor platform,
            ChatMessage message,
            Audience viewer
    ) {
        Component body = renderContent(message.content(),
                viewer instanceof Player player ? player : null);
        return formatter.externalPublicMessage(
                platform.displayName(), externalSenderIdentity(
                        platform, message.submission().sender(), viewer), viewer,
                body, message.submission().sourceText());
    }

    private Component externalSenderIdentity(
            SocialPlatformDescriptor platform,
            ChatSender sender,
            Audience viewer
    ) {
        Player onlinePlayer = sender.minecraftId()
                .map(Bukkit::getPlayer).orElse(null);
        Component visibleIdentity;
        String visiblePlayerName = null;
        Component details;
        if (sender.minecraftId().isPresent()) {
            UUID playerId = sender.minecraftId().orElseThrow();
            visiblePlayerName = onlinePlayer == null
                    ? sender.minecraftName() : onlinePlayer.getName();
            OfflinePlayer identityPlayer = onlinePlayer == null
                    ? Bukkit.getOfflinePlayer(playerId) : onlinePlayer;
            Component baseName = onlinePlayer == null
                    ? ChatDisplayRenderer.clickablePlayerName(
                    Component.text(visiblePlayerName, NamedTextColor.WHITE),
                    visiblePlayerName)
                    : ChatDisplayRenderer.playerName(onlinePlayer);
            details = externalSenderDetails(platform, sender,
                    visiblePlayerName, viewer);
            visibleIdentity = formatter.externalPlayerIdentity(
                    identityPlayer, viewer, baseName,
                    new PlayerNameTag(sender.prefix(), sender.suffix()), details);
        } else {
            details = externalSenderDetails(platform, sender, null, viewer);
            visibleIdentity = Component.text(sender.displayName(),
                            NamedTextColor.WHITE)
                    .hoverEvent(HoverEvent.showText(details));
        }
        return visibleIdentity;
    }

    private Component externalSenderDetails(
            SocialPlatformDescriptor platform,
            ChatSender sender,
            String visiblePlayerName,
            Audience viewer
    ) {
        TextComponent.Builder details = Component.text()
                .append(Component.text(platform.displayName(), NamedTextColor.AQUA))
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

    private void routePublic(AsyncChatEvent event, Player sender) {
        String copyText = PlainTextComponentSerializer.plainText().serialize(event.message());
        Set<Player> channelPlayers = playersIn(event.viewers());
        ChatMessage message = processMessage(sender, copyText,
                ChatConversation.minecraftPublic(), channelPlayers);
        ChatRepeatActionStore.PendingAction repeat =
                repeatTracker.wouldRepeatPublic(sender.getUniqueId(), copyText)
                        ? repeatActionStore.preparePublic(copyText) : null;
        pendingTransactions.put(event, new PendingChatTransaction(
                ChatChannel.PUBLIC, message, channelPlayers, null, repeat));
        String repeatCommand = repeat == null ? null : repeatCommand(repeat.token());
        event.message(renderContent(message.content(), sender));
        event.renderer((source, sourceDisplayName, rendered, viewer) -> formatter.publicMessage(source, viewer, rendered,
                copyText, repeatCommand));
    }

    private void routeStaff(AsyncChatEvent event, Player sender) {
        if (!sender.hasPermission(STAFF_PERMISSION)) {
            channelStates.put(sender.getUniqueId(), ChatChannelState.publicChannel());
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            routePublic(event, sender);
            return;
        }
        Set<Audience> viewers = event.viewers();
        viewers.clear();
        viewers.add(plugin.getServer().getConsoleSender());
        Set<Player> channelPlayers = contextFactory.staffPlayers();
        viewers.addAll(channelPlayers);
        String copyText = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatMessage message = processMessage(sender, copyText,
                ChatConversation.minecraftStaff(), channelPlayers);
        ChatRepeatActionStore.PendingAction repeat =
                repeatTracker.wouldRepeatStaff(sender.getUniqueId(), copyText)
                        ? repeatActionStore.prepareStaff(copyText) : null;
        pendingTransactions.put(event, new PendingChatTransaction(
                ChatChannel.STAFF, message, channelPlayers, null, repeat));
        String repeatCommand = repeat == null ? null : repeatCommand(repeat.token());
        event.message(renderContent(message.content(), sender));
        event.renderer((source, sourceDisplayName, rendered, viewer) -> formatter.staffMessage(source, viewer, rendered,
                copyText, repeatCommand));
    }

    private void routePrivate(AsyncChatEvent event, Player sender, ChatChannelState state) {
        Player target = contextFactory.onlinePlayer(state.targetId());
        if (target == null) {
            channelStates.put(sender.getUniqueId(), ChatChannelState.publicChannel());
            event.setCancelled(true);
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
            return;
        }
        Set<Audience> viewers = event.viewers();
        viewers.clear();
        viewers.add(plugin.getServer().getConsoleSender());
        viewers.add(sender);
        viewers.add(target);
        Set<Player> channelPlayers = Set.of(sender, target);
        String copyText = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatMessage message = processMessage(sender, copyText,
                ChatConversation.minecraftPrivate(
                        sender.getUniqueId(), target.getUniqueId()),
                channelPlayers);
        ChatRepeatActionStore.PendingAction repeat =
                repeatTracker.wouldRepeatPrivate(sender.getUniqueId(),
                        target.getUniqueId(), copyText)
                        ? repeatActionStore.preparePrivate(copyText,
                        sender.getUniqueId(), target.getUniqueId()) : null;
        pendingTransactions.put(event, new PendingChatTransaction(
                ChatChannel.PRIVATE, message, channelPlayers, target, repeat));
        String repeatCommand = repeat == null ? null : repeatCommand(repeat.token());
        event.message(renderContent(message.content(), sender));
        event.renderer((source, sourceDisplayName, rendered, viewer) -> formatter.privateMessage(source, target, viewer,
                rendered, copyText, repeatCommand));
    }

    private void commit(
            Player sender,
            PendingChatTransaction transaction,
            Component finalBody
    ) {
        ChatMessage message = transaction.message();
        String source = message.submission().sourceText();
        switch (transaction.channel()) {
            case PUBLIC -> repeatTracker.recordPublic(sender.getUniqueId(), source);
            case STAFF -> repeatTracker.recordStaff(sender.getUniqueId(), source);
            case PRIVATE -> {
                Player target = transaction.privateTarget();
                if (target == null) {
                    throw new IllegalStateException(
                            "Private chat transaction has no target");
                }
                repeatTracker.recordPrivate(sender.getUniqueId(),
                        target.getUniqueId(), source);
                touchPrivatePartners(sender, target);
            }
        }
        if (transaction.repeatAction() != null) {
            repeatActionStore.commit(transaction.repeatAction());
        }
        mentionService.notifyEffects(
                sender, message.effects(), transaction.recipients());
        if (transaction.channel() == ChatChannel.PUBLIC) {
            publishPublicChat(new ChatMessage(message.submission(),
                    componentImporter.importComponent(finalBody),
                    message.effects()));
        }
    }

    private void toggleStaff(Player player) {
        ChatChannelState current = channelStates.getOrDefault(player.getUniqueId(), ChatChannelState.publicChannel());
        if (current.channel() == ChatChannel.STAFF) {
            switchPublic(player);
            return;
        }
        channelStates.put(player.getUniqueId(), ChatChannelState.staff());
        player.sendMessage(Component.text(languageService.t(player, Message.STAFFCHAT_ENTER), NamedTextColor.GREEN));
    }

    private void switchPublic(Player player) {
        channelStates.put(player.getUniqueId(), ChatChannelState.publicChannel());
        player.sendMessage(Component.text(languageService.t(player, Message.CHAT_PUBLIC_ENTER), NamedTextColor.YELLOW));
    }

    private void switchPrivateOrShowUsage(Player player) {
        ChatChannelState current = channelStates.get(player.getUniqueId());
        if (current != null && current.channel() == ChatChannel.PRIVATE) {
            switchPublic(player);
            return;
        }
        player.sendMessage(Component.text("/msg <player> [message]", NamedTextColor.YELLOW));
    }

    private void switchPrivate(Player sender, String targetName) {
        Player target = findOnlinePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_NOT_FOUND, targetName), NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_SELF), NamedTextColor.RED));
            return;
        }
        ChatChannelState current = channelStates.get(sender.getUniqueId());
        if (current != null && current.channel() == ChatChannel.PRIVATE
                && target.getUniqueId().equals(current.targetId())) {
            switchPublic(sender);
            return;
        }
        channelStates.put(sender.getUniqueId(), ChatChannelState.privateChannel(target.getUniqueId(), target.getName()));
        touchPrivatePartners(sender, target);
        sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_ENTER, target.getName()), NamedTextColor.GREEN));
    }

    private void sendTemporaryPrivateCommand(Player sender, String targetName, String plainMessage) {
        Player target = findOnlinePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_NOT_FOUND, targetName), NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_SELF), NamedTextColor.RED));
            return;
        }
        if (delayScheduler.queue(sender, plainMessage, ChatChannelState.privateChannel(target.getUniqueId(), target.getName()))) {
            touchPrivatePartners(sender, target);
            return;
        }
        sendPrivateMessage(sender, target, plainMessage);
    }

    private void sendTemporaryPublicCommand(Player sender, String plainMessage) {
        if (delayScheduler.queue(sender, plainMessage, ChatChannelState.publicChannel())) {
            return;
        }
        sendPublicMessage(sender, plainMessage);
    }

    private void sendPrivateMessage(Player sender, Player target, String plainMessage) {
        touchPrivatePartners(sender, target);
        Set<Player> channelPlayers = Set.of(sender, target);
        ChatMessage processed = processMessage(sender, plainMessage,
                ChatConversation.minecraftPrivate(
                        sender.getUniqueId(), target.getUniqueId()),
                channelPlayers);
        Component message = renderContent(processed.content(), sender);
        boolean repeated = repeatTracker.recordPrivate(sender.getUniqueId(), target.getUniqueId(), plainMessage);
        String repeatCommand = repeated ? repeatCommand(repeatActionStore.createPrivate(
                plainMessage, sender.getUniqueId(), target.getUniqueId())) : null;
        sender.sendMessage(formatter.privateMessage(sender, target, sender, message, plainMessage, repeatCommand));
        target.sendMessage(formatter.privateMessage(sender, target, target, message, plainMessage, repeatCommand));
        plugin.getServer().getConsoleSender().sendMessage(formatter.privateMessage(sender, target,
                plugin.getServer().getConsoleSender(), message, plainMessage, repeatCommand));
        mentionService.notifyEffects(sender, processed.effects(), channelPlayers);
    }

    private void sendStaffCommand(Player sender, String plainMessage) {
        if (!sender.hasPermission(STAFF_PERMISSION)) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            return;
        }

        if (delayScheduler.queue(sender, plainMessage, ChatChannelState.staff())) {
            return;
        }
        sendStaffMessage(sender, plainMessage);
    }

    private void sendStaffMessage(Player sender, String plainMessage) {
        if (!sender.hasPermission(STAFF_PERMISSION)) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            return;
        }

        Set<Player> channelPlayers = staffChannelPlayers();
        ChatMessage processed = processMessage(sender, plainMessage,
                ChatConversation.minecraftStaff(), channelPlayers);
        Component message = renderContent(processed.content(), sender);
        boolean repeated = repeatTracker.recordStaff(sender.getUniqueId(), plainMessage);
        String repeatCommand = repeated ? repeatCommand(repeatActionStore.createStaff(plainMessage)) : null;
        plugin.getServer().getConsoleSender().sendMessage(formatter.staffMessage(sender,
                plugin.getServer().getConsoleSender(), message, plainMessage, repeatCommand));
        for (Player player : channelPlayers) {
            player.sendMessage(formatter.staffMessage(sender, player, message, plainMessage, repeatCommand));
        }
        mentionService.notifyEffects(sender, processed.effects(), channelPlayers);
    }

    private void switchReply(Player sender) {
        UUID targetId = lastPrivatePartner.get(sender.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_NO_REPLY_TARGET), NamedTextColor.RED));
            return;
        }
        switchPrivate(sender, target.getName());
    }

    private void sendReplyCommand(Player sender, String plainMessage) {
        UUID targetId = lastPrivatePartner.get(sender.getUniqueId());
        Player target = targetId == null ? null : Bukkit.getPlayer(targetId);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_NO_REPLY_TARGET), NamedTextColor.RED));
            return;
        }
        if (delayScheduler.queue(sender, plainMessage, ChatChannelState.privateChannel(target.getUniqueId(), target.getName()))) {
            return;
        }
        sendPrivateMessage(sender, target, plainMessage);
    }

    private ChatMessage processMessage(
            Player sender,
            String source,
            ChatConversation conversation,
            Set<Player> channelPlayers
    ) {
        ChatProcessingContext context = contextFactory.capture(
                sender, channelPlayers,
                conversation.kind() == ChatConversation.Kind.PUBLIC);
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(),
                new ChatOrigin.Minecraft(sender.getUniqueId()),
                ChatSender.minecraft(sender.getUniqueId(), sender.getName()),
                conversation, source, ChatReferences.empty(), Instant.now(), context);
        return chatProcessor.process(submission);
    }

    private Component renderContent(ChatContent content, Player viewer) {
        return contentRenderer.render(content,
                new MinecraftChatContentRenderer.Context(
                        viewer == null
                                ? languageService.t(Language.DEFAULT,
                                Message.CHAT_ITEM_EMPTY_HOVER)
                                : languageService.t(viewer,
                                Message.CHAT_ITEM_EMPTY_HOVER),
                        viewer == null
                                ? languageService.t(Language.DEFAULT,
                                Message.CHAT_MENTION_ALL_HOVER)
                                : languageService.t(viewer,
                                Message.CHAT_MENTION_ALL_HOVER)));
    }

    private Set<Player> playersIn(Set<Audience> viewers) {
        Set<Player> players = new HashSet<>();
        for (Audience viewer : viewers) {
            if (viewer instanceof Player player) {
                players.add(player);
            }
        }
        return players;
    }

    private Set<Player> staffChannelPlayers() {
        Set<Player> players = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(STAFF_PERMISSION)) {
                players.add(player);
            }
        }
        return players;
    }

    private void touchPrivatePartners(Player sender, Player target) {
        lastPrivatePartner.put(sender.getUniqueId(), target.getUniqueId());
        lastPrivatePartner.put(target.getUniqueId(), sender.getUniqueId());
    }

    private void sendDelayedPreview(Player sender, String plainMessage, ChatChannelState state) {
        if (Bukkit.isPrimaryThread()) {
            if (sender.isOnline()) {
                sender.sendMessage(delayedPreviewLine(sender, plainMessage, state));
            }
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (sender.isOnline()) {
                sender.sendMessage(delayedPreviewLine(sender, plainMessage, state));
            }
        });
    }

    private Component delayedPreviewLine(Player sender, String plainMessage, ChatChannelState state) {
        return Component.text(languageService.t(sender, Message.CHAT_DELAY_PREVIEW_MARKER) + " ", NamedTextColor.YELLOW)
                .append(delayedPreviewMessage(sender, plainMessage, state));
    }

    private Component delayedPreviewMessage(Player sender, String plainMessage, ChatChannelState state) {
        return switch (state.channel()) {
            case PUBLIC -> {
                Set<Player> channelPlayers = new HashSet<>(Bukkit.getOnlinePlayers());
                Component message = renderContent(processMessage(sender, plainMessage,
                        ChatConversation.minecraftPublic(), channelPlayers).content(), sender);
                yield formatter.publicMessage(sender, sender, message,
                        plainMessage, null);
            }
            case STAFF -> {
                Set<Player> channelPlayers = staffChannelPlayers();
                Component message = renderContent(processMessage(sender, plainMessage,
                        ChatConversation.minecraftStaff(), channelPlayers).content(), sender);
                yield formatter.staffMessage(sender, sender, message,
                        plainMessage, null);
            }
            case PRIVATE -> {
                Player target = Bukkit.getPlayer(state.targetId());
                Set<Player> channelPlayers = target == null ? Set.of(sender) : Set.of(sender, target);
                ChatConversation conversation = target == null
                        ? new ChatConversation(ChatConversation.Kind.PRIVATE,
                        "minecraft:private:preview")
                        : ChatConversation.minecraftPrivate(
                        sender.getUniqueId(), target.getUniqueId());
                Component message = renderContent(processMessage(sender, plainMessage,
                        conversation, channelPlayers).content(), sender);
                yield target == null
                        ? formatPrivatePreviewWithOfflineTarget(sender, state.targetName(), message, plainMessage)
                        : formatter.privateMessage(sender, target, sender, message, plainMessage, null);
            }
        };
    }

    private Component formatPrivatePreviewWithOfflineTarget(Player sender, String targetName, Component message, String copyText) {
        return formatter.privatePreview(sender, targetName, message, copyText);
    }

    private void sendDelayedMessage(Player sender, String plainMessage, ChatChannelState state) {
        switch (state.channel()) {
            case PUBLIC -> sendPublicMessage(sender, plainMessage);
            case STAFF -> sendStaffMessage(sender, plainMessage);
            case PRIVATE -> {
                Player target = Bukkit.getPlayer(state.targetId());
                if (target == null) {
                    sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
                    return;
                }
                sendPrivateMessage(sender, target, plainMessage);
            }
        }
    }

    private void sendPublicMessage(Player sender, String plainMessage) {
        Set<Player> channelPlayers = new HashSet<>(Bukkit.getOnlinePlayers());
        ChatMessage processed = processMessage(sender, plainMessage,
                ChatConversation.minecraftPublic(), channelPlayers);
        Component message = renderContent(processed.content(), sender);
        boolean repeated = repeatTracker.recordPublic(sender.getUniqueId(), plainMessage);
        String repeatCommand = repeated ? repeatCommand(repeatActionStore.createPublic(plainMessage)) : null;
        plugin.getServer().getConsoleSender().sendMessage(formatter.publicMessage(sender,
                plugin.getServer().getConsoleSender(), message, plainMessage, repeatCommand));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formatter.publicMessage(sender, player, message, plainMessage, repeatCommand));
        }
        mentionService.notifyEffects(sender, processed.effects(), channelPlayers);
        publishPublicChat(processed);
    }

    private void publishPublicChat(ChatMessage message) {
        publishSocialChat(message);
    }

    private void publishSocialChat(ChatMessage message) {
        try {
            SocialChatPublishReport report = socialChat.publish(message);
            if (report.backpressured() > 0) {
                plugin.getLogger().warning("Social chat publication was backpressured for "
                        + report.backpressured() + " platform(s)");
            }
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Could not publish public chat to social platforms: "
                    + error.getMessage());
        }
    }

    private String repeatCommand(String token) {
        return "/" + REPEAT_COMMAND + " " + token;
    }

    private void repeatMessage(Player player, String token) {
        repeatActionStore.resolve(token).ifPresent(action -> {
            switch (action.channel()) {
                case PUBLIC -> sendTemporaryPublicCommand(player, action.message());
                case STAFF -> sendStaffCommand(player, action.message());
                case PRIVATE -> repeatPrivateMessage(player, action);
            }
        });
    }

    private void repeatPrivateMessage(Player player, ChatRepeatActionStore.RepeatAction action) {
        UUID targetId = action.privateTargetFor(player.getUniqueId());
        if (targetId == null) {
            return;
        }
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            player.sendMessage(Component.text(languageService.t(player, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
            return;
        }
        if (delayScheduler.queue(player, action.message(), ChatChannelState.privateChannel(target.getUniqueId(), target.getName()))) {
            return;
        }
        sendPrivateMessage(player, target, action.message());
    }

    private void cancelDelayedMessages(Player player) {
        int count = delayScheduler.cancel(player.getUniqueId());
        Message message = count > 0 ? Message.CHAT_DELAY_CANCELLED : Message.CHAT_DELAY_NOTHING_TO_CANCEL;
        NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        player.sendMessage(Component.text(languageService.t(player, message), color));
    }

    private void clearPrivateChannelsTargeting(Player quitter) {
        UUID quitterId = quitter.getUniqueId();
        channelStates.entrySet().removeIf(entry -> {
            ChatChannelState state = entry.getValue();
            if (state.channel() != ChatChannel.PRIVATE || !quitterId.equals(state.targetId())) {
                return false;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.sendMessage(Component.text(languageService.t(player, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
            }
            return true;
        });
    }

    private Player findOnlinePlayer(String name) {
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) {
            return exact;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    private record PendingChatTransaction(
            ChatChannel channel,
            ChatMessage message,
            Set<Player> recipients,
            Player privateTarget,
            ChatRepeatActionStore.PendingAction repeatAction
    ) {
        private PendingChatTransaction {
            java.util.Objects.requireNonNull(channel, "channel");
            java.util.Objects.requireNonNull(message, "message");
            recipients = Set.copyOf(recipients);
        }
    }

}
