package org.encinet.mik.module.chat.mention;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class MentionService implements Listener {

    private static final int MENU_SIZE = 9;
    private static final String ACTION_BACK_MAIN = "back:main";
    private static final boolean DEFAULT_ALERTS = true;
    private static final boolean DEFAULT_SOUND = true;
    private static final boolean DEFAULT_ACTION_BAR = true;
    private static final boolean DEFAULT_MUTE_WHILE_AFK = false;
    private static final Pattern AT_ALL_PATTERN = Pattern.compile("(?iu)(?<![\\p{Alnum}_])@all(?![\\p{Alnum}_])");

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final MenuNavigation menuNavigation;
    private final LanguageService languageService;
    private final Function<Player, Component> senderDisplayRenderer;
    private final NamespacedKey actionKey;
    private final Map<UUID, MentionSettings> settingsCache = new HashMap<>();

    private File settingsFile;
    private YamlConfiguration settingsData;

    public MentionService(JavaPlugin plugin, AfkService afkService, MenuNavigation menuNavigation,
                          LanguageService languageService, Function<Player, Component> senderDisplayRenderer) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.menuNavigation = menuNavigation;
        this.languageService = languageService;
        this.senderDisplayRenderer = senderDisplayRenderer;
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
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

    public void notifyPrivateMessage(Player sender, String message, Player target) {
        notifyMentions(sender, message, Set.of(target));
    }

    public void notifyMessage(Player sender, String message, Set<Player> recipients) {
        notifyMentions(sender, message, recipients);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatMention(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.originalMessage());
        notifyMentions(event.getPlayer(), message, playersIn(event.viewers()));
    }

    private void notifyMentions(Player sender, String message, Set<Player> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        UUID senderId = sender.getUniqueId();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            boolean allMention = sender.hasPermission("group." + Mik.GROUP_MANAGER)
                    && AT_ALL_PATTERN.matcher(message).find();
            Component senderDisplay = senderDisplayRenderer.apply(sender);
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
        settingsCache.remove(event.getPlayer().getUniqueId());
    }

    private boolean mentionsPlayer(Player target, String message) {
        return message.toLowerCase(Locale.ROOT).contains(target.getName().toLowerCase(Locale.ROOT));
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

    private ItemStack sectionItem(Player player) {
        return MenuItems.item(Material.BELL,
                Component.text(languageService.t(player, Message.MENTION_MENU_TITLE), NamedTextColor.GOLD),
                List.of());
    }

    private ItemStack toggleItem(Player player, SettingKey settingKey, boolean enabled) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(player, enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(languageService.t(player, settingKey.description()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(player, Message.CLICK_SWITCH), NamedTextColor.YELLOW));
        return MenuItems.action(enabled ? settingKey.enabledMaterial() : settingKey.disabledMaterial(),
                Component.text(languageService.t(player, settingKey.label()), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore, actionKey, settingKey.id());
    }

    private ItemStack backToMainItem(Player player) {
        return MenuItems.action(Material.ARROW,
                Component.text(languageService.t(player, Message.BACK_TO_MAIN), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)),
                actionKey, ACTION_BACK_MAIN);
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
