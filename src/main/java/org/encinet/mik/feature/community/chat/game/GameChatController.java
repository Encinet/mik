package org.encinet.mik.feature.community.chat.game;

import org.encinet.mik.feature.community.chat.settings.ChatSettingsStore;
import org.encinet.mik.shared.role.RolePermissions;
import org.encinet.mik.feature.community.chat.bridge.ChatParticipants;
import org.encinet.mik.feature.community.chat.bridge.ChatSendAllowance;
import org.encinet.mik.feature.community.chat.model.ChatDelivery;
import org.encinet.mik.feature.community.chat.runtime.ChatService;
import org.encinet.mik.feature.community.chat.pipeline.AdventureComponentImporter;
import org.encinet.mik.feature.community.chat.model.ChatAudience;
import org.encinet.mik.feature.community.chat.model.ChatParticipant;
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
import org.encinet.mik.feature.community.chat.model.ChatConversation;
import org.encinet.mik.feature.community.chat.model.ChatMessage;
import org.encinet.mik.feature.community.chat.model.ChatMessageId;
import org.encinet.mik.feature.community.chat.model.ChatOrigin;
import org.encinet.mik.feature.community.chat.model.ChatProcessingContext;
import org.encinet.mik.feature.community.chat.model.ChatReferences;
import org.encinet.mik.feature.community.chat.model.ChatSender;
import org.encinet.mik.feature.community.chat.model.ChatSubmission;
import org.encinet.mik.feature.community.chat.render.ChatMessageFormatter;
import org.encinet.mik.shared.i18n.Language;
import org.encinet.mik.shared.i18n.LanguageService;
import org.encinet.mik.shared.i18n.Message;
import org.encinet.mik.feature.player.profile.PlayerSourceRenderer;
import org.encinet.mik.feature.player.profile.PlayerNameTagRenderer;
import org.encinet.mik.feature.player.guidance.TipScope;
import org.encinet.mik.feature.community.identity.IdentityBindingManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.BiPredicate;

public final class GameChatController implements Listener {

    private ChatParticipants participants;
    private ChatSendAllowance allowance;
    private final ChatService messages;
    private final AdventureComponentImporter componentImporter = new AdventureComponentImporter();

    private final Plugin plugin;
    private final LanguageService languageService;
    private final GameMentionNotifier mentionNotifier;
    private final ChatSettingsStore settingsStore;
    private final BukkitChatContextFactory contextFactory;
    private final ChatSettingsMenu settingsMenu;
    private final ChatMessageFormatter formatter;
    private final GameChatIdentityResolver playerNames;
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

    public GameChatController(Plugin plugin, GameMentionNotifier mentionNotifier,
                      LanguageService languageService,
                      ChatSettingsStore settingsStore,
                      PlayerNameTagRenderer nameTags, PlayerSourceRenderer sources,
                      GameChatIdentityResolver playerNames, ChatService messages,
                      IdentityBindingManager identityBindings,
                      TipScope tips, BiPredicate<UUID, String> menuChatInput) {
        this.plugin = plugin;
        this.playerNames = java.util.Objects.requireNonNull(playerNames, "playerNames");
        this.languageService = languageService;
        this.mentionNotifier = mentionNotifier;
        this.settingsStore = settingsStore;
        this.menuChatInput = java.util.Objects.requireNonNull(menuChatInput, "menuChatInput");
        this.contextFactory = new BukkitChatContextFactory(plugin, identityBindings);
        this.messages = java.util.Objects.requireNonNull(messages, "messages");
        this.settingsMenu = new ChatSettingsMenu(languageService, settingsStore);
        this.formatter = new ChatMessageFormatter(languageService, nameTags, sources, contextFactory::onlinePlayer);
        this.delayScheduler = new ChatDelayScheduler(plugin, languageService, settingsStore,
                this::sendDelayedMessage, this::sendDelayedPreview);
        privateCommandSent = ChatTips.privateChannel(plugin, tips, this::isPublicChannel);
    }

