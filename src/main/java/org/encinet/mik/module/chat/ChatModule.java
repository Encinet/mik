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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.encinet.mik.Mik;
import org.encinet.mik.module.chat.mention.MentionService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;
import org.encinet.mik.util.NameMetaRenderer;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatModule implements Listener {

    private static final String STAFF_PERMISSION = "group." + Mik.GROUP_HELPER;
    private static final ZoneId CHAT_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss zzzz");
    private static final int SETTINGS_MENU_SIZE = 18;
    private static final String ACTION_BACK_MAIN = "back:main";
    private static final String ACTION_MENTION_PREFIX = "mention:";
    private static final String ACTION_DELAY_PREFIX = "delay:";

    private final JavaPlugin plugin;
    private final LanguageService languageService;
    private final MentionService mentionService;
    private final ChatSettingsStore settingsStore;
    private final MenuNavigation menuNavigation;
    private final ChatMessageParser messageParser = new ChatMessageParser();
    private final Map<UUID, ChatChannelState> channelStates = new HashMap<>();
    private final Map<UUID, UUID> lastPrivatePartner = new HashMap<>();
    private final Map<UUID, List<BukkitTask>> pendingDelayedMessages = new ConcurrentHashMap<>();
    private final NamespacedKey settingsActionKey;

    private LuckPerms luckPerms;

    public ChatModule(JavaPlugin plugin, MentionService mentionService, LanguageService languageService,
                      ChatSettingsStore settingsStore, MenuNavigation menuNavigation) {
        this.plugin = plugin;
        this.languageService = languageService;
        this.mentionService = mentionService;
        this.settingsStore = settingsStore;
        this.menuNavigation = menuNavigation;
        this.settingsActionKey = new NamespacedKey(plugin, "chat_settings_action");
    }

    public void enable() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            plugin.getLogger().warning("LuckPerms not found; chat prefix and suffix rendering will use defaults.");
        } else {
            luckPerms = provider.getProvider();
        }

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

        if (queueDelayedMessage(sender, plainMessage, state)) {
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
        cancelDelayedMessages(playerId);
        clearPrivateChannelsTargeting(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!isSettingsMenuTitle(title)) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String action = MenuItems.readAction(item, settingsActionKey);
        if (action == null) return;

        if (ACTION_BACK_MAIN.equals(action)) {
            menuNavigation.openMainMenu(player);
            return;
        }
        if (action.startsWith(ACTION_MENTION_PREFIX)) {
            ChatMentionSetting setting = ChatMentionSetting.fromId(action.substring(ACTION_MENTION_PREFIX.length()));
            if (setting != null) {
                settingsStore.toggleMention(player.getUniqueId(), setting);
                openSettingsMenu(player);
            }
            return;
        }
        if (action.startsWith(ACTION_DELAY_PREFIX)) {
            ChatDelayOption delay = ChatDelayOption.fromInput(action.substring(ACTION_DELAY_PREFIX.length()));
            if (delay != null) {
                settingsStore.setDelay(player.getUniqueId(), delay);
                openSettingsMenu(player);
            }
        }
    }

    public void openSettingsMenu(Player player) {
        ChatSettingsStore.ChatSettings settings = settingsStore.get(player.getUniqueId());
        MenuBuilder.create(SETTINGS_MENU_SIZE, Component.text(languageService.t(player, Message.CHAT_SETTINGS_MENU_TITLE), MenuItems.TITLE_COLOR))
                .item(0, sectionItem(player, Material.BELL, Message.MENTION_MENU_TITLE))
                .item(2, mentionToggleItem(player, settings, ChatMentionSetting.ALERTS))
                .item(3, mentionToggleItem(player, settings, ChatMentionSetting.SOUND))
                .item(4, mentionToggleItem(player, settings, ChatMentionSetting.ACTION_BAR))
                .item(5, mentionToggleItem(player, settings, ChatMentionSetting.MUTE_WHILE_AFK))
                .item(9, sectionItem(player, Material.CLOCK, Message.CHAT_DELAY_SECTION))
                .item(11, delayItem(player, settings, ChatDelayOption.OFF, Material.GRAY_DYE, Message.CHAT_DELAY_OFF))
                .item(12, delayItem(player, settings, ChatDelayOption.THREE_SECONDS, Material.LIME_DYE, Message.CHAT_DELAY_3S))
                .item(13, delayItem(player, settings, ChatDelayOption.FIVE_SECONDS, Material.YELLOW_DYE, Message.CHAT_DELAY_5S))
                .item(14, delayItem(player, settings, ChatDelayOption.SEVEN_SECONDS, Material.ORANGE_DYE, Message.CHAT_DELAY_7S))
                .item(17, backToMainItem(player))
                .open(player);
    }

    public List<Component> settingsSummary(Player player) {
        return List.of(
                Component.text(mentionService.summary(player), NamedTextColor.GRAY),
                Component.text(languageService.t(player, Message.CHAT_DELAY_CURRENT, settingsStore.get(player.getUniqueId()).delay().id()), NamedTextColor.GRAY)
        );
    }

    private ItemStack sectionItem(Player player, Material material, Message title) {
        return MenuItems.item(material,
                Component.text(languageService.t(player, title), NamedTextColor.GOLD),
                List.of());
    }

    private ItemStack mentionToggleItem(Player player, ChatSettingsStore.ChatSettings settings, ChatMentionSetting setting) {
        boolean enabled = mentionEnabled(settings, setting);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(player, enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(languageService.t(player, setting.description()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.CLICK_SWITCH), NamedTextColor.YELLOW));
        return MenuItems.action(enabled ? setting.enabledMaterial() : setting.disabledMaterial(),
                Component.text(languageService.t(player, setting.label()), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore, settingsActionKey, ACTION_MENTION_PREFIX + setting.id());
    }

    private boolean mentionEnabled(ChatSettingsStore.ChatSettings settings, ChatMentionSetting setting) {
        return switch (setting) {
            case ALERTS -> settings.mentionAlerts();
            case SOUND -> settings.mentionSound();
            case ACTION_BAR -> settings.mentionActionBar();
            case MUTE_WHILE_AFK -> settings.mentionMuteWhileAfk();
        };
    }

    private ItemStack delayItem(Player player, ChatSettingsStore.ChatSettings settings, ChatDelayOption option,
                                Material material, Message label) {
        boolean selected = settings.delay() == option;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(player, selected ? Message.CURRENT_ON : Message.CURRENT_OFF),
                selected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(languageService.t(player, Message.CHAT_DELAY_OPTION_DESC), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.CLICK_SET), NamedTextColor.YELLOW));
        return MenuItems.action(material,
                Component.text(languageService.t(player, label), selected ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore, settingsActionKey, ACTION_DELAY_PREFIX + option.id());
    }

    private ItemStack backToMainItem(Player player) {
        return MenuItems.action(Material.ARROW,
                Component.text(languageService.t(player, Message.BACK_TO_MAIN), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)),
                settingsActionKey, ACTION_BACK_MAIN);
    }

    private boolean isSettingsMenuTitle(String title) {
        for (Language language : Language.values()) {
            if (languageService.t(language, Message.CHAT_SETTINGS_MENU_TITLE).equals(title)) {
                return true;
            }
        }
        return false;
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
        if (queueDelayedMessage(sender, plainMessage, ChatChannelState.privateChannel(target.getUniqueId(), target.getName()))) {
            touchPrivatePartners(sender, target);
            return;
        }
        sendPrivateMessage(sender, target, plainMessage);
    }

    private void sendPrivateMessage(Player sender, Player target, String plainMessage) {
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

        if (queueDelayedMessage(sender, plainMessage, ChatChannelState.staff())) {
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
        if (queueDelayedMessage(sender, plainMessage, ChatChannelState.privateChannel(target.getUniqueId(), target.getName()))) {
            return;
        }
        sendPrivateMessage(sender, target, plainMessage);
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

    private boolean queueDelayedMessage(Player sender, String plainMessage, ChatChannelState state) {
        ChatDelayOption option = settingsStore.get(sender.getUniqueId()).delay();
        if (option.delaySeconds() <= 0) {
            return false;
        }

        UUID playerId = sender.getUniqueId();
        BukkitTask[] taskHolder = new BukkitTask[1];
        taskHolder[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeDelayedTask(playerId, taskHolder[0]);
            Player onlineSender = Bukkit.getPlayer(playerId);
            if (onlineSender == null) {
                return;
            }
            sendDelayedMessage(onlineSender, plainMessage, state);
        }, option.delaySeconds() * 20L);

        pendingDelayedMessages
                .computeIfAbsent(playerId, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(taskHolder[0]);
        sendActionBarMessage(sender, Component.text(languageService.t(sender, Message.CHAT_DELAY_QUEUED, option.id()), NamedTextColor.YELLOW));
        sendDelayedPreview(sender, plainMessage, state);
        return true;
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
                yield formatPublicMessage(sender, parseMessage(sender, plainMessage, channelPlayers));
            }
            case STAFF -> {
                Set<Player> channelPlayers = staffChannelPlayers();
                yield formatStaffMessage(sender, parseMessage(sender, plainMessage, channelPlayers));
            }
            case PRIVATE -> {
                Player target = Bukkit.getPlayer(state.targetId());
                Set<Player> channelPlayers = target == null ? Set.of(sender) : Set.of(sender, target);
                Component message = parseMessage(sender, plainMessage, channelPlayers);
                yield target == null
                        ? formatPrivatePreviewWithOfflineTarget(sender, state.targetName(), message)
                        : formatPrivateMessage(sender, target, sender, message);
            }
        };
    }

    private Component formatPrivatePreviewWithOfflineTarget(Player sender, String targetName, Component message) {
        return Component.text(privateLabel(sender) + " ", NamedTextColor.LIGHT_PURPLE)
                .append(ChatDisplayRenderer.playerName(sender))
                .append(Component.text(" -> ", NamedTextColor.DARK_GRAY))
                .append(Component.text(targetName == null ? "?" : targetName, NamedTextColor.WHITE))
                .append(Component.text(" » ", NamedTextColor.GOLD))
                .append(timeHoveredMessage(message));
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
        Component rendered = formatPublicMessage(sender, message);
        plugin.getServer().getConsoleSender().sendMessage(rendered);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(rendered);
        }
        mentionService.notifyMessage(sender, plainMessage, channelPlayers);
    }

    private void cancelDelayedMessages(Player player) {
        int count = cancelDelayedMessages(player.getUniqueId());
        Message message = count > 0 ? Message.CHAT_DELAY_CANCELLED : Message.CHAT_DELAY_NOTHING_TO_CANCEL;
        NamedTextColor color = count > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        player.sendMessage(Component.text(languageService.t(player, message), color));
    }

    private int cancelDelayedMessages(UUID playerId) {
        List<BukkitTask> tasks = pendingDelayedMessages.remove(playerId);
        if (tasks == null || tasks.isEmpty()) {
            return 0;
        }
        int count = 0;
        synchronized (tasks) {
            for (BukkitTask task : tasks) {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                    count += 1;
                }
            }
        }
        return count;
    }

    private void removeDelayedTask(UUID playerId, BukkitTask task) {
        List<BukkitTask> tasks = pendingDelayedMessages.get(playerId);
        if (tasks == null) {
            return;
        }
        tasks.remove(task);
        if (tasks.isEmpty()) {
            pendingDelayedMessages.remove(playerId, tasks);
        }
    }

    private void sendSystemMessage(Player player, Component message) {
        if (Bukkit.isPrimaryThread()) {
            player.sendMessage(message);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(message);
            }
        });
    }

    private void sendActionBarMessage(Player player, Component message) {
        if (Bukkit.isPrimaryThread()) {
            player.sendActionBar(message);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.sendActionBar(message);
            }
        });
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
