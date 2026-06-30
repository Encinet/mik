package org.encinet.mik.module.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.encinet.mik.Mik;
import org.encinet.mik.module.i18n.Language;
import org.encinet.mik.module.i18n.LanguageService;
import org.encinet.mik.module.i18n.Message;
import org.encinet.mik.module.menu.MenuBuilder;
import org.encinet.mik.module.menu.MenuItems;
import org.encinet.mik.module.menu.MenuNavigation;
import org.encinet.mik.util.PlayerDisplay;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class PvpMenuController implements Listener {

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    private static final int SETTINGS_MENU_SIZE = 9;
    private static final int ADMIN_MENU_SIZE = 54;
    private static final int ADMIN_PAGE_SIZE = 45;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private static final String ACTION_BACK_MAIN = "back:main";
    private static final String ACTION_BACK_ADMIN = "back:admin";
    private static final String ACTION_SETTING_PREFIX = "setting:";
    private static final String ACTION_ADMIN_PLAYER_PREFIX = "admin:player:";
    private static final String ACTION_ADMIN_PAGE_PREFIX = "admin:page:";

    private final JavaPlugin plugin;
    private final MenuNavigation menuNavigation;
    private final LanguageService languageService;
    private final PvpSettingsStore settingsStore;
    private final PvpCombatController combatController;
    private final NamespacedKey actionKey;

    PvpMenuController(JavaPlugin plugin, MenuNavigation menuNavigation, LanguageService languageService,
                      PvpSettingsStore settingsStore, PvpCombatController combatController) {
        this.plugin = plugin;
        this.menuNavigation = menuNavigation;
        this.languageService = languageService;
        this.settingsStore = settingsStore;
        this.combatController = combatController;
        this.actionKey = new NamespacedKey(plugin, "pvp_action");
    }

    void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player viewer)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!isPvpMenuTitle(title)) return;

        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        String action = MenuItems.readAction(item, actionKey);
        if (action == null) return;

        if (ACTION_BACK_MAIN.equals(action)) {
            menuNavigation.openMainMenu(viewer);
            return;
        }
        if (ACTION_BACK_ADMIN.equals(action)) {
            openAdminMenu(viewer, 0);
            return;
        }
        if (action.startsWith(ACTION_ADMIN_PAGE_PREFIX)) {
            if (!canManageOthers(viewer)) {
                denyManageOthers(viewer);
                return;
            }
            openAdminMenu(viewer, parseInt(action.substring(ACTION_ADMIN_PAGE_PREFIX.length()), 0));
            return;
        }
        if (action.startsWith(ACTION_ADMIN_PLAYER_PREFIX)) {
            if (!canManageOthers(viewer)) {
                denyManageOthers(viewer);
                return;
            }
            UUID targetId = parseUuid(action.substring(ACTION_ADMIN_PLAYER_PREFIX.length()));
            Player target = targetId != null ? Bukkit.getPlayer(targetId) : null;
            if (target == null) {
                viewer.sendMessage(mm(viewer, Message.PVP_PLAYER_NOT_FOUND_MM));
                openAdminMenu(viewer, 0);
                return;
            }
            if (event.isRightClick()) {
                openMenu(viewer, target);
            } else {
                setTargetPvp(viewer, target, !settingsStore.get(target.getUniqueId()).enabled());
                openAdminMenu(viewer, currentAdminPage(title));
            }
            return;
        }
        if (!action.startsWith(ACTION_SETTING_PREFIX)) return;

        String[] parts = action.substring(ACTION_SETTING_PREFIX.length()).split(":", 2);
        if (parts.length != 2) return;

        PvpSettingKey key = PvpSettingKey.fromId(parts[0]);
        if (key == null) return;

        UUID targetId = parseUuid(parts[1]);
        Player target = targetId != null ? Bukkit.getPlayer(targetId) : null;
        if (target == null) {
            viewer.closeInventory();
            viewer.sendMessage(mm(viewer, Message.PVP_PLAYER_NOT_FOUND_MM));
            return;
        }
        if (!viewer.getUniqueId().equals(target.getUniqueId()) && !canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }

        toggleSetting(viewer, target, key);
        openMenu(viewer, target);
    }

    void openMenu(Player player) {
        openMenu(player, player);
    }

    void openMenu(Player viewer, Player target) {
        boolean self = viewer.getUniqueId().equals(target.getUniqueId());
        if (!self && !canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }
        PvpSettings settings = settingsStore.get(target.getUniqueId());
        Component title = Component.text(self
                ? languageService.t(viewer, Message.PVP_MENU_TITLE)
                : languageService.t(viewer, Message.PVP_TARGET_MENU_TITLE, target.getName()), MenuItems.TITLE_COLOR);

        MenuBuilder.create(SETTINGS_MENU_SIZE, title)
                .item(0, sectionItem(viewer, target, self))
                .item(3, toggleItem(viewer, target, PvpSettingKey.ENABLED, settings.enabled()))
                .item(4, toggleItem(viewer, target, PvpSettingKey.PROTECT_MOBS, settings.protectMobs()))
                .item(5, toggleItem(viewer, target, PvpSettingKey.ALLOW_MOUNTED_DAMAGE, settings.allowMountedMobDamage()))
                .item(6, toggleItem(viewer, target, PvpSettingKey.ENABLE_ON_DEATH, settings.enableOnDeath()))
                .item(8, self ? backToMainItem(viewer) : backToAdminItem(viewer))
                .open(viewer);
    }

    void openAdminMenu(Player viewer, int requestedPage) {
        if (!canManageOthers(viewer)) {
            denyManageOthers(viewer);
            return;
        }
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .map(Player.class::cast)
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil(players.size() / (double) ADMIN_PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        MenuBuilder builder = MenuBuilder.create(ADMIN_MENU_SIZE,
                Component.text(languageService.t(viewer, Message.PVP_ADMIN_MENU_TITLE, page + 1, totalPages), MenuItems.TITLE_COLOR));
        int from = page * ADMIN_PAGE_SIZE;
        int to = Math.min(players.size(), from + ADMIN_PAGE_SIZE);
        for (int index = from; index < to; index++) {
            builder.item(index - from, adminPlayerItem(viewer, players.get(index)));
        }
        if (page > 0) {
            builder.item(45, pageItem(viewer, Material.ARROW, Message.PVP_PREV_PAGE, page - 1));
        }
        builder.item(49, backToMainItem(viewer));
        if (page < totalPages - 1) {
            builder.item(53, pageItem(viewer, Material.ARROW, Message.PVP_NEXT_PAGE, page + 1));
        }
        builder.open(viewer);
    }

    private void setTargetPvp(CommandSender sender, Player target, boolean enabled) {
        PvpSettings current = settingsStore.get(target.getUniqueId());
        settingsStore.save(target.getUniqueId(), current.withEnabled(enabled));
        combatController.onPvpStateSet(target.getUniqueId(), enabled);

        if (sender instanceof Player viewer) {
            viewer.sendMessage(languageService.rich(viewer, Message.PVP_SET_OTHER_RICH, NamedTextColor.GREEN,
                    org.encinet.mik.module.i18n.RichArg.component("player", PlayerDisplay.name(target, NamedTextColor.YELLOW), target.getName()),
                    org.encinet.mik.module.i18n.RichArg.component("state", Component.text(languageService.t(viewer, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                            enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY), languageService.t(viewer, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF))));
        }

        target.sendActionBar(mm(target, Message.PVP_SET_BY_STAFF_MM,
                languageService.t(target, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)));
    }

    private ItemStack sectionItem(Player viewer, Player target, boolean self) {
        List<Component> lore = new ArrayList<>();
        lore.add(stateLine(viewer, Message.PVP_STATE_LABEL, settingsStore.get(target.getUniqueId()).enabled()));
        lore.add(stateLine(viewer, Message.PVP_MOB_PROTECTION_LABEL, settingsStore.get(target.getUniqueId()).protectMobs()));
        lore.add(stateLine(viewer, Message.PVP_MOUNTED_DAMAGE_LABEL, settingsStore.get(target.getUniqueId()).allowMountedMobDamage()));
        lore.add(stateLine(viewer, Message.PVP_ENABLE_ON_DEATH_LABEL, settingsStore.get(target.getUniqueId()).enableOnDeath()));
        if (combatController.isCombatTagged(target.getUniqueId())) {
            lore.add(combatLine(viewer, target.getUniqueId()));
        }
        ItemStack item = self
                ? new ItemStack(Material.IRON_SWORD)
                : playerHead(target);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(self ? languageService.t(viewer, Message.PVP_MENU_TITLE) : target.getName(), NamedTextColor.GOLD));
        meta.lore(lore);
        MenuItems.style(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack toggleItem(Player viewer, Player target, PvpSettingKey settingKey, boolean enabled) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(viewer, enabled ? Message.CURRENT_ON : Message.CURRENT_OFF),
                enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(Component.text(languageService.t(viewer, settingKey.description()), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(viewer, Message.CLICK_SWITCH), NamedTextColor.YELLOW));
        return MenuItems.action(enabled ? settingKey.enabledMaterial() : settingKey.disabledMaterial(),
                Component.text(languageService.t(viewer, settingKey.label()), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                lore, actionKey, ACTION_SETTING_PREFIX + settingKey.id() + ":" + target.getUniqueId());
    }

    private ItemStack adminPlayerItem(Player viewer, Player target) {
        ItemStack item = playerHead(target);
        PvpSettings settings = settingsStore.get(target.getUniqueId());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(PlayerDisplay.name(target, settings.enabled() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(languageService.t(viewer, Message.PVP_ADMIN_PLAYER_LORE), NamedTextColor.GRAY));
        lore.add(stateLine(viewer, Message.PVP_STATE_LABEL, settings.enabled()));
        lore.add(stateLine(viewer, Message.PVP_MOB_PROTECTION_LABEL, settings.protectMobs()));
        lore.add(stateLine(viewer, Message.PVP_MOUNTED_DAMAGE_LABEL, settings.allowMountedMobDamage()));
        lore.add(stateLine(viewer, Message.PVP_ENABLE_ON_DEATH_LABEL, settings.enableOnDeath()));
        if (combatController.isCombatTagged(target.getUniqueId())) {
            lore.add(combatLine(viewer, target.getUniqueId()));
        }
        lore.add(Component.empty());
        lore.add(Component.text(languageService.t(viewer, Message.PVP_ADMIN_LEFT_CLICK), NamedTextColor.YELLOW));
        lore.add(Component.text(languageService.t(viewer, Message.PVP_ADMIN_RIGHT_CLICK), NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, org.bukkit.persistence.PersistentDataType.STRING,
                ACTION_ADMIN_PLAYER_PREFIX + target.getUniqueId());
        MenuItems.style(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(Player player) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack pageItem(Player viewer, Material material, Message label, int page) {
        return MenuItems.action(material,
                Component.text(languageService.t(viewer, label), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(viewer, Message.CLICK_OPEN), NamedTextColor.YELLOW)),
                actionKey, ACTION_ADMIN_PAGE_PREFIX + page);
    }

    private ItemStack backToMainItem(Player player) {
        return MenuItems.action(Material.ARROW, Component.text(languageService.t(player, Message.BACK_TO_MAIN), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)), actionKey, ACTION_BACK_MAIN);
    }

    private ItemStack backToAdminItem(Player player) {
        return MenuItems.action(Material.ARROW, Component.text(languageService.t(player, Message.PVP_BACK_ADMIN), NamedTextColor.GREEN),
                List.of(Component.text(languageService.t(player, Message.BACK_TO_MAIN_LORE), NamedTextColor.GRAY)), actionKey, ACTION_BACK_ADMIN);
    }

    private void toggleSetting(Player viewer, Player target, PvpSettingKey key) {
        PvpSettings current = settingsStore.get(target.getUniqueId());
        PvpSettings next = current.toggle(key);
        boolean self = viewer.getUniqueId().equals(target.getUniqueId());
        if (key == PvpSettingKey.ENABLED && !next.enabled() && self && combatController.isCombatTagged(target.getUniqueId())) {
            viewer.sendMessage(mm(viewer, Message.PVP_COMBAT_LOCKED_MM,
                    combatController.combatTagRemainingSeconds(target.getUniqueId())));
            return;
        }
        settingsStore.save(target.getUniqueId(), next);
        if (key == PvpSettingKey.ENABLED) {
            combatController.onPvpStateSet(target.getUniqueId(), next.enabled());
            if (!viewer.getUniqueId().equals(target.getUniqueId())) {
                target.sendActionBar(mm(target, Message.PVP_SET_BY_STAFF_MM,
                        languageService.t(target, next.enabled() ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF)));
            }
        }
    }

    private boolean isPvpMenuTitle(String title) {
        return languageService.titleMatches(Message.PVP_MENU_TITLE, title)
                || titleMatchesTargetMenu(title)
                || titleMatchesAdminMenu(title);
    }

    private int currentAdminPage(String title) {
        for (Language language : Language.values()) {
            if (!title.startsWith(staticTitlePrefix(languageService.t(language, Message.PVP_ADMIN_MENU_TITLE, 1, 1), "1"))) {
                continue;
            }
            Matcher matcher = FIRST_NUMBER.matcher(title);
            return matcher.find() ? Math.max(0, parseInt(matcher.group(), 1) - 1) : 0;
        }
        return 0;
    }

    private boolean titleMatchesTargetMenu(String title) {
        for (Language language : Language.values()) {
            if (title.startsWith(staticTitlePrefix(languageService.t(language, Message.PVP_TARGET_MENU_TITLE, ""), ""))) {
                return true;
            }
        }
        return false;
    }

    private boolean titleMatchesAdminMenu(String title) {
        for (Language language : Language.values()) {
            if (title.startsWith(staticTitlePrefix(languageService.t(language, Message.PVP_ADMIN_MENU_TITLE, 1, 1), "1"))) {
                return true;
            }
        }
        return false;
    }

    private String staticTitlePrefix(String sample, String marker) {
        int index = marker.isEmpty() ? sample.length() : sample.indexOf(marker);
        return index < 0 ? sample : sample.substring(0, index);
    }

    private Component stateLine(CommandSender sender, Message label, boolean enabled) {
        return Component.text()
                .append(Component.text(t(sender, label) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, enabled ? Message.PVP_STATE_ON : Message.PVP_STATE_OFF),
                        enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                .build();
    }

    private Component combatLine(CommandSender sender, UUID playerId) {
        return Component.text()
                .append(Component.text(t(sender, Message.PVP_COMBAT_TAG_LABEL) + ": ", NamedTextColor.GRAY))
                .append(Component.text(t(sender, Message.PVP_COMBAT_TAG_VALUE,
                        combatController.combatTagRemainingSeconds(playerId)), NamedTextColor.RED))
                .build();
    }

    private Language senderLanguage(CommandSender sender) {
        if (sender instanceof Player player) {
            return languageService.language(player);
        }
        return Language.DEFAULT;
    }

    private String t(CommandSender sender, Message message, Object... args) {
        return languageService.t(senderLanguage(sender), message, args);
    }

    private Component mm(Player player, Message message, Object... args) {
        return MINI_MESSAGE.deserialize(languageService.t(player, message, args));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean canManageOthers(Player player) {
        return player.hasPermission("group." + Mik.GROUP_HELPER);
    }

    private void denyManageOthers(Player player) {
        player.closeInventory();
        player.sendMessage(mm(player, Message.PVP_NO_PERMISSION_MM));
    }
}
