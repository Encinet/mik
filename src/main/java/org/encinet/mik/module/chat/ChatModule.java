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
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;
import org.encinet.mik.util.NameMetaRenderer;
import org.encinet.mik.util.PlayerDisplay;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class ChatModule implements Listener {

    private static final int MENU_SIZE = 9;
    private static final String ACTION_BACK_MAIN = "back:main";
    private static final boolean DEFAULT_ALERTS = true;
    private static final boolean DEFAULT_SOUND = true;
    private static final boolean DEFAULT_ACTION_BAR = true;
    private static final boolean DEFAULT_MUTE_WHILE_AFK = false;
    private static final String STAFF_PERMISSION = "mik.staff";
    private static final ZoneId CHAT_TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter CHAT_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss zzzz");
    private static final Pattern AT_ALL_PATTERN = Pattern.compile("(?iu)(?<![\\p{Alnum}_])@all(?![\\p{Alnum}_])");

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final MenuNavigation menuNavigation;
    private final LanguageService languageService;
    private final NamespacedKey actionKey;
    private final ChatMessageParser messageParser = new ChatMessageParser();
    private final Map<UUID, ChatChannelState> channelStates = new HashMap<>();
    private final Map<UUID, UUID> lastPrivatePartner = new HashMap<>();
    private final Map<UUID, MentionSettings> settingsCache = new HashMap<>();

    private File settingsFile;
    private YamlConfiguration settingsData;
    private LuckPerms luckPerms;

    public ChatModule(JavaPlugin plugin, AfkService afkService, MenuNavigation menuNavigation, LanguageService languageService) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.menuNavigation = menuNavigation;
        this.languageService = languageService;
        this.actionKey = new NamespacedKey(plugin, "mention_action");
    }

    public void enable() {
        settingsFile = new File(plugin.getDataFolder(), "mention.yml");
        if (!settingsFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().severe("Failed to create plugin data folder.");
                }
                if (!settingsFile.createNewFile()) {
                    plugin.getLogger().warning("mention.yml already exists but was not visible during setup.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create mention.yml: " + e.getMessage());
            }
        }
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);

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
                    }).build(), languageService.t(Language.DEFAULT, Message.CHAT_STAFF_COMMAND_DESCRIPTION), List.of("staffchat"));

            commands.register(Commands.literal("chat")
                    .executes(ctx -> {
                        Player player = requirePlayer(ctx.getSource().getSender());
                        if (player != null) {
                            switchPublic(player);
                        }
                        return Command.SINGLE_SUCCESS;
                    }).build(), languageService.t(Language.DEFAULT, Message.CHAT_PUBLIC_COMMAND_DESCRIPTION), List.of("global"));

            commands.register(Commands.literal("msg")
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
                                            sendPrivateCommand(player,
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
        Component parsedMessage = messageParser.parse(sender, event.message(),
                languageService.t(sender, Message.CHAT_ITEM_EMPTY_HOVER),
                languageService.t(sender, Message.CHAT_MENTION_ALL_HOVER),
                languageService.t(sender, Message.CHAT_BILIBILI_HOVER));
        event.message(parsedMessage);

        switch (state.channel()) {
            case PUBLIC -> event.renderer((source, sourceDisplayName, message, viewer) ->
                    formatPublicMessage(source, message));
            case STAFF -> routeStaff(event, sender);
            case PRIVATE -> routePrivate(event, sender, state);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatMention(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        Set<Player> recipients = playerViewers(event.viewers());
        notifyMentions(event.getPlayer(), message, recipients);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!isMenuTitle(title)) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String action = MenuItems.readAction(item, actionKey);
        if (action == null) return;

        if (ACTION_BACK_MAIN.equals(action)) {
            menuNavigation.openMainMenu(player);
            return;
        }

        SettingKey settingKey = SettingKey.fromId(action);
        if (settingKey == null) return;

        MentionSettings settings = getSettings(player.getUniqueId());
        MentionSettings next = settings.toggle(settingKey);
        settingsCache.put(player.getUniqueId(), next);
        saveSettings(player.getUniqueId(), next);
        openMenu(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        channelStates.remove(playerId);
        lastPrivatePartner.remove(playerId);
        settingsCache.remove(playerId);
        clearPrivateChannelsTargeting(event.getPlayer());
    }

    public void openMenu(Player player) {
        MentionSettings settings = getSettings(player.getUniqueId());
        MenuBuilder.create(MENU_SIZE, Component.text(languageService.t(player, Message.MENTION_MENU_TITLE), MenuItems.TITLE_COLOR))
                .item(0, sectionItem(player))
                .item(2, toggleItem(player, SettingKey.ALERTS, settings.alerts()))
                .item(3, toggleItem(player, SettingKey.SOUND, settings.sound()))
                .item(4, toggleItem(player, SettingKey.ACTION_BAR, settings.actionBar()))
                .item(5, toggleItem(player, SettingKey.MUTE_WHILE_AFK, settings.muteWhileAfk()))
                .item(8, backToMainItem(player))
                .open(player);
    }

    public String summary(Player player) {
        MentionSettings settings = getSettings(player.getUniqueId());
        if (!settings.alerts()) {
            return languageService.t(player, Message.MENTION_SUMMARY_DISABLED);
        }
        List<String> enabled = new ArrayList<>();
        if (settings.sound()) enabled.add(languageService.t(player, Message.MENTION_SUMMARY_SOUND));
        if (settings.actionBar()) enabled.add(languageService.t(player, Message.MENTION_SUMMARY_ACTION_BAR));
        String suffix = settings.muteWhileAfk() ? languageService.t(player, Message.MENTION_SUMMARY_AFK_SUFFIX) : "";
        return enabled.isEmpty()
                ? languageService.t(player, Message.MENTION_SUMMARY_ENABLED) + suffix
                : String.join(" + ", enabled) + suffix;
    }

    private void routeStaff(AsyncChatEvent event, Player sender) {
        if (!sender.hasPermission(STAFF_PERMISSION)) {
            channelStates.put(sender.getUniqueId(), ChatChannelState.publicChannel());
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_STAFF_PERMISSION_MISSING), NamedTextColor.RED));
            event.renderer((source, sourceDisplayName, message, viewer) -> formatPublicMessage(source, message));
            return;
        }
        Set<Audience> viewers = event.viewers();
        viewers.clear();
        viewers.add(plugin.getServer().getConsoleSender());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(STAFF_PERMISSION)) {
                viewers.add(player);
            }
        }
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
        viewers.add(sender);
        viewers.add(target);
        touchPrivatePartners(sender, target);
        event.renderer((source, sourceDisplayName, message, viewer) -> formatPrivateMessage(source, target, message));
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
        channelStates.put(sender.getUniqueId(), ChatChannelState.privateChannel(target.getUniqueId(), target.getName()));
        touchPrivatePartners(sender, target);
        sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_ENTER, target.getName()), NamedTextColor.GREEN));
    }

    private void sendPrivateCommand(Player sender, String targetName, String plainMessage) {
        Player target = findOnlinePlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_TARGET_NOT_FOUND, targetName), NamedTextColor.RED));
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(Component.text(languageService.t(sender, Message.CHAT_PRIVATE_SELF), NamedTextColor.RED));
            return;
        }
        channelStates.put(sender.getUniqueId(), ChatChannelState.privateChannel(target.getUniqueId(), target.getName()));
        touchPrivatePartners(sender, target);
        Component message = messageParser.parse(sender, plainMessage,
                languageService.t(sender, Message.CHAT_ITEM_EMPTY_HOVER),
                languageService.t(sender, Message.CHAT_MENTION_ALL_HOVER),
                languageService.t(sender, Message.CHAT_BILIBILI_HOVER));
        Component rendered = formatPrivateMessage(sender, target, message);
        sender.sendMessage(rendered);
        target.sendMessage(rendered);
        notifyMentions(sender, plainMessage, Set.of(target));
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
        sendPrivateCommand(sender, target.getName(), plainMessage);
    }

    private Component formatPublicMessage(Player sender, Component message) {
        return basePlayerLine(sender, message);
    }

    private Component formatStaffMessage(Player sender, Component message) {
        return Component.text("[STAFF] ", NamedTextColor.GOLD)
                .append(basePlayerLine(sender, message));
    }

    private Component formatPrivateMessage(Player sender, Player target, Component message) {
        return Component.text("[MSG] ", NamedTextColor.LIGHT_PURPLE)
                .append(PlayerDisplay.name(sender, usernameColor(sender)))
                .append(Component.text(" -> ", NamedTextColor.DARK_GRAY))
                .append(PlayerDisplay.name(target, usernameColor(target)))
                .append(Component.text(" » ", NamedTextColor.GOLD))
                .append(timeHoveredMessage(message));
    }

    private Component basePlayerLine(Player sender, Component message) {
        return Component.text()
                .append(metaComponent(sender, true))
                .append(PlayerDisplay.name(sender, usernameColor(sender)))
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

    private TextColor usernameColor(Player player) {
        return player.hasPermission("group." + Mik.GROUP_MEMBER) ? NamedTextColor.WHITE : NamedTextColor.YELLOW;
    }

    private CachedMetaData cachedMetaData(Player player) {
        if (luckPerms == null) {
            return null;
        }
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        return user == null ? null : user.getCachedData().getMetaData();
    }

    private void notifyMentions(Player sender, String message, Set<Player> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        boolean allMention = sender.hasPermission("group." + Mik.GROUP_MANAGER)
                && AT_ALL_PATTERN.matcher(message).find();
        UUID senderId = sender.getUniqueId();
        Component senderDisplay = PlayerDisplay.name(sender, usernameColor(sender));
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : recipients) {
                if (player.getUniqueId().equals(senderId)) {
                    continue;
                }
                MentionSettings settings = getSettings(player.getUniqueId());
                if (!settings.alerts()) {
                    continue;
                }
                if (settings.muteWhileAfk() && afkService.isAfk(player.getUniqueId())) {
                    continue;
                }
                if (!allMention && !mentionsPlayer(player, message)) {
                    continue;
                }
                if (settings.sound()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.35F);
                }
                if (settings.actionBar()) {
                    player.sendActionBar(Component.text()
                            .append(senderDisplay)
                            .append(Component.text(languageService.t(player, Message.MENTION_ACTION_BAR_TEXT), NamedTextColor.AQUA))
                            .build());
                }
            }
        });
    }

    private boolean mentionsPlayer(Player target, String message) {
        return message.toLowerCase(Locale.ROOT).contains(target.getName().toLowerCase(Locale.ROOT));
    }

    private Set<Player> playerViewers(Set<Audience> viewers) {
        Set<Player> players = new HashSet<>();
        for (Audience viewer : viewers) {
            if (viewer instanceof Player player) {
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

    private ItemStack sectionItem(Player player) {
        return MenuItems.item(Material.BELL,
                Component.text(languageService.t(player, Message.MENTION_MENU_TITLE), NamedTextColor.GOLD),
                List.of());
    }

    private ItemStack toggleItem(Player player, SettingKey settingKey, boolean enabled) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(player, enabled ? Message.CURRENT_ON : Message.CURRENT_OFF), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(languageService.t(player, settingKey.description()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.CLICK_SWITCH), NamedTextColor.YELLOW));
        return MenuItems.action(enabled ? settingKey.enabledMaterial() : settingKey.disabledMaterial(),
                Component.text(languageService.t(player, settingKey.label()), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY), lore, actionKey, settingKey.id());
    }

    private ItemStack backToMainItem(Player player) {
        return MenuItems.action(Material.ARROW, Component.text(languageService.t(player, Message.BACK_TO_MAIN), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)), actionKey, ACTION_BACK_MAIN);
    }

    private boolean isMenuTitle(String title) {
        for (Language language : Language.values()) {
            if (languageService.t(language, Message.MENTION_MENU_TITLE).equals(title)) {
                return true;
            }
        }
        return false;
    }

    private MentionSettings getSettings(UUID playerId) {
        return settingsCache.computeIfAbsent(playerId, this::loadSettings);
    }

    private MentionSettings loadSettings(UUID playerId) {
        String path = playerId.toString();
        return new MentionSettings(
                loadBoolean(path, "mention-alerts", DEFAULT_ALERTS),
                loadBoolean(path, "mention-sound", DEFAULT_SOUND),
                loadBoolean(path, "mention-actionbar", DEFAULT_ACTION_BAR),
                loadBoolean(path, "mute-mentions-while-afk", DEFAULT_MUTE_WHILE_AFK)
        );
    }

    private boolean loadBoolean(String path, String key, boolean fallback) {
        String fullPath = path + "." + key;
        if (settingsData.contains(fullPath)) {
            return settingsData.getBoolean(fullPath, fallback);
        }
        return fallback;
    }

    private void saveSettings(UUID playerId, MentionSettings settings) {
        String path = playerId.toString();
        settingsData.set(path + ".mention-alerts", settings.alerts());
        settingsData.set(path + ".mention-sound", settings.sound());
        settingsData.set(path + ".mention-actionbar", settings.actionBar());
        settingsData.set(path + ".mute-mentions-while-afk", settings.muteWhileAfk());
        try {
            settingsData.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save mention settings for " + playerId + ": " + e.getMessage());
        }
    }

    private record MentionSettings(
            boolean alerts,
            boolean sound,
            boolean actionBar,
            boolean muteWhileAfk
    ) {
        MentionSettings toggle(SettingKey key) {
            return switch (key) {
                case ALERTS -> new MentionSettings(!alerts, sound, actionBar, muteWhileAfk);
                case SOUND -> new MentionSettings(alerts, !sound, actionBar, muteWhileAfk);
                case ACTION_BAR -> new MentionSettings(alerts, sound, !actionBar, muteWhileAfk);
                case MUTE_WHILE_AFK -> new MentionSettings(alerts, sound, actionBar, !muteWhileAfk);
            };
        }
    }

    private enum SettingKey {
        ALERTS("mention-alerts", Message.MENTION_ALERTS, Message.MENTION_ALERTS_DESC, Material.LIME_DYE, Material.GRAY_DYE),
        SOUND("mention-sound", Message.MENTION_SOUND, Message.MENTION_SOUND_DESC, Material.NOTE_BLOCK, Material.GRAY_DYE),
        ACTION_BAR("mention-actionbar", Message.MENTION_ACTION_BAR, Message.MENTION_ACTION_BAR_DESC, Material.PAPER, Material.GRAY_DYE),
        MUTE_WHILE_AFK("mute-mentions-while-afk", Message.MENTION_MUTE_AFK, Message.MENTION_MUTE_AFK_DESC, Material.CLOCK, Material.GRAY_DYE);

        private final String id;
        private final Message label;
        private final Message description;
        private final Material enabledMaterial;
        private final Material disabledMaterial;

        SettingKey(String id, Message label, Message description, Material enabledMaterial, Material disabledMaterial) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.enabledMaterial = enabledMaterial;
            this.disabledMaterial = disabledMaterial;
        }

        String id() {
            return id;
        }

        Message label() {
            return label;
        }

        Message description() {
            return description;
        }

        Material enabledMaterial() {
            return enabledMaterial;
        }

        Material disabledMaterial() {
            return disabledMaterial;
        }

        static SettingKey fromId(String id) {
            for (SettingKey key : values()) {
                if (key.id.equals(id)) {
                    return key;
                }
            }
            return null;
        }
    }
}