    public void enable(ChatParticipants participants, ChatSendAllowance allowance) {
        this.participants = java.util.Objects.requireNonNull(participants, "participants");
        this.allowance = java.util.Objects.requireNonNull(allowance, "allowance");
        contextFactory.enable();
        for (Player player : Bukkit.getOnlinePlayers()) {
            settingsStore.get(player.getUniqueId());
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ChatModule enabled");
    }

    public void disable() {
        HandlerList.unregisterAll(this);
        contextFactory.close();
        delayScheduler.cancelAll();
        pendingTransactions.clear();
        repeatActionStore.clear();
        repeatTracker.clear();
        channelStates.clear();
        lastPrivatePartner.clear();
    }

    public void registerCommands(LifecycleEventManager<Plugin> lifecycleManager) {
        new GameChatCommands(this, languageService).register(lifecycleManager);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatRoute(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        String original = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        if (menuChatInput.test(sender.getUniqueId(), original)) {
            event.setCancelled(true);
            return;
        }
        ChatChannelState state = channelStates.getOrDefault(sender.getUniqueId(), ChatChannelState.publicChannel());
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (delayScheduler.queue(sender, plainMessage, state)) {
            event.setCancelled(true);
            return;
        }

        if (!allowGameMessage(sender)) { event.setCancelled(true); return; }
        routeGameEvent(event, sender, state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChatCommit(AsyncChatEvent event) {
        PendingChatTransaction transaction = pendingTransactions.remove(event);
        if (transaction != null && !event.isCancelled()) {
            commit(transaction, event.message(), playersIn(event.viewers()).stream()
                    .map(Player::getUniqueId).collect(java.util.stream.Collectors.toUnmodifiableSet()));
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
        plugin.getServer().getScheduler().runTask(plugin, () -> clearPrivateChannelsTargeting(playerId));
    }

    public void openSettingsMenu(Player player) {
        settingsMenu.open(player);
    }

    public MenuPanel settingsPanel(Player player, Runnable redraw) {
        return settingsMenu.panel(player, redraw);
    }

    private void routeGameEvent(AsyncChatEvent event, Player sender, ChatChannelState state) {
        if (state.channel() == ChatChannel.STAFF && !RolePermissions.canModerate(sender)) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            event.setCancelled(true); return;
        }
        ChatParticipant peer = state.channel() == ChatChannel.PRIVATE
                ? participants.peer(sender.getUniqueId(), state.targetId()) : null;
        if (state.channel() == ChatChannel.PRIVATE && peer == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
            event.setCancelled(true); return;
        }
        Player target = peer != null && peer.game() ? contextFactory.onlinePlayer(peer.playerUuid()) : null;
        Set<Player> viewers = switch (state.channel()) {
            case PUBLIC -> playersIn(event.viewers());
            case STAFF -> contextFactory.staffPlayers();
            case PRIVATE -> target == null ? Set.of(sender) : Set.of(sender, target);
        };
        if (state.channel() != ChatChannel.PUBLIC) {
            event.viewers().clear(); event.viewers().addAll(viewers);
        }
        UUID targetId = peer == null ? null : peer.playerUuid();
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatMessage message = processMessage(sender, text, conversation(sender.getUniqueId(), state), viewers, targetId);
        ChatRepeatActionStore.PendingAction repeat = wouldRepeat(message, targetId) ? prepareRepeat(message, targetId) : null;
        prepareGameEvent(event, message, targetId == null ? null : playerSnapshot(targetId, peer.name()), repeat);
    }

    private ChatConversation conversation(UUID sender, ChatChannelState state) {
        return switch (state.channel()) {
            case PUBLIC -> ChatConversation.minecraftPublic();
            case STAFF -> ChatConversation.minecraftStaff();
            case PRIVATE -> ChatConversation.minecraftPrivate(sender, state.targetId());
        };
    }

    private boolean wouldRepeat(ChatMessage message, UUID target) {
        UUID sender = message.submission().sender().minecraftId().orElseThrow();
        String text = message.submission().sourceText();
        return switch (message.submission().conversation().channel()) {
            case PUBLIC -> repeatTracker.wouldRepeatPublic(sender, text);
            case STAFF -> repeatTracker.wouldRepeatStaff(sender, text);
            case PRIVATE -> repeatTracker.wouldRepeatPrivate(sender, target, text);
        };
    }

    private ChatRepeatActionStore.PendingAction prepareRepeat(ChatMessage message, UUID target) {
        String text = message.submission().sourceText();
        return switch (message.submission().conversation().channel()) {
            case PUBLIC -> repeatActionStore.preparePublic(text);
            case STAFF -> repeatActionStore.prepareStaff(text);
            case PRIVATE -> repeatActionStore.preparePrivate(text, message.submission().sender().minecraftId().orElseThrow(), target);
        };
    }

    /** Retain the complete message while Paper decides the final body and audience. */
    private void prepareGameEvent(AsyncChatEvent event, ChatMessage message,
                                  ChatSender target, ChatRepeatActionStore.PendingAction repeat) {
        event.message(formatter.content(message.content(), event.getPlayer()));
        UUID targetId = target == null ? null : target.minecraftId().orElseThrow();
        pendingTransactions.put(event, new PendingChatTransaction(
                message, event.message(), targetId, target == null ? "" : target.minecraftName(),
                participants.gameRecipients(ChatAudience.of(message, target)), repeat));
        Key repeatIdentifier = repeat == null ? null : repeat.identifier();
        event.renderer((_, _, rendered, viewer) ->
                formatter.message(message, target, viewer, rendered, repeatIdentifier));
    }

    private void commit(PendingChatTransaction transaction, Component finalBody, Set<UUID> finalViewers) {
        ChatMessage message = transaction.message();
        recordPlayerMessage(message, transaction.privateTarget());
        if (transaction.repeatAction() != null) repeatActionStore.commit(transaction.repeatAction());
        ChatMessage accepted = transaction.renderedBody().equals(finalBody) ? message
                : messages.acceptFinalContent(message, componentImporter.importComponent(finalBody));
        ChatSender target = transaction.privateTarget() == null ? null
                : playerSnapshot(transaction.privateTarget(), transaction.targetName());
        messages.publish(ChatDelivery.nativeGame(accepted, target, finalViewers, transaction.channelAudience()));
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
        ChatParticipant target = participants.findPeer(sender.getUniqueId(), targetName);
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
        recordPrivatePartners(sender.getUniqueId(), target.playerUuid());
        sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_ENTER, target.name()), NamedTextColor.GREEN));
    }

    void sendTemporaryPrivateCommand(Player sender, String targetName, String plainMessage) {
        ChatParticipant target = participants.findPeer(sender.getUniqueId(), targetName);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_NOT_FOUND, targetName), NamedTextColor.RED)); return;
        }
        if (target.playerUuid().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_SELF), NamedTextColor.RED)); return;
        }
        boolean fromPublic = isPublicChannel(sender);
        if (delayScheduler.queue(sender, plainMessage, ChatChannelState.privateChannel(target.playerUuid(), target.name()),
                fromPublic ? privateCommandSent : null)) {
            recordPrivatePartners(sender.getUniqueId(), target.playerUuid()); return;
        }
        sendMessage(sender, plainMessage, ChatChannelState.privateChannel(target.playerUuid(), target.name()));
        if (fromPublic) privateCommandSent.accept(sender);
    }

    private boolean isPublicChannel(Player player) {
        ChatChannelState state = channelStates.get(player.getUniqueId());
        return state == null || state.channel() == ChatChannel.PUBLIC;
    }

    void sendTemporaryPublicCommand(Player sender, String plainMessage) {
        if (delayScheduler.queue(sender, plainMessage, ChatChannelState.publicChannel())) {
            return;
        }
        sendMessage(sender, plainMessage, ChatChannelState.publicChannel());
    }


    void sendStaffCommand(Player sender, String plainMessage) {
        if (!RolePermissions.canModerate(sender)) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            return;
        }

        if (delayScheduler.queue(sender, plainMessage, ChatChannelState.staff())) {
            return;
        }
        sendMessage(sender, plainMessage, ChatChannelState.staff());
    }


    void switchReply(Player sender) {
        UUID targetId = lastPrivatePartner.get(sender.getUniqueId());
        ChatParticipant target = targetId == null ? null : participants.peer(sender.getUniqueId(), targetId);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_NO_REPLY_TARGET), NamedTextColor.RED));
            return;
        }
        switchPrivate(sender, target.name());
    }

    void sendReplyCommand(Player sender, String plainMessage) {
        UUID targetId = lastPrivatePartner.get(sender.getUniqueId());
        ChatParticipant target = targetId == null ? null : participants.peer(sender.getUniqueId(), targetId);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_NO_REPLY_TARGET), NamedTextColor.RED));
            return;
        }
        if (delayScheduler.queue(sender, plainMessage, ChatChannelState.privateChannel(target.playerUuid(), target.name()))) {
            return;
        }
        sendMessage(sender, plainMessage, ChatChannelState.privateChannel(target.playerUuid(), target.name()));
    }

    private ChatMessage processMessage(
            Player sender,
            String source,
            ChatConversation conversation,
            Set<Player> channelPlayers,
            UUID privateTarget
    ) {
        ChatProcessingContext context = contextFactory.capture(
                sender, channelPlayers,
                conversation.channel() == ChatChannel.PUBLIC);
        ChatChannel channel = conversation.channel();
        var targets = participants.channelPeers(sender.getUniqueId(), channel, privateTarget).stream()
                .map(ChatParticipant::mentionTarget).toList();
        context = new ChatProcessingContext(context.capabilities(), context.mentions().including(targets),
                context.mainHand(), context.inventory());
        ChatSubmission submission = new ChatSubmission(
                ChatMessageId.random(),
                new ChatOrigin.Minecraft(sender.getUniqueId()),
                ChatSender.minecraft(sender.getUniqueId(), sender.getName()),
                conversation, source, ChatReferences.empty(), Instant.now(), context);
        return messages.process(submission);
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

    private void recordPrivatePartners(UUID sender, UUID target) {
        lastPrivatePartner.put(sender, target);
        lastPrivatePartner.put(target, sender);
        if (lastPrivatePartner.size() > 2048) lastPrivatePartner.keySet().removeIf(id -> contextFactory.onlinePlayer(id) == null);
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
        UUID targetId = state.channel() == ChatChannel.PRIVATE ? state.targetId() : null;
        Player target = targetId == null ? null : contextFactory.onlinePlayer(targetId);
        Set<Player> recipients = switch (state.channel()) {
            case PUBLIC -> new HashSet<>(Bukkit.getOnlinePlayers());
            case STAFF -> contextFactory.staffPlayers();
            case PRIVATE -> target == null ? Set.of(sender) : Set.of(sender, target);
        };
        ChatConversation conversation = conversation(sender.getUniqueId(), state);
        ChatMessage message = processMessage(sender, plainMessage, conversation, recipients, targetId);
        return formatter.message(message, targetId == null ? null : playerSnapshot(targetId, state.targetName()),
                sender, null);
    }

    private boolean sendDelayedMessage(Player sender, String text, ChatChannelState state) {
        return sendMessage(sender, text, state);
    }

    private boolean sendMessage(Player sender, String text, ChatChannelState state) {
        if (!allowGameMessage(sender)) return false;
        if (state.channel() == ChatChannel.STAFF && !RolePermissions.canModerate(sender)) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            return false;
        }
        ChatParticipant peer = state.channel() == ChatChannel.PRIVATE
                ? participants.peer(sender.getUniqueId(), state.targetId()) : null;
        if (state.channel() == ChatChannel.PRIVATE && peer == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
            return false;
        }
        UUID targetId = peer == null ? null : peer.playerUuid();
        Player target = peer != null && peer.game() ? Bukkit.getPlayer(targetId) : null;
        Set<Player> viewers = switch (state.channel()) {
            case PUBLIC -> Set.copyOf(Bukkit.getOnlinePlayers());
            case STAFF -> contextFactory.staffPlayers();
            case PRIVATE -> target == null ? Set.of(sender) : Set.of(sender, target);
        };
        ChatMessage message = processMessage(sender, text, conversation(sender.getUniqueId(), state), viewers, targetId);
        messages.publish(ChatDelivery.managed(message, targetId == null ? null : playerSnapshot(targetId, peer.name()), null));
        return true;
    }




    @EventHandler
    public void onCustomClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)
                || repeatActionStore.resolve(event.getIdentifier()).isEmpty()) return;
        Runnable repeat = () -> {
            if (!plugin.isEnabled() || !connection.isConnected()) return;
            Player player = connection.getPlayer();
            if (!player.isOnline()) return;
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
        ChatParticipant target = participants.peer(player.getUniqueId(), targetId);
        if (target == null) {
            player.sendMessage(Component.text(languageService.t(player, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
            return;
        }
        if (delayScheduler.queue(player, action.message(), ChatChannelState.privateChannel(target.playerUuid(), target.name()))) {
            return;
        }
        sendMessage(player, action.message(), ChatChannelState.privateChannel(target.playerUuid(), target.name()));
    }

    void cancelDelayedMessages(Player player) {
        int count = delayScheduler.cancel(player.getUniqueId());
        Message message = count > 0 ? Message.CHAT_DELAY_CANCELLED : Message.CHAT_DELAY_NOTHING_TO_CANCEL;
        NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        player.sendMessage(Component.text(languageService.t(player, message), color));
    }

    private void clearPrivateChannelsTargeting(UUID quitterId) {
        if (participants.peer(quitterId, quitterId) != null) return;
        for (var entry : channelStates.entrySet()) {
            if (entry.getValue().channel() != ChatChannel.PRIVATE || !quitterId.equals(entry.getValue().targetId())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) player.sendMessage(Component.text(languageService.t(player, Message.CHAT_PRIVATE_TARGET_OFFLINE), NamedTextColor.RED));
        }
    }

    private boolean allowGameMessage(Player player) {
        if (allowance.allowMessage(player.getUniqueId())) return true;
        player.sendMessage(Component.text(languageService.t(player, Message.CHAT_RATE_LIMITED), NamedTextColor.YELLOW));
        return false;
    }

    List<String> onlinePeerNames(CommandSender viewer) {
        return viewer instanceof Player player
                ? participants.peers(player.getUniqueId()).stream().map(ChatParticipant::name).toList()
                : List.of();
    }

    /** Applies game rendering and effects after the shared commit. */
    public void deliver(ChatDelivery delivery, Set<UUID> recipientIds) {
        ChatMessage message = delivery.message();
        Set<Player> viewers = recipientIds.stream().map(Bukkit::getPlayer).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (delivery.game() instanceof ChatDelivery.Managed) {
            ChatSender target = delivery.target();
            UUID targetId = target == null ? null : target.minecraftId().orElseThrow();
            Key repeat = message.submission().origin() instanceof ChatOrigin.Player
                    && recordPlayerMessage(message, targetId) ? createRepeatIdentifier(message, targetId) : null;
            if (target != null) target = playerSnapshot(targetId, target.minecraftName());
            if (message.submission().conversation().channel() == ChatChannel.PUBLIC) {
                Audience console = plugin.getServer().getConsoleSender();
                console.sendMessage(formatter.message(message, target, console, repeat));
            }
            for (Player viewer : viewers) {
                try { viewer.sendMessage(formatter.message(message, target, viewer, repeat)); }
                catch (RuntimeException failed) { plugin.getLogger().warning("Could not render chat for a game connection"); }
            }
        }
        mentionNotifier.notifyEffects(message.submission().sender(), message.effects(), viewers);
    }

    private boolean recordPlayerMessage(ChatMessage message, UUID target) {
        UUID sender = message.submission().sender().minecraftId().orElseThrow();
        String source = message.submission().sourceText();
        return switch (message.submission().conversation().channel()) {
            case PUBLIC -> repeatTracker.recordPublic(sender, source);
            case STAFF -> repeatTracker.recordStaff(sender, source);
            case PRIVATE -> {
                recordPrivatePartners(sender, target);
                yield repeatTracker.recordPrivate(sender, target, source);
            }
        };
    }

    private Key createRepeatIdentifier(ChatMessage message, UUID target) {
        ChatRepeatActionStore.PendingAction pending = prepareRepeat(message, target);
        repeatActionStore.commit(pending);
        return pending.identifier();
    }

    private ChatSender playerSnapshot(UUID id, String name) {
        return playerNames.snapshot(id, name);
    }

    Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        sender.sendMessage(Component.text(languageService.t(Language.DEFAULT, Message.PLAYER_ONLY), NamedTextColor.RED));
        return null;
    }

    private record PendingChatTransaction(
            ChatMessage message,
            Component renderedBody,
            UUID privateTarget,
            String targetName,
            Set<UUID> channelAudience,
            ChatRepeatActionStore.PendingAction repeatAction
    ) {
        private PendingChatTransaction {
            java.util.Objects.requireNonNull(message, "message");
            channelAudience = Set.copyOf(channelAudience);
        }
    }

}
