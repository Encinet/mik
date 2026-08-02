package org.encinet.mik.module.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.module.afk.AfkService;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.i18n.RichArg;
import org.encinet.mik.module.menu.FloatingMenuDefinition;
import org.encinet.mik.module.menu.FloatingMenuLayouts;
import org.encinet.mik.module.menu.FloatingMenus;
import org.encinet.mik.util.PlayerDisplay;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TeleportPreferenceModule implements Listener {

    private static final String STAFF_TELEPORT_BYPASS_PERMISSION = "group.helper";
    private static final Set<String> TP_COMMANDS = Set.of("tp", "teleport", "minecraft:tp", "minecraft:teleport");
    private static final boolean DEFAULT_ALLOW_BEING_TELEPORTED = true;
    private static final boolean DEFAULT_BLOCK_TELEPORTS_WHILE_AFK = false;

    private final JavaPlugin plugin;
    private final AfkService afkService;
    private final LanguageService languageService;
    private final Map<UUID, TeleportSettings> settingsCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingTeleports = new ConcurrentHashMap<>();

    private File settingsFile;
    private YamlConfiguration settingsData;

    public TeleportPreferenceModule(JavaPlugin plugin, AfkService afkService, LanguageService languageService) {
        this.plugin = plugin;
        this.afkService = afkService;
        this.languageService = languageService;
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
                sender.sendMessage(languageService.rich(sender, Message.TELEPORT_DENIED, NamedTextColor.RED,
                        RichArg.component("player", PlayerDisplay.name(victim, NamedTextColor.YELLOW), victim.getName())));
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
            targetPlayer.sendActionBar(Component.text(languageService.t(targetPlayer, Message.TELEPORT_MOVED_HERE, senderName), NamedTextColor.AQUA));
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
        FloatingMenuDefinition.Builder menu = FloatingMenuDefinition.screen(
                        "teleport-settings",
                        Component.text(languageService.t(player, Message.TELEPORT_MENU_TITLE),
                                NamedTextColor.DARK_PURPLE))
                .layout(FloatingMenuLayouts.actions(3));
        for (SettingKey key : SettingKey.values()) {
            boolean enabled = switch (key) {
                case ALLOW_BEING_TELEPORTED -> settings.allowBeingTeleported();
                case BLOCK_TELEPORTS_WHILE_AFK -> settings.blockTeleportsWhileAfk();
            };
            menu.toggle("setting:" + key.name().toLowerCase(Locale.ROOT),
                            enabled, key.enabledMaterial(), key.disabledMaterial(),
                            toggleLabel(player, key, enabled))
                    .primary((p, handle) -> toggle(p, key));
        }
        menu.back(
                        Component.text(languageService.t(player, Message.BACK_TO_MAIN),
                                NamedTextColor.GREEN));
        FloatingMenus.present(player, menu.build());
    }

    private void toggle(Player player, SettingKey settingKey) {
        TeleportSettings next = getSettings(player.getUniqueId()).toggle(settingKey);
        settingsCache.put(player.getUniqueId(), next);
        saveSettings(player.getUniqueId(), next);
        openMenu(player);
    }

    public String summary(Player player) {
        TeleportSettings settings = getSettings(player.getUniqueId());
        if (!settings.allowBeingTeleported()) {
            return languageService.t(player, Message.TELEPORT_SUMMARY_DENY);
        }
        if (settings.blockTeleportsWhileAfk()) {
            return languageService.t(player, Message.TELEPORT_SUMMARY_AFK);
        }
        return languageService.t(player, Message.TELEPORT_SUMMARY_ALLOW);
    }

    private boolean shouldDenyTeleport(Player sender, Player victim) {
        if (sender.hasPermission(STAFF_TELEPORT_BYPASS_PERMISSION)) {
            return false;
        }
        TeleportSettings settings = getSettings(victim.getUniqueId());
        return !settings.allowBeingTeleported()
                || settings.blockTeleportsWhileAfk() && afkService.isAfk(victim.getUniqueId());
    }


    private Component toggleLabel(Player player, SettingKey settingKey, boolean enabled) {
        return Component.text(languageService.t(player, settingKey.label()),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .append(Component.newline())
                .append(Component.text(languageService.t(player,
                                enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
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
        ALLOW_BEING_TELEPORTED("allow-being-teleported", Message.TELEPORT_ALLOW, Message.TELEPORT_ALLOW_DESC, Material.ENDER_PEARL, Material.GRAY_DYE),
        BLOCK_TELEPORTS_WHILE_AFK("block-teleports-while-afk", Message.TELEPORT_BLOCK_AFK, Message.TELEPORT_BLOCK_AFK_DESC, Material.SHIELD, Material.GRAY_DYE);

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
