package org.encinet.mik.feature.community.chat.game;

import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.feature.community.chat.game.ChatRelay.Outcome;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.input.AdventureComponentImporter;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.game.GameChatDirectory.Participant;
import org.encinet.mik.shared.menu.MenuPanel;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.encinet.mik.feature.community.chat.menu.ChatSettingsMenu;
import org.encinet.mik.feature.community.chat.model.ChatChannel;
import org.encinet.mik.feature.community.chat.model.ChatContent;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatMentions;
import org.encinet.mik.feature.community.chat.model.ChatPresence;
import org.encinet.mik.feature.community.chat.input.AuthorizedChatMarkup;
import org.encinet.mik.feature.community.chat.parse.ChatProcessor;
import org.encinet.mik.feature.community.chat.model.ChatNode;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.render.ChatMessageFormatter;
import org.encinet.mik.feature.community.chat.render.ChatMessageFormatter.Presentation;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.guidance.TipScope;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.logging.Level;

public final class GameChatController implements Listener {

    private final GameChatDirectory directory;
    private final Predicate<UUID> allowance;
    private final GameChatService messages;
    private final AdventureComponentImporter componentImporter = new AdventureComponentImporter();
    private final ChatProcessor processor = new ChatProcessor(new AuthorizedChatMarkup()::parse);
    private final Set<Player> announced = Collections.newSetFromMap(new IdentityHashMap<>());

    private final Plugin plugin;
    private final LanguageService languageService;
    private final GameMentionNotifier mentionNotifier;
    private final ChatSettingsStore settingsStore;
    private final GameChatSessions sessions;
    private final ChatSettingsMenu settingsMenu;
    private final ChatMessageFormatter formatter;
    private final ChatDelayScheduler delayScheduler;
    private final ChatRepeatTracker repeatTracker = new ChatRepeatTracker();
    private final ChatRepeatActionStore repeatActionStore = new ChatRepeatActionStore();
    private final Consumer<Player> privateCommandSent;
    private final BiPredicate<UUID, String> menuChatInput;
    private final Map<AsyncChatEvent, PendingChatTransaction> pendingTransactions =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<UUID, ChatChannelState> channelStates =
            new ConcurrentHashMap<>();
    private final Map<UUID, UUID> lastPrivatePartner = new ConcurrentHashMap<>();
    private boolean active;

    public GameChatController(Plugin plugin, GameChatDirectory directory, GameMentionNotifier mentionNotifier,
                      LanguageService languageService,
                      ChatSettingsStore settingsStore,
                      PlayerNameTagRenderer nameTags, PlayerSourceRenderer sources,
                      GameChatService messages,
                      TipScope tips, BiPredicate<UUID, String> menuChatInput, Predicate<UUID> allowance) {
        this.plugin = plugin;
        this.directory = directory;
        this.allowance = allowance;
        this.languageService = languageService;
        this.mentionNotifier = mentionNotifier;
        this.settingsStore = settingsStore;
        this.menuChatInput = java.util.Objects.requireNonNull(menuChatInput, "menuChatInput");
        this.sessions = new GameChatSessions(plugin, directory, languageService, nameTags, sources);
        this.messages = java.util.Objects.requireNonNull(messages, "messages");
        this.settingsMenu = new ChatSettingsMenu(languageService, settingsStore);
        this.formatter = new ChatMessageFormatter(languageService, sources);
        this.delayScheduler = new ChatDelayScheduler(plugin, languageService, settingsStore,
                this::sendMessage, this::sendDelayedPreview);
        privateCommandSent = ChatTips.privateChannel(plugin, tips, this::isPublicChannel);
    }

