package org.encinet.mik.module.chat;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.chat.mention.MentionService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.util.NameMetaRenderer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ChatModule implements Listener {

    private static final String STAFF_PERMISSION = "group." + Mik.GROUP_HELPER;
    private static final ZoneId CHAT_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss zzzz");

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final MentionService mentionService;
    private final ChatMessageParser messageParser = new ChatMessageParser();
    private final Map<UUID, ChatChannelState> channelStates = new HashMap<>();
    private final Map<UUID, UUID> lastPrivatePartner = new HashMap<>();

    private LuckPerms luckPerms;

    public ChatModule(JavaPlugin plugin, MentionService mentionService, LanguageService languageService) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.mentionService = mentionService;
    }

    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found; chat prefix and suffix rendering will use defaults.");
        } else {
            luckPerms = provider.getProvider();
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
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatRoute(AsyncChatEvent event) {
        Player sender = event.getPlayer();
        ChatChannelState state = channelStates.getOrDefault(sender.getUniqueId(), ChatChannelState.publicChannel());

        switch (state.channel()) {
            case PUBLIC -> routePublic(event, sender);
            case STAFF -> routeStaff(event, sender);
            case PRIVATE -> routePrivate(event, sender, state);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        channelStates.remove(playerId);
        lastPrivatePartner.remove(playerId);
        clearPrivateChannelsTargeting(event.getPlayer());
    }

    private void routePublic(AsyncChatEvent event, Player sender) {
        event.message(parseMessage(sender, event.message(), playersIn(event.viewers())));
        event.renderer((source, sourceDisplayName, message, viewer) -> formatPublicMessage(source, message));
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
        event.message(parseMessage(sender, event.message(), channelPlayers));
        event.renderer((source, sourceDisplayName, message, viewer) -> formatStaffMessage(source, message));
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
        event.message(parseMessage(sender, event.message(), channelPlayers));
        touchPrivatePartners(sender, target);
        event.renderer((source, sourceDisplayName, message, viewer) -> formatPrivateMessage(source, target, viewer, message));
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
        touchPrivatePartners(sender, target);
        Set<Player> channelPlayers = Set.of(sender, target);
        Component message = parseMessage(sender, plainMessage, channelPlayers);
        sender.sendMessage(formatPrivateMessage(sender, target, sender, message));
        target.sendMessage(formatPrivateMessage(sender, target, target, message));
        plugin.getServer().getConsoleSender().sendMessage(formatPrivateMessage(sender, target,
                plugin.getServer().getConsoleSender(), message));
        mentionService.notifyPrivateMessage(sender, plainMessage, target);
    }

    private void sendStaffCommand(Player sender, String plainMessage) {
        if (!sender.hasPermission(STAFF_PERMISSION)) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            return;
        }

        Set<Player> channelPlayers = staffChannelPlayers();
        Component message = parseMessage(sender, plainMessage, channelPlayers);
        Component rendered = formatStaffMessage(sender, message);
        plugin.getServer().getConsoleSender().sendMessage(rendered);
        for (Player player : channelPlayers) {
            player.sendMessage(rendered);
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
        sendTemporaryPrivateCommand(sender, target.getName(), plainMessage);
    }

    private Component formatPublicMessage(Player sender, Component message) {
        return basePlayerLine(sender, message);
    }

    private Component formatStaffMessage(Player sender, Component message) {
        return Component.text("[STAFF] ", NamedTextColor.GOLD)
                .append(basePlayerLine(sender, message));
    }

    private Component formatPrivateMessage(Player sender, Player target, Audience viewer, Component message) {
        return Component.text(privateLabel(viewer) + " ", NamedTextColor.LIGHT_PURPLE)
                .append(ChatDisplayRenderer.playerName(sender))
                .append(Component.text(" -> ", NamedTextColor.DARK_GRAY))
                .append(ChatDisplayRenderer.playerName(target))
                .append(Component.text(" » ", NamedTextColor.GOLD))
                .append(timeHoveredMessage(message));
    }

    private String privateLabel(Audience viewer) {
        if (viewer instanceof Player player) {
            return languageService.t(player, Message.CHAT_PRIVATE_LABEL);
        }
        return languageService.t(Language.DEFAULT, Message.CHAT_PRIVATE_LABEL);
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

    private Component basePlayerLine(Player sender, Component message) {
        return Component.text()
                .append(metaComponent(sender, true))
                .append(ChatDisplayRenderer.playerName(sender))
                .append(metaComponent(sender, false))
                .append(Component.text(" »", NamedTextColor.GOLD))
                .append(timeHoveredMessage(message))
                .build();
    }

    private Component timeHoveredMessage(Component message) {
        Component hover = Component.text(ZonedDateTime.now(CHAT_TIME_ZONE).format(CHAT_TIME_FORMAT), NamedTextColor.GRAY);
        return Component.space()
                .append(message.colorIfAbsent(NamedTextColor.WHITE))
                .hoverEvent(HoverEvent.showText(hover));
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
