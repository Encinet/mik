package org.encinet.mik.module.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.chat.delay.ChatDelayScheduler;
import org.encinet.mik.module.chat.menu.ChatSettingsMenu;
import org.encinet.mik.module.chat.mention.MentionService;
import org.encinet.mik.module.chat.render.ChatMessageFormatter;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.MenuNavigation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChatModule implements Listener {

    private static final String STAFF_PERMISSION = "group." + Mik.GROUP_HELPER;

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final MentionService mentionService;
    private final ChatSettingsStore settingsStore;
    private final ChatMessageParser messageParser = new ChatMessageParser();
    private final ChatSettingsMenu settingsMenu;
    private final ChatMessageFormatter formatter;
    private final ChatDelayScheduler delayScheduler;
    private final Map<UUID, ChatChannelState> channelStates = new HashMap<>();
    private final Map<UUID, UUID> lastPrivatePartner = new HashMap<>();

    public ChatModule(JavaPlugin plugin, MentionService mentionService, LanguageService languageService,
                      ChatSettingsStore settingsStore, MenuNavigation menuNavigation) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.mentionService = mentionService;
        this.settingsStore = settingsStore;
        this.settingsMenu = new ChatSettingsMenu(plugin, languageService, mentionService, settingsStore, menuNavigation);
        this.formatter = new ChatMessageFormatter(plugin, languageService);
        this.delayScheduler = new ChatDelayScheduler(plugin, languageService, settingsStore,
                this::sendDelayedMessage, this::sendDelayedPreview);
    }

    public void enable() {
        formatter.enable();

        for (Player player : Bukkit.getOnlinePlayers()) {
            settingsStore.get(player.getUniqueId());
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("ChatModule enabled");
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

            commands.register(Commands.literal("chat")
                    .executes(ctx -> {
                        Player player = requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            switchPublic(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), languageService.t(Language.DEFAULT, Message.CHAT_PUBLIC_COMMAND_DESCRIPTION), List.of("global"));

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
        clearPrivateChannelsTargeting(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        settingsMenu.handleClick(event);
    }

    public void openSettingsMenu(Player player) {
        settingsMenu.open(player);
    }

    public List<Component> settingsSummary(Player player) {
        return settingsMenu.summary(player);
    }

    private void routePublic(AsyncChatEvent event, Player sender) {
        String copyText = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.message(parseMessage(sender, event.message(), playersIn(event.viewers())));
        event.renderer((source, sourceDisplayName, message, viewer) -> formatter.publicMessage(source, viewer, message, copyText));
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
        Set<Player> channelPlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(STAFF_PERMISSION)) {
                viewers.add(player);
                channelPlayers.add(player);
            }
        }
        String copyText = PlainTextComponentSerializer.plainText().serialize(event.message());
        event.message(parseMessage(sender, event.message(), channelPlayers));
        event.renderer((source, sourceDisplayName, message, viewer) -> formatter.staffMessage(source, viewer, message, copyText));
    }

    private void routePrivate(AsyncChatEvent event, Player sender, ChatChannelState state) {
        Player target = Bukkit.getPlayer(state.targetId());
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
        event.message(parseMessage(sender, event.message(), channelPlayers));
        touchPrivatePartners(sender, target);
        event.renderer((source, sourceDisplayName, message, viewer) -> formatter.privateMessage(source, target, viewer, message, copyText));
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

    private void sendPrivateMessage(Player sender, Player target, String plainMessage) {
        touchPrivatePartners(sender, target);
        Set<Player> channelPlayers = Set.of(sender, target);
        Component message = parseMessage(sender, plainMessage, channelPlayers);
        sender.sendMessage(formatter.privateMessage(sender, target, sender, message, plainMessage));
        target.sendMessage(formatter.privateMessage(sender, target, target, message, plainMessage));
        plugin.getServer().getConsoleSender().sendMessage(formatter.privateMessage(sender, target,
                plugin.getServer().getConsoleSender(), message, plainMessage));
        mentionService.notifyPrivateMessage(sender, plainMessage, target);
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
        Component message = parseMessage(sender, plainMessage, channelPlayers);
        plugin.getServer().getConsoleSender().sendMessage(formatter.staffMessage(sender,
                plugin.getServer().getConsoleSender(), message, plainMessage));
        for (Player player : channelPlayers) {
            player.sendMessage(formatter.staffMessage(sender, player, message, plainMessage));
        }
        mentionService.notifyMessage(sender, plainMessage, channelPlayers);
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

    private Component parseMessage(Player sender, Component message, Set<Player> channelPlayers) {
        return messageParser.parse(sender, message, channelPlayers,
                languageService.t(sender, Message.CHAT_ITEM_EMPTY_HOVER),
                languageService.t(sender, Message.CHAT_MENTION_ALL_HOVER),
                languageService.t(sender, Message.CHAT_BILIBILI_HOVER));
    }

    private Component parseMessage(Player sender, String message, Set<Player> channelPlayers) {
        return messageParser.parse(sender, message, channelPlayers,
                languageService.t(sender, Message.CHAT_ITEM_EMPTY_HOVER),
                languageService.t(sender, Message.CHAT_MENTION_ALL_HOVER),
                languageService.t(sender, Message.CHAT_BILIBILI_HOVER));
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
                yield formatter.publicMessage(sender, sender, parseMessage(sender, plainMessage, channelPlayers), plainMessage);
            }
            case STAFF -> {
                Set<Player> channelPlayers = staffChannelPlayers();
                yield formatter.staffMessage(sender, sender, parseMessage(sender, plainMessage, channelPlayers), plainMessage);
            }
            case PRIVATE -> {
                Player target = Bukkit.getPlayer(state.targetId());
                Set<Player> channelPlayers = target == null ? Set.of(sender) : Set.of(sender, target);
                Component message = parseMessage(sender, plainMessage, channelPlayers);
                yield target == null
                        ? formatPrivatePreviewWithOfflineTarget(sender, state.targetName(), message, plainMessage)
                        : formatter.privateMessage(sender, target, sender, message, plainMessage);
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
        Component message = parseMessage(sender, plainMessage, channelPlayers);
        plugin.getServer().getConsoleSender().sendMessage(formatter.publicMessage(sender,
                plugin.getServer().getConsoleSender(), message, plainMessage));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formatter.publicMessage(sender, player, message, plainMessage));
        }
        mentionService.notifyMessage(sender, plainMessage, channelPlayers);
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

}