    public void enable() {
        sessions.enable();
        delayScheduler.enable();
        for (Player player : Bukkit.getOnlinePlayers()) {
            settingsStore.get(player.getUniqueId());
            if (directory.eligible(player)) announced.add(player);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        active = true;
        plugin.getLogger().info("ChatModule enabled");
    }

    public void disable() {
        active = false;
        HandlerList.unregisterAll(this);
        sessions.close();
        delayScheduler.close();
        pendingTransactions.clear();
        repeatActionStore.clear();
        repeatTracker.clear();
        channelStates.clear();
        lastPrivatePartner.clear();
        announced.clear();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        new GameChatCommands(this, languageService).register(lifecycleManager);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatRoute(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        boolean gameOnly = directory.gameOnly(sender.getUniqueId());
        String original = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        if (!gameOnly && menuChatInput.test(sender.getUniqueId(), original)) {
            event.setCancelled(true);
            return;
        }
        if (!directory.eligible(sender)) {
            event.setCancelled(true);
            return;
        }
        ChatChannelState state = gameOnly ? ChatChannelState.publicChannel()
                : channelStates.getOrDefault(sender.getUniqueId(), ChatChannelState.publicChannel());
        var route = resolveChannel(sender, state);
        if (route == null) {
            event.setCancelled(true);
            return;
        }
        var expected = directory.gameRecipients(route.audience(), sender.getUniqueId(), Optional.empty());
        Set<Player> channelAudience = GameChatService.sessions(expected.values());
        event.viewers().removeIf(viewer -> viewer instanceof Player
                ? !channelAudience.contains(viewer)
                : state.channel() != ChatChannel.PUBLIC || !directory.canSee(null, sender.getUniqueId()));
        Set<Player> viewers = playersIn(event.viewers());
        // A delayed event becomes a new game draft, but cannot escape a restriction already applied to it.
        var scope = !gameOnly && GameChatService.sameSessions(viewers, channelAudience)
                ? GameChatService.Scope.CURRENT : GameChatService.Scope.limited(viewers);
        if (!allowGameMessage(sender) || delayScheduler.queue(sender, event.message(), state, scope, null)) {
            event.setCancelled(true);
            return;
        }

        var draft = prepareMessage(sender, event.message(), route, scope.select(expected).keySet(), scope.mayShare());
        if (draft == null) {
            event.setCancelled(true);
            return;
        }
        boolean remoteDirect = scope.mayShare() && draft.message().targetId() != null
                && !expected.containsKey(draft.message().targetId());
        prepareGameEvent(event, draft, channelAudience, scope.mayShare(), remoteDirect, gameOnly);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatCommit(AsyncChatEvent event) {
        PendingChatTransaction transaction = pendingTransactions.remove(event);
        if (transaction != null && !event.isCancelled()) {
            commit(event.getPlayer(), transaction, event.message(), playersIn(event.viewers()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        settingsStore.get(player.getUniqueId());
        sessions.joined(player);
        if (directory.eligible(player) && announced.add(player)) publishPresence(player, true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        if (announced.remove(player)) publishPresence(player, false);
        channelStates.remove(playerId);
        lastPrivatePartner.remove(playerId);
        settingsStore.forget(playerId);
        delayScheduler.cancel(playerId);
        repeatTracker.forget(playerId);
        repeatActionStore.forgetPrivateActions(playerId);
        sessions.forget(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> informPrivateChannelsTargeting(playerId));
    }

    private void publishPresence(Player player, boolean joined) {
        if (!directory.allowed(player.getUniqueId(), player.getName())) return;
        messages.publishPresence(new ChatPresence(ChatMessageId.random(), Instant.now(), player.getUniqueId(),
                player.getName(), joined));
    }

    public void openSettingsMenu(Player player) {
        settingsMenu.open(player);
    }

    public MenuPanel settingsPanel(Player player, Runnable redraw) {
        return settingsMenu.panel(player, redraw);
    }

    private ResolvedChannel resolveChannel(Player sender, ChatChannelState state) {
        if (state.channel() == ChatChannel.STAFF && !directory.staff(sender)) {
            replyToCurrentSession(sender, Message.CHAT_STAFF_PERMISSION_MISSING, NamedTextColor.RED);
            return null;
        }
        UUID author = sender.getUniqueId();
        if (state.channel() != ChatChannel.PRIVATE) return new ResolvedChannel(ChatAudience.of(state.channel(), author, null), null);
        var peer = directory.peer(author, state.targetId());
        if (peer == null) {
            replyToCurrentSession(sender, Message.CHAT_PRIVATE_TARGET_OFFLINE, NamedTextColor.RED);
            return null;
        }
        return new ResolvedChannel(ChatAudience.of(state.channel(), author, peer.playerUuid()),
                ChatSender.minecraft(peer.playerUuid(), peer.name()));
    }

    private boolean wouldRepeat(ChatMessage message, String text) {
        UUID sender = message.sender().minecraftId().orElseThrow();
        return switch (message.channel()) {
            case PUBLIC -> repeatTracker.wouldRepeatPublic(sender, text);
            case STAFF -> repeatTracker.wouldRepeatStaff(sender, text);
            case PRIVATE -> repeatTracker.wouldRepeatPrivate(sender, message.targetId(), text);
        };
    }

    private ChatRepeatActionStore.PendingAction prepareRepeat(ChatMessage message, String text) {
        return switch (message.channel()) {
            case PUBLIC -> repeatActionStore.preparePublic(text);
            case STAFF -> repeatActionStore.prepareStaff(text);
            case PRIVATE -> repeatActionStore.preparePrivate(text, message.sender().minecraftId().orElseThrow(), message.targetId());
        };
    }

    /** Retain parsed labels so unchanged subtrees keep their semantics after other plugins edit the event. */
    private void prepareGameEvent(AsyncChatEvent event, PreparedChat draft, Set<Player> channelAudience,
            boolean fullAudience, boolean remoteDirect, boolean gameOnly) {
        ChatMessage message = draft.message();
        var repeat = fullAudience && !remoteDirect && wouldRepeat(message, draft.source())
                && directory.globallyVisible(message.audience(), message.sender().minecraftId().orElseThrow())
                ? new NativeRepeat(prepareRepeat(message, draft.source())) : null;
        Map<Component, ChatNode> renderedNodes = new IdentityHashMap<>();
        Component renderedBody = formatter.content(message.content(), draft.presentation(),
                sessions.viewerLanguage(event.getPlayer()), renderedNodes::put);
        event.message(renderedBody);
        pendingTransactions.put(event, new PendingChatTransaction(draft, renderedBody, renderedNodes, channelAudience, repeat, gameOnly));
        event.renderer((sender, _, rendered, viewer) -> {
            boolean edited = !renderedBody.equals(rendered);
            var language = sessions.viewerLanguage(viewer);
            Key action = !edited && repeat != null && repeat.visible ? repeat.action.identifier() : null;
            var line = formatter.message(message, draft.presentation(), language, rendered, action,
                    edited ? PlainTextComponentSerializer.plainText().serialize(rendered) : draft.source());
            if (!remoteDirect || viewer != sender) return line;
            var pending = Component.text(languageService.t(language, Message.CHAT_PRIVATE_WEB_PENDING) + " ", NamedTextColor.YELLOW);
            return Component.empty().append(pending).append(line);
        });
    }

    private void commit(Player sender, PendingChatTransaction transaction, Component finalBody, Set<Player> finalViewers) {
        var draft = transaction.draft();
        ChatMessage message = draft.message();
        boolean edited = !transaction.renderedBody().equals(finalBody);
        if (edited) {
            try {
                var content = componentImporter.importFinal(finalBody, transaction.renderedNodes());
                if (content.plainText().isBlank()) return;
                message = message.withContent(content);
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().log(Level.WARNING, "Could not interpret final game chat " + message.id().value(), invalid);
                return;
            }
        }
        ChatMessage accepted = message;
        var scope = transaction.gameOnly() ? GameChatService.Scope.limited(finalViewers)
                : GameChatService.Scope.nativeEvent(finalViewers, transaction.channelAudience());
        var repeat = transaction.repeat();
        if (repeat != null) repeat.visible = !edited && scope.mayShare();
        publish(sender, accepted, scope, recipients -> {
            if (recipients.players().isEmpty()) return;
            rememberPrivateConversation(accepted, recipients);
            if (!edited && recipients.unrestricted()) {
                recordRepeat(accepted, draft.source());
                if (repeat != null && repeat.visible) repeatActionStore.commit(repeat.action);
            }
            mentionNotifier.notifyRecipients(accepted.sender(), draft.presentation().authorColor(),
                    ChatMentions.fromContent(accepted.content()), recipients.players().values());
        });
    }

    void toggleStaff(Player player) {
        ChatChannelState current = channelStates.getOrDefault(player.getUniqueId(), ChatChannelState.publicChannel());
        if (current.channel() == ChatChannel.STAFF) {
            switchPublic(player);
            return;
        }
        channelStates.put(player.getUniqueId(), ChatChannelState.staff());
        player.sendMessage(Component.text(languageService.t(player, Message.STAFFCHAT_ENTER), NamedTextColor.GREEN));
    }

    void switchPublic(Player player) {
        channelStates.put(player.getUniqueId(), ChatChannelState.publicChannel());
        player.sendMessage(Component.text(languageService.t(player, Message.CHAT_PUBLIC_ENTER), NamedTextColor.YELLOW));
    }

    void switchPrivateOrShowUsage(Player player) {
        ChatChannelState current = channelStates.get(player.getUniqueId());
        if (current != null && current.channel() == ChatChannel.PRIVATE) {
            switchPublic(player);
            return;
        }
        player.sendMessage(Component.text("/msg <player> [message]", NamedTextColor.YELLOW));
    }

    void switchPrivate(Player sender, String targetName) {
        Participant target = directory.findPeer(sender.getUniqueId(), targetName);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_NOT_FOUND, targetName), NamedTextColor.RED)); return;
        }
        if (target.playerUuid().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_SELF), NamedTextColor.RED)); return;
        }
        ChatChannelState current = channelStates.get(sender.getUniqueId());
        if (current != null && current.channel() == ChatChannel.PRIVATE && target.playerUuid().equals(current.targetId())) {
            switchPublic(sender); return;
        }
        channelStates.put(sender.getUniqueId(), ChatChannelState.privateChannel(target.playerUuid(), target.name()));
        sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_ENTER, target.name()), NamedTextColor.GREEN));
    }

    void sendTemporaryPrivateCommand(Player sender, String targetName, String plainMessage) {
        Participant target = directory.findPeer(sender.getUniqueId(), targetName);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_NOT_FOUND, targetName), NamedTextColor.RED)); return;
        }
        if (target.playerUuid().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_SELF), NamedTextColor.RED)); return;
        }
        submitCommand(sender, plainMessage, ChatChannelState.privateChannel(target.playerUuid(), target.name()),
                isPublicChannel(sender) ? privateCommandSent : null);
    }

    private boolean isPublicChannel(Player player) {
        ChatChannelState state = channelStates.get(player.getUniqueId());
        return state == null || state.channel() == ChatChannel.PUBLIC;
    }

    void sendTemporaryPublicCommand(Player sender, String plainMessage) {
        submitCommand(sender, plainMessage, ChatChannelState.publicChannel(), null);
    }


    void sendStaffCommand(Player sender, String plainMessage) {
        if (!directory.staff(sender)) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            return;
        }
        submitCommand(sender, plainMessage, ChatChannelState.staff(), null);
    }


    void switchReply(Player sender) {
        UUID targetId = lastPrivatePartner.get(sender.getUniqueId());
        Participant target = targetId == null ? null : directory.peer(sender.getUniqueId(), targetId);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_NO_REPLY_TARGET), NamedTextColor.RED));
            return;
        }
        switchPrivate(sender, target.name());
    }

    void sendReplyCommand(Player sender, String plainMessage) {
        UUID targetId = lastPrivatePartner.get(sender.getUniqueId());
        Participant target = targetId == null ? null : directory.peer(sender.getUniqueId(), targetId);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_NO_REPLY_TARGET), NamedTextColor.RED));
            return;
        }
        submitCommand(sender, plainMessage, ChatChannelState.privateChannel(target.playerUuid(), target.name()), null);
    }

    private PreparedChat prepareMessage(Player sender, Component body, ResolvedChannel route, Set<UUID> gameViewers,
            boolean mayShare) {
        UUID author = sender.getUniqueId();
        var mentions = directory.mentions(author, route.audience(), gameViewers, mayShare);
        var inputState = sessions.input(sender);
        ChatProcessingContext context = inputState.context(mentions);
        if (mayShare && route.audience().channel() == ChatChannel.PUBLIC && directory.globallyVisible(route.audience(), author))
            context = context.withBoundPlayerMentions();
        var input = componentImporter.importComponent(body);
        String source = input.plainText().strip();
        if (source.isEmpty() || source.length() > 4_000) return null;
        var parsed = directory.gameOnly(author) ? ChatContent.plain(source) : processor.process(input, context);
        if (parsed.plainText().isBlank()) return null;
        var message = new ChatMessage(ChatMessageId.random(), ChatOrigin.Player.MINECRAFT,
                ChatSender.minecraft(author, sender.getName()), route.audience().channel(), route.target(),
                Instant.now(), parsed);
        return new PreparedChat(message, source, sessions.presentation(message, inputState.hovers()));
    }

    private Set<Player> playersIn(Set<Audience> viewers) {
        return GameChatService.sessions(viewers.stream().filter(Player.class::isInstance).map(Player.class::cast).toList());
    }

    private void recordPrivatePartners(UUID sender, UUID target) {
        lastPrivatePartner.put(sender, target);
        lastPrivatePartner.put(target, sender);
        if (lastPrivatePartner.size() > 2048) lastPrivatePartner.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void sendDelayedPreview(Player sender, Component body, ChatChannelState state, GameChatService.Scope scope) {
        UUID author = sender.getUniqueId();
        var audience = ChatAudience.of(state.channel(), author, state.targetId());
        var expected = directory.gameRecipients(audience, author, Optional.empty());
        var target = state.targetId() == null ? null : ChatSender.minecraft(state.targetId(), state.targetName());
        var draft = prepareMessage(sender, body, new ResolvedChannel(audience, target),
                scope.select(expected).keySet(), scope.mayShare());
        if (draft == null) return;
        sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_DELAY_PREVIEW_MARKER) + " ", NamedTextColor.YELLOW)
                .append(formatter.message(draft.message(), draft.presentation(), sessions.viewerLanguage(sender),
                        null, draft.source())));
    }

    private void submitCommand(Player player, String text, ChatChannelState state, Consumer<Player> onAccepted) {
        if (directory.gameOnly(player.getUniqueId()) || !directory.eligible(player) || !allowGameMessage(player)) return;
        var body = Component.text(text);
        var scope = GameChatService.Scope.CURRENT;
        if (!delayScheduler.queue(player, body, state, scope, onAccepted))
            sendMessage(player, body, state, scope, onAccepted);
    }

    private void sendMessage(Player sender, Component body, ChatChannelState state, GameChatService.Scope scope,
            Consumer<Player> onAccepted) {
        if (!directory.eligible(sender)) return;
        if (directory.gameOnly(sender.getUniqueId()) && (state.channel() != ChatChannel.PUBLIC || scope.mayShare())) return;
        var route = resolveChannel(sender, state);
        if (route == null) return;
        var expected = directory.gameRecipients(route.audience(), sender.getUniqueId(), Optional.empty());
        var draft = prepareMessage(sender, body, route, scope.select(expected).keySet(), scope.mayShare());
        if (draft == null) return;
        var message = draft.message();
        publish(sender, message, scope, recipients -> {
            display(message, draft.presentation(), recipients, ChatMentions.fromContent(message.content()), draft.source());
            if (onAccepted != null && !recipients.players().isEmpty()) onAccepted.accept(sender);
        });
    }

    private void publish(Player sender, ChatMessage message, GameChatService.Scope scope,
            Consumer<GameChatService.Recipients> output) {
        messages.publish(message, sender, scope, output, outcome -> sendPrivateWebResult(sender, message.target(), outcome));
    }

    private void sendPrivateWebResult(Player sender, ChatSender target, Outcome outcome) {
        if (outcome == Outcome.SAVED)
            recordPrivatePartners(sender.getUniqueId(), target.minecraftId().orElseThrow());
        Message message = switch (outcome) {
            case SAVED -> Message.CHAT_PRIVATE_WEB_SAVED;
            case NOT_SUBMITTED -> Message.CHAT_PRIVATE_WEB_NOT_SUBMITTED;
            case UNCONFIRMED -> Message.CHAT_PRIVATE_WEB_UNCONFIRMED;
        };
        var color = outcome == Outcome.SAVED ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        sender.sendMessage(Component.text(languageService.t(sender, message, target.minecraftName()), color));
    }

    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)
                || repeatActionStore.resolve(event.getIdentifier()).isEmpty()) return;
        Runnable repeat = () -> {
            if (!plugin.isEnabled() || !connection.isConnected()) return;
            Player player = connection.getPlayer();
            if (!player.isOnline() || directory.gameOnly(player.getUniqueId())) return;
            repeatActionStore.resolve(event.getIdentifier()).ifPresent(action -> {
                switch (action.channel()) {
                    case PUBLIC -> sendTemporaryPublicCommand(player, action.message());
                    case STAFF -> sendStaffCommand(player, action.message());
                    case PRIVATE -> repeatPrivateMessage(player, action);
                }
            });
        };
        if (Bukkit.isPrimaryThread()) repeat.run();
        else Bukkit.getScheduler().runTask(plugin, repeat);
    }

    private void repeatPrivateMessage(Player player, ChatRepeatActionStore.RepeatAction action) {
        UUID targetId = action.privateTargetFor(player.getUniqueId());
        if (targetId == null) {
            return;
        }
        Participant target = directory.peer(player.getUniqueId(), targetId);
        if (target == null) {
            player.sendMessage(Component.text(languageService.t(player, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
            return;
        }
        submitCommand(player, action.message(), ChatChannelState.privateChannel(target.playerUuid(), target.name()), null);
    }

    void cancelDelayedMessages(Player player) {
        int count = delayScheduler.cancel(player.getUniqueId());
        Message message = count > 0 ? Message.CHAT_DELAY_CANCELLED : Message.CHAT_DELAY_NOTHING_TO_CANCEL;
        NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        player.sendMessage(Component.text(languageService.t(player, message), color));
    }

    private void informPrivateChannelsTargeting(UUID quitterId) {
        if (directory.peer(quitterId, quitterId) != null) return;
        for (var entry : channelStates.entrySet()) {
            if (entry.getValue().channel() != ChatChannel.PRIVATE || !quitterId.equals(entry.getValue().targetId())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.sendMessage(Component.text(languageService.t(player, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
        }
    }

    private boolean allowGameMessage(Player player) {
        if (allowance.test(player.getUniqueId())) return true;
        replyToCurrentSession(player, Message.CHAT_RATE_LIMITED, NamedTextColor.YELLOW);
        return false;
    }

    private void replyToCurrentSession(Player player, Message text, NamedTextColor color) {
        UUID id = player.getUniqueId();
        Runnable reply = () -> {
            if (active && Bukkit.getPlayer(id) == player && player.isOnline())
                player.sendMessage(languageService.text(player, text, color));
        };
        if (Bukkit.isPrimaryThread()) reply.run();
        else Bukkit.getScheduler().runTask(plugin, reply);
    }

    List<String> onlinePeerNames(CommandSender viewer) {
        return viewer instanceof Player player
                ? directory.peers(player.getUniqueId()).stream().map(Participant::name).toList()
                : List.of();
    }

    /** The bridge only decodes. The game rechecks current sessions and visibility before display. */
    public void deliverRouted(ChatDelivery delivery) {
        var recipients = messages.routedRecipients(delivery);
        if (recipients == null) return;
        var message = delivery.message();
        display(message, sessions.presentation(message, Map.of()), recipients, delivery.mentions(), null);
    }

    public void deliverWebPresence(ChatPresence notice, Optional<Set<UUID>> visibility) {
        Message message = notice.joined() ? Message.WEBCHAT_JOINED : Message.WEBCHAT_LEFT;
        for (var viewer : messages.presenceRecipients(notice, visibility).values()) {
            viewer.sendMessage(Component.text(languageService.t(viewer, message, notice.name()), NamedTextColor.AQUA));
        }
    }

    private void display(ChatMessage message, Presentation presentation, GameChatService.Recipients recipients,
            ChatMentions mentions, String original) {
        var viewers = recipients.players().values();
        boolean consoleOutput = recipients.unrestricted() && message.channel() == ChatChannel.PUBLIC;
        if (viewers.isEmpty() && !consoleOutput) return;
        rememberPrivateConversation(message, recipients);
        String repeatText = original == null ? message.content().plainText() : original;
        Key repeat = recipients.unrestricted() && message.origin() instanceof ChatOrigin.Player
                && recordRepeat(message, repeatText) ? createRepeatIdentifier(message, repeatText) : null;
        if (consoleOutput) {
            var console = plugin.getServer().getConsoleSender();
            console.sendMessage(formatter.message(message, presentation, Language.DEFAULT, repeat, original));
        }
        for (Player viewer : viewers) {
            try { viewer.sendMessage(formatter.message(message, presentation, sessions.viewerLanguage(viewer), repeat, original)); }
            catch (RuntimeException failed) {
                plugin.getLogger().log(Level.WARNING, "Could not render chat " + message.id().value() + " for a game connection", failed);
            }
        }
        mentionNotifier.notifyRecipients(message.sender(), presentation.authorColor(), mentions, viewers);
    }

    private void rememberPrivateConversation(ChatMessage message, GameChatService.Recipients recipients) {
        if (message.channel() == ChatChannel.PRIVATE
                && (recipients.players().containsKey(message.targetId()) || message.origin() == ChatOrigin.Player.WEB))
            recordPrivatePartners(message.sender().minecraftId().orElseThrow(), message.targetId());
    }

    private boolean recordRepeat(ChatMessage message, String source) {
        UUID sender = message.sender().minecraftId().orElseThrow();
        return switch (message.channel()) {
            case PUBLIC -> repeatTracker.recordPublic(sender, source);
            case STAFF -> repeatTracker.recordStaff(sender, source);
            case PRIVATE -> repeatTracker.recordPrivate(sender, message.targetId(), source);
        };
    }

    private Key createRepeatIdentifier(ChatMessage message, String source) {
        ChatRepeatActionStore.PendingAction pending = prepareRepeat(message, source);
        repeatActionStore.commit(pending);
        return pending.identifier();
    }

    Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    private record ResolvedChannel(ChatAudience audience, ChatSender target) { }
    private record PreparedChat(ChatMessage message, String source, Presentation presentation) { }
    private record PendingChatTransaction(
            PreparedChat draft,
            Component renderedBody,
            Map<Component, ChatNode> renderedNodes,
            Set<Player> channelAudience,
            NativeRepeat repeat,
            boolean gameOnly
    ) { }

    private static final class NativeRepeat {
        private final ChatRepeatActionStore.PendingAction action;
        private volatile boolean visible;
        private NativeRepeat(ChatRepeatActionStore.PendingAction action) { this.action = action; }
    }
}
