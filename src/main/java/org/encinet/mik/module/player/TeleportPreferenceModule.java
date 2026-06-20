package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportPreferenceModule implements Listener {

    private static final String MENU_TITLE = "被传送设置";
    private static final int MENU_SIZE = 9;
    private static final String ACTION_BACK_MAIN = "back:main";
    private static final String STAFF_TELEPORT_BYPASS_PERMISSION = "group.helper";
    private static final Set<String> TP_COMMANDS = Set.of("tp", "teleport", "minecraft:tp", "minecraft:teleport");
    private static final boolean DEFAULT_ALLOW_BEING_TELEPORTED = true;
    private static final boolean DEFAULT_BLOCK_TELEPORTS_WHILE_AFK = false;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final MenuNavigation menuNavigation;
    private final NamespacedKey actionKey;
    private final Map<UUID, TeleportSettings> settingsCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingTeleports = new ConcurrentHashMap<>();

    private File settingsFile;
    private YamlConfiguration settingsData;

    public TeleportPreferenceModule(JavaPlugin plugin, AfkService afkService, MenuNavigation menuNavigation) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.menuNavigation = menuNavigation;
        this.actionKey = new NamespacedKey(plugin, "teleport_preference_action");
    }

    public void enable() {
        settingsFile = new File(plugin.getDataFolder(), "teleport-preferences.yml");
        if (!settingsFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().severe("Failed to create plugin data folder.");
                }
                if (!settingsFile.createNewFile()) {
                    plugin.getLogger().warning("teleport-preferences.yml already exists but was not visible during setup.");
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create teleport-preferences.yml: " + e.getMessage());
            }
        }
        settingsData = YamlConfiguration.loadConfiguration(settingsFile);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("TeleportPreferenceModule enabled");
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

        TeleportSettings settings = getSettings(player.getUniqueId());
        TeleportSettings next = settings.toggle(settingKey);
        settingsCache.put(player.getUniqueId(), next);
        saveSettings(player.getUniqueId(), next);
        openMenu(player);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String[] args = event.getMessage().substring(1).trim().split("\\s+");
        if (args.length < 3) return;

        String cmd = args[0].toLowerCase(Locale.ROOT);
        if (!TP_COMMANDS.contains(cmd)) return;

        String victimArg = args[1];
        if (victimArg.startsWith("@")) return;

        Player sender = event.getPlayer();
        Player victim = Bukkit.getPlayerExact(victimArg);
        if (victim != null && !victim.equals(sender)) {
            if (shouldDenyTeleport(sender, victim)) {
                event.setCancelled(true);
                sender.sendMessage(Component.text()
                        .append(Component.text(victim.getName(), NamedTextColor.YELLOW))
                        .append(Component.text(" 当前拒绝被普通玩家传送", NamedTextColor.RED))
                        .build());
                return;
            }
            pendingTeleports.put(victim.getUniqueId(), sender.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) return;

        Player targetPlayer = event.getPlayer();
        String senderName = pendingTeleports.remove(targetPlayer.getUniqueId());

        if (senderName != null) {
            targetPlayer.sendActionBar(Component.text("你被 " + senderName + " 传送到了这里", NamedTextColor.AQUA));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        settingsCache.remove(playerId);
        pendingTeleports.remove(playerId);
    }

    public void openMenu(Player player) {
        TeleportSettings settings = getSettings(player.getUniqueId());
        MenuBuilder.create(MENU_SIZE, Component.text(MENU_TITLE, MenuItems.TITLE_COLOR))
                .item(0, sectionItem())
                .item(3, toggleItem(SettingKey.ALLOW_BEING_TELEPORTED, settings.allowBeingTeleported()))
                .item(4, toggleItem(SettingKey.BLOCK_TELEPORTS_WHILE_AFK, settings.blockTeleportsWhileAfk()))
                .item(8, backToMainItem())
                .open(player);
    }

    public String summary(UUID playerId) {
        TeleportSettings settings = getSettings(playerId);
        if (!settings.allowBeingTeleported()) {
            return "拒绝普通玩家传送";
        }
        if (settings.blockTeleportsWhileAfk()) {
            return "允许传送，挂机时拒绝";
        }
        return "允许普通玩家传送";
    }

    private boolean shouldDenyTeleport(Player sender, Player victim) {
        if (sender.hasPermission(STAFF_TELEPORT_BYPASS_PERMISSION)) {
            return false;
        }
        TeleportSettings settings = getSettings(victim.getUniqueId());
        return !settings.allowBeingTeleported()
                || settings.blockTeleportsWhileAfk() && afkService.isAfk(victim.getUniqueId());
    }

    private ItemStack sectionItem() {
        return MenuItems.item(Material.SHIELD, Component.text("被传送设置", NamedTextColor.GOLD),
                List.of(Component.text("需要隐私保护时再开启限制", NamedTextColor.GRAY)));
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

    private TeleportSettings getSettings(UUID playerId) {
        return settingsCache.computeIfAbsent(playerId, this::loadSettings);
    }

    private TeleportSettings loadSettings(UUID playerId) {
        String path = playerId.toString();
        return new TeleportSettings(
                loadBoolean(path, "allow-being-teleported", DEFAULT_ALLOW_BEING_TELEPORTED),
                loadBoolean(path, "block-teleports-while-afk", DEFAULT_BLOCK_TELEPORTS_WHILE_AFK)
        );
    }

    private boolean loadBoolean(String path, String key, boolean fallback) {
        String fullPath = path + "." + key;
        if (settingsData.contains(fullPath)) {
            return settingsData.getBoolean(fullPath, fallback);
        }
        return fallback;
    }

    private void saveSettings(UUID playerId, TeleportSettings settings) {
        String path = playerId.toString();
        settingsData.set(path + ".allow-being-teleported", settings.allowBeingTeleported());
        settingsData.set(path + ".block-teleports-while-afk", settings.blockTeleportsWhileAfk());
        try {
            settingsData.save(settingsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save teleport preferences for " + playerId + ": " + e.getMessage());
        }
    }

    private record TeleportSettings(
            boolean allowBeingTeleported,
            boolean blockTeleportsWhileAfk
    ) {
        TeleportSettings toggle(SettingKey key) {
            return switch (key) {
                case ALLOW_BEING_TELEPORTED -> new TeleportSettings(!allowBeingTeleported, blockTeleportsWhileAfk);
                case BLOCK_TELEPORTS_WHILE_AFK -> new TeleportSettings(allowBeingTeleported, !blockTeleportsWhileAfk);
            };
        }
    }

    private enum SettingKey {
        ALLOW_BEING_TELEPORTED("allow-being-teleported", "接受普通传送", "关闭后普通玩家不能用 /tp 把你传送走", Material.ENDER_PEARL, Material.GRAY_DYE),
        BLOCK_TELEPORTS_WHILE_AFK("block-teleports-while-afk", "挂机时拒绝传送", "挂机时普通玩家不能用 /tp 把你传送走", Material.SHIELD, Material.GRAY_DYE);

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
