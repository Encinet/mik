package org.encinet.mik.module.communication;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
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
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

public class MentionModule implements Listener {

    private static final String MENU_TITLE = "提及提醒";
    private static final int MENU_SIZE = 9;
    private static final String ACTION_BACK_MAIN = "back:main";
    private static final boolean DEFAULT_ALERTS = true;
    private static final boolean DEFAULT_SOUND = true;
    private static final boolean DEFAULT_ACTION_BAR = true;
    private static final boolean DEFAULT_MUTE_WHILE_AFK = false;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final MenuNavigation menuNavigation;
    private final NamespacedKey actionKey;
    private final Map<UUID, MentionSettings> settingsCache = new HashMap<>();

    private File settingsFile;
    private YamlConfiguration settingsData;

    public MentionModule(JavaPlugin plugin, AfkService afkService, MenuNavigation menuNavigation) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.menuNavigation = menuNavigation;
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
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("MentionModule enabled");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!MENU_TITLE.equals(title)) return;

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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatMention(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        UUID senderId = event.getPlayer().getUniqueId();
        String senderName = event.getPlayer().getName();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getUniqueId().equals(senderId) || !shouldNotifyMention(player, message)) {
                    continue;
                }
                MentionSettings settings = getSettings(player.getUniqueId());
                if (settings.sound()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.35F);
                }
                if (settings.actionBar()) {
                    player.sendActionBar(Component.text()
                            .append(Component.text(senderName, NamedTextColor.YELLOW))
                            .append(Component.text(" 提及了你", NamedTextColor.AQUA))
                            .build());
                }
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        settingsCache.remove(event.getPlayer().getUniqueId());
    }

    public void openMenu(Player player) {
        MentionSettings settings = getSettings(player.getUniqueId());
        MenuBuilder.create(MENU_SIZE, Component.text(MENU_TITLE, MenuItems.TITLE_COLOR))
                .item(0, sectionItem())
                .item(2, toggleItem(SettingKey.ALERTS, settings.alerts()))
                .item(3, toggleItem(SettingKey.SOUND, settings.sound()))
                .item(4, toggleItem(SettingKey.ACTION_BAR, settings.actionBar()))
                .item(5, toggleItem(SettingKey.MUTE_WHILE_AFK, settings.muteWhileAfk()))
                .item(8, backToMainItem())
                .open(player);
    }

    public String summary(UUID playerId) {
        MentionSettings settings = getSettings(playerId);
        if (!settings.alerts()) {
            return "已关闭提醒";
        }
        List<String> enabled = new ArrayList<>();
        if (settings.sound()) enabled.add("声音");
        if (settings.actionBar()) enabled.add("屏幕提示");
        String suffix = settings.muteWhileAfk() ? "，挂机时静音" : "";
        return enabled.isEmpty() ? "提醒开启" + suffix : String.join(" + ", enabled) + suffix;
    }

    private ItemStack sectionItem() {
        return MenuItems.item(Material.BELL, Component.text("提及提醒", NamedTextColor.GOLD),
                List.of(Component.text("别人提及你时的反馈", NamedTextColor.GRAY)));
    }

    private ItemStack toggleItem(SettingKey settingKey, boolean enabled) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(enabled ? "当前: 开启" : "当前: 关闭", enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(settingKey.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("点击切换", NamedTextColor.YELLOW));
        return MenuItems.action(enabled ? settingKey.enabledMaterial() : settingKey.disabledMaterial(),
                Component.text(settingKey.label(), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY), lore, actionKey, settingKey.id());
    }

    private ItemStack backToMainItem() {
        return MenuItems.action(Material.ARROW, Component.text("返回主菜单", NamedTextColor.GREEN),
                List.of(Component.text("回到主菜单", NamedTextColor.GRAY)), actionKey, ACTION_BACK_MAIN);
    }

    private boolean shouldNotifyMention(Player target, String message) {
        MentionSettings settings = getSettings(target.getUniqueId());
        if (!settings.alerts()) return false;
        if (settings.muteWhileAfk() && afkService.isAfk(target.getUniqueId())) return false;
        Pattern mentionPattern = Pattern.compile("(?iu)(?<![\\p{Alnum}_])" + Pattern.quote(target.getName()) + "(?![\\p{Alnum}_])");
        return mentionPattern.matcher(message).find();
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
        ALERTS("mention-alerts", "提及提醒总开关", "关闭后不再接收提及提醒", Material.LIME_DYE, Material.GRAY_DYE),
        SOUND("mention-sound", "提醒声音", "被提及时播放一声提示音", Material.NOTE_BLOCK, Material.GRAY_DYE),
        ACTION_BAR("mention-actionbar", "屏幕提示", "被提及时在屏幕下方显示提示", Material.PAPER, Material.GRAY_DYE),
        MUTE_WHILE_AFK("mute-mentions-while-afk", "挂机静音", "挂机时自动屏蔽提及提醒", Material.CLOCK, Material.GRAY_DYE);

        private final String id;
        private final String label;
        private final String description;
        private final Material enabledMaterial;
        private final Material disabledMaterial;

        SettingKey(String id, String label, String description, Material enabledMaterial, Material disabledMaterial) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.enabledMaterial = enabledMaterial;
            this.disabledMaterial = disabledMaterial;
        }

        String id() {
            return id;
        }

        String label() {
            return label;
        }

        String description() {
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
